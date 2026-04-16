package io.concentric

import io.concentric.internal.ViewMacro

// ── Transform algebra ────────────────────────────────────────────────────────

/** A single transform applied by a [[View]] to a [[RawObject]]. */
sealed trait ViewTransform[T]:
  def applyTo(raw: RawObject): RawObject

object ViewTransform:
  /** Remove a field from the output entirely. */
  final case class Omit[T](fieldName: String) extends ViewTransform[T]:
    def applyTo(raw: RawObject): RawObject = raw - fieldName

  /** Replace a field's value with a fixed mask string. */
  final case class Mask[T](fieldName: String, maskStr: String) extends ViewTransform[T]:
    def applyTo(raw: RawObject): RawObject =
      if raw.contains(fieldName) then raw.updated(fieldName, maskStr) else raw

  /**
   * Compute a new (or replacement) field from the current raw object.
   * The function receives the raw map *after* previous transforms have
   * been applied, so [[Omit]] / [[Mask]] transforms that precede a
   * [[Compute]] are already reflected.
   */
  final case class Compute[T](name: String, fn: RawObject => Any) extends ViewTransform[T]:
    def applyTo(raw: RawObject): RawObject = raw.updated(name, fn(raw))

// ── View ─────────────────────────────────────────────────────────────────────

/**
 * A composable pipeline of output transforms for a contract type `T`.
 *
 * Build a view using the fluent API, then call [[apply]] to transform a
 * [[RawObject]]:
 *
 * {{{
 *   val publicView = View[User]
 *     .omit(_.id)
 *     .omit(_.password)
 *     .mask(_.ssn, "***")
 *     .compute("displayName", raw =>
 *       s"${raw.getOrElse("name", "?")} <${raw.getOrElse("email", "no-reply")}>")
 *
 *   val outRaw: RawObject = publicView(rawUser)
 * }}}
 *
 * Transforms are applied in the order they are added.  A [[Compute]] that
 * references a field omitted by an earlier [[Omit]] will see `None` /
 * absence for that key.
 *
 * @param transforms  Ordered list of transforms to apply.
 */
final class View[T](val transforms: List[ViewTransform[T]]):

  /**
   * Omit a field from the output.
   *
   * The selector must be a simple field accessor — e.g. `_.id`.
   * Field name is extracted at compile time.
   */
  inline def omit[A](inline selector: T => A): View[T] =
    ${ ViewMacro.omit[T, A]('this, 'selector) }

  /**
   * Replace a field's value with a mask string (default `"***"`).
   *
   * The selector must be a simple field accessor — e.g. `_.password`.
   */
  inline def mask[A](inline selector: T => A, maskStr: String = "***"): View[T] =
    ${ ViewMacro.mask[T, A]('this, 'selector, 'maskStr) }

  /**
   * Compute a synthetic or derived field and add/replace it in the output.
   *
   * The `fn` receives the [[RawObject]] after all preceding transforms
   * have been applied.
   *
   * @param name  Output field name (may be a new key or an existing one).
   * @param fn    Function from the current (partially transformed) raw
   *              object to the computed value.
   */
  def compute(name: String, fn: RawObject => Any): View[T] =
    new View[T](transforms :+ ViewTransform.Compute(name, fn))

  /**
   * Apply all transforms in order to `raw`, returning the transformed
   * [[RawObject]].
   */
  def apply(raw: RawObject): RawObject =
    transforms.foldLeft(raw)((acc, t) => t.applyTo(acc))

  /** Number of transforms registered in this view. */
  def size: Int = transforms.size

  /** True when the view has no transforms (identity). */
  def isEmpty: Boolean = transforms.isEmpty

object View:
  /** Start building a view with no transforms (identity). */
  def apply[T]: View[T] = new View[T](Nil)

  /** Alias for [[apply]]. */
  def empty[T]: View[T] = new View[T](Nil)

  /** Internal helper called by the ViewMacro — adds a transform. */
  def addTransform[T](view: View[T], t: ViewTransform[T]): View[T] =
    new View[T](view.transforms :+ t)
