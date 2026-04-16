package io.dsentric

import scala.jdk.CollectionConverters.*

/**
 * Open-contract variant of [[JvmContract]] for Java and Kotlin consumers.
 *
 * Validation preserves undeclared fields and returns them alongside the typed
 * declared value.
 */
class JvmOpenContract[T] private (private val underlying: JvmContract[T]):

  val fieldMetas: List[FieldMeta] = underlying.fieldMetas
  private val declaredNames: Set[String] = fieldMetas.map(_.name).toSet

  def validate(raw: java.util.Map[String, AnyRef]): OpenValidationResult[T] =
    toOpenResult(underlying.validate(raw), underlying.extraFields(raw))

  def validatePatch(
    current: java.util.Map[String, AnyRef],
    patch:   java.util.Map[String, AnyRef]
  ): OpenValidationResult[T] =
    val mergedRaw =
      RawObjectOps.deltaTraverseConcat(
        toScalaRaw(current),
        toScalaRaw(patch)
      )
    val extras = mergedRaw.filterNot { case (k, _) => declaredNames.contains(k) }
    toOpenResult(
      underlying.validatePatch(current, patch),
      JvmValueConversions.deepToJava(extras).asInstanceOf[java.util.Map[String, AnyRef]]
    )

  def validatePatch(
    current: java.util.Map[String, AnyRef],
    patch:   JvmPatch
  ): OpenValidationResult[T] =
    validatePatch(current, patch.toMap())

  def validatePartial(raw: java.util.Map[String, AnyRef]): java.util.List[JvmViolation] =
    underlying.validatePartial(raw)

  def sanitize(raw: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] =
    underlying.sanitize(raw)

  def collectViolations(raw: java.util.Map[String, AnyRef]): java.util.List[JvmViolation] =
    underlying.collectViolations(raw)

  def toRaw(t: T): java.util.Map[String, AnyRef] =
    underlying.toRaw(t)

  def toJson(t: T): String = underlying.toJson(t)
  def toJson(t: T, indent: Int): String = underlying.toJson(t, indent)
  def sanitizeJson(raw: java.util.Map[String, AnyRef]): String = underlying.sanitizeJson(raw)
  def sanitizeJson(raw: java.util.Map[String, AnyRef], indent: Int): String =
    underlying.sanitizeJson(raw, indent)

  def jsonSchemaJson(): String = underlying.jsonSchemaJson()
  def jsonSchemaJson(indent: Int): String = underlying.jsonSchemaJson(indent)
  def jsonSchema(): java.util.Map[String, AnyRef] = underlying.jsonSchema()

  def extraFields(raw: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] =
    underlying.extraFields(raw)

  private def toScalaRaw(javaMap: java.util.Map[String, AnyRef]): RawObject =
    javaMap.asScala.iterator.map { case (k, v) => k -> JvmValueConversions.deepToScala(v) }.toMap

  private def toOpenResult(
    result: ValidationResult[T],
    extras: java.util.Map[String, AnyRef]
  ): OpenValidationResult[T] =
    if result.isValid then OpenValidationResult.success(result.getValue.get, extras)
    else OpenValidationResult.failure(result.getErrors.asScala.toList)

object JvmOpenContract:

  def of[T](
    clazz: Class[T],
    constructFn: java.util.function.Function[java.util.Map[String, AnyRef], T]
  ): JvmOpenContract[T] =
    new JvmOpenContract(new JvmContract(clazz, constructFn, true))

  def ofRecord[T](clazz: Class[T]): JvmOpenContract[T] =
    new JvmOpenContract(JvmContract.ofRecord(clazz, true))

  def ofPrimary[T](clazz: Class[T]): JvmOpenContract[T] =
    new JvmOpenContract(JvmContract.ofPrimary(clazz, true))
