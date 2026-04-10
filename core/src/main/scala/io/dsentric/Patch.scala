package io.dsentric

import io.dsentric.internal.PatchMacro

/**
 * A type-safe partial update for a [[Contract]]-annotated case class.
 *
 * Build a patch with [[Patch.empty]][T], then call [[set]] / [[unset]] /
 * [[modify]] for each field you want to change.  Pass the finished patch to
 * [[Contract.applyPatch]] to validate and apply it against the current state.
 *
 * {{{
 * val patch = Patch.empty[User]
 *   .set(_.name, "Alice")      // replace with a fixed value
 *   .modify(_.age, _ + 1)      // derive new value from current value
 *   .unset(_.nickname)         // clear an optional field
 *
 * userContract.applyPatch(currentRaw, patch)
 * }}}
 *
 * Field names are extracted at **compile time** from the selector lambda, so
 * a typo like `_.naem` is a compile error rather than a runtime surprise.
 *
 * Only non-`@immutable`, non-`@reserved` fields should be set; the
 * [[Contract.applyPatch]] call enforces those constraints at runtime.
 *
 * @param fields     Accumulated static field replacements keyed by field name.
 * @param modifiers  Accumulated functional transforms keyed by field name.
 *                   Each transform receives the current raw value and returns
 *                   the new raw value.  Applied before static `fields` are
 *                   merged when [[applyTo]] is called.
 */
final class Patch[T](
  val fields:    Map[String, Any],
  val modifiers: Map[String, Any => Any] = Map.empty
):

  /**
   * Add or overwrite a field with a fixed value.
   *
   * The selector must be a simple field accessor — e.g. `_.name`, not
   * `_.address.city`.  Nested patches are not yet supported.
   *
   * @param selector  A lambda of the form `(t: T) => t.fieldName`.
   * @param value     The new value for that field.
   */
  inline def set[A](inline selector: T => A, value: A): Patch[T] =
    ${ PatchMacro.setField[T, A]('this, 'selector, 'value) }

  /**
   * Explicitly set an optional field to [[None]], effectively clearing it.
   *
   * @param selector  A lambda of the form `(t: T) => t.optionalField`
   *                  where the field type is `Option[A]`.
   */
  inline def unset[A](inline selector: T => Option[A]): Patch[T] =
    ${ PatchMacro.unsetField[T, A]('this, 'selector) }

  /**
   * Apply a function to the *current* value of a field, producing the new value.
   *
   * Unlike [[set]], the new value depends on the field's value in the current
   * raw state — useful for increments, toggles, list appends, etc.
   *
   * {{{
   *   Patch.empty[User]
   *     .modify(_.age,     _ + 1)           // increment
   *     .modify(_.score,   _ * 1.1)         // scale
   *     .modify(_.active,  !_)              // toggle boolean
   * }}}
   *
   * If the field is absent from the current raw object the modifier is skipped
   * (no violation is produced — use [[set]] when you need a guaranteed value).
   *
   * `modify` and [[set]] on the same field: the [[set]] wins because static
   * fields are applied after modifiers in [[applyTo]].
   *
   * @param selector  A lambda of the form `(t: T) => t.fieldName`.
   * @param fn        A pure function `A => A` applied to the current value.
   */
  inline def modify[A](inline selector: T => A, fn: A => A): Patch[T] =
    ${ PatchMacro.modifyField[T, A]('this, 'selector, 'fn) }

  /**
   * Apply this patch on top of a base [[RawObject]].
   *
   * Processing order:
   *  1. For each [[modifiers]] entry: look up the current value in `base` and
   *     apply the transform function. If the key is absent, skip silently.
   *  2. Merge static [[fields]] on top (static values win over modifiers).
   *
   * The result is a raw patch map ready to pass to [[Contract.validatePatch]].
   */
  def applyTo(base: RawObject): RawObject =
    val withModifiers: RawObject = modifiers.foldLeft(base) { case (acc, (key, fn)) =>
      acc.get(key).fold(acc)(currentVal => acc.updated(key, fn(currentVal)))
    }
    withModifiers ++ fields

  /** The static fields as a [[RawObject]]. Does NOT apply modifiers. */
  def toRaw: RawObject = fields

  /** True when no fields have been set or modified yet. */
  def isEmpty: Boolean = fields.isEmpty && modifiers.isEmpty

  /** Total number of fields touched (set + modify, deduplicated). */
  def size: Int = (fields.keySet ++ modifiers.keySet).size

  override def toString: String =
    val parts =
      fields.map    { case (k, v) => s"$k=$v" } ++
      modifiers.map { case (k, _) => s"$k=<fn>" }
    s"Patch(${parts.mkString(", ")})"

object Patch:
  /** Start building a patch — no fields set initially. */
  def empty[T]: Patch[T] = new Patch[T](Map.empty)

  /** Wrap an already-assembled raw map as a typed patch (useful for interop). */
  def fromRaw[T](raw: RawObject): Patch[T] = new Patch[T](raw)
