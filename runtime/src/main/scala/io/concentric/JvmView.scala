package io.concentric

import JvmValueConversions.*

/**
 * JVM wrapper around [[View]][T] using string field names instead of
 * compile-time selectors.
 *
 * Build a view using the fluent API, then call [[apply]] to transform a
 * raw Java map:
 *
 * {{{
 * // Kotlin
 * val view = JvmView.of(User::class.java)
 *     .omit("password")
 *     .mask("ssn")
 *     .compute("displayName") { raw -> "${raw["name"]} <${raw["email"]}>" }
 *
 * val outRaw: Map<String, Any> = view.apply(storedRaw)
 * }}}
 */
final class JvmView[T] private[concentric] (private val view: View[T]):

  /** Remove a field from the output entirely. */
  def omit(fieldName: String): JvmView[T] =
    new JvmView[T](View.addTransform(view, ViewTransform.Omit[T](fieldName)))

  /** Replace a field's value with the default mask string `"***"`. */
  def mask(fieldName: String): JvmView[T] =
    new JvmView[T](View.addTransform(view, ViewTransform.Mask[T](fieldName, "***")))

  /** Replace a field's value with the given mask string. */
  def mask(fieldName: String, maskStr: String): JvmView[T] =
    new JvmView[T](View.addTransform(view, ViewTransform.Mask[T](fieldName, maskStr)))

  /**
   * Compute a synthetic or derived field.
   *
   * The function receives the current raw object as a `java.util.Map<String, Object>`.
   * Its return value is passed through [[JvmValueConversions.deepToScala]] before
   * being stored in the output raw object so that Scala-side processing stays
   * consistent.
   */
  def compute(
    name: String,
    fn:   java.util.function.Function[java.util.Map[String, AnyRef], AnyRef]
  ): JvmView[T] =
    val scalaFn: RawObject => Any = (raw: RawObject) =>
      val javaInput  = deepToJava(raw).asInstanceOf[java.util.Map[String, AnyRef]]
      val javaResult = fn.apply(javaInput)
      deepToScala(javaResult)
    new JvmView[T](View.addTransform(view, ViewTransform.Compute[T](name, scalaFn)))

  /**
   * Apply all transforms in order to `raw` and return the transformed map.
   */
  def apply(raw: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] =
    toJavaRaw(view.apply(toScalaRaw(raw)))

  /** Number of transforms registered in this view. */
  def size: Int = view.size

  /** True when the view has no transforms (identity). */
  def isEmpty: Boolean = view.isEmpty

object JvmView:
  /** Start building a view with no transforms (identity). */
  def of[T](clazz: Class[T]): JvmView[T] = new JvmView[T](View.empty[T])
