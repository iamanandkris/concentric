package io.concentric

import scala.deriving.Mirror

/** Type alias for the raw untyped input / storage format. */
type RawObject = Map[String, Any]

/**
 * The central abstraction of concentric.
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
 *   @contract
 *   case class User(
 *     @immutable @internal          id:       Long,
 *     @nonEmpty  @maxLength(100)    name:     String,
 *                                   email:    Option[String],
 *     @min(0)    @max(150)          age:      Int = 0,
 *     @masked                       password: Option[String]
 *   ) derives Contract
 *
 *   // At the API layer:
 *   val userContract = summon[Contract[User]]
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
   * Validate a typed aspect value as a patch against the current raw state.
   *
   * Equivalent to calling [[validatePatch]] with [[toPatchRaw]] applied to
   * `aspect` — but without the manual `.collect`.  Both the aspect contract
   * and the compile-time proof that `A` is an `@aspectOf[T]` are resolved
   * implicitly, making the call site a clean two-step:
   *
   * {{{
   *   for
   *     patch   <- patchContract.validate(requestBody)
   *     updated <- userContract.validatePatch(currentRaw, patch)
   *   yield updated
   * }}}
   *
   * A compile error is emitted if `A` does not carry `@aspectOf[T]` — the
   * relationship is enforced at compile time, not by convention.
   *
   * @param currentRaw     The current persisted state as a raw map.
   * @param aspect         The typed aspect / patch value to apply.
   * @param aspectContract Implicitly summoned [[Contract]][A].
   * @param evidence       Compile-time proof that `A` is an `@aspectOf[T]`.
   */
  def validatePatch[A <: Product](currentRaw: RawObject, aspect: A)(using
    aspectContract: Contract[A],
    evidence: IsAspectOf[T, A]
  ): Either[ContractViolations, T] =
    validatePatch(currentRaw, aspectContract.toPatchRaw(aspect))

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
   * For contracts with [[io.concentric.annotations.include]] fields the result
   * is the flat wire format — inner fields are promoted to the top level.
   */
  def toRaw(t: T): RawObject

  /**
   * Serialize `t` to a patch-ready [[RawObject]]: `Option` fields that are
   * `None` are omitted (absent from patch), `Some(v)` fields are unwrapped
   * to `v`, and non-`Option` fields are passed through as-is.
   *
   * This is the strip step that makes aspect-based PATCH handlers possible
   * without a manual `.collect`.  It is used internally by the typed
   * [[validatePatch]] overload.
   *
   * {{{
   *   val patchRaw = patchContract.toPatchRaw(patch)
   *   // Map("email" -> "new@example.com")  — None fields omitted, Some unwrapped
   * }}}
   */
  def toPatchRaw(t: T): RawObject =
    toRaw(t).flatMap {
      case (_, None)         => None
      case (k, Some(v: Any)) => Some(k -> v)
      case (k, v)            => Some(k -> v)
    }

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
   * This is primarily meaningful for legacy open contracts created with
   * `@contract(open = true)`. Prefer [[io.concentric.OpenContract]] when
   * unknown fields need to remain first-class after validation.
   *
   * For closed contracts all unknown fields are rejected during [[validate]],
   * so the result will always be empty in practice.
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
   * Summon the in-scope [[Contract]][T].
   *
   * This enables the concise call style:
   *
   * {{{
   *   Contract[User].validate(raw)
   * }}}
   */
  inline def apply[T](using c: Contract[T]): Contract[T] = c

  /**
   * Derive a [[Contract]][T] for a case class T annotated with concentric
   * annotations.
   *
   * This is a Scala 3 inline macro — all annotation reading and constructor
   * code generation happens at compile time with zero runtime overhead.
   *
   * When T is annotated with `@aspectOf[S]`, the macro automatically switches
   * to aspect derivation: constraint annotations are inherited from S's
   * matching fields, optionality is determined by T's field types, and any
   * field not listed in T is excluded from the contract.  See [[AspectOf]]
   * for the full aspect documentation.
   *
   * {{{
   *   @contract
   *   case class User(
   *     @email    val email: String,
   *     @nonEmpty val name:  String,
   *     @reserved val role:  Option[String] = None
   *   ) derives Contract
   *
   *   @aspectOf[User]
   *   case class UserPatch(
   *     val email: Option[String] = None,
   *     val name:  Option[String] = None
   *   ) derives Contract
   *
   *   val userContract  = summon[Contract[User]]
   *   val patchContract = summon[Contract[UserPatch]]
   * }}}
   */
  inline def derived[T <: Product](using Mirror.ProductOf[T]): Contract[T] =
    internal.ContractMacro.derived[T]

  /**
   * Structural aspect (variant) of an existing contract type `S`.
   *
   * `Contract.AspectOf[S]` is a kind-`* -> *` type constructor — `derives
   * Contract.AspectOf[S]` on a case class `T` generates a full
   * `Contract.AspectOf[S, T]` (which extends `Contract[T]`) that can be
   * summoned as either:
   *
   *   - `summon[Contract[T]]`                — standard contract interface
   *   - `summon[Contract.AspectOf[S, T]]`    — typed aspect handle
   *
   * Constraint annotations (`@email`, `@nonEmpty`, `@min`, etc.) are
   * inherited from `S`'s matching fields.  Policy annotations (`@reserved`,
   * `@internal`, `@immutable`, `@masked`) are NOT inherited — the aspect
   * author explicitly controls access permissions.  Any annotation declared
   * on a field in `T` overrides the corresponding one from `S`.
   *
   * A compile error is emitted if:
   *  - `Contract[S]` is not in scope (dependency enforcement)
   *  - any field in `T` does not exist in `S` (drift detection)
   *
   * {{{
   *   @contract
   *   case class User(
   *     @email    val email: String,
   *     @nonEmpty val name:  String,
   *     @reserved val role:  Option[String] = None
   *   ) derives Contract
   *
   *   // PATCH variant — @email / @nonEmpty inherited, role excluded by omission.
   *   case class UserPatch(
   *     val email: Option[String] = None,
   *     val name:  Option[String] = None
   *   ) derives Contract.AspectOf[User]
   *
   *   val userContract  = summon[Contract[User]]
   *   val patchContract = summon[Contract[UserPatch]]   // works — AspectOf extends Contract
   *
   *   // Typical PATCH handler:
   *   for
   *     patch   <- patchContract.validate(requestBody)
   *     patchRaw = patchContract.toRaw(patch).collect { case (k, Some(v)) => k -> v }
   *     updated <- userContract.validatePatch(current, patchRaw)
   *   yield updated
   * }}}
   *
   * @tparam S  The source contract type this aspect is derived from.
   * @tparam T  The aspect case class (filled in by the `derives` mechanism).
   */
  sealed abstract class AspectOf[S <: Product, T <: Product] extends Contract[T]

  /** Concrete [[AspectOf]] instance produced by [[AspectOf.derived]].
   *  Delegates all [[Contract]][T] operations to an underlying [[ContractImpl]].
   *  Private to this file so the `sealed` invariant is preserved. */
  private final class AspectOfImpl[S <: Product, T <: Product](
    private val impl: Contract[T]
  ) extends AspectOf[S, T]:
    export impl.{
      fieldMetas, validate, validatePatch, sanitize,
      validatePartial, collectViolations, toRaw, jsonSchema, extraFields
    }

  object AspectOf:
    /**
     * Derives a [[Contract.AspectOf]][S, T] for `T` as a structural aspect of `S`.
     *
     * Called automatically by `derives Contract.AspectOf[S]`.
     * Requires `Contract[S]` in implicit scope — compile error if absent.
     */
    inline def derived[S <: Product, T <: Product](
      using Mirror.ProductOf[T],
      Contract[S]
    ): Contract.AspectOf[S, T] =
      new AspectOfImpl[S, T](internal.ContractMacro.derivedAspect[T, S])
