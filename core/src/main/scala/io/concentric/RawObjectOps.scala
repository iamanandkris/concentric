package io.concentric

/**
 * Utility operations on [[RawObject]] (Map[String, Any]) values.
 *
 * These are particularly useful when building patch / delta workflows:
 *  - [[rightDifference]]    — which fields changed or were added?
 *  - [[differenceDelta]]    — produce a minimal patch from before→after.
 *  - [[deltaTraverseConcat]] — deep-merge a delta onto a base, recursing
 *                              into nested [[RawObject]] values.
 */
object RawObjectOps:

  /**
   * Returns entries in `updated` that are either absent in `base` or have a
   * different value.  Keys present in `base` but absent in `updated` are not
   * included.
   *
   * {{{
   *   val base    = Map("a" -> 1, "b" -> 2)
   *   val updated = Map("a" -> 1, "b" -> 99, "c" -> 3)
   *   rightDifference(base, updated)
   *   // → Map("b" -> 99, "c" -> 3)
   * }}}
   */
  def rightDifference(base: RawObject, updated: RawObject): RawObject =
    updated.filter { case (k, v) => base.get(k).forall(_ != v) }

  /**
   * Produces a minimal delta (patch) from `before` to `after`:
   * keys that are new in `after` or whose value changed vs `before`.
   * Keys only in `before` are excluded.
   *
   * For nested [[RawObject]] values, comparison is shallow (no recursion).
   * Use [[deltaTraverseConcat]] to apply the produced delta.
   *
   * {{{
   *   val before = Map("name" -> "Alice", "age" -> 25)
   *   val after  = Map("name" -> "Alice", "age" -> 26, "email" -> "a@b.com")
   *   differenceDelta(before, after)
   *   // → Map("age" -> 26, "email" -> "a@b.com")
   * }}}
   */
  def differenceDelta(before: RawObject, after: RawObject): RawObject =
    after.filter { case (k, v) =>
      before.get(k) match
        case None       => true
        case Some(bVal) => bVal != v
    }

  /**
   * Deep-merge `delta` onto `base`.
   *
   * Rules:
   *  - If both `base` and `delta` have a [[RawObject]] (Map) at the same key,
   *    they are merged recursively — this is the "traverse" behaviour.
   *  - For all other value types the delta value wins (shallow replace).
   *  - Keys present in `base` but absent in `delta` are kept unchanged.
   *
   * {{{
   *   val base  = Map("name" -> "Alice", "address" -> Map("city" -> "Old York", "zip" -> "12345"))
   *   val delta = Map("address" -> Map("city" -> "New York"))
   *   deltaTraverseConcat(base, delta)
   *   // → Map("name" -> "Alice", "address" -> Map("city" -> "New York", "zip" -> "12345"))
   * }}}
   */
  def deltaTraverseConcat(base: RawObject, delta: RawObject): RawObject =
    delta.foldLeft(base) { case (acc, (k, dv)) =>
      acc.get(k) match
        case Some(bv: Map[?, ?]) if dv.isInstanceOf[Map[?, ?]] =>
          acc.updated(k, deltaTraverseConcat(
            bv.asInstanceOf[RawObject],
            dv.asInstanceOf[RawObject]
          ))
        case _ =>
          acc.updated(k, dv)
    }
