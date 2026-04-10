package io.dsentric

/**
 * Type-safe filter builder for a single field of a contract type T.
 *
 * Instances are created by [[Filter.field]] — a compile-time macro that
 * extracts the field name from a selector lambda and summons the appropriate
 * [[RawDecoder]] at compile time.
 *
 * {{{
 *   // ff: FieldFilter[User, Int]
 *   val ff = userContract.filter.field(_.age)
 *
 *   val expr: FilterExpr[User] = ff.gte(18) && ff.lt(65)
 * }}}
 *
 * @tparam T  The contract type (e.g. `User`).
 * @tparam A  The field's value type (e.g. `Int`, `String`, `Option[String]`).
 */
final class FieldFilter[T, A](val fieldName: String, val decoder: RawDecoder[A]):

  // ── Existence ─────────────────────────────────────────────────────────────

  /** The field is present and non-null.  MongoDB: `{field: {$exists: true}}`. */
  def exists: FilterExpr[T] = FilterExpr.Exists[T](fieldName, mustExist = true)

  /** The field is absent or null.  MongoDB: `{field: {$exists: false}}`. */
  def notExists: FilterExpr[T] = FilterExpr.Exists[T](fieldName, mustExist = false)

  // ── Equality ──────────────────────────────────────────────────────────────

  /** `field == value`.  MongoDB: `{field: value}`. */
  def is(value: A): FilterExpr[T] =
    FilterExpr.Eq[T, A](fieldName, value, decoder.decode)

  /** Alias for [[is]]. */
  def ===(value: A): FilterExpr[T] = is(value)

  /** `field != value`.  MongoDB: `{field: {$ne: value}}`. */
  def isNot(value: A): FilterExpr[T] =
    FilterExpr.Neq[T, A](fieldName, value, decoder.decode)

  /** Alias for [[isNot]]. */
  def !==(value: A): FilterExpr[T] = isNot(value)

  // ── Ordered comparison — requires Ordering[A] ─────────────────────────────

  /** `field > value`.  MongoDB: `{field: {$gt: value}}`. */
  def gt(value: A)(using ord: Ordering[A]): FilterExpr[T] =
    FilterExpr.Cmp[T, A](fieldName, value, "$gt", ord.gt, decoder.decode)

  /** Alias for [[gt]]. */
  def >(value: A)(using Ordering[A]): FilterExpr[T] = gt(value)

  /** `field >= value`.  MongoDB: `{field: {$gte: value}}`. */
  def gte(value: A)(using ord: Ordering[A]): FilterExpr[T] =
    FilterExpr.Cmp[T, A](fieldName, value, "$gte", ord.gteq, decoder.decode)

  /** Alias for [[gte]]. */
  def >=(value: A)(using Ordering[A]): FilterExpr[T] = gte(value)

  /** `field < value`.  MongoDB: `{field: {$lt: value}}`. */
  def lt(value: A)(using ord: Ordering[A]): FilterExpr[T] =
    FilterExpr.Cmp[T, A](fieldName, value, "$lt", ord.lt, decoder.decode)

  /** Alias for [[lt]]. */
  def <(value: A)(using Ordering[A]): FilterExpr[T] = lt(value)

  /** `field <= value`.  MongoDB: `{field: {$lte: value}}`. */
  def lte(value: A)(using ord: Ordering[A]): FilterExpr[T] =
    FilterExpr.Cmp[T, A](fieldName, value, "$lte", ord.lteq, decoder.decode)

  /** Alias for [[lte]]. */
  def <=(value: A)(using Ordering[A]): FilterExpr[T] = lte(value)

  // ── Set membership ────────────────────────────────────────────────────────

  /** `field ∈ values`.  MongoDB: `{field: {$in: [...]}}`. */
  def in(values: Seq[A]): FilterExpr[T] =
    FilterExpr.In[T, A](fieldName, values, decoder.decode)

  /** `field ∉ values`.  MongoDB: `{field: {$nin: [...]}}`. */
  def notIn(values: Seq[A]): FilterExpr[T] =
    FilterExpr.NotIn[T, A](fieldName, values, decoder.decode)


/**
 * Extension methods for `FieldFilter[T, Option[A]]` — convenience wrappers
 * that avoid writing `Some(...)` at every call site for optional fields.
 *
 * {{{
 *   // email: Option[String]
 *   val f = userContract.filter.field(_.email)
 *   f.isSome("alice@example.com")  // matches when email is present and equals the value
 *   f.isNone                       // matches when email is absent or null
 * }}}
 */
extension [T, A](ff: FieldFilter[T, Option[A]])

  /** Passes when the optional field is present and its inner value equals `value`. */
  def isSome(value: A)(using d: RawDecoder[A]): FilterExpr[T] =
    FilterExpr.Eq[T, Option[A]](ff.fieldName, Some(value), ff.decoder.decode)

  /** Passes when the optional field is absent or null (equivalent to `notExists`). */
  def isNone: FilterExpr[T] =
    FilterExpr.Exists[T](ff.fieldName, mustExist = false)

  /** Passes when the optional field is present and non-null (same as `exists`). */
  def isDefined: FilterExpr[T] = ff.exists

  /** Passes when the optional field is absent or null (same as `notExists`). */
  def isEmpty: FilterExpr[T] = ff.notExists


/**
 * Extension methods for `FieldFilter[T, String]` — string-specific predicates.
 *
 * These are defined as extensions so that `FieldFilter[T, A]` stays generic
 * and the string operations only surface when the field type is exactly
 * `String`.
 */
extension [T](ff: FieldFilter[T, String])

  /**
   * Substring containment.
   *
   * In-memory: `String.contains`.
   * MongoDB: `{field: {$regex: "<escaped-substring>"}}`.
   */
  def contains(substring: String): FilterExpr[T] =
    FilterExpr.StringContains[T](ff.fieldName, substring)

  /**
   * Prefix match.
   *
   * In-memory: `String.startsWith`.
   * MongoDB: `{field: {$regex: "^<escaped-prefix>"}}`.
   */
  def startsWith(prefix: String): FilterExpr[T] =
    FilterExpr.StringStartsWith[T](ff.fieldName, prefix)

  /**
   * Full regex match (anchored — equivalent to `java.lang.String.matches`).
   *
   * In-memory: `String.matches(pattern)`.
   * MongoDB: `{field: {$regex: "^(?:pattern)$"}}`.
   */
  def matches(pattern: String): FilterExpr[T] =
    FilterExpr.StringMatches[T](ff.fieldName, pattern)
