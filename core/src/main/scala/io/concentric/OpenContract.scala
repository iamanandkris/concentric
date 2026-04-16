package io.concentric

import scala.deriving.Mirror

/**
 * Open-contract variant of [[Contract]].
 *
 * Open contracts preserve undeclared input fields instead of rejecting them.
 * Validation returns both the typed declared value and the extra raw fields.
 */
trait OpenContract[T]:

  /** All field-level metadata, keyed by field name. */
  def fieldMetas: List[FieldMeta]

  /**
   * Validate a raw object against this open contract.
   *
   * Returns the typed declared value plus any undeclared extra fields.
   */
  def validate(raw: RawObject): Either[ContractViolations, (T, RawObject)]

  /** Run validation and collect all violations without constructing T. */
  def collectViolations(raw: RawObject): List[Violation]

  /** Return only undeclared fields from the raw object. */
  def extraFields(raw: RawObject): RawObject

  /** Serialize an already-constructed T back to a [[RawObject]]. */
  def toRaw(t: T): RawObject

  /** Sanitize a raw object by stripping/masking sensitive declared fields. */
  def sanitize(raw: RawObject): RawObject

  /** Derive a JSON Schema (draft-07) document for this contract. */
  def jsonSchema: Map[String, Any]

  /** Create a type-safe [[Filter]][T] builder for this contract. */
  def filter: Filter[T] = new Filter[T]

  def toJson(t: T): String = RawJson.stringify(toRaw(t))

  def toJson(t: T, indent: Int): String = RawJson.stringify(toRaw(t), indent)

  def sanitizeJson(raw: RawObject): String = RawJson.stringify(sanitize(raw))

  def sanitizeJson(raw: RawObject, indent: Int): String = RawJson.stringify(sanitize(raw), indent)

object OpenContract:

  /** Summon the in-scope [[OpenContract]][T]. */
  inline def apply[T](using c: OpenContract[T]): OpenContract[T] = c

  /**
   * Derive an [[OpenContract]][T] for a case class T annotated with concentric
   * annotations.
   *
   * Unknown fields are always preserved as extras regardless of `@contract(open = ...)`.
   */
  inline def derived[T <: Product](using Mirror.ProductOf[T]): OpenContract[T] =
    internal.ContractMacro.derivedOpen[T]
