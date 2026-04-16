package io.concentric

/**
 * Contract for a synchronous cross-field validator.
 *
 * Implement this trait to express invariants that span multiple fields of `T`
 * — rules that cannot be expressed with per-field annotations such as
 * `@validateWith`, `@min`, or `@email`.
 *
 * Implementations must have a public no-argument constructor so the contract
 * engine can instantiate them via reflection at derivation time.
 *
 * == Lifecycle ==
 *
 * A [[ContractValidator]] runs *after* all field-level validation has passed
 * and the fully typed `T` has been constructed.  It therefore receives a
 * well-formed value: all required fields are present, all type coercions have
 * succeeded, and all per-field constraints (minLength, email, etc.) are
 * satisfied.  If any field-level violation exists the contract validators are
 * not called.
 *
 * == Violation path ==
 *
 * Violations produced by a [[ContractValidator]] carry an empty path
 * (`FieldPath(Nil)`) because they are not attributable to a single field.
 * When reporting errors to an API caller you can surface them under a
 * dedicated key (e.g. `"_errors"`) or merge them with the field map.
 *
 * == Example ==
 *
 * {{{
 *   class CheckInBeforeCheckOut extends ContractValidator[Booking]:
 *     def validate(b: Booking): List[String] =
 *       if b.checkIn >= b.checkOut
 *       then List("checkIn must be before checkOut")
 *       else Nil
 *
 *   @contract
 *   @validateContract(Array(classOf[CheckInBeforeCheckOut]))
 *   case class Booking(checkIn: IsoDate, checkOut: IsoDate)
 * }}}
 *
 * @tparam T  The contract type this validator operates on.
 */
trait ContractValidator[T]:
  /**
   * Validate a fully-constructed `T` and return a list of human-readable
   * error messages.  Return `Nil` when the value is valid.
   */
  def validate(value: T): List[String]
