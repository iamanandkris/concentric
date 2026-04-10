package io.dsentric

/**
 * Lightweight, zero-dependency JSON serialiser for [[RawObject]] values.
 *
 * Converts the untyped `Map[String, Any]` produced by operations such as
 * [[Contract.sanitize]] and [[Contract.toRaw]] into a JSON string.  Handles
 * every value type that can legally appear in a validated [[RawObject]]:
 *
 *  - `String`                     → `"escaped string"`
 *  - `Boolean`                    → `true` / `false`
 *  - `Int`, `Long`, `BigInt`      → integer literal
 *  - `Double`, `Float`, `BigDecimal` → decimal literal (`NaN`/`Infinity` → `null`)
 *  - `Map[String, Any]`           → JSON object (keys sorted, `None`/`null` values omitted)
 *  - `Seq[Any]` / `Array[Any]`    → JSON array
 *  - `None` / `null`              → omitted when inside an object; `null` in arrays
 *  - `Some(v)`                    → unwrapped and serialised as `v`
 *  - `java.util.Optional`         → empty → `null`; present → inner value
 *  - `java.util.Map`              → JSON object (Java interop)
 *  - `java.util.Collection`       → JSON array (Java interop)
 *  - Anything else                → `toString`, then treated as a string
 *
 * == Scala usage ==
 *
 * {{{
 *   val raw     = userContract.sanitize(storedRaw)
 *   val compact = RawJson.stringify(raw)
 *   // → {"age":30,"email":"alice@example.com","name":"Alice"}
 *
 *   val pretty  = RawJson.stringify(raw, indent = 2)
 *   // → {
 *   //     "age": 30,
 *   //     "email": "alice@example.com",
 *   //     "name": "Alice"
 *   //   }
 * }}}
 *
 * == Convenience methods ==
 *
 * [[Contract]] and `JvmContract` expose `sanitizeJson` and `toJson` which call
 * this internally, so most callers never need to reference `RawJson` directly.
 *
 * == Third-party JSON libraries ==
 *
 * When you already have circe, Jackson, or play-json on the classpath it is
 * often more convenient to convert the `RawObject` via that library's own
 * `Map → Json` conversion rather than going through `RawJson`.  `RawJson` is
 * provided as a self-contained default that works with no additional
 * dependencies.
 */
object RawJson:

  /**
   * Serialize `raw` to a compact, single-line JSON string.
   *
   * Object keys are sorted alphabetically for deterministic output.
   * Fields whose value is `None` or `null` are omitted.
   */
  def stringify(raw: RawObject): String =
    renderValue(raw, indent = 0, depth = 0, pretty = false)

  /**
   * Serialize `raw` to an indented, human-readable JSON string.
   *
   * @param raw     The raw object to serialise.
   * @param indent  Number of spaces per indentation level.  Defaults to `2`.
   */
  def stringify(raw: RawObject, indent: Int): String =
    renderValue(raw, indent = indent, depth = 0, pretty = true)

  // ── Java-friendly overloads ───────────────────────────────────────────────

  /**
   * Compact JSON from a Java `Map<String, Object>`.  Useful when calling
   * directly on the result of [[io.dsentric.JvmContract#sanitize]].
   */
  def stringify(javaMap: java.util.Map[String, AnyRef]): String =
    import scala.jdk.CollectionConverters.*
    stringify(javaMap.asScala.toMap)

  /**
   * Pretty-printed JSON from a Java `Map<String, Object>`.
   */
  def stringify(javaMap: java.util.Map[String, AnyRef], indent: Int): String =
    import scala.jdk.CollectionConverters.*
    stringify(javaMap.asScala.toMap, indent)

  // ── Core rendering ────────────────────────────────────────────────────────

  private def renderValue(v: Any, indent: Int, depth: Int, pretty: Boolean): String =
    v match
      case null | None                           => "null"
      case Some(inner)                           => renderValue(inner, indent, depth, pretty)
      case opt: java.util.Optional[?] if opt.isEmpty => "null"
      case opt: java.util.Optional[?]            => renderValue(opt.get(), indent, depth, pretty)
      case s: String                             => "\"" + escape(s) + "\""
      case b: Boolean                            => b.toString
      case n: Int                                => n.toString
      case n: Long                               => n.toString
      case n: BigInt                             => n.toString
      case n: Double   if n.isNaN || n.isInfinite => "null"
      case n: Double                             =>
        java.math.BigDecimal.valueOf(n).stripTrailingZeros.toPlainString
      case n: Float    if n.isNaN || n.isInfinite => "null"
      case n: Float                              =>
        java.math.BigDecimal.valueOf(n.toDouble).stripTrailingZeros.toPlainString
      case n: BigDecimal                         => n.underlying.stripTrailingZeros.toPlainString
      case n: Number                             => n.toString
      case m: Map[?, ?]                          =>
        renderObject(m.asInstanceOf[Map[String, Any]], indent, depth, pretty)
      case m: java.util.Map[?, ?]                =>
        import scala.jdk.CollectionConverters.*
        renderObject(m.asScala.toMap.asInstanceOf[Map[String, Any]], indent, depth, pretty)
      case arr: Array[?]                         =>
        renderArray(arr.toSeq, indent, depth, pretty)
      case seq: Iterable[?]                      =>
        renderArray(seq.toSeq, indent, depth, pretty)
      case col: java.util.Collection[?]          =>
        import scala.jdk.CollectionConverters.*
        renderArray(col.asScala.toSeq, indent, depth, pretty)
      case other                                 =>
        "\"" + escape(other.toString) + "\""

  private def renderObject(
    m:      Map[String, Any],
    indent: Int,
    depth:  Int,
    pretty: Boolean
  ): String =
    // Omit None / null values — absent optional fields should not appear in JSON
    val entries = m
      .filter { case (_, v) => v != null && v != None }
      .toList
      .sortBy(_._1)   // deterministic key order

    if entries.isEmpty then "{}"
    else if !pretty then
      entries
        .map { case (k, v) => "\"" + escape(k) + "\":" + renderValue(v, indent, depth, pretty) }
        .mkString("{", ",", "}")
    else
      val pad    = " " * (indent * (depth + 1))
      val padEnd = " " * (indent * depth)
      val body   = entries
        .map { case (k, v) =>
          pad + "\"" + escape(k) + "\": " + renderValue(v, indent, depth + 1, pretty)
        }
        .mkString(",\n")
      "{\n" + body + "\n" + padEnd + "}"

  private def renderArray(
    seq:    Seq[?],
    indent: Int,
    depth:  Int,
    pretty: Boolean
  ): String =
    if seq.isEmpty then "[]"
    else if !pretty then
      seq.map(renderValue(_, indent, depth, pretty)).mkString("[", ",", "]")
    else
      val pad    = " " * (indent * (depth + 1))
      val padEnd = " " * (indent * depth)
      val body   = seq
        .map(v => pad + renderValue(v, indent, depth + 1, pretty))
        .mkString(",\n")
      "[\n" + body + "\n" + padEnd + "]"

  // ── String escaping ───────────────────────────────────────────────────────

  private def escape(s: String): String =
    val sb = new StringBuilder(s.length + 4)
    s.foreach {
      case '"'               => sb.append("\\\"")
      case '\\'              => sb.append("\\\\")
      case '\n'              => sb.append("\\n")
      case '\r'              => sb.append("\\r")
      case '\t'              => sb.append("\\t")
      case '\b'              => sb.append("\\b")
      case '\f'              => sb.append("\\f")
      case c if c.toInt < 32 => sb.append(f"\\u${c.toInt}%04x")
      case c                 => sb.append(c)
    }
    sb.toString
