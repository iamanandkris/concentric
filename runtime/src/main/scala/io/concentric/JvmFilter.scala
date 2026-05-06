package io.concentric

import scala.jdk.CollectionConverters.*
import JvmValueConversions.*

/**
 * JVM filter builder for contract type `T`.
 *
 * Obtain an instance from [[JvmContract.filter]], then call [[field]] to get
 * a per-field builder:
 *
 * {{{
 * // Kotlin
 * val f = userContract.filter
 * val expr = f.field("age").gte(18)
 *     .and(f.field("name").startsWith("A"))
 *     .and(f.field("email").exists())
 *
 * val matching = expr.apply(allUsersRaw)
 * val mongoQ   = expr.toMongoQuery()
 * }}}
 */
final class JvmFilter[T]:

  /**
   * Return a [[JvmFieldFilter]] for the named field.
   *
   * The field name must match the name declared in your `@contract` data class.
   */
  def field(fieldName: String): JvmFieldFilter[T] = new JvmFieldFilter[T](fieldName)

object JvmFilter:
  /** Create a [[JvmFilter]] for the given class. */
  def of[T](clazz: Class[T]): JvmFilter[T] = new JvmFilter[T]


/**
 * Per-field filter builder.
 *
 * Produced by [[JvmFilter.field]].  All methods return a [[JvmFilterExpr]]
 * which can be combined with [[JvmFilterExpr.and]], [[JvmFilterExpr.or]],
 * and [[JvmFilterExpr.not]].
 */
final class JvmFieldFilter[T](fieldName: String):

  // ── Existence ──────────────────────────────────────────────────────────────

  /** The field is present and non-null.  MongoDB: `{field: {$exists: true}}`. */
  def exists(): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Exists[T](fieldName, true))

  /** The field is absent or null.  MongoDB: `{field: {$exists: false}}`. */
  def notExists(): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Exists[T](fieldName, false))

  // ── Numeric comparisons ────────────────────────────────────────────────────

  private val numDecode: Any => Option[Double] = {
    case n: Number => Some(n.doubleValue())
    case _         => None
  }

  /** `field > v`.  MongoDB: `{field: {$gt: v}}`. */
  def gt(v: Number): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Cmp[T, Double](fieldName, v.doubleValue(), "$gt", _ > _, numDecode))

  /** `field >= v`.  MongoDB: `{field: {$gte: v}}`. */
  def gte(v: Number): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Cmp[T, Double](fieldName, v.doubleValue(), "$gte", _ >= _, numDecode))

  /** `field < v`.  MongoDB: `{field: {$lt: v}}`. */
  def lt(v: Number): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Cmp[T, Double](fieldName, v.doubleValue(), "$lt", _ < _, numDecode))

  /** `field <= v`.  MongoDB: `{field: {$lte: v}}`. */
  def lte(v: Number): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Cmp[T, Double](fieldName, v.doubleValue(), "$lte", _ <= _, numDecode))

  // ── String comparisons ─────────────────────────────────────────────────────

  private val strDecode: Any => Option[String] = {
    case s: String => Some(s)
    case _         => None
  }

  /** `field > v` (lexicographic).  MongoDB: `{field: {$gt: v}}`. */
  def gt(v: String): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Cmp[T, String](fieldName, v, "$gt", _ > _, strDecode))

  /** `field >= v` (lexicographic).  MongoDB: `{field: {$gte: v}}`. */
  def gte(v: String): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Cmp[T, String](fieldName, v, "$gte", _ >= _, strDecode))

  /** `field < v` (lexicographic).  MongoDB: `{field: {$lt: v}}`. */
  def lt(v: String): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Cmp[T, String](fieldName, v, "$lt", _ < _, strDecode))

  /** `field <= v` (lexicographic).  MongoDB: `{field: {$lte: v}}`. */
  def lte(v: String): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Cmp[T, String](fieldName, v, "$lte", _ <= _, strDecode))

  // ── Equality ───────────────────────────────────────────────────────────────

  private val anyDecode: Any => Option[Any] = raw => Some(raw)

  /** `field == value`.  MongoDB: `{field: value}`. */
  def equalTo(value: AnyRef): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Eq[T, Any](fieldName, value, anyDecode))

  /** `field != value`.  MongoDB: `{field: {$ne: value}}`. */
  def notEqualTo(value: AnyRef): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.Neq[T, Any](fieldName, value, anyDecode))

  // ── Membership ─────────────────────────────────────────────────────────────

  /** `field ∈ values`.  MongoDB: `{field: {$in: [...]}}`. */
  def in(values: java.util.List[?]): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.In[T, Any](fieldName, values.asScala.toSeq.asInstanceOf[Seq[Any]], anyDecode))

  /** `field ∉ values`.  MongoDB: `{field: {$nin: [...]}}`. */
  def notIn(values: java.util.List[?]): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.NotIn[T, Any](fieldName, values.asScala.toSeq.asInstanceOf[Seq[Any]], anyDecode))

  // ── String-specific ────────────────────────────────────────────────────────

  /**
   * Prefix match.  MongoDB: `{field: {$regex: "^<escaped-prefix>"}}`.
   */
  def startsWith(prefix: String): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.StringStartsWith[T](fieldName, prefix))

  /**
   * Substring containment.  MongoDB: `{field: {$regex: "<escaped-substring>"}}`.
   */
  def contains(s: String): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.StringContains[T](fieldName, s))

  /**
   * Full regex match (anchored).  MongoDB: `{field: {$regex: "^(?:pattern)$"}}`.
   */
  def matches(pattern: String): JvmFilterExpr[T] =
    new JvmFilterExpr(FilterExpr.StringMatches[T](fieldName, pattern))


/**
 * A composable filter expression wrapping [[FilterExpr]][T].
 *
 * Combine expressions with [[and]], [[or]], [[not]], evaluate in-memory with
 * [[test]] or [[apply]], and export a MongoDB query document with
 * [[toMongoQuery]].
 */
final class JvmFilterExpr[T] private[concentric] (private[concentric] val expr: FilterExpr[T]):

  /** Logical AND of this and `other`. */
  def and(other: JvmFilterExpr[T]): JvmFilterExpr[T] =
    new JvmFilterExpr(expr && other.expr)

  /** Logical OR of this and `other`. */
  def or(other: JvmFilterExpr[T]): JvmFilterExpr[T] =
    new JvmFilterExpr(expr || other.expr)

  /** Logical NOT of this expression. */
  def not(): JvmFilterExpr[T] =
    new JvmFilterExpr(!expr)

  /**
   * Evaluate this expression against a single raw Java map.
   *
   * @return `true` when `raw` matches.
   */
  def test(raw: java.util.Map[String, AnyRef]): Boolean =
    expr.test(toScalaRaw(raw))

  /**
   * Filter a list of raw Java maps, returning only those that match.
   */
  def apply(rawList: java.util.List[java.util.Map[String, AnyRef]]): java.util.List[java.util.Map[String, AnyRef]] =
    rawList.asScala
      .filter(m => expr.test(toScalaRaw(m)))
      .map(identity)
      .toList
      .asJava

  /**
   * Translate this expression to a MongoDB-compatible query document.
   */
  def toMongoQuery(): java.util.Map[String, AnyRef] =
    toJavaRaw(expr.toMongoQuery)

  override def toString: String = s"JvmFilterExpr(${expr})"
