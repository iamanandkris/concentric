package io.concentric

import scala.jdk.CollectionConverters.*

/**
 * Synchronous, Java/Kotlin-friendly result of a contract validation operation.
 *
 * Intentionally avoids Scala-specific effect types.  Use [[isValid]] to branch,
 * then [[getValue]] or [[getErrors]]:
 *
 * {{{
 * // Java
 * ValidationResult<User> result = userContract.validate(raw);
 * if (result.isValid()) {
 *     User user = result.getValue().get();
 * } else {
 *     result.getErrors().forEach(e -> log.warn(e.getMessage()));
 * }
 *
 * // Kotlin
 * val result = userContract.validate(raw)
 * if (result.isValid) {
 *     val user = result.value.get()
 * } else {
 *     result.errors.forEach { println(it.message) }
 * }
 * }}}
 */
final class ValidationResult[T] private[concentric] (
  private val _value:  Option[T],
  private val _errors: List[JvmViolation]
):
  /** `true` when validation succeeded and [[getValue]] is non-empty. */
  def isValid: Boolean = _errors.isEmpty

  /**
   * The validated value, present only when [[isValid]] is `true`.
   * Returns [[java.util.Optional.empty]] on validation failure.
   */
  def getValue: java.util.Optional[T] =
    _value.fold(java.util.Optional.empty[T]())(java.util.Optional.of)

  /**
   * The list of validation failures.  Empty when [[isValid]] is `true`.
   * Returned as a [[java.util.List]] for Java/Kotlin convenience.
   */
  def getErrors: java.util.List[JvmViolation] = _errors.asJava

  // Kotlin-style property aliases (no `get` prefix)
  def value:  java.util.Optional[T]          = getValue
  def errors: java.util.List[JvmViolation]   = getErrors

  override def toString: String =
    if isValid then s"ValidationResult.success(${_value.get})"
    else           s"ValidationResult.failure(${_errors.map(_.code).mkString(", ")})"

object ValidationResult:
  def success[T](value: T): ValidationResult[T] =
    new ValidationResult[T](Some(value), Nil)

  def failure[T](errors: List[JvmViolation]): ValidationResult[T] =
    new ValidationResult[T](None, errors)
