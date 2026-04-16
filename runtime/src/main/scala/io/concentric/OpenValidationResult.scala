package io.concentric

import scala.jdk.CollectionConverters.*

/**
 * Synchronous, Java/Kotlin-friendly result of an open-contract validation.
 *
 * Carries both the typed declared value and any undeclared extra fields.
 */
final class OpenValidationResult[T] private[concentric] (
  private val _value:  Option[T],
  private val _extras: java.util.Map[String, AnyRef],
  private val _errors: List[JvmViolation]
):
  def isValid: Boolean = _errors.isEmpty

  def getValue: java.util.Optional[T] =
    _value.fold(java.util.Optional.empty[T]())(java.util.Optional.of)

  /** Unknown fields preserved from the raw input. Empty on validation failure. */
  def getExtras: java.util.Map[String, AnyRef] = _extras

  def getErrors: java.util.List[JvmViolation] = _errors.asJava

  def value: java.util.Optional[T] = getValue
  def extras: java.util.Map[String, AnyRef] = getExtras
  def errors: java.util.List[JvmViolation] = getErrors

  override def toString: String =
    if isValid then s"OpenValidationResult.success(value=${_value.get}, extras=${_extras})"
    else s"OpenValidationResult.failure(${_errors.map(_.code).mkString(", ")})"

object OpenValidationResult:
  def success[T](value: T, extras: java.util.Map[String, AnyRef]): OpenValidationResult[T] =
    new OpenValidationResult[T](Some(value), extras, Nil)

  def failure[T](errors: List[JvmViolation]): OpenValidationResult[T] =
    new OpenValidationResult[T](
      None,
      java.util.Collections.emptyMap[String, AnyRef](),
      errors
    )

