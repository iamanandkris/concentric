package io.dsentric

/** Type alias for the raw untyped input / storage format. */
type RawObject = Map[String, Any]

/**
 * The central abstraction of dsentric.
 *
 * A [[Contract]][T] describes the rules for a contract type T and provides
 * three core operations:
 *
 *  - [[validate]]      — parse + fully validate a raw object, returning T.
 *  - [[validatePatch]] — validate a partial update (patch) against the
 *                        current raw state and return the merged T.
 *  - [[sanitize]]      — produce an output-safe [[RawObject]] with internal
 *                        fields stripped and masked fields replaced.
 *
 * Instances are obtained via the compile-time macro [[Contract.derived]]:
 *
 * {{{
 *   @contract
 *   case class User(
 *     @immutable @internal          id:       Long,
 *     @nonEmpty  @maxLength(100)    name:     String,
 *                                   email:    Option[String],
 *     @min(0)    @max(150)          age:      Int = 0,
 *     @masked                       password: Option[String]
 *   )
 *
 *   given userContract: Contract[User] = Contract.derived[User]
 *
 *   // At the API layer:
 *   val result: Either[ContractViolations, User] =
 *     userContract.validate(incomingMap)
 * }}}
 */
trait Contract[T]:

  /** All field-level metadata, keyed by field name. */
  def fieldMetas: List[FieldMeta]

  /**
   * Validate a raw object against this contract.
   *
   * Steps performed:
   *  1. Check required fields are present.
   *  2. Decode each field to its declared type (using the macro-generated decoder).
   *  3. Apply all constraint annotations (@nonEmpty, @min, @max, etc.).
   *  4. Check for @reserved fields that must not be set by callers.
   *  5. Reject unknown fields when the contract is closed.
   *  6. Construct and return T using the macro-generated constructor.
   *
   * All violations are accumulated — the operation never short-circuits on
   * the first failure.
   */
  def validate(raw: RawObject): Either[ContractViolations, T]

  /**
   * Validate a partial update (patch) and return the merged result.
   *
   * The patch is applied on top of @param currentRaw (the existing persisted
   * state).  Rules:
   *  - Fields absent from the patch are taken from currentRaw unchanged.
   *  - @immutable fields present in the patch are rejected.
   *  - @reserved fields present in the patch are rejected.
   *  - All constraint annotations are applied to the patched values.
   *  - The merged object is fully re-validated before constructing T.
   */
  def validatePatch(currentRaw: RawObject, patch: RawObject): Either[ContractViolations, T]

  /**
   * Produce an output-safe representation of T as a [[RawObject]].
   *
   * Transformations applied:
   *  - @internal fields are removed entirely.
   *  - @masked fields have their values replaced with the mask string.
   *
   * The input raw object is returned with only these two transformations; no
   * validation is performed.
   */
  def sanitize(raw: RawObject): RawObject

  /**
   * Validate a typed [[Patch]][T] against the current raw state.
   *
   * Equivalent to calling [[validatePatch]] with `patch.toRaw`.  Prefer this
   * overload when building patches with the type-safe [[Patch]] builder:
   *
   * {{{
   *   val p = Patch.empty[User].set(_.name, "Alice").set(_.age, 30)
   *   userContract.applyPatch(currentRaw, p)
   * }}}
   */
  def applyPatch(currentRaw: RawObject, patch: Patch[T]): Either[ContractViolations, T] =
    // patch.applyTo resolves modifiers against currentRaw, then merges static
    // fields on top, producing the effective raw patch to validate.
    val effectivePatch = patch.applyTo(currentRaw)
    // Only send the *changed* keys to validatePatch, not the whole merged object.
    // applyTo returns the full merged base+modifiers+fields, so we diff it.
    val changedKeys = (patch.fields.keySet ++ patch.modifiers.keySet)
    val rawPatch    = effectivePatch.view.filterKeys(changedKeys.contains).toMap
    validatePatch(currentRaw, rawPatch)

  /**
   * Validate a *partial* raw object — identical to [[validate]] except that
   * absent required fields do NOT produce [[ViolationCode.ExpectedMissing]]
   * violations.  All other checks (type, constraint, unknown-field) still run.
   *
   * Returns a [[Draft]][T] wrapping the subset of fields that were present
   * and valid.  Use [[Draft.merge]] to accumulate more fields over time, then
   * call [[Draft.finalize]] for full validation.
   */
  def validatePartial(raw: RawObject): Either[ContractViolations, Draft[T]]

  /**
   * Run validation and collect all [[Violation]]s without constructing T.
   *
   * Returns an empty list when the raw object is fully valid.
   * This is used internally for nested contract validation and is also
   * useful for building custom error-reporting pipelines.
   */
  def collectViolations(raw: RawObject): List[Violation]

  /**
   * Serialize an already-constructed T back to a [[RawObject]].
   *
   * Field values are stored as-is (Option fields keep their Option wrapper
   * so they round-trip correctly through [[RawDecoder]]).
   *
   * For contracts with [[io.dsentric.annotations.include]] fields the result
   * is the flat wire format — inner fields are promoted to the top level.
   */
  def toRaw(t: T): RawObject

  /**
   * Derive a JSON Schema (draft-07) document for this contract.
   *
   * The returned map represents a JSON Schema object and can be serialised
   * directly to JSON.  Structure:
   *
   * {{{
   *   {
   *     "$schema":             "http://json-schema.org/draft-07/schema#",
   *     "type":                "object",
   *     "properties": {
   *       "<field>": { "type": "string", "minLength": 1, ... },
   *       ...
   *     },
   *     "required":            ["field1", "field2"],   // absent when empty
   *     "additionalProperties": false                  // for closed contracts
   *   }
   * }}}
   *
   * Annotation metadata is mapped as follows:
   *  - `@nonEmpty`              → `"minLength": 1` (strings) / `"minItems": 1` (arrays)
   *  - `@minLength` / `@maxLength` → `"minLength"` / `"maxLength"`
   *  - `@min` / `@max`         → `"minimum"` / `"maximum"`
   *  - `@pattern`              → `"pattern"`
   *  - `@email`                → `"format": "email"`
   *  - `@immutable`            → `"x-immutable": true`
   *  - `@internal`             → `"x-internal": true`
   *  - `@reserved`             → `"x-reserved": true`
   *  - `@masked("str")`        → `"x-masked": "str"`
   *
   * Nested contract types are inlined as sub-schemas.  `@include`-flattened
   * fields appear at the top level (matching the wire format).
   */
  def jsonSchema: Map[String, Any]

  /**
   * Return the extra (unknown) fields present in a raw object that are not
   * declared by this contract.
   *
   * This is only meaningful for ''open'' contracts (annotated with
   * `@contract(open = true)`); for closed contracts all unknown fields are
   * rejected during [[validate]], so the result will always be empty in
   * practice.
   *
   * {{{
   *   val extra: RawObject = openContract.extraFields(raw)
   *   // extra only contains keys not declared in the contract
   * }}}
   */
  def extraFields(raw: RawObject): RawObject

  /**
   * Create a type-safe [[Filter]][T] builder for this contract.
   *
   * The returned builder lets you compose [[FilterExpr]][T] values using
   * compile-time field names and automatically summoned [[RawDecoder]]
   * instances.
   *
   * {{{
   *   val expr: FilterExpr[User] =
   *     userContract.filter.field(_.age).gte(18) &&
   *     userContract.filter.field(_.name).startsWith("A")
   *
   *   // In-memory test
   *   val matches: Boolean = expr.test(rawMap)
   *
   *   // MongoDB query document
   *   val q: RawObject = expr.toMongoQuery
   * }}}
   *
   * Equivalent to `Filter[T]` — the contract is not consulted at runtime;
   * only the type parameter is used.
   */
  def filter: Filter[T] = new Filter[T]

  /**
   * Serialize `t` to a compact JSON string.
   *
   * Equivalent to `RawJson.stringify(toRaw(t))`.
   *
   * {{{
   *   val json: String = userContract.toJson(user)
   *   // → {"age":30,"email":"alice@example.com","id":1,"name":"Alice"}
   * }}}
   */
  def toJson(t: T): String = RawJson.stringify(toRaw(t))

  /**
   * Serialize `t` to an indented JSON string.
   *
   * @param indent  Spaces per level.  Defaults to `2`.
   */
  def toJson(t: T, indent: Int): String = RawJson.stringify(toRaw(t), indent)

  /**
   * Sanitize `raw` and return the result as a compact JSON string.
   *
   * `@internal` fields are stripped and `@masked` fields are replaced before
   * serialisation, identical to calling `RawJson.stringify(sanitize(raw))`.
   *
   * {{{
   *   val json: String = userContract.sanitizeJson(storedRaw)
   *   // id is gone (@internal), password is "***" (@masked)
   * }}}
   */
  def sanitizeJson(raw: RawObject): String = RawJson.stringify(sanitize(raw))

  /**
   * Sanitize `raw` and return the result as an indented JSON string.
   *
   * @param indent  Spaces per level.  Defaults to `2`.
   */
  def sanitizeJson(raw: RawObject, indent: Int): String = RawJson.stringify(sanitize(raw), indent)


object Contract:

  /**
   * Derive a [[Contract]][T] for a case class T annotated with dsentric
   * annotations.
   *
   * This is a Scala 3 inline macro — all annotation reading and constructor
   * code generation happens at compile time with zero runtime overhead.
   *
   * {{{
   *   given Contract[User] = Contract.derived[User]
   * }}}
   */
  inline def derived[T <: Product]: Contract[T] =
    internal.ContractMacro.derived[T]
