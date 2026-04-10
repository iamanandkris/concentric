package io.dsentric

/**
 * Java/Kotlin-friendly validation violation.
 *
 * All fields are plain JVM types (String) — no Scala-specific wrappers such as
 * [[ViolationCode]] or [[FieldPath]].  Accessor methods follow the JavaBean
 * convention so that Java callers can use either direct field access or getters.
 *
 * @param path     Dot-separated field path (e.g. `"address.city"`).
 *                 Empty string when the violation is at the contract root
 *                 (e.g. from a cross-field validator).
 * @param code     Short symbolic code for programmatic switch/when handling.
 *                 Possible values: `"MISSING"`, `"TYPE_MISMATCH"`,
 *                 `"CONSTRAINT(name)"`, `"IMMUTABLE"`, `"RESERVED"`,
 *                 `"UNKNOWN_FIELD"`, `"CONTRACT_RULE(name)"`.
 * @param message  Human-readable description suitable for API error responses.
 */
final class JvmViolation(val path: String, val code: String, val message: String):
  def getPath:    String = path
  def getCode:    String = code
  def getMessage: String = message
  override def toString: String = s"JvmViolation(path=$path, code=$code)"

object JvmViolation:
  /** Convert an internal [[Violation]] to a [[JvmViolation]]. */
  def fromViolation(v: Violation): JvmViolation =
    val path = v.path.segments.mkString(".")
    val code = v.code match
      case ViolationCode.ExpectedMissing        => "MISSING"
      case ViolationCode.TypeMismatch(exp, act) => s"TYPE_MISMATCH($exp,$act)"
      case ViolationCode.ConstraintFailed(c)    => s"CONSTRAINT($c)"
      case ViolationCode.ImmutableField         => "IMMUTABLE"
      case ViolationCode.ReservedField          => "RESERVED"
      case ViolationCode.UnknownField           => "UNKNOWN_FIELD"
      case ViolationCode.ContractRule(r)        => s"CONTRACT_RULE($r)"
    new JvmViolation(path, code, v.message)
