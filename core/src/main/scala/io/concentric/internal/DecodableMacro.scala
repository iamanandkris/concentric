package io.concentric.internal

import scala.quoted.*
import io.concentric.RawDecoder
import io.concentric.annotations

/**
 * Compile-time macro that derives a [[RawDecoder]][T] for a case class
 * annotated with `@decodable`.
 *
 * Entry point: [[RawDecoder.derived]].
 *
 * Two derivation modes are selected at compile time:
 *
 * == Single-field mode (no `@extract`) ==
 *
 * The case class must have exactly one constructor field.  The macro summons
 * `RawDecoder[U]` for the inner type and generates a decoder that delegates
 * to it and wraps the result:
 *
 * {{{
 *   @decodable case class Email(value: String)
 *   // →
 *   new RawDecoder[Email]:
 *     def decode(raw: Any): Option[Email] =
 *       RawDecoder[String].decode(raw).map(Email(_))
 * }}}
 *
 * == Extraction mode (`@extract("regex")` on the class) ==
 *
 * The regex is compiled at macro-expansion time to:
 *  - validate the pattern syntax (compile error on bad regex),
 *  - count its capture groups N.
 *
 * Rules:
 *  - N == 0, 1 field  → validate-only: the full matched string is wrapped.
 *  - N == fields      → extract: group k → field k, decoded by the field's
 *                       `RawDecoder[fieldType]`.
 *  - anything else   → compile error.
 *
 * The generated decoder returns `None` when:
 *  - the raw value is not a `String`,
 *  - the string does not fully match the regex (`Matcher.matches()`), or
 *  - any individual group cannot be decoded to its field type.
 *
 * {{{
 *   @decodable
 *   @extract("(\\d{4})-(\\d{2})-(\\d{2})")
 *   case class IsoDate(year: Int, month: Int, day: Int)
 *   given RawDecoder[IsoDate] = RawDecoder.derived[IsoDate]
 *   // Input "2024-03-15" → Some(IsoDate(2024, 3, 15))
 * }}}
 */
object DecodableMacro:

  def derived[T <: Product: Type](using Quotes): Expr[RawDecoder[T]] =
    import quotes.reflect.*

    val tRepr = TypeRepr.of[T]
    val sym   = tRepr.typeSymbol

    if !sym.flags.is(Flags.Case) then
      report.errorAndAbort(
        s"RawDecoder.derived[${sym.name}]: '${sym.name}' must be a case class."
      )

    val params: List[Symbol] =
      sym.primaryConstructor.paramSymss.headOption.getOrElse(List.empty)

    val paramTypes: List[TypeRepr] =
      tRepr.memberType(sym.primaryConstructor) match
        case MethodType(_, types, _)                  => types
        case PolyType(_, _, MethodType(_, types, _))  => types
        case _ =>
          report.errorAndAbort(s"RawDecoder.derived[${sym.name}]: unexpected constructor shape")

    // Check for @extract on the class itself
    val extractAnnotSym = TypeRepr.of[annotations.extract].typeSymbol
    val maybeRegex: Option[String] =
      sym.annotations
        .find(_.tpe.typeSymbol == extractAnnotSym)
        .flatMap {
          case Apply(_, List(Literal(StringConstant(s))))               => Some(s)
          case Apply(_, List(NamedArg(_, Literal(StringConstant(s))))) => Some(s)
          case _                                                         => None
        }

    maybeRegex match
      case Some(regex) => deriveExtract[T](sym, params, paramTypes, regex)
      case None        => deriveSingle[T](sym, params, paramTypes)

  // ── Single-field derivation (no @extract) ────────────────────────────────

  private def deriveSingle[T <: Product: Type](using Quotes)(
    sym:        quotes.reflect.Symbol,
    params:     List[quotes.reflect.Symbol],
    paramTypes: List[quotes.reflect.TypeRepr]
  ): Expr[RawDecoder[T]] =
    import quotes.reflect.*

    if params.size != 1 then
      report.errorAndAbort(
        s"RawDecoder.derived[${sym.name}]: only single-field case classes are supported " +
        s"(${sym.name} has ${params.size} field(s)). " +
        "For multi-field extraction, annotate the class with @extract(\"regex\")."
      )

    val companion = sym.companionModule
    val applyMethod: Symbol =
      companion.methodMember("apply")
        .find(_.paramSymss.headOption.exists(_.size == 1))
        .getOrElse(
          report.errorAndAbort(
            s"RawDecoder.derived[${sym.name}]: no single-argument apply found on companion"
          )
        )

    val innerType = paramTypes.head
    innerType.asType match
      case '[u] =>
        Expr.summon[RawDecoder[u]] match
          case Some(innerDecoder) =>
            '{
              new RawDecoder[T]:
                private val inner: RawDecoder[u] = $innerDecoder
                def decode(raw: Any): Option[T] =
                  inner.decode(raw).map { (v: u) =>
                    ${ Apply(Select(Ref(companion), applyMethod), List('{ v }.asTerm)).asExprOf[T] }
                  }
            }
          case None =>
            report.errorAndAbort(
              s"RawDecoder.derived[${sym.name}]: no RawDecoder[${innerType.show}] in scope " +
              s"for field '${params.head.name}'. Provide: given RawDecoder[...] = ..."
            )

  // ── Extraction-mode derivation (@extract("regex") on the class) ──────────

  private def deriveExtract[T <: Product: Type](using Quotes)(
    sym:        quotes.reflect.Symbol,
    params:     List[quotes.reflect.Symbol],
    paramTypes: List[quotes.reflect.TypeRepr],
    regex:      String
  ): Expr[RawDecoder[T]] =
    import quotes.reflect.*

    // Validate the regex syntax and count capture groups at compile time.
    val numGroups: Int =
      try java.util.regex.Pattern.compile(regex).matcher("").groupCount()
      catch case e: java.util.regex.PatternSyntaxException =>
        report.errorAndAbort(
          s"@extract on '${sym.name}': invalid regex '$regex' — ${e.getMessage}"
        )

    // Enforce the group-count ↔ field-count contract.
    if numGroups > 0 && numGroups != params.length then
      report.errorAndAbort(
        s"@extract on '${sym.name}': regex has $numGroups capture group(s) but " +
        s"'${sym.name}' has ${params.length} field(s). " +
        "The number of capture groups must equal the number of fields."
      )
    if numGroups == 0 && params.length != 1 then
      report.errorAndAbort(
        s"@extract on '${sym.name}': regex has no capture groups but " +
        s"'${sym.name}' has ${params.length} fields. " +
        "Either add one capture group per field, or use a single-field class."
      )

    val regexExpr     = Expr(regex)
    val useGroupsExpr = Expr(numGroups > 0)

    // Summon RawDecoder[t] for each param; cast to RawDecoder[Any] at runtime.
    val decoderExprs: List[Expr[RawDecoder[Any]]] =
      params.zip(paramTypes).map { case (p, pt) =>
        pt.asType match
          case '[t] =>
            val d = Expr.summon[RawDecoder[t]].getOrElse(
              report.errorAndAbort(
                s"@extract on '${sym.name}': no RawDecoder[${pt.show}] for field '${p.name}'. " +
                "Provide: given RawDecoder[...] = ..."
              )
            )
            '{ $d.asInstanceOf[RawDecoder[Any]] }
      }

    val decodersExpr: Expr[List[RawDecoder[Any]]] =
      decoderExprs.foldRight('{ Nil: List[RawDecoder[Any]] }) { (d, acc) =>
        '{ $d :: $acc }
      }

    // Build the constructor function once in the outer Quotes context so it can
    // be spliced into the inner quote without a Quotes-context mismatch.
    val ctorFnExpr: Expr[List[Any] => T] = buildCtorFn[T](paramTypes, sym)

    '{
      new RawDecoder[T]:
        private val compiled: java.util.regex.Pattern =
          java.util.regex.Pattern.compile($regexExpr)
        private val fieldDecoders: List[RawDecoder[Any]] = $decodersExpr
        private val ctor: List[Any] => T = $ctorFnExpr

        def decode(raw: Any): Option[T] =
          raw match
            case s: String =>
              val m = compiled.matcher(s)
              if !m.matches() then None
              else if !$useGroupsExpr then
                // Validate-only (0 groups, 1 field): pass the whole matched string.
                fieldDecoders.head.decode(s).map(v => ctor(List(v)))
              else
                // Extract: group(i+1) → field i, decoded by fieldDecoders(i).
                val extracted: List[Option[Any]] =
                  fieldDecoders.zipWithIndex.map { case (dec, i) =>
                    dec.decode(m.group(i + 1))
                  }
                if extracted.forall(_.isDefined) then
                  Some(ctor(extracted.map(_.get)))
                else None
            case _ => None
    }

  // ── Constructor function builder ─────────────────────────────────────────
  //
  // Generates: (args: List[Any]) => new T(args(0).asInstanceOf[T0], …)
  // Built in the *outer* Quotes context so it can be cleanly spliced into
  // the inner quote in deriveExtract without a context mismatch.

  private def buildCtorFn[T: Type](using Quotes)(
    paramTypes: List[quotes.reflect.TypeRepr],
    sym:        quotes.reflect.Symbol
  ): Expr[List[Any] => T] =
    import quotes.reflect.*
    '{ (args: List[Any]) =>
      ${
        val argsRef: Expr[List[Any]] = '{ args }
        val ctorTerms: List[Term] = paramTypes.zipWithIndex.map { case (pt, i) =>
          val iExpr = Expr(i)
          pt.asType match
            case '[t] => '{ $argsRef($iExpr).asInstanceOf[t] }.asTerm
        }
        Apply(Select(New(TypeTree.of[T]), sym.primaryConstructor), ctorTerms).asExprOf[T]
      }
    }
