package io.concentric

import scala.jdk.CollectionConverters.*

/**
 * Minimal Java/Kotlin-friendly patch builder backed by a mutable map.
 *
 * Unlike Scala [[Patch]], this builder is stringly-typed: field names are
 * provided explicitly and checked only when the patch is validated against a
 * [[JvmContract]].
 */
final class JvmPatch private[concentric] (
  private val fields: java.util.LinkedHashMap[String, AnyRef]
):
  /** Add or overwrite a field with a fixed value. */
  def set(field: String, value: AnyRef): JvmPatch =
    fields.put(field, value)
    this

  /** Merge all entries from a raw map into this patch. */
  def putAll(raw: java.util.Map[String, AnyRef]): JvmPatch =
    fields.putAll(raw)
    this

  /** Snapshot the accumulated fields as a fresh mutable map. */
  def toMap(): java.util.Map[String, AnyRef] =
    new java.util.LinkedHashMap[String, AnyRef](fields)

  /** Number of fields currently included in the patch. */
  def size(): Int = fields.size()

  /** `true` when the patch contains no fields. */
  def isEmpty(): Boolean = fields.isEmpty

  override def toString: String =
    val body = fields.asScala.map { case (k, v) => s"$k=$v" }.mkString(", ")
    s"JvmPatch($body)"

object JvmPatch:
  /** Start building a patch with no fields. */
  def empty(): JvmPatch =
    new JvmPatch(new java.util.LinkedHashMap[String, AnyRef]())

  /** Wrap an existing raw map as a mutable builder. */
  def from(raw: java.util.Map[String, AnyRef]): JvmPatch =
    new JvmPatch(new java.util.LinkedHashMap[String, AnyRef](raw))
