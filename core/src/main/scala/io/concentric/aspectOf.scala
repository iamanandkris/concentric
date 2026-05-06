package io.concentric

/**
 * Marks a case class as a structural aspect (variant) of an existing contract
 * type `S`.
 *
 * When combined with `derives Contract`, the [[io.concentric.internal.ContractMacro]]
 * will:
 *
 *  1. Verify `Contract[S]` is in scope — compile error if `S` hasn't derived one.
 *  2. Verify every field declared in this class exists in `S` — compile error on
 *     name mismatch or drift.
 *  3. Inherit constraint annotations from `S`'s matching field (e.g. `@email`,
 *     `@nonEmpty`, `@min`) without repeating them here.
 *  4. Honour this class's own field types for optionality — wrapping a field in
 *     `Option[T]` makes it optional even if it was required in `S`.
 *  5. Allow local annotation overrides: annotations placed on fields here take
 *     precedence over the inherited ones from `S`.
 *  6. NOT inherit policy annotations (`@reserved`, `@internal`, `@immutable`,
 *     `@masked`) — the aspect author explicitly controls access.
 *
 * The resulting `given Contract[T]` is fully identical to any other contract
 * instance and is summoned the same way:
 *
 * {{{
 *   @contract
 *   case class User(
 *     @email    val email: String,
 *     @nonEmpty val name:  String,
 *     @reserved val role:  Option[String] = None
 *   ) derives Contract
 *
 *   // PATCH variant — only email and name are accepted, both optional.
 *   // @email and @nonEmpty are inherited from User; role is excluded by omission.
 *   @aspectOf[User]
 *   case class UserPatch(
 *     val email: Option[String] = None,
 *     val name:  Option[String] = None
 *   ) derives Contract
 *
 *   val userContract  = summon[Contract[User]]
 *   val patchContract = summon[Contract[UserPatch]]
 *
 *   // Typical PATCH handler:
 *   for
 *     patch    <- patchContract.validate(requestBody)
 *     patchRaw  = patchContract.toRaw(patch).collect { case (k, Some(v)) => k -> v }
 *     updated  <- userContract.validatePatch(current, patchRaw)
 *   yield updated
 * }}}
 *
 * Fields not listed in the aspect class are excluded from validation and schema
 * generation entirely — they are neither required nor optional; they simply do
 * not exist in this contract variant.
 *
 * @tparam S       The source contract type this aspect is derived from.
 * @param inherit  When `true`, enables exhaustiveness checking: every field of
 *                 `S` must either be declared in this class or listed in
 *                 `exclude`.  Adding a new field to `S` without updating here
 *                 becomes a compile error.  Defaults to `false` (opt-in, current
 *                 behaviour unchanged).
 * @param exclude  Source field names to explicitly omit when `inherit = true`.
 *                 Each name must exist in `S`; using a non-existent name or
 *                 specifying `exclude` without `inherit = true` is a compile
 *                 error.  Ignored when `inherit = false`.
 */
class aspectOf[S <: Product](
  val inherit: Boolean     = false,
  val exclude: Seq[String] = Nil
) extends scala.annotation.StaticAnnotation
