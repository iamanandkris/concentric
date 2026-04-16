package io.concentric

/**
 * A type-safe, composable filter expression for contract type T.
 *
 * `FilterExpr[T]` represents a predicate that can be evaluated against a raw
 * object (`test`) or translated to a MongoDB-style query document
 * (`toMongoQuery`).
 *
 * Expressions are built via [[Filter]][T] and [[FieldFilter]][T, A]:
 *
 * {{{
 *   val f: FilterExpr[User] =
 *     userContract.filter.field(_.age).gte(18) &&
 *     userContract.filter.field(_.name).startsWith("A")
 *
 *   // In-memory predicate
 *   val matches: Boolean = f.test(rawMap)
 *
 *   // MongoDB query document
 *   val q: RawObject = f.toMongoQuery
 *   // → Map("age" -> Map("$gte" -> 18), "name" -> Map("$regex" -> "^A"))
 * }}}
 *
 * Logical combinators (`&&`, `||`, `!`) produce new `FilterExpr` values and
 * are safe to chain without limit.  The MongoDB output of `&&` uses a flat
 * merge when the two sides address different fields with no top-level logical
 * operators, falling back to `$and` otherwise.
 */
sealed trait FilterExpr[T]:

  /** Evaluate this expression against a raw (unvalidated) object. */
  def test(raw: RawObject): Boolean

  /**
   * Translate this expression to a MongoDB-compatible query document.
   *
   * For field-level expressions the result is a single-entry map
   * `{field: condition}`.  For logical expressions (`&&`, `||`, `!`)
   * the result uses `$and`, `$or`, or `$nor`.
   *
   * Note: the generated document is structurally correct for the MongoDB wire
   * protocol but no validation against the actual driver types is performed.
   */
  def toMongoQuery: RawObject

  /** Logical AND.  Smart-merges flat field conditions; falls back to `$and`. */
  def &&(other: FilterExpr[T]): FilterExpr[T] = FilterExpr.And(this, other)

  /** Logical OR.  Always emits `$or`. */
  def ||(other: FilterExpr[T]): FilterExpr[T] = FilterExpr.Or(this, other)

  /** Logical NOT.  Emits `$nor`. */
  def unary_! : FilterExpr[T] = FilterExpr.Not(this)

  /**
   * Apply this expression as a predicate to a collection of raw objects and
   * return those that match.
   */
  def apply(rawItems: Iterable[RawObject]): List[RawObject] =
    rawItems.filter(test).toList


object FilterExpr:

  // ── Logical combinators ───────────────────────────────────────────────────

  /**
   * Logical AND of two expressions.
   *
   * MongoDB output is a flat merge (e.g. `{a: 1, b: 2}`) when:
   *  - neither side has top-level logical keys (`$and`, `$or`, `$nor`)
   *  - both sides address distinct fields (no overlapping keys)
   *
   * Otherwise falls back to explicit `{"$and": [...]}`.
   */
  final case class And[T](left: FilterExpr[T], right: FilterExpr[T]) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean = left.test(raw) && right.test(raw)
    def toMongoQuery: RawObject =
      val l = left.toMongoQuery
      val r = right.toMongoQuery
      val logicalKeys = Set("$and", "$or", "$nor")
      val lHasLogical = l.keys.exists(logicalKeys.contains)
      val rHasLogical = r.keys.exists(logicalKeys.contains)
      val overlapping = l.keySet.intersect(r.keySet)
      if !lHasLogical && !rHasLogical && overlapping.isEmpty then l ++ r
      else Map("$and" -> List(l, r))

  /** Logical OR — always emits `{"$or": [...]}`. */
  final case class Or[T](left: FilterExpr[T], right: FilterExpr[T]) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean = left.test(raw) || right.test(raw)
    def toMongoQuery: RawObject = Map("$or" -> List(left.toMongoQuery, right.toMongoQuery))

  /**
   * Logical NOT — emits `{"$nor": [...]}`.
   *
   * MongoDB's `$not` only applies at the field level; `$nor` is the correct
   * document-level negation operator.
   */
  final case class Not[T](expr: FilterExpr[T]) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean = !expr.test(raw)
    def toMongoQuery: RawObject = Map("$nor" -> List(expr.toMongoQuery))

  // ── Field-level expressions ───────────────────────────────────────────────

  /** `{field: {$exists: bool}}` — tests field presence (non-null). */
  final case class Exists[T](field: String, mustExist: Boolean) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean =
      val present = raw.contains(field) && raw(field) != null && raw(field) != None
      if mustExist then present else !present
    def toMongoQuery: RawObject =
      Map(field -> Map("$exists" -> mustExist))

  /**
   * `{field: value}` — equality.
   *
   * @param decode  The field's [[RawDecoder]] decode function (captured at
   *                macro time, evaluated at test time).
   */
  final case class Eq[T, A](
    field:  String,
    value:  A,
    decode: Any => Option[A]
  ) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean = raw.get(field).flatMap(decode).contains(value)
    def toMongoQuery: RawObject = Map(field -> value.asInstanceOf[Any])

  /** `{field: {$ne: value}}` — inequality. */
  final case class Neq[T, A](
    field:  String,
    value:  A,
    decode: Any => Option[A]
  ) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean = !raw.get(field).flatMap(decode).contains(value)
    def toMongoQuery: RawObject = Map(field -> Map("$ne" -> value.asInstanceOf[Any]))

  /**
   * Ordered comparison (`$gt`, `$gte`, `$lt`, `$lte`).
   *
   * @param mongoOp  The MongoDB operator string, e.g. `"$gt"`.
   * @param pred     Binary predicate derived from `Ordering[A]` at build time.
   */
  final case class Cmp[T, A](
    field:   String,
    value:   A,
    mongoOp: String,
    pred:    (A, A) => Boolean,
    decode:  Any => Option[A]
  ) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean = raw.get(field).flatMap(decode).exists(pred(_, value))
    def toMongoQuery: RawObject = Map(field -> Map(mongoOp -> value.asInstanceOf[Any]))

  /**
   * `{field: {$in: [...]}}` — membership test.
   *
   * Pre-computes a `Set` for O(1) in-memory lookup while keeping the original
   * sequence for correct MongoDB serialisation order.
   */
  final case class In[T, A](
    field:  String,
    values: Seq[A],
    decode: Any => Option[A]
  ) extends FilterExpr[T]:
    private val valueSet: Set[A] = values.toSet
    def test(raw: RawObject): Boolean = raw.get(field).flatMap(decode).exists(valueSet.contains)
    def toMongoQuery: RawObject = Map(field -> Map("$in" -> values.toList.map(_.asInstanceOf[Any])))

  /** `{field: {$nin: [...]}}` — non-membership. */
  final case class NotIn[T, A](
    field:  String,
    values: Seq[A],
    decode: Any => Option[A]
  ) extends FilterExpr[T]:
    private val valueSet: Set[A] = values.toSet
    def test(raw: RawObject): Boolean = !raw.get(field).flatMap(decode).exists(valueSet.contains)
    def toMongoQuery: RawObject = Map(field -> Map("$nin" -> values.toList.map(_.asInstanceOf[Any])))

  // ── String-specific expressions ───────────────────────────────────────────

  /**
   * Substring containment check.
   *
   * In-memory: `String.contains`.
   * MongoDB: `{field: {$regex: substring}}` (literal, unanchored).
   */
  final case class StringContains[T](field: String, substring: String) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean = raw.get(field) match
      case Some(s: String) => s.contains(substring)
      case _               => false
    def toMongoQuery: RawObject =
      Map(field -> Map("$regex" -> java.util.regex.Pattern.quote(substring)))

  /**
   * Prefix match.
   *
   * In-memory: `String.startsWith`.
   * MongoDB: `{field: {$regex: "^<escaped-prefix>"}}`.
   */
  final case class StringStartsWith[T](field: String, prefix: String) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean = raw.get(field) match
      case Some(s: String) => s.startsWith(prefix)
      case _               => false
    def toMongoQuery: RawObject =
      Map(field -> Map("$regex" -> s"^${java.util.regex.Pattern.quote(prefix)}"))

  /**
   * Regex pattern match (full string, anchored).
   *
   * In-memory: `String.matches` (anchored, Java regex).
   * MongoDB: `{field: {$regex: pattern}}` (also anchored with `^...$`).
   */
  final case class StringMatches[T](field: String, pattern: String) extends FilterExpr[T]:
    def test(raw: RawObject): Boolean = raw.get(field) match
      case Some(s: String) => s.matches(pattern)
      case _               => false
    def toMongoQuery: RawObject =
      val anchored = if pattern.startsWith("^") && pattern.endsWith("$") then pattern
                     else s"^(?:$pattern)$$"
      Map(field -> Map("$regex" -> anchored))
