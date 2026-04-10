package io.dsentric

import io.dsentric.NonEmptyList

// ── Violation codes ──────────────────────────────────────────────────────────

/** Describes the category of a validation failure. */
enum ViolationCode:
  /** A required field was absent from the input. */
  case ExpectedMissing

  /** The field value could not be decoded to the expected type. */
  case TypeMismatch(expected: String, actual: String)

  /** A field-level constraint annotation was not satisfied. */
  case ConstraintFailed(constraint: String)

  /** An @immutable field was included in a patch / update operation. */
  case ImmutableField

  /** A @reserved field was supplied by the caller. */
  case ReservedField

  /** A field not declared in the contract was present in the input,
   *  and the contract is closed (open = false). */
  case UnknownField

  /** A cross-field or contract-level rule was violated. */
  case ContractRule(rule: String)

// ── Violation ────────────────────────────────────────────────────────────────

/**
 * A single validation failure, carrying its location and reason.
 *
 * @param path     The [[FieldPath]] pointing to the offending field.
 * @param code     Structured [[ViolationCode]] for programmatic handling.
 * @param message  Human-readable description (suitable for API error responses).
 */
final case class Violation(
  path:    FieldPath,
  code:    ViolationCode,
  message: String
)

// ── ContractViolations ────────────────────────────────────────────────────────

/**
 * A non-empty collection of [[Violation]]s, used as the error channel of
 * every contract operation.
 *
 * Using [[io.dsentric.NonEmptyList]] guarantees that a failure always carries
 * at least one violation; callers never need to guard against an empty list.
 */
final case class ContractViolations(violations: NonEmptyList[Violation]) {

  /** Combine two violation sets — useful when accumulating from sub-contracts. */
  def ++(other: ContractViolations): ContractViolations =
    ContractViolations(violations ++ other.violations)

  /** Number of individual violations. */
  def size: Int = violations.size

  /** Flat list of human-readable messages, for logging / simple error display. */
  def messages: List[String] = violations.map(_.message).toList

  override def toString: String =
    violations.map(v => s"  [${v.path}] ${v.message}").mkString("ContractViolations(\n", "\n", "\n)")
}

object ContractViolations {

  /** Build from a single violation. */
  def single(path: FieldPath, code: ViolationCode, message: String): ContractViolations =
    ContractViolations(NonEmptyList(Violation(path, code, message)))

  /** Build from a non-empty sequence; returns None if the sequence is empty. */
  def fromList(vs: List[Violation]): Option[ContractViolations] =
    NonEmptyList.fromIterableOption(vs).map(ContractViolations(_))
}
