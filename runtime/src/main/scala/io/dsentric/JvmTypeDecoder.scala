package io.dsentric

import java.lang.reflect.{Type, ParameterizedType}

/**
 * Maps Java/Kotlin field types to the `Any => Option[Any]` decoder signature
 * expected by [[FieldMeta]].
 *
 * Supported types:
 *  - `String`
 *  - `int` / `Integer`  (Long→Int widening from JSON parsers accepted)
 *  - `long` / `Long`    (Int→Long widening accepted)
 *  - `double` / `Double`
 *  - `float` / `Float`
 *  - `boolean` / `Boolean`
 *  - `java.util.Optional<T>` (treated as optional field)
 *  - Any other type → pass-through (caller handles in `constructFn`)
 */
object JvmTypeDecoder:

  /** Produce a decoder function for the given Java type. */
  def forType(javaType: Type): Any => Option[Any] =
    javaType match
      case c: Class[?]            => forClass(c)
      case pt: ParameterizedType  =>
        val raw = pt.getRawType.asInstanceOf[Class[?]]
        if raw == classOf[java.util.Optional[?]] then
          val innerDecoder = forType(pt.getActualTypeArguments()(0))
          optionalDecoder(innerDecoder)
        else
          passThrough
      case _ => passThrough

  /**
   * Returns `true` when `javaType` is `java.util.Optional<T>`.
   * Used by [[JvmContractDeriver]] to set `isOptional` on the [[FieldMeta]].
   */
  def isOptionalType(javaType: Type): Boolean =
    javaType match
      case pt: ParameterizedType =>
        pt.getRawType.asInstanceOf[Class[?]] == classOf[java.util.Optional[?]]
      case _ => false

  // ── Per-class decoders ────────────────────────────────────────────────────

  private def forClass(c: Class[?]): Any => Option[Any] =
    if      c == classOf[String]            then stringDecoder
    else if c == classOf[java.lang.Integer]
         || c == java.lang.Integer.TYPE     then intDecoder
    else if c == classOf[java.lang.Long]
         || c == java.lang.Long.TYPE        then longDecoder
    else if c == classOf[java.lang.Double]
         || c == java.lang.Double.TYPE      then doubleDecoder
    else if c == classOf[java.lang.Float]
         || c == java.lang.Float.TYPE       then floatDecoder
    else if c == classOf[java.lang.Boolean]
         || c == java.lang.Boolean.TYPE     then boolDecoder
    else passThrough

  private val stringDecoder: Any => Option[Any] = {
    case s: String => Some(s)
    case null      => None
    case _         => None
  }

  private val intDecoder: Any => Option[Any] = {
    case i: Int  => Some(i)
    case l: Long if l >= Int.MinValue && l <= Int.MaxValue => Some(l.toInt)
    case _       => None
  }

  private val longDecoder: Any => Option[Any] = {
    case l: Long => Some(l)
    case i: Int  => Some(i.toLong)
    case _       => None
  }

  private val doubleDecoder: Any => Option[Any] = {
    case d: Double => Some(d)
    case f: Float  => Some(f.toDouble)
    case i: Int    => Some(i.toDouble)
    case l: Long   => Some(l.toDouble)
    case _         => None
  }

  private val floatDecoder: Any => Option[Any] = {
    case f: Float  => Some(f)
    case d: Double => Some(d.toFloat)
    case _         => None
  }

  private val boolDecoder: Any => Option[Any] = {
    case b: Boolean => Some(b)
    case _          => None
  }

  private val passThrough: Any => Option[Any] = v => Option(v)

  /**
   * Decoder for `Optional<T>` fields.
   *
   * Returns the **unwrapped inner value** so that [[io.dsentric.ContractImpl]]
   * can apply constraint annotations (e.g. `@min`) directly to the inner value.
   * The `Optional` wrapper is re-introduced by [[io.dsentric.JvmContract]]'s
   * `scalaConstructFn` after all validation has passed.
   *
   * - `Optional.of(v)` / plain value  → `inner(v)` (decoded inner value)
   * - `Optional.empty()` / null / None → `None` (absent; ContractImpl skips constraints)
   * - Decoding failure of inner value  → `None`
   */
  private def optionalDecoder(inner: Any => Option[Any]): Any => Option[Any] =
    case opt: java.util.Optional[?] =>
      if opt.isEmpty then None
      else inner(opt.get())
    case null => None
    case None => None
    case other => inner(other)
