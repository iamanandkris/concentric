package io.dsentric


/**
 * A partially-validated contract object.
 *
 * Created by [[Contract.validatePartial]] — which runs all constraint
 * checks on the fields that are present but does NOT fail for absent
 * required fields.  This allows gradual construction of a contract object
 * across multiple steps.
 *
 * Typical workflow:
 * {{{
 *   // Step 1 — validate the first batch of fields (e.g. screen 1 of a form)
 *   for
 *     draft1 <- userContract.validatePartial(Map("id" -> 1L, "name" -> "Alice"))
 *
 *     // Step 2 — validate the next batch (e.g. screen 2 of a form)
 *     draft2 <- userContract.validatePartial(Map("age" -> 30))
 *
 *     // Step 3 — combine the two validated halves and finalize
 *     user <- draft1.merge(draft2).finalize(userContract)
 *   yield user
 * }}}
 *
 * @param validatedFields  The subset of fields that were present and
 *                         passed all constraint checks so far.
 */
final class Draft[T](val validatedFields: RawObject):

  /** The raw representation of validated fields so far. */
  def toRaw: RawObject = validatedFields

  /** True when no fields have been validated yet. */
  def isEmpty: Boolean = validatedFields.isEmpty

  /** Number of validated fields in this draft. */
  def size: Int = validatedFields.size

  /**
   * Merge another [[Draft]][T] into this one.
   *
   * Both halves have already been through [[Contract.validatePartial]], so
   * this is a safe combination of two partially-validated field sets.
   * Fields in `other` take precedence over fields with the same name in
   * this draft (right-biased), consistent with [[merge(RawObject)]].
   *
   * Use this when the fields for a single contract are collected in two
   * separate validation passes — e.g. from two different request payloads —
   * and you want to combine them before calling [[finalize]].
   *
   * Note: a [[Draft]][T] can only be obtained from a successful
   * [[Contract.validatePartial]] call, so both sides of the merge are
   * guaranteed to be violation-free by construction.  Violations cannot
   * be smuggled in through this overload — use [[merge(RawObject)]] if
   * you intentionally want to add unvalidated fields.
   */
  def merge(other: Draft[T]): Draft[T] = new Draft[T](validatedFields ++ other.validatedFields)

  /**
   * Attempt to finalize this draft into a fully-constructed `T`.
   *
   * Runs [[Contract.validate]] on the accumulated fields, which will
   * report any missing required fields or constraint violations.
   *
   * @param contract  The contract that governs T.
   */
  def finalize(contract: Contract[T]): Either[ContractViolations, T] =
    contract.validate(validatedFields)

  override def toString: String =
    s"Draft[${validatedFields.keys.mkString(", ")}]"

object Draft:
  /**
   * An empty draft with no validated fields.
   *
   * Useful as a starting point when combining two or more
   * [[Contract.validatePartial]] results via [[Draft.merge(Draft)]].
   *
   * Prefer [[Contract.validatePartial]] over `Draft.empty` + [[merge(RawObject)]]
   * when building a multi-step workflow — `validatePartial` gives immediate
   * per-step feedback, whereas `merge(RawObject)` is unvalidated and defers
   * all constraint checks to [[finalize]].
   */
  def empty[T]: Draft[T] = new Draft[T](Map.empty)
