package io.dsentric

import java.lang.annotation.Annotation
import java.lang.reflect.{Constructor, Field, Parameter}
import io.dsentric.annotations.*
import scala.reflect.ClassTag

/**
 * Derives a [[List]][[[FieldMeta]]] from a Java or Kotlin class by inspecting
 * its declared fields and annotations at runtime.
 *
 * == Annotation resolution ==
 *
 * dsentric annotations carry `@Retention(RUNTIME)` and target both
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
 *  - `@decodable` / `@extract` (wrapper-type macro derivation)
 *  - `@include` (field flattening from an inner contract type)
 *  - `@discriminator` (sealed-trait tagged unions)
 *  - `@validateContract` / `@validateWith` (cross-field / custom validators)
 *  - Nested `Contract[T]` validation for inner object fields
 */
object JvmContractDeriver:

  /** Derive [[FieldMeta]] for every non-synthetic, non-static declared field. */
  def derive[T](clazz: Class[T]): List[FieldMeta] =
    val fields     = getFieldsInOrder(clazz)
    val ctorParams = primaryConstructorParams(clazz)

    fields.zipWithIndex.map { (field, idx) =>
      val anns  = effectiveAnnotations(field, ctorParams.lift(idx))
      val jType = field.getGenericType
      val isOpt = JvmTypeDecoder.isOptionalType(jType)

      FieldMeta(
        name           = field.getName,
        isOptional     = isOpt,
        hasDefault     = false,          // Java/Kotlin defaults live in constructFn
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
        extractPattern = None,           // @extract is compile-time only
        decoder        = JvmTypeDecoder.forType(jType),
        schemaType     = schemaTypeFor(jType),
        arrayItemType  = "any"
      )
    }

  // ── Record support (Java 16+) ─────────────────────────────────────────────

  /**
   * True when running on JVM 16+ and the class is a record.
   * Uses reflection so the code compiles against any JDK but exploits the
   * real API when available.
   */
  private def isRecord(clazz: Class[?]): Boolean =
    try classOf[Class[?]].getMethod("isRecord").invoke(clazz).asInstanceOf[Boolean]
    catch case _: NoSuchMethodException => false

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
    clazz.getDeclaredConstructors.toList
      .sortBy(-_.getParameterCount)
      .headOption
      .fold(List.empty[Parameter]) { c =>
        c.setAccessible(true)
        c.getParameters.toList
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
