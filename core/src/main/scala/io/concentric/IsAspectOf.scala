package io.concentric

import scala.annotation.implicitNotFound

/**
 * Compile-time evidence that `Aspect` is a structural aspect of `Source`,
 * i.e. that `Aspect` is annotated with `@aspectOf[Source]`.
 *
 * Instances are provided automatically by the macro-backed [[IsAspectOf.derived]]
 * `given` — there is no need to create or import them manually.
 *
 * The only way to obtain an instance is to have `@aspectOf[Source]` on
 * `Aspect`; the macro emits a clear compile error otherwise:
 *
 * {{{
 *   @aspectOf[User]
 *   case class UserPatch(val email: Option[String] = None) derives Contract
 *
 *   // compiles — UserPatch is declared as an aspect of User
 *   summon[IsAspectOf[User, UserPatch]]
 *
 *   case class Unrelated(x: Int) derives Contract
 *
 *   // compile error — Unrelated is not an @aspectOf[User]
 *   summon[IsAspectOf[User, Unrelated]]
 * }}}
 *
 * The primary use of this witness is the typed [[Contract.validatePatch]]
 * overload, which uses it to guarantee at compile time that the aspect value
 * passed in is actually declared as a structural aspect of the contract's
 * source type.
 */
@implicitNotFound(
  "'${Aspect}' is not a structural aspect of '${Source}'. " +
  "Annotate it with @aspectOf[${Source}] to use it in a typed patch."
)
sealed abstract class IsAspectOf[Source, Aspect]

object IsAspectOf:

  // Single reusable singleton — safe because the JVM erases generic parameters.
  private[concentric] object singleton extends IsAspectOf[Nothing, Nothing]

  /**
   * Summon compile-time evidence that `A` is an `@aspectOf[S]`.
   *
   * The macro checks the annotation at compile time and either:
   *  - returns the evidence when `@aspectOf[S]` is found on `A`, or
   *  - emits a compile error naming the missing annotation.
   */
  inline given derived[S, A]: IsAspectOf[S, A] =
    ${ internal.ContractMacro.isAspectOfImpl[S, A] }
