package io.concentric

/**
 * An immutable path to a field inside a contract object, represented as a
 * sequence of string segments.  Supports nested navigation via the `/` operator.
 *
 * Examples:
 * {{{
 *   FieldPath("name")                   // top-level field
 *   FieldPath("address") / "street"     // nested field
 *   FieldPath.root                      // empty path (contract root)
 * }}}
 */
final case class FieldPath(segments: List[String]) {

  /** Append a child segment, producing a deeper path. */
  def /(segment: String): FieldPath = FieldPath(segments :+ segment)

  /** Append a numeric index (for array elements). */
  def /(index: Int): FieldPath = FieldPath(segments :+ index.toString)

  /** True when the path refers to the root of the object (no segments). */
  def isRoot: Boolean = segments.isEmpty

  /** Dot-separated string representation, e.g. {@code "address.street"}. */
  override def toString: String =
    if segments.isEmpty then "<root>" else segments.mkString(".")
}

object FieldPath {

  /** The root (empty) path — refers to the whole contract object. */
  val root: FieldPath = FieldPath(Nil)

  /** Convenience constructor for a single top-level segment. */
  def apply(segment: String): FieldPath = FieldPath(List(segment))
}
