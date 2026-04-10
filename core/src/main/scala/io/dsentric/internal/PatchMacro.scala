package io.dsentric.internal

import scala.quoted.*
import io.dsentric.{Patch, RawObject}

/**
 * Compile-time macros backing [[io.dsentric.Patch.set]] and
 * [[io.dsentric.Patch.unset]].
 *
 * The primary job is **field-name extraction**: given a selector lambda of the
 * form `(t: T) => t.fieldName`, return the string `"fieldName"` at compile
 * time so the patch key is always correct.
 *
 * Supported selector shapes (all compile to equivalent trees):
 *  - `_.name`
 *  - `(u: User) => u.name`
 *  - compiler-inlined variants (Inlined / Block wrappers)
 */
object PatchMacro:

  // ── Public macro entry points ─────────────────────────────────────────────

  def setField[T: Type, A: Type](using Quotes)(
    patch:    Expr[Patch[T]],
    selector: Expr[T => A],
    value:    Expr[A]
  ): Expr[Patch[T]] =
    val name = extractFieldName(selector)
    '{ new Patch[T]($patch.fields + (${ Expr(name) } -> ($value: Any)), $patch.modifiers) }

  def unsetField[T: Type, A: Type](using Quotes)(
    patch:    Expr[Patch[T]],
    selector: Expr[T => Option[A]]
  ): Expr[Patch[T]] =
    val name = extractFieldName(selector)
    '{ new Patch[T]($patch.fields + (${ Expr(name) } -> (None: Any)), $patch.modifiers) }

  def modifyField[T: Type, A: Type](using Quotes)(
    patch:    Expr[Patch[T]],
    selector: Expr[T => A],
    fn:       Expr[A => A]
  ): Expr[Patch[T]] =
    val name = extractFieldName(selector)
    '{
      val modifier: Any => Any = (v: Any) => $fn(v.asInstanceOf[A])
      new Patch[T]($patch.fields, $patch.modifiers + (${ Expr(name) } -> modifier))
    }

  // ── Field-name extractor ──────────────────────────────────────────────────

  private def extractFieldName[T: Type](using q: Quotes)(
    selector: Expr[T => ?]
  ): String =
    import q.reflect.*

    // Walk the term tree stripping wrapper nodes until we hit a Select.
    def extract(term: Term): String = term match

      // The field access itself: (something).fieldName
      case Select(_, name) => name

      // Lambda body: { def $anonfun(x) = body; closure }
      // DefDef in Scala 3.4.x: (name, paramClauses, returnTpt, rhs)
      case Block(List(DefDef(_, _, _, Some(body))), _) => extract(body)

      // Plain block (last expression is the result)
      case Block(_, body) => extract(body)

      // Inlined / macro-expanded wrapper
      case Inlined(_, _, body) => extract(body)

      // Typed ascription: (expr: T)
      case Typed(inner, _) => extract(inner)

      case other =>
        report.errorAndAbort(
          s"Patch.set / Patch.unset selector must be a simple field accessor " +
          s"like '_.fieldName', but got: ${other.show}"
        )

    extract(selector.asTerm)
