package io.concentric

import java.lang.annotation.Annotation
import java.lang.reflect.{Constructor, Field, Parameter}
import io.concentric.annotations.*
import io.concentric.internal.ValidatorHelper
import scala.reflect.ClassTag

/**
 * Derives a [[List]][[[FieldMeta]]] from a Java or Kotlin class by inspecting
 * its declared fields and annotations at runtime.
 *
 * == Annotation resolution ==
 *
 * concentric annotations carry `@Retention(RUNTIME)` and target both
 * `ElementType.FIELD` and `ElementType.PARAMETER`, so they can appear on:
 *
 *  - Java record components — annotations are propagated to the backing field
 *    and to the canonical constructor parameter by the Java compiler.
 *  - Java POJO fields.
 *  - Kotlin data class constructor parameters — use `@field:nonEmpty` (use-site
 *    target) to ensure the annotation lands on the JVM backing field; plain
 *    `@nonEmpty` lands on the constructor parameter and is also checked.
 *
 * `JvmContractDeriver` checks *both* the field and the corresponding primary-
 * constructor parameter, so both placement styles work without modification.
 *
 * == Field ordering ==
 *
 * Uses `getDeclaredFields()`, which preserves declaration order for `javac`-
 * and `kotlinc`-compiled classes (including Java records).  Synthetic and
 * static fields are excluded automatically.
 *
 * == Unsupported Scala-only features ==
 *
 *  - `@decodable` (wrapper-type macro derivation)
 *  - `@discriminator` (sealed-trait tagged unions)
 *  - Nested `Contract[T]` validation for inner object fields
 */
object JvmContractDeriver:

  enum OptionalKind:
    case Required, JavaOptional, Nullable

  private[concentric] final case class DerivedField(
    name:        String,
    field:       Field,
    javaType:    java.lang.reflect.Type,
    annotations: List[Annotation],
    optionalKind: OptionalKind,
    hasDefault:  Boolean,
    isInclude:   Boolean,
    nestedSupport: Option[JvmNestedSupport],
    children:    List[DerivedField] = Nil
  )

  /** Derive [[FieldMeta]] for every non-synthetic, non-static declared field. */
  def derive[T](clazz: Class[T]): List[FieldMeta] =
    describe(clazz).flatMap(toFieldMetas)

  /**
   * Derive [[FieldMeta]] for an aspect class, merging constraint annotations
   * from the corresponding fields of the source (base) contract class.
   *
   * == Inheritance rules ==
   *
   *  - **Constraint** annotations (`@nonEmpty`, `@email`, `@min`, `@max`,
   *    `@minLength`, `@maxLength`, `@pattern`, `@url`, `@uuid`, `@future`,
   *    `@past`, `@positive`, `@multipleOf`, `@extract`, `@validateWith`) are
   *    inherited from the matching source field when not already present on the
   *    aspect field.  Aspect-level annotations always win.
   *  - **Policy** annotations (`@immutable`, `@internal`, `@reserved`,
   *    `@masked`) are **never** inherited — the aspect author controls access
   *    rules independently.
   *  - Fields present in the aspect but absent from the source receive only
   *    their own annotations (new fields).
   *
   * Aspects are always closed (unknown fields rejected), regardless of the
   * source contract's openness.
   *
   * @param aspectClazz The aspect class (annotated with `@aspectOf`).
   * @param sourceClazz The source (base) contract class.
   */
  def deriveAspect[T, S](aspectClazz: Class[T], sourceClazz: Class[S]): List[FieldMeta] =
    // Policy annotation class names — never inherited from source.
    val policyAnnotationNames: Set[String] = Set(
      classOf[immutable].getName,
      classOf[internal].getName,
      classOf[reserved].getName,
      classOf[masked].getName
    )

    // Read @aspectOf(inherit, exclude) from the aspect class.
    val aspectAnn    = aspectClazz.getAnnotation(classOf[io.concentric.annotations.aspectOf])
    val inheritAll   = aspectAnn != null && aspectAnn.inherit() == io.concentric.annotations.aspectOf.InheritMode.ALL
    val excludeNames = if (aspectAnn != null) aspectAnn.exclude().toSet else Set.empty[String]

    // Error if exclude is specified without inherit = ALL.
    if (!inheritAll && excludeNames.nonEmpty)
      throw new IllegalArgumentException(
        s"@aspectOf on ${aspectClazz.getName}: exclude requires inherit = ALL"
      )

    // Build a map of source field effective annotations by field name.
    val sourceFields: List[Field]     = getFieldsInOrder(sourceClazz)
    val sourceParams: List[Parameter] = primaryConstructorParams(sourceClazz)
    val sourceAnnotsByName: Map[String, List[Annotation]] =
      sourceFields.zipWithIndex.map { (field, idx) =>
        field.getName -> effectiveAnnotations(field, sourceParams.lift(idx))
      }.toMap

    // Describe aspect fields (the fields the aspect actually declares in its constructor).
    val aspectNodes = describe(aspectClazz)

    // inherit = ALL exhaustiveness checks.
    if (inheritAll)
      val aspectFieldNames = aspectNodes.map(_.name).toSet
      val sourceFieldNames = sourceAnnotsByName.keySet

      // 1. Every exclude name must exist in source.
      excludeNames.foreach { name =>
        if (!sourceFieldNames.contains(name))
          throw new IllegalArgumentException(
            s"@aspectOf(inherit = ALL) on ${aspectClazz.getName}: " +
            s"exclude field '$name' does not exist in source contract ${sourceClazz.getName}"
          )
      }

      // 2. Every source field must be either declared in aspect or excluded.
      val unaccounted = sourceFieldNames -- aspectFieldNames -- excludeNames
      if (unaccounted.nonEmpty)
        throw new IllegalArgumentException(
          s"@aspectOf(inherit = ALL) on ${aspectClazz.getName}: " +
          s"source field(s) [${unaccounted.toSeq.sorted.mkString(", ")}] from ${sourceClazz.getName} " +
          s"are neither declared in the aspect nor listed in exclude"
        )

    // Merge inherited constraint annotations into aspect nodes.
    val mergedNodes = aspectNodes.map { node =>
      sourceAnnotsByName.get(node.name) match
        case None =>
          // Field not in source — use aspect annotations as-is.
          node
        case Some(sourceAnns) =>
          // Inherit constraint annotations from source that are:
          //   (a) not policy annotations, and
          //   (b) not already declared on the aspect field.
          val aspectAnnotTypes = node.annotations.map(_.annotationType).toSet
          val inherited = sourceAnns.filterNot { a =>
            policyAnnotationNames.contains(a.annotationType.getName) ||
            aspectAnnotTypes.contains(a.annotationType)
          }
          node.copy(annotations = node.annotations ++ inherited)
    }

    // Error if the resulting field list is empty.
    if (mergedNodes.isEmpty)
      throw new IllegalArgumentException(
        s"@aspectOf on ${aspectClazz.getName}: aspect has no fields after applying exclude"
      )

    mergedNodes.flatMap(toFieldMetas)

  /** Read `@validateContract` validators declared on the class. */
  def contractValidators[T](clazz: Class[T]): List[T => List[String]] =
    Option(clazz.getAnnotation(classOf[validateContract]))
      .toList
      .flatMap(_.value().toList)
      .map(vc => ValidatorHelper.makeContractValidator[T](vc.getName))

  /** Describe the top-level field structure, expanding `@include` one level. */
  private[concentric] def describe[T](clazz: Class[T]): List[DerivedField] =
    describeFields(clazz, expandIncludes = true)

  // ── Record support (Java 16+) ─────────────────────────────────────────────

  /**
   * True when running on JVM 16+ and the class is a record.
   * Uses reflection so the code compiles against any JDK but exploits the
   * real API when available.
   */
  private def isRecord(clazz: Class[?]): Boolean =
    try classOf[Class[?]].getMethod("isRecord").invoke(clazz).asInstanceOf[Boolean]
    catch case _: NoSuchMethodException => false

  private[concentric] def isRecordClass(clazz: Class[?]): Boolean = isRecord(clazz)

  /**
   * Returns the record component names in declaration order.
   * `RecordComponent.getName()` is also accessed reflectively for the same
   * source-compatibility reason.
   * Returns `None` on pre-16 JVMs or for non-record classes.
   */
  private def recordComponentNames(clazz: Class[?]): Option[List[String]] =
    try
      val rcMethod    = classOf[Class[?]].getMethod("getRecordComponents")
      val components  = rcMethod.invoke(clazz).asInstanceOf[Array[AnyRef]]
      if components == null then None
      else
        val nameMethod = components.headOption
          .map(_.getClass.getMethod("getName"))
          .orNull
        if nameMethod == null then Some(List.empty)
        else Some(components.toList.map(c => nameMethod.invoke(c).asInstanceOf[String]))
    catch case _: (NoSuchMethodException | java.lang.reflect.InvocationTargetException) => None

  // ── Field discovery ───────────────────────────────────────────────────────

  /**
   * Returns all non-synthetic, non-static declared fields in declaration order.
   *
   * For **Java records** (Java 16+) we sort by the canonical component order
   * obtained from `getRecordComponents()`, which is authoritative and matches
   * the canonical constructor parameter order exactly.  This matters because
   * `getDeclaredFields()` on a record may include the backing fields in an
   * unspecified order on some JVM implementations.
   *
   * For **Java POJOs** and **Kotlin data classes** `getDeclaredFields()`
   * preserves declaration order for both `javac` and `kotlinc`.
   */
  private def getFieldsInOrder(clazz: Class[?]): List[Field] =
    val rawFields = clazz.getDeclaredFields.toList
      .filterNot(f => f.isSynthetic || java.lang.reflect.Modifier.isStatic(f.getModifiers))

    val ordered = recordComponentNames(clazz) match
      case Some(names) if names.nonEmpty =>
        // Sort backing fields by canonical component order; fall back to
        // getDeclaredFields order for any field not found in the component list.
        val rank = names.zipWithIndex.toMap
        rawFields.sortBy(f => rank.getOrElse(f.getName, Int.MaxValue))
      case _ =>
        rawFields

    ordered.foreach(_.setAccessible(true))
    ordered

  // ── Constructor parameter discovery ──────────────────────────────────────

  /**
   * Returns the parameters of the primary (all-args) constructor.
   *
   * Chooses the constructor with the most parameters.  For Java records this
   * is the canonical constructor; for Kotlin data classes it is the primary
   * constructor.
   */
  private def primaryConstructorParams(clazz: Class[?]): List[Parameter] =
    KotlinSupport.info(clazz)
      .map(_.javaParams)
      .getOrElse {
        clazz.getDeclaredConstructors.toList
          .filterNot(_.isSynthetic)
          .sortBy(-_.getParameterCount)
          .headOption
          .fold(List.empty[Parameter]) { c =>
            c.setAccessible(true)
            c.getParameters.toList
          }
      }

  private def describeFields(clazz: Class[?], expandIncludes: Boolean): List[DerivedField] =
    val fields     = getFieldsInOrder(clazz)
    val ctorParams = primaryConstructorParams(clazz)
    val kotlinParams = KotlinSupport.info(clazz)
      .map(_.params.map(p => p.name -> p).toMap)
      .getOrElse(Map.empty[String, KotlinSupport.ParamInfo])

    fields.zipWithIndex.map { (field, idx) =>
      val anns      = effectiveAnnotations(field, ctorParams.lift(idx))
      val jType     = field.getGenericType
      val kotlinParam = kotlinParams.get(field.getName)
      val optKind   = optionalKindFor(field, jType, anns, kotlinParam)
      val canExpand = expandIncludes && has[include](anns)
      val nested    = if canExpand then None else JvmNestedSupport.forType(jType)
      val children  =
        if canExpand then describeFields(field.getType, expandIncludes = false)
        else Nil

      DerivedField(
        name        = field.getName,
        field       = field,
        javaType    = jType,
        annotations = anns,
        optionalKind = optKind,
        hasDefault  = kotlinParam.exists(_.hasDefault),
        isInclude   = canExpand,
        nestedSupport = nested,
        children    = children
      )
    }

  // ── Annotation merging ────────────────────────────────────────────────────

  /**
   * Build an effective annotation list for a field, merging field-level and
   * constructor-parameter-level annotations.  Field annotations take precedence.
   *
   * This dual-check ensures both `@nonEmpty String name` (Kotlin parameter site)
   * and `@field:nonEmpty val name: String` (Kotlin field site) are detected.
   */
  private def effectiveAnnotations(field: Field, param: Option[Parameter]): List[Annotation] =
    val fromField = field.getDeclaredAnnotations.toList
    val fromParam = param.fold(List.empty[Annotation])(_.getDeclaredAnnotations.toList)
    val fieldTypes = fromField.map(_.annotationType).toSet
    fromField ++ fromParam.filterNot(a => fieldTypes.contains(a.annotationType))

  private def has[A <: Annotation : ClassTag](anns: List[Annotation]): Boolean =
    val rc = implicitly[ClassTag[A]].runtimeClass
    anns.exists(rc.isInstance)

  private def get[A <: Annotation : ClassTag](anns: List[Annotation]): Option[A] =
    val rc = implicitly[ClassTag[A]].runtimeClass
    anns.collectFirst { case a if rc.isInstance(a) => a.asInstanceOf[A] }

  private def toFieldMetas(node: DerivedField): List[FieldMeta] =
    if node.isInclude then node.children.flatMap(toFieldMetas)
    else
      val anns  = node.annotations
      val jType = node.javaType
      val nestedDecoder = node.nestedSupport.map(_.decoder).getOrElse(JvmTypeDecoder.forType(jType))
      List(
        FieldMeta(
          name           = node.name,
          isOptional     = node.optionalKind != OptionalKind.Required,
          hasDefault     = node.hasDefault,
          isImmutable    = has[immutable](anns),
          isInternal     = has[internal](anns),
          isReserved     = has[reserved](anns),
          masked         = get[masked](anns).map(_.value()),
          isNonEmpty     = has[nonEmpty](anns),
          minLength      = get[minLength](anns).map(_.value().toInt),
          maxLength      = get[maxLength](anns).map(_.value().toInt),
          min            = get[min](anns).map(_.value()),
          max            = get[max](anns).map(_.value()),
          pattern        = get[pattern](anns).map(_.value()),
          isEmail        = has[email](anns),
          isUrl          = has[url](anns),
          isUuid         = has[uuid](anns),
          isFuture       = has[future](anns),
          isPast         = has[past](anns),
          isPositive     = has[positive](anns),
          multipleOf     = get[multipleOf](anns).map(_.value()),
          extractPattern = get[extract](anns).map(_.value()),
          decoder        = nestedDecoder,
          validators     = get[validateWith](anns).toList
            .flatMap(_.value().toList)
            .map(vc => ValidatorHelper.make(vc.getName)),
          nestedCollect  = node.nestedSupport.map(ns => (raw: Any, path: FieldPath) => ns.collect(raw, path)),
          nestedPatchCollect = node.nestedSupport.map(ns =>
            (current: Any, patch: Any, path: FieldPath) => ns.patchCollect(current, patch, path)
          ),
          nestedSanitize = node.nestedSupport.map(ns => (raw: Any) => ns.sanitize(raw)),
          schemaType     = schemaTypeFor(jType),
          arrayItemType  = "any",
          schemaFn       = node.nestedSupport.map(ns => () => ns.schema)
        )
      )

  // ── Auto constructor builders ─────────────────────────────────────────────

  /**
   * Build a reflective constructor function for a **Java record** (Java 16+).
   *
   * Uses `getRecordComponents()` to determine field names and their exact types
   * in canonical declaration order, then reflectively invokes the canonical
   * constructor.  Called once at contract-creation time; zero overhead per
   * validation request.
   *
   * @throws IllegalArgumentException if the class is not a Java record or is
   *                                  running on a JVM older than 16.
   */
  def buildRecordConstructFn[T](
    clazz: Class[T]
  ): java.util.function.Function[java.util.Map[String, AnyRef], T] =
    require(isRecord(clazz),
      s"${clazz.getName} is not a Java record (or JVM < 16). " +
      "Use JvmContract.of(...) with an explicit constructFn instead.")

    val rcMethod   = classOf[Class[?]].getMethod("getRecordComponents")
    val components = rcMethod.invoke(clazz).asInstanceOf[Array[AnyRef]]

    val rcClass  = components(0).getClass
    val getName  = rcClass.getMethod("getName")
    val getType  = rcClass.getMethod("getType")

    val info: List[(String, Class[?])] = components.toList.map { c =>
      getName.invoke(c).asInstanceOf[String] ->
      getType.invoke(c).asInstanceOf[Class[?]]
    }

    // The canonical constructor has exactly the component types in declaration order.
    val ctor = clazz.getDeclaredConstructor(info.map(_._2)*)
    ctor.setAccessible(true)

    (fields: java.util.Map[String, AnyRef]) =>
      val args: Array[AnyRef] = info.map { (name, _) => fields.get(name) }.toArray
      try ctor.newInstance(args*).asInstanceOf[T]
      catch
        case e: java.lang.reflect.InvocationTargetException => throw e.getCause

  /**
   * Build a reflective constructor function using the **primary non-synthetic
   * constructor** — the right choice for Kotlin data classes and Java POJOs.
   *
   * Kotlin's compiler generates a synthetic "default-argument" constructor
   * alongside the real primary constructor.  That synthetic one is marked
   * `ACC_SYNTHETIC` and is filtered out here so we always target the real one.
   *
   * @param fieldOrder  The field names in the order they appear in the primary
   *                    constructor's parameter list.  Pass `fieldMetas.map(_.name)`
   *                    from the corresponding [[JvmContractDeriver.derive]] call.
   *
   * @throws IllegalArgumentException if no non-synthetic constructor is found.
   */
  def buildPrimaryConstructFn[T](
    clazz:      Class[T],
    fieldOrder: List[String]
  ): java.util.function.Function[java.util.Map[String, AnyRef], T] =
    val ctor = clazz.getDeclaredConstructors.toList
      .filterNot(_.isSynthetic)
      .sortBy(-_.getParameterCount)
      .headOption
      .getOrElse(throw new IllegalArgumentException(
        s"No non-synthetic constructor found on ${clazz.getName}. " +
        "Use JvmContract.of(...) with an explicit constructFn instead."))
    ctor.setAccessible(true)

    (fields: java.util.Map[String, AnyRef]) =>
      val args: Array[AnyRef] = fieldOrder.map(name => fields.get(name)).toArray
      try ctor.newInstance(args*).asInstanceOf[T]
      catch
        case e: java.lang.reflect.InvocationTargetException => throw e.getCause

  private[concentric] def buildPrimaryConstructFnFromDerived[T](
    clazz:      Class[T],
    fieldOrder: List[DerivedField]
  ): java.util.function.Function[java.util.Map[String, AnyRef], T] =
    buildPrimaryConstructFn(clazz, fieldOrder.map(_.name))

  // ── JSON Schema type hint ─────────────────────────────────────────────────

  private def schemaTypeFor(jType: java.lang.reflect.Type): String =
    import java.lang.reflect.ParameterizedType
    jType match
      case c: Class[?] =>
        if      c == classOf[String]            then "string"
        else if c == classOf[java.lang.Integer]
             || c == java.lang.Integer.TYPE     then "integer"
        else if c == classOf[java.lang.Long]
             || c == java.lang.Long.TYPE        then "integer"
        else if c == classOf[java.lang.Double]
             || c == java.lang.Double.TYPE      then "number"
        else if c == classOf[java.lang.Float]
             || c == java.lang.Float.TYPE       then "number"
        else if c == classOf[java.lang.Boolean]
             || c == java.lang.Boolean.TYPE     then "boolean"
        else "object"
      case pt: ParameterizedType =>
        val raw = pt.getRawType.asInstanceOf[Class[?]]
        if      raw == classOf[java.util.Optional[?]] then
          schemaTypeFor(pt.getActualTypeArguments()(0))
        else if raw == classOf[java.util.List[?]]     then "array"
        else "object"
      case _ => "any"

  private def optionalKindFor(
    field: Field,
    jType: java.lang.reflect.Type,
    anns: List[Annotation],
    kotlinParam: Option[KotlinSupport.ParamInfo]
  ): OptionalKind =
    if JvmTypeDecoder.isOptionalType(jType) then OptionalKind.JavaOptional
    else
      val fieldClass = field.getType
      if kotlinParam.exists(_.isNullable) || (!fieldClass.isPrimitive && hasNullableAnnotation(anns)) then OptionalKind.Nullable
      else OptionalKind.Required

  private def hasNullableAnnotation(anns: List[Annotation]): Boolean =
    val names = Set(
      "org.jetbrains.annotations.Nullable",
      "javax.annotation.Nullable",
      "jakarta.annotation.Nullable",
      "androidx.annotation.Nullable",
      "android.annotation.Nullable"
    )
    anns.exists(a => names.contains(a.annotationType().getName))
