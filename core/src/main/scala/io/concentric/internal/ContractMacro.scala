package io.concentric.internal

import scala.quoted.*
import io.concentric.*
import io.concentric.annotations.*

/**
 * Scala 3 compile-time macro — derives a [[Contract]][T] for any annotated
 * case class.
 *
 * Entry point: [[Contract.derived]].
 *
 * Design notes
 * ────────────
 * Every private helper accepts `(using q: Quotes)` as its *first* implicit
 * parameter and declares its reflect-based argument types as `q.reflect.Symbol`
 * etc.  This is the correct Scala 3 pattern for dependent method types in
 * macros; it ensures the `Quotes` context is always in scope when the compiler
 * resolves `quotes.reflect.*` types.
 *
 * `Expr(...)` calls all carry explicit type parameters (`Expr[String](name)`,
 * `Expr[Boolean](flag)`, …) to prevent the compiler from selecting an
 * ambiguous `ToExpr` instance.
 *
 * Parameter types are obtained via `TypeRepr.of[T].memberType(primaryConstructor)`
 * which yields a `MethodType` — this avoids the `@experimental` `Symbol.info`.
 *
 * The constructor lambda (`Map[String, Any] => T`) is built entirely inside
 * `mkConstructFn` using a single nested `${ ... }` splice so that all
 * `reflect.Symbol` / `reflect.TypeRepr` values remain in the same `Quotes`
 * context and `paramType.asType match { case '[…] => … }` executes in a
 * proper macro body, never inside a plain Scala closure.
 *
 * `@include` support
 * ──────────────────
 * Fields annotated with `@include` have their inner type's fields *flattened*
 * into the parent's wire format.  In `derivedImpl` such params are expanded
 * into multiple `FieldMeta` entries (one per inner field).  `mkConstructFn`
 * and `mkToRawFn` are aware of the expansion and generate the correct
 * round-trip code.
 *
 * `@discriminator` support
 * ────────────────────────
 * `@discriminator("type", left = "cat", right = "dog")` on an `Either[A,B]`
 * field overrides the default `{"left":…}` / `{"right":…}` envelope with a
 * flat map containing a discriminator key.  The annotation is detected in
 * `mkFieldMeta`, which then generates a custom decoder expression.
 */
object ContractMacro:

  inline def derived[T <: Product]: Contract[T] = ${ derivedImpl[T] }
  inline def derivedOpen[T <: Product]: OpenContract[T] = ${ derivedOpenImpl[T] }
  // Called by Contract.AspectOf.derived — T is the aspect class, S is the source.
  inline def derivedAspect[T <: Product, S <: Product]: Contract[T] = ${ derivedAspectImpl[T, S] }
  // Called by IsAspectOf.derived — checks @aspectOf[S] on A at compile time.
  def isAspectOfImpl[S: Type, A: Type](using Quotes): Expr[IsAspectOf[S, A]] =
    import quotes.reflect.*
    val aSym = TypeRepr.of[A].typeSymbol
    val sSym = TypeRepr.of[S].typeSymbol

    val hasAnnot = aSym.annotations.exists { annot =>
      annot.tpe match
        case AppliedType(fn, List(sType)) =>
          fn.typeSymbol.fullName == "io.concentric.aspectOf" &&
          (sType =:= TypeRepr.of[S])
        case _ => false
    }

    if !hasAnnot then
      report.errorAndAbort(
        s"'${aSym.name}' is not a structural aspect of '${sSym.name}': " +
        s"annotate it with @aspectOf[${sSym.name}]"
      )

    '{ IsAspectOf.singleton.asInstanceOf[IsAspectOf[S, A]] }

  // ── Top-level derivation ──────────────────────────────────────────────────

  private def derivedImpl[T <: Product: Type](using Quotes): Expr[Contract[T]] =
    import quotes.reflect.*
    // Detect @aspectOf[S] on T and dispatch to aspect derivation if present.
    val sym = TypeRepr.of[T].typeSymbol
    val aspectSTypeOpt: Option[TypeRepr] =
      sym.annotations
        .find { annot =>
          annot.tpe match
            case AppliedType(fn, List(_)) => fn.typeSymbol.fullName == "io.concentric.aspectOf"
            case _                        => false
        }
        .map { annot =>
          annot.tpe match
            case AppliedType(_, List(sType)) => sType
            case _ => report.errorAndAbort("@aspectOf requires exactly one type argument")
        }

    aspectSTypeOpt match
      case Some(sType) =>
        derivedAspectFromTypeRepr[T](sType)
      case None =>
        derivedExpr[T, Contract[T]](forceOpen = false) { (metasExpr, isOpenExpr, ctorExpr, toRawExpr, cvExpr) =>
          '{ new ContractImpl[T]($metasExpr, $isOpenExpr, $ctorExpr, $toRawExpr, $cvExpr) }
        }

  private def derivedOpenImpl[T <: Product: Type](using Quotes): Expr[OpenContract[T]] =
    derivedExpr[T, OpenContract[T]](forceOpen = true) { (metasExpr, isOpenExpr, ctorExpr, toRawExpr, cvExpr) =>
      '{ new OpenContractImpl[T](new ContractImpl[T]($metasExpr, $isOpenExpr, $ctorExpr, $toRawExpr, $cvExpr)) }
    }

  private def derivedExpr[T <: Product: Type, C](using Quotes)(
    forceOpen: Boolean
  )(
    build: (
      Expr[List[FieldMeta]],
      Expr[Boolean],
      Expr[Map[String, Any] => T],
      Expr[T => RawObject],
      Expr[List[T => List[String]]]
    ) => Expr[C]
  ): Expr[C] =
    import quotes.reflect.*

    val tRepr   = TypeRepr.of[T]
    val sym     = tRepr.typeSymbol

    if !sym.flags.is(Flags.Case) then
      report.errorAndAbort(
        s"Contract.derived[${sym.name}]: '${sym.name}' must be a case class"
      )

    val companion = sym.companionModule
    val params: List[Symbol] =
      sym.primaryConstructor.paramSymss.headOption.getOrElse(
        report.errorAndAbort(s"'${sym.name}' has no constructor parameters")
      )

    // Extract parameter types from the constructor's MethodType.
    val paramTypes: List[TypeRepr] =
      tRepr.memberType(sym.primaryConstructor) match
        case MethodType(_, types, _)  => types
        case PolyType(_, _, res)      =>
          res match
            case MethodType(_, types, _) => types
            case _ => report.errorAndAbort(s"'${sym.name}': unexpected constructor shape (PolyType)")
        case _ =>
          report.errorAndAbort(s"'${sym.name}': unexpected constructor type shape")

    val includeAnnotSym = TypeRepr.of[annotations.include].typeSymbol

    // Build flattened FieldMeta list — @include params are expanded.
    val metaEntries: List[Expr[FieldMeta]] =
      params.zip(paramTypes).zipWithIndex.flatMap { case ((p, pt), outerIdx) =>
        if p.annotations.exists(_.tpe.typeSymbol == includeAnnotSym) then
          // Expand: emit one FieldMeta per inner-type field
          val innerSym       = pt.typeSymbol
          val innerCompanion = innerSym.companionModule
          val innerParams: List[Symbol] =
            innerSym.primaryConstructor.paramSymss.headOption.getOrElse(List.empty)
          val innerTypes: List[TypeRepr] =
            pt.memberType(innerSym.primaryConstructor) match
              case MethodType(_, types, _) => types
              case _                        => List.empty
          innerParams.zip(innerTypes).zipWithIndex.map { case ((ip, ipt), j) =>
            mkFieldMeta(ip, ipt, j, innerCompanion)
          }
        else
          List(mkFieldMeta(p, pt, outerIdx, companion))
      }

    val isOpenExpr: Expr[Boolean]               = if forceOpen then Expr(true) else readOpenFlag(sym)
    val metasExpr:  Expr[List[FieldMeta]]       = Expr.ofList(metaEntries)
    val ctorExpr:   Expr[Map[String, Any] => T] = mkConstructFn[T](sym, params, paramTypes, companion)
    val toRawExpr:  Expr[T => RawObject]        = mkToRawFn[T](sym, params, paramTypes)
    val cvExpr:     Expr[List[T => List[String]]] = readContractValidators[T](sym)

    build(metasExpr, isOpenExpr, ctorExpr, toRawExpr, cvExpr)

  // ── Read @contract(open = …) from the class annotation ───────────────────

  private def readOpenFlag(using q: Quotes)(sym: q.reflect.Symbol): Expr[Boolean] =
    import q.reflect.*
    val contractSym = TypeRepr.of[contract].typeSymbol
    sym.annotations
      .find(_.tpe.typeSymbol == contractSym)
      .collect {
        case Apply(_, List(Literal(BooleanConstant(v))))                => v
        case Apply(_, List(NamedArg(_, Literal(BooleanConstant(v)))))   => v
      }
      .map(Expr[Boolean](_))
      .getOrElse(Expr[Boolean](false))

  // ── Read @validateContract(Array(classOf[V1], …)) from the class ─────────

  private def readContractValidators[T: Type](using q: Quotes)(
    sym: q.reflect.Symbol
  ): Expr[List[T => List[String]]] =
    import q.reflect.*

    val annotSym = TypeRepr.of[annotations.validateContract].typeSymbol

    // Reuse the same class-literal collector used for @validateWith.
    def collectClassLiterals(t: Term): List[TypeRepr] = t match
      case TypeApply(Ident("classOf"), List(typeArg)) => List(typeArg.tpe)
      case Repeated(elems, _)                         => elems.flatMap(collectClassLiterals)
      case Apply(fun, args)                            => collectClassLiterals(fun) ++ args.flatMap(collectClassLiterals)
      case Typed(inner, _)                             => collectClassLiterals(inner)
      case Block(_, expr)                              => collectClassLiterals(expr)
      case _                                           => Nil

    val validatorTypeReprs: List[TypeRepr] =
      sym.annotations
        .find(_.tpe.typeSymbol == annotSym)
        .toList
        .flatMap(collectClassLiterals)

    val parts: List[Expr[T => List[String]]] = validatorTypeReprs.map { tpe =>
      val className = Expr[String](tpe.typeSymbol.fullName)
      '{ io.concentric.internal.ValidatorHelper.makeContractValidator[T]($className) }
    }

    parts.foldRight('{ Nil: List[T => List[String]] }) { (elem, acc) =>
      '{ $elem :: $acc }
    }

  // ── Read @discriminator(key, left, right) ────────────────────────────────

  private def readDiscriminatorAnnot(using q: Quotes)(
    annots: List[q.reflect.Term]
  ): Option[(String, String, String)] =
    import q.reflect.*
    annots.find(_.tpe.typeSymbol == TypeRepr.of[annotations.discriminator].typeSymbol).flatMap {
      case Apply(_, args) =>
        val strArgs: List[String] = args.flatMap {
          case Literal(StringConstant(s))               => Some(s)
          case NamedArg(_, Literal(StringConstant(s))) => Some(s)
          case _                                        => None
        }
        strArgs match
          case List(key, leftVal, rightVal) => Some((key, leftVal, rightVal))
          case _                            => None
      case _ => None
    }

  // ── Aspect derivation ────────────────────────────────────────────────────
  //
  // Derives Contract[T] where T is a structural aspect of source type S.
  // Called via Contract.AspectOf.derived — do not call directly.
  //
  // Constraint annotations are inherited from S's matching fields; T's own
  // annotations override S's for the same annotation type.  T's field types
  // determine optionality and decoding.
  //
  // We accept sTypeRepr: TypeRepr in the internal helper rather than a type
  // parameter S to keep the implementation uniform regardless of call site.

  private def derivedAspectFromTypeRepr[T <: Product: Type](using Quotes)(
    sTypeRepr: quotes.reflect.TypeRepr
  ): Expr[Contract[T]] =
    import quotes.reflect.*

    val sSym = sTypeRepr.typeSymbol

    if !sSym.flags.is(Flags.Case) then
      report.errorAndAbort(
        s"@aspectOf: source type '${sSym.name}' must be a case class. Got: ${sTypeRepr.show}"
      )

    // 1. Require Contract[S] or OpenContract[S] to be in scope — compile-time
    //    dependency check.  Both are valid sources for an aspect; we only need
    //    the source's field annotations, not its runtime contract instance.
    val hasSourceContract: Boolean = sTypeRepr.asType match
      case '[s] =>
        Expr.summon[Contract[s]].isDefined || Expr.summon[OpenContract[s]].isDefined

    if !hasSourceContract then
      report.errorAndAbort(
        s"@aspectOf[${sSym.name}]: no Contract[${sSym.name}] or OpenContract[${sSym.name}] found in scope. " +
        s"Ensure '${sSym.name}' derives Contract (or OpenContract) before defining this aspect."
      )

    val tRepr = TypeRepr.of[T]
    val tSym  = tRepr.typeSymbol

    if !tSym.flags.is(Flags.Case) then
      report.errorAndAbort(s"@aspectOf: '${tSym.name}' must be a case class")

    // 2. Collect S's constructor params and their annotations, keyed by name.
    val sParamAnnots: Map[String, List[Term]] =
      sSym.primaryConstructor.paramSymss.headOption
        .getOrElse(List.empty)
        .map(p => p.name -> p.annotations)
        .toMap

    // 3. T's constructor params and types.
    val tCompanion = tSym.companionModule
    val tParams: List[Symbol] =
      tSym.primaryConstructor.paramSymss.headOption.getOrElse(
        report.errorAndAbort(s"'${tSym.name}' has no constructor parameters")
      )
    val tParamTypes: List[TypeRepr] =
      tRepr.memberType(tSym.primaryConstructor) match
        case MethodType(_, types, _)              => types
        case PolyType(_, _, MethodType(_, ts, _)) => ts
        case _ => report.errorAndAbort(s"'${tSym.name}': unexpected constructor type shape")

    // 4. Read inherit/exclude args from the @aspectOf annotation on T.
    //
    // The annotation term has the shape:
    //   Apply(TypeApply(Select(New(<aspectOf type>), "<init>"), ...), List(<args>))
    // where args may include NamedArg("inherit", Literal(BooleanConstant(b))) and
    // NamedArg("exclude", <Seq expression>).
    def collectApplyArgs(term: Term): List[Term] =
      term match
        case Apply(inner, args)  => collectApplyArgs(inner) ++ args
        case TypeApply(inner, _) => collectApplyArgs(inner)
        case _                   => Nil

    def collectStrings(t: Term): List[String] = t match
      case Literal(StringConstant(s)) => List(s)
      case Apply(_, args)             => args.flatMap(collectStrings)
      case TypeApply(inner, _)        => collectStrings(inner)
      case Typed(inner, _)            => collectStrings(inner)
      case Inlined(_, _, inner)       => collectStrings(inner)
      case Repeated(elems, _)         => elems.flatMap(collectStrings)
      case Block(_, expr)             => collectStrings(expr)
      case _                          => Nil

    val aspectAnnotOpt: Option[Term] =
      tSym.annotations.find { a =>
        a.tpe match
          case AppliedType(fn, List(_)) => fn.typeSymbol.fullName == "io.concentric.aspectOf"
          case _                        => false
      }

    val (inheritAll: Boolean, excludeNames: List[String]) =
      aspectAnnotOpt.map { annot =>
        // When only some params are explicit (others use defaults), Scala 3 may
        // emit the annotation as a Block with let-bindings, e.g.:
        //   { val exclude$1 = Seq("x"); new aspectOf[S](defaultMethod, exclude = exclude$1) }
        // We unwrap the Block, build a name→term map from its ValDefs, and
        // resolve Idents through it before extracting inherit/exclude values.
        val (bindings: Map[String, Term], innerTerm: Term) = annot match
          case Block(stmts, expr) =>
            val defs = stmts.flatMap {
              case vd: ValDef => vd.rhs.toList.map(rhs => vd.name -> rhs)
              case _          => Nil
            }.toMap
            (defs, expr)
          case other => (Map.empty[String, Term], other)

        def resolve(t: Term): Term = t match
          case Ident(name) => bindings.getOrElse(name, t)
          case _           => t

        val allArgs = collectApplyArgs(innerTerm)

        // Named args take priority; fall back to positional for any not found.
        // Resolve Idents through the let-bindings before extracting values.
        val namedInherit: Option[Boolean] = allArgs.collectFirst {
          case NamedArg("inherit", v) => resolve(v) match
            case Literal(BooleanConstant(b)) => b
            case _                           => false
        }
        val namedExclude: Option[List[String]] = allArgs.collectFirst {
          case NamedArg("exclude", v) => collectStrings(resolve(v))
        }
        // Positional args (not wrapped in NamedArg), resolved through bindings.
        // Identify inherit by BooleanConstant literal; everything else is exclude.
        val positional = allArgs.flatMap {
          case NamedArg(_, _) => None
          case other          => Some(resolve(other))
        }
        val inherit = namedInherit.orElse(
          positional.collectFirst { case Literal(BooleanConstant(b)) => b }
        ).getOrElse(false)
        val exclude = namedExclude.orElse(
          positional.find { case Literal(BooleanConstant(_)) => false; case _ => true }
                    .map(collectStrings)
        ).getOrElse(Nil)
        (inherit, exclude)
      }.getOrElse((false, Nil))

    // 4a. `exclude` without `inherit = true` is an error.
    if excludeNames.nonEmpty && !inheritAll then
      report.errorAndAbort(
        s"@aspectOf[${sSym.name}] on '${tSym.name}': `exclude` is only valid when `inherit = true`. " +
        s"Remove the `exclude` list or add `inherit = true`."
      )

    if inheritAll then
      val sFieldNames = sParamAnnots.keySet
      // 4b. Exclude names must all exist in the source.
      val badExclude = excludeNames.filterNot(sFieldNames.contains)
      if badExclude.nonEmpty then
        report.errorAndAbort(
          s"@aspectOf[${sSym.name}] on '${tSym.name}': `exclude` contains names not found in source: " +
          badExclude.sorted.mkString(", ")
        )
      // 4c. Every source field must be either declared in T or listed in exclude.
      val tFieldNames = tParams.map(_.name).toSet
      val unaccounted = sFieldNames -- tFieldNames -- excludeNames.toSet
      if unaccounted.nonEmpty then
        report.errorAndAbort(
          s"@aspectOf[${sSym.name}] on '${tSym.name}' uses `inherit = true` but the following " +
          s"source fields are neither declared in the aspect nor listed in `exclude`: " +
          unaccounted.toList.sorted.mkString(", ") +
          s". Add them to the aspect class or include them in `exclude`."
        )

    // 4. Build FieldMeta list.
    //
    // For fields that exist in S: inherit S's constraint annotations; T's own
    // annotations override S's on a per-annotation-type basis.  Policy
    // annotations (@reserved, @internal, @immutable, @masked) are NOT
    // inherited — the aspect author explicitly controls access.
    //
    // For fields that do NOT exist in S: accepted as fresh fields using only
    // their own annotations.  When inherit = true, we've already verified above
    // that every source field is accounted for; extra fields on T are allowed.
    val policyAnnotSyms: Set[Symbol] = Set(
      TypeRepr.of[annotations.reserved].typeSymbol,
      TypeRepr.of[annotations.internal].typeSymbol,
      TypeRepr.of[annotations.immutable].typeSymbol,
      TypeRepr.of[annotations.masked].typeSymbol
    )

    val metaEntries: List[Expr[FieldMeta]] =
      tParams.zip(tParamTypes).zipWithIndex.map { case ((tp, tpt), idx) =>
        if sParamAnnots.contains(tp.name) then
          // Source field — merge annotations.
          val sAnnots     = sParamAnnots(tp.name)
          val tAnnots     = tp.annotations
          val tAnnotTypes = tAnnots.map(_.tpe.typeSymbol).toSet
          val merged      = sAnnots.filterNot { a =>
            policyAnnotSyms.contains(a.tpe.typeSymbol) ||
            tAnnotTypes.contains(a.tpe.typeSymbol)
          } ++ tAnnots
          mkFieldMetaWithAnnots(tp, tpt, idx, tCompanion, merged)
        else
          // New field not present in S — use only its own annotations.
          mkFieldMeta(tp, tpt, idx, tCompanion)
      }

    val metasExpr:  Expr[List[FieldMeta]]         = Expr.ofList(metaEntries)
    // Aspects always derive Contract (not OpenContract), so they are always closed.
    // There is no mechanism for an open aspect; @contract(open=true) is deprecated.
    val isOpenExpr: Expr[Boolean]                = Expr(false)
    val ctorExpr:   Expr[Map[String, Any] => T]  = mkConstructFn[T](tSym, tParams, tParamTypes, tCompanion)
    val toRawExpr:  Expr[T => RawObject]         = mkToRawFn[T](tSym, tParams, tParamTypes)
    val cvExpr:     Expr[List[T => List[String]]] = readContractValidators[T](tSym)

    '{ new ContractImpl[T]($metasExpr, $isOpenExpr, $ctorExpr, $toRawExpr, $cvExpr) }

  // Keep the typed entry point for potential direct call use.
  private def derivedAspectImpl[T <: Product: Type, S <: Product: Type](using Quotes): Expr[Contract[T]] =
    derivedAspectFromTypeRepr[T](quotes.reflect.TypeRepr.of[S])

  // ── Build FieldMeta for one constructor parameter ─────────────────────────

  private def mkFieldMeta(using q: Quotes)(
    param:     q.reflect.Symbol,
    paramType: q.reflect.TypeRepr,
    idx:       Int,
    companion: q.reflect.Symbol
  ): Expr[FieldMeta] =
    mkFieldMetaWithAnnots(param, paramType, idx, companion, param.annotations)

  private def mkFieldMetaWithAnnots(using q: Quotes)(
    param:     q.reflect.Symbol,
    paramType: q.reflect.TypeRepr,
    idx:       Int,
    companion: q.reflect.Symbol,
    annots:    List[q.reflect.Term]
  ): Expr[FieldMeta] =
    import q.reflect.*

    val name   = param.name

    // ── annotation helpers ──────────────────────────────────────────────────

    def hasA[A: Type]: Boolean =
      annots.exists(_.tpe.typeSymbol == TypeRepr.of[A].typeSymbol)

    def intArg[A: Type]: Option[Int] =
      annots.find(_.tpe.typeSymbol == TypeRepr.of[A].typeSymbol).flatMap {
        case Apply(_, List(Literal(IntConstant(v))))                    => Some(v)
        case Apply(_, List(NamedArg(_, Literal(IntConstant(v)))))       => Some(v)
        case _                                                           => None
      }

    def longArg[A: Type]: Option[Long] =
      annots.find(_.tpe.typeSymbol == TypeRepr.of[A].typeSymbol).flatMap {
        case Apply(_, List(Literal(LongConstant(v))))                   => Some(v)
        case Apply(_, List(Literal(IntConstant(v))))                    => Some(v.toLong)
        case Apply(_, List(NamedArg(_, Literal(LongConstant(v)))))      => Some(v)
        case Apply(_, List(NamedArg(_, Literal(IntConstant(v)))))       => Some(v.toLong)
        case _                                                           => None
      }

    def strArg[A: Type]: Option[String] =
      annots.find(_.tpe.typeSymbol == TypeRepr.of[A].typeSymbol).flatMap {
        case Apply(_, List(Literal(StringConstant(v))))                 => Some(v)
        case Apply(_, List(NamedArg(_, Literal(StringConstant(v)))))    => Some(v)
        case _                                                           => None
      }

    // ── structural flags ────────────────────────────────────────────────────

    val isOpt   = paramType <:< TypeRepr.of[Option[?]]
    val defName = s"$$lessinit$$greater$$default$$${idx + 1}"
    val hasDef  = companion.declarations.exists(_.name == defName)

    // ── policy / constraint values ──────────────────────────────────────────

    val isImmutable  = hasA[annotations.immutable]
    val isInternal   = hasA[annotations.internal]
    val isReserved   = hasA[annotations.reserved]
    val maskedOpt    = if hasA[annotations.masked] then Some(strArg[annotations.masked].getOrElse("***")) else None
    val isNonEmpty   = hasA[annotations.nonEmpty]
    val minLenOpt    = intArg[annotations.minLength]
    val maxLenOpt    = intArg[annotations.maxLength]
    val minNumOpt    = longArg[annotations.min]
    val maxNumOpt    = longArg[annotations.max]
    val patternOpt        = strArg[annotations.pattern]
    val isEmail           = hasA[annotations.email]
    val isUrl             = hasA[annotations.url]
    val isUuid            = hasA[annotations.uuid]
    val isFuture          = hasA[annotations.future]
    val isPast            = hasA[annotations.past]
    val isPositive        = hasA[annotations.positive]
    val multipleOfOpt: Option[Double] =
      annots.find(_.tpe.typeSymbol == TypeRepr.of[annotations.multipleOf].typeSymbol).flatMap {
        case Apply(_, List(Literal(DoubleConstant(v))))                 => Some(v)
        case Apply(_, List(Literal(FloatConstant(v))))                  => Some(v.toDouble)
        case Apply(_, List(Literal(IntConstant(v))))                    => Some(v.toDouble)
        case Apply(_, List(NamedArg(_, Literal(DoubleConstant(v)))))    => Some(v)
        case Apply(_, List(NamedArg(_, Literal(FloatConstant(v)))))     => Some(v.toDouble)
        case Apply(_, List(NamedArg(_, Literal(IntConstant(v)))))       => Some(v.toDouble)
        case _                                                           => None
      }
    val extractPatternOpt = strArg[annotations.extract]

    // ── decoder: either @discriminator-custom or standard summon ─────────────

    val decoderExpr: Expr[Any => Option[Any]] =
      readDiscriminatorAnnot(annots) match
        case Some((discKey, leftVal, rightVal)) =>
          // Custom discriminated-union decoder for Either[A, B]
          paramType.asType match
            case '[Either[a, b]] =>
              val da = Expr.summon[RawDecoder[a]].getOrElse(
                report.errorAndAbort(
                  s"@discriminator on '$name': no RawDecoder[...] found for the Left branch. " +
                  "Provide: given RawDecoder[...] = ..."
                )
              )
              val db = Expr.summon[RawDecoder[b]].getOrElse(
                report.errorAndAbort(
                  s"@discriminator on '$name': no RawDecoder[...] found for the Right branch. " +
                  "Provide: given RawDecoder[...] = ..."
                )
              )
              val dkExpr = Expr(discKey)
              val lvExpr = Expr(leftVal)
              val rvExpr = Expr(rightVal)
              '{
                (v: Any) => v match
                  case m: Map[?, ?] =>
                    val sm    = m.asInstanceOf[Map[String, Any]]
                    val inner = sm - $dkExpr
                    sm.get($dkExpr) match
                      case Some(s: String) if s == $lvExpr =>
                        $da.decode(inner).map(a => (Left(a): Either[a, b])).asInstanceOf[Option[Any]]
                      case Some(s: String) if s == $rvExpr =>
                        $db.decode(inner).map(b => (Right(b): Either[a, b])).asInstanceOf[Option[Any]]
                      case _ => None
                  case _ => None
              }
            case _ =>
              report.errorAndAbort(
                s"@discriminator on '$name' requires the field type to be Either[A, B]"
              )

        case None =>
          // Standard: summon RawDecoder[FieldType] at compile time
          paramType.asType match
            case '[t] =>
              Expr.summon[RawDecoder[t]] match
                case Some(d) =>
                  '{ (v: Any) => $d.decode(v).asInstanceOf[Option[Any]] }
                case None =>
                  report.errorAndAbort(
                    s"No RawDecoder[${paramType.show}] instance for field '$name'. " +
                    "Provide: given RawDecoder[...] = ..."
                  )

    // ── @validateWith — collect Class<?>[] literals ─────────────────────────

    def collectClassLiterals(t: Term): List[TypeRepr] = t match
      case TypeApply(Ident("classOf"), List(typeArg)) => List(typeArg.tpe)
      case Repeated(elems, _)                         => elems.flatMap(collectClassLiterals)
      case Apply(fun, args)                           => collectClassLiterals(fun) ++ args.flatMap(collectClassLiterals)
      case Typed(inner, _)                            => collectClassLiterals(inner)
      case Block(_, expr)                             => collectClassLiterals(expr)
      case _                                          => Nil

    val validatorTypeReprs: List[TypeRepr] =
      annots
        .find(_.tpe.typeSymbol == TypeRepr.of[annotations.validateWith].typeSymbol)
        .toList
        .flatMap(collectClassLiterals)

    val validatorParts: List[Expr[Any => List[String]]] = validatorTypeReprs.map { tpe =>
      val className = Expr[String](tpe.typeSymbol.fullName)
      '{ io.concentric.internal.ValidatorHelper.make($className) }
    }

    val validatorsExpr: Expr[List[Any => List[String]]] =
      validatorParts.foldRight('{ Nil: List[Any => List[String]] }) { (elem, acc) =>
        '{ $elem :: $acc }
      }

    // ── nestedCollect & nestedSanitize ──────────────────────────────────────

    val noneCollect:  Expr[Option[(Any, FieldPath) => List[Violation]]] = '{ None }
    val noneSanitize: Expr[Option[Any => Any]]                          = '{ None }

    val (nestedCollectExpr, nestedSanitizeExpr) = paramType.asType match

      case '[List[elemT]] =>
        Expr.summon[Contract[elemT]] match
          case Some(ct) =>
            (mkListNestedCollect[elemT](ct), mkListNestedSanitize[elemT](ct))
          case None => (noneCollect, noneSanitize)

      case '[Vector[elemT]] =>
        Expr.summon[Contract[elemT]] match
          case Some(ct) =>
            (mkSeqNestedCollect[elemT](ct), mkSeqNestedSanitize[elemT](ct))
          case None => (noneCollect, noneSanitize)

      case '[Option[elemT]] =>
        Expr.summon[Contract[elemT]] match
          case Some(ct) =>
            (mkOptionNestedCollect[elemT](ct), mkOptionNestedSanitize[elemT](ct))
          case None => (noneCollect, noneSanitize)

      // Either[A, B] — sanitize left/right branches if contracts are available.
      // The default tagged wire format uses "left"/"right" envelope keys.
      // @discriminator format uses a flat map; we cannot reliably recurse into
      // it here without knowing the discriminator key, so only tagged format
      // is handled.  nestedCollect is left as None: violations are already
      // caught by the discriminator / default Either decoder.
      case '[Either[a, b]] =>
        val sanitizeExpr: Expr[Option[Any => Any]] =
          (Expr.summon[Contract[a]], Expr.summon[Contract[b]]) match
            case (Some(ca), Some(cb)) =>
              '{
                Some((rawVal: Any) => rawVal match
                  case m: Map[?, ?] =>
                    val sm = m.asInstanceOf[Map[String, Any]]
                    if sm.contains("left") then
                      sm.updated("left",
                        $ca.sanitize(sm("left").asInstanceOf[Map[String, Any]]))
                    else if sm.contains("right") then
                      sm.updated("right",
                        $cb.sanitize(sm("right").asInstanceOf[Map[String, Any]]))
                    else rawVal
                  case _ => rawVal
                )
              }
            case (Some(ca), None) =>
              '{
                Some((rawVal: Any) => rawVal match
                  case m: Map[?, ?] =>
                    val sm = m.asInstanceOf[Map[String, Any]]
                    if sm.contains("left") then
                      sm.updated("left",
                        $ca.sanitize(sm("left").asInstanceOf[Map[String, Any]]))
                    else rawVal
                  case _ => rawVal
                )
              }
            case (None, Some(cb)) =>
              '{
                Some((rawVal: Any) => rawVal match
                  case m: Map[?, ?] =>
                    val sm = m.asInstanceOf[Map[String, Any]]
                    if sm.contains("right") then
                      sm.updated("right",
                        $cb.sanitize(sm("right").asInstanceOf[Map[String, Any]]))
                    else rawVal
                  case _ => rawVal
                )
              }
            case (None, None) => '{ None }
        (noneCollect, sanitizeExpr)

      case '[t] =>
        Expr.summon[Contract[t]] match
          case Some(ct) =>
            (mkDirectNestedCollect[t](ct), mkDirectNestedSanitize[t](ct))
          case None => (noneCollect, noneSanitize)

    // ── JSON Schema type info ───────────────────────────────────────────────

    val (schemaTypeStr, arrayItemTypeStr, schemaFnExpr) = resolveSchemaType(paramType)

    // ── assemble ────────────────────────────────────────────────────────────

    '{
      FieldMeta(
        name           = ${ Expr[String](name)               },
        isOptional     = ${ Expr[Boolean](isOpt)              },
        hasDefault     = ${ Expr[Boolean](hasDef)             },
        isImmutable    = ${ Expr[Boolean](isImmutable)        },
        isInternal     = ${ Expr[Boolean](isInternal)         },
        isReserved     = ${ Expr[Boolean](isReserved)         },
        masked         = ${ Expr[Option[String]](maskedOpt)   },
        isNonEmpty     = ${ Expr[Boolean](isNonEmpty)         },
        minLength      = ${ Expr[Option[Int]](minLenOpt)      },
        maxLength      = ${ Expr[Option[Int]](maxLenOpt)      },
        min            = ${ Expr[Option[Long]](minNumOpt)     },
        max            = ${ Expr[Option[Long]](maxNumOpt)     },
        pattern        = ${ Expr[Option[String]](patternOpt)          },
        isEmail        = ${ Expr[Boolean](isEmail)                   },
        isUrl          = ${ Expr[Boolean](isUrl)                     },
        isUuid         = ${ Expr[Boolean](isUuid)                    },
        isFuture       = ${ Expr[Boolean](isFuture)                  },
        isPast         = ${ Expr[Boolean](isPast)                    },
        isPositive     = ${ Expr[Boolean](isPositive)                },
        multipleOf     = ${ Expr[Option[Double]](multipleOfOpt)      },
        extractPattern = ${ Expr[Option[String]](extractPatternOpt)  },
        decoder        = $decoderExpr,
        validators     = $validatorsExpr,
        nestedCollect  = $nestedCollectExpr,
        nestedSanitize = $nestedSanitizeExpr,
        schemaType     = ${ Expr[String](schemaTypeStr)       },
        arrayItemType  = ${ Expr[String](arrayItemTypeStr)    },
        schemaFn       = $schemaFnExpr
      )
    }

  // ── nestedCollect helpers ─────────────────────────────────────────────────

  private def mkListNestedCollect[E: Type](using Quotes)(
    contract: Expr[Contract[E]]
  ): Expr[Option[(Any, FieldPath) => List[Violation]]] =
    '{
      Some((rawVal: Any, basePath: FieldPath) => rawVal match
        case xs: Seq[?] =>
          xs.asInstanceOf[Seq[Map[String, Any]]]
            .zipWithIndex.toList
            .flatMap { case (elem, idx) =>
              $contract.collectViolations(elem).map(viol =>
                Violation(
                  FieldPath((basePath.segments :+ idx.toString) ::: viol.path.segments),
                  viol.code,
                  viol.message
                )
              )
            }
        case _ => Nil
      )
    }

  private def mkSeqNestedCollect[E: Type](using Quotes)(
    contract: Expr[Contract[E]]
  ): Expr[Option[(Any, FieldPath) => List[Violation]]] =
    mkListNestedCollect[E](contract)

  private def mkOptionNestedCollect[E: Type](using Quotes)(
    contract: Expr[Contract[E]]
  ): Expr[Option[(Any, FieldPath) => List[Violation]]] =
    '{
      Some((rawVal: Any, basePath: FieldPath) => rawVal match
        case null     => Nil
        case None     => Nil
        case Some(inner) =>
          $contract.collectViolations(inner.asInstanceOf[Map[String, Any]]).map(viol =>
            Violation(
              FieldPath(basePath.segments ::: viol.path.segments),
              viol.code, viol.message
            )
          )
        case m: Map[?, ?] =>
          $contract.collectViolations(m.asInstanceOf[Map[String, Any]]).map(viol =>
            Violation(
              FieldPath(basePath.segments ::: viol.path.segments),
              viol.code, viol.message
            )
          )
        case _ => Nil
      )
    }

  private def mkDirectNestedCollect[E: Type](using Quotes)(
    contract: Expr[Contract[E]]
  ): Expr[Option[(Any, FieldPath) => List[Violation]]] =
    '{
      Some((rawVal: Any, basePath: FieldPath) => rawVal match
        case m: Map[?, ?] =>
          $contract.collectViolations(m.asInstanceOf[Map[String, Any]]).map(viol =>
            Violation(
              FieldPath(basePath.segments ::: viol.path.segments),
              viol.code, viol.message
            )
          )
        case _ => Nil
      )
    }

  // ── nestedSanitize helpers ────────────────────────────────────────────────

  private def mkListNestedSanitize[E: Type](using Quotes)(
    contract: Expr[Contract[E]]
  ): Expr[Option[Any => Any]] =
    '{
      Some((rawVal: Any) => rawVal match
        case xs: Seq[?] =>
          xs.asInstanceOf[Seq[Map[String, Any]]]
            .map(elem => $contract.sanitize(elem).asInstanceOf[Any])
            .toList
        case _ => rawVal
      )
    }

  private def mkSeqNestedSanitize[E: Type](using Quotes)(
    contract: Expr[Contract[E]]
  ): Expr[Option[Any => Any]] =
    mkListNestedSanitize[E](contract)

  private def mkOptionNestedSanitize[E: Type](using Quotes)(
    contract: Expr[Contract[E]]
  ): Expr[Option[Any => Any]] =
    '{
      Some((rawVal: Any) => rawVal match
        case null        => rawVal
        case None        => rawVal
        case Some(inner) =>
          Some($contract.sanitize(inner.asInstanceOf[Map[String, Any]]).asInstanceOf[Any])
        case m: Map[?, ?] =>
          $contract.sanitize(m.asInstanceOf[Map[String, Any]]).asInstanceOf[Any]
        case _ => rawVal
      )
    }

  private def mkDirectNestedSanitize[E: Type](using Quotes)(
    contract: Expr[Contract[E]]
  ): Expr[Option[Any => Any]] =
    '{
      Some((rawVal: Any) => rawVal match
        case m: Map[?, ?] =>
          $contract.sanitize(m.asInstanceOf[Map[String, Any]]).asInstanceOf[Any]
        case _ => rawVal
      )
    }

  // ── JSON Schema type resolution ──────────────────────────────────────────
  //
  // Determines, at compile time, the JSON Schema `type` for a constructor
  // parameter.  Returns a triple: (schemaType, arrayItemType, schemaFnExpr).
  //
  //  • schemaType     — "string", "integer", "number", "boolean",
  //                     "array", "object", or "any"
  //  • arrayItemType  — element type for arrays; "any" otherwise
  //  • schemaFnExpr   — Expr[Option[() => RawObject]] for nested contracts

  private def resolveSchemaType(using q: Quotes)(
    paramType: q.reflect.TypeRepr
  ): (String, String, Expr[Option[() => RawObject]]) =
    import q.reflect.*

    val noSchema: Expr[Option[() => RawObject]] = '{ None }

    // Maps a TypeRepr to a JSON Schema primitive type name.
    def primType(t: TypeRepr): Option[String] =
      if      t =:= TypeRepr.of[String]     then Some("string")
      else if t =:= TypeRepr.of[Int]        then Some("integer")
      else if t =:= TypeRepr.of[Long]       then Some("integer")
      else if t =:= TypeRepr.of[Float]      then Some("number")
      else if t =:= TypeRepr.of[Double]     then Some("number")
      else if t =:= TypeRepr.of[BigDecimal] then Some("number")
      else if t =:= TypeRepr.of[BigInt]     then Some("number")
      else if t =:= TypeRepr.of[Boolean]    then Some("boolean")
      else None

    // Resolve a collection element type: primitive → its type name;
    // contract-backed → ("object", schemaFnExpr); fallback → ("any", noSchema).
    def elemSchema(t: TypeRepr): (String, Expr[Option[() => RawObject]]) =
      primType(t).map(pt => (pt, noSchema)).getOrElse {
        t.asType match
          case '[ct] =>
            Expr.summon[Contract[ct]] match
              case Some(c) => ("object", '{ Some(() => $c.jsonSchema) })
              case None    => ("any", noSchema)
      }

    paramType.asType match

      // ── Option[T] — unwrap and resolve inner type ─────────────────────────
      case '[Option[a]] =>
        val inner = TypeRepr.of[a]
        primType(inner).map(t => (t, "any", noSchema)).getOrElse {
          inner.asType match
            case '[ct] =>
              Expr.summon[Contract[ct]] match
                case Some(c) => ("object", "any", '{ Some(() => $c.jsonSchema) })
                case None    => ("any", "any", noSchema)
        }

      // ── List[T] ────────────────────────────────────────────────────────────
      case '[List[a]] =>
        val (it, sfn) = elemSchema(TypeRepr.of[a])
        ("array", it, sfn)

      // ── Vector[T] ──────────────────────────────────────────────────────────
      case '[Vector[a]] =>
        val (it, sfn) = elemSchema(TypeRepr.of[a])
        ("array", it, sfn)

      // ── Set[T] ─────────────────────────────────────────────────────────────
      case '[Set[a]] =>
        val (it, sfn) = elemSchema(TypeRepr.of[a])
        ("array", it, sfn)

      // ── Either[A,B] — discriminated union → plain object ──────────────────
      case '[Either[a, b]] => ("object", "any", noSchema)

      // ── Map[String, V] ─────────────────────────────────────────────────────
      case '[Map[String, a]] => ("object", "any", noSchema)

      // ── Everything else: primitives and nested contracts ───────────────────
      case '[t] =>
        primType(TypeRepr.of[t]).map(tp => (tp, "any", noSchema)).getOrElse {
          Expr.summon[Contract[t]] match
            case Some(c) => ("object", "any", '{ Some(() => $c.jsonSchema) })
            case None    => ("any", "any", noSchema)
        }

  // ── Constructor lambda: Map[String, Any] => T ────────────────────────────
  //
  // For regular params: fields("fieldName").asInstanceOf[T]
  // For @include params: reconstruct inner type from its flattened fields.

  private def mkConstructFn[T: Type](using q: Quotes)(
    sym:        q.reflect.Symbol,
    params:     List[q.reflect.Symbol],
    paramTypes: List[q.reflect.TypeRepr],
    companion:  q.reflect.Symbol
  ): Expr[Map[String, Any] => T] =
    import q.reflect.*

    val includeAnnotSym = TypeRepr.of[annotations.include].typeSymbol

    '{ (fields: Map[String, Any]) =>
      ${
        val fieldsRef: Expr[Map[String, Any]] = '{ fields }

        val ctorArgs: List[Term] = params.zip(paramTypes).zipWithIndex.map { case ((param, pType), idx) =>

          val isInclude = param.annotations.exists(_.tpe.typeSymbol == includeAnnotSym)

          if isInclude then
            // Build the nested type from its flattened fields in the map
            val innerSym       = pType.typeSymbol
            val innerCompanion = innerSym.companionModule
            val innerParams: List[Symbol] =
              innerSym.primaryConstructor.paramSymss.headOption.getOrElse(List.empty)
            val innerTypes: List[TypeRepr] =
              pType.memberType(innerSym.primaryConstructor) match
                case MethodType(_, types, _) => types
                case _                        => List.empty

            val innerCtorArgs: List[Term] = innerParams.zip(innerTypes).zipWithIndex.map { case ((ip, ipt), iIdx) =>
              val iName    = Expr(ip.name)
              val iDefName = s"$$lessinit$$greater$$default$$${iIdx + 1}"
              val iHasDef  = innerCompanion.declarations.exists(_.name == iDefName)

              ipt.asType match
                case '[Option[a]] =>
                  '{ $fieldsRef.get($iName).fold(None: Option[a])(_.asInstanceOf[Option[a]]) }.asTerm
                case '[t] if iHasDef =>
                  innerCompanion.declarations.find(_.name == iDefName) match
                    case Some(defSym) =>
                      val defExpr: Expr[t] = Ref(innerCompanion).select(defSym).asExprOf[t]
                      '{ $fieldsRef.getOrElse($iName, $defExpr).asInstanceOf[t] }.asTerm
                    case None =>
                      '{ $fieldsRef($iName).asInstanceOf[t] }.asTerm
                case '[t] =>
                  '{ $fieldsRef($iName).asInstanceOf[t] }.asTerm
            }

            // new InnerType(arg0, arg1, ...)
            pType.asType match
              case '[innerT] =>
                Apply(
                  Select(New(TypeTree.of[innerT]), innerSym.primaryConstructor),
                  innerCtorArgs
                ).asExprOf[Any].asTerm

          else
            val name     = param.name
            val defName  = s"$$lessinit$$greater$$default$$${idx + 1}"
            val hasDef   = companion.declarations.exists(_.name == defName)
            val nameExpr = Expr[String](name)

            pType.asType match
              case '[Option[a]] =>
                '{
                  $fieldsRef
                    .get($nameExpr)
                    .fold(None: Option[a])(_.asInstanceOf[Option[a]])
                }.asTerm
              case '[t] if hasDef =>
                companion.declarations.find(_.name == defName) match
                  case Some(defSym) =>
                    val defExpr: Expr[t] = Ref(companion).select(defSym).asExprOf[t]
                    '{
                      $fieldsRef
                        .getOrElse($nameExpr, $defExpr)
                        .asInstanceOf[t]
                    }.asTerm
                  case None =>
                    '{ $fieldsRef($nameExpr).asInstanceOf[t] }.asTerm
              case '[t] =>
                '{ $fieldsRef($nameExpr).asInstanceOf[t] }.asTerm
        }

        // new T(arg0, arg1, …)
        Apply(
          Select(New(TypeTree.of[T]), sym.primaryConstructor),
          ctorArgs
        ).asExprOf[T]
      }
    }

  // ── T => RawObject lambda ────────────────────────────────────────────────
  //
  // Uses Product.productElement(n) for safe field access without reflection.
  // For @include params, the inner type is accessed at productElement(outerIdx),
  // then each inner field via productElement(innerIdx) on that sub-Product.

  private def mkToRawFn[T: Type](using q: Quotes)(
    sym:        q.reflect.Symbol,
    params:     List[q.reflect.Symbol],
    paramTypes: List[q.reflect.TypeRepr]
  ): Expr[T => RawObject] =
    import q.reflect.*

    val includeAnnotSym = TypeRepr.of[annotations.include].typeSymbol

    '{ (t: T) =>
      ${
        val tProd: Expr[Product] = '{ t.asInstanceOf[Product] }

        val pairExprs: List[Expr[(String, Any)]] =
          params.zip(paramTypes).zipWithIndex.flatMap { case ((p, pt), outerIdx) =>
            val isInclude = p.annotations.exists(_.tpe.typeSymbol == includeAnnotSym)
            val discrOpt  = readDiscriminatorAnnot(using q)(p.annotations)
            val outerIdxExpr = Expr(outerIdx)

            if isInclude then
              val innerSym    = pt.typeSymbol
              val innerParams = innerSym.primaryConstructor.paramSymss.headOption.getOrElse(List.empty)
              innerParams.zipWithIndex.map { case (ip, innerIdx) =>
                val innerNameExpr = Expr(ip.name)
                val innerIdxExpr  = Expr(innerIdx)
                '{
                  $innerNameExpr ->
                    ($tProd.productElement($outerIdxExpr)
                      .asInstanceOf[Product]
                      .productElement($innerIdxExpr)
                      .asInstanceOf[Any])
                }
              }
            else
              val nameExpr = Expr(p.name)

              val pair: Expr[(String, Any)] = (discrOpt, pt.asType) match
                case (Some((discKey, leftTag, rightTag)), '[Either[a, b]]) =>
                  val discKeyExpr:  Expr[String] = Expr(discKey)
                  val leftTagExpr:  Expr[String] = Expr(leftTag)
                  val rightTagExpr: Expr[String] = Expr(rightTag)
                  val caOpt        = Expr.summon[Contract[a]]
                  val cbOpt        = Expr.summon[Contract[b]]

                  '{
                    $nameExpr ->
                      ($tProd.productElement($outerIdxExpr).asInstanceOf[Either[a, b]] match
                        case Left(l) =>
                          val body: Map[String, Any] = ${ caOpt match
                            case Some(ca) => '{ $ca.toRaw(l) }
                            case None     => '{ Map("value" -> l.asInstanceOf[Any]) }
                          }
                          body + ($discKeyExpr -> $leftTagExpr)
                        case Right(r) =>
                          val body: Map[String, Any] = ${ cbOpt match
                            case Some(cb) => '{ $cb.toRaw(r) }
                            case None     => '{ Map("value" -> r.asInstanceOf[Any]) }
                          }
                          body + ($discKeyExpr -> $rightTagExpr)
                      )
                  }

                case (None, '[Either[a, b]]) =>
                  val caOpt = Expr.summon[Contract[a]]
                  val cbOpt = Expr.summon[Contract[b]]
                  '{
                    $nameExpr ->
                      ($tProd.productElement($outerIdxExpr).asInstanceOf[Either[a, b]] match
                        case Left(l) =>
                          val body: Any = ${ caOpt match
                            case Some(ca) => '{ $ca.toRaw(l) }
                            case None     => '{ l.asInstanceOf[Any] }
                          }
                          Map("left" -> body)
                        case Right(r) =>
                          val body: Any = ${ cbOpt match
                            case Some(cb) => '{ $cb.toRaw(r) }
                            case None     => '{ r.asInstanceOf[Any] }
                          }
                          Map("right" -> body)
                      )
                  }

                case _ =>
                  '{
                    $nameExpr -> $tProd.productElement($outerIdxExpr).asInstanceOf[Any]
                  }

              List(pair)
          }

        // Build the map without an intermediate list allocation using foldRight
        val mapExpr: Expr[RawObject] =
          pairExprs.foldRight('{ Map.empty[String, Any] }) { (pair, acc) =>
            '{ $acc + $pair }
          }
        mapExpr
      }
    }
