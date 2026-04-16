package io.concentric.internal

import scala.quoted.*
import io.concentric.{View, ViewTransform}

/**
 * Compile-time macros backing [[io.concentric.View.omit]] and
 * [[io.concentric.View.mask]].
 *
 * The primary job is **field-name extraction**: given a selector lambda of
 * the form `(t: T) => t.fieldName`, return the string `"fieldName"` at
 * compile time and generate the correct [[ViewTransform]] value.
 */
object ViewMacro:

  def omit[T: Type, A: Type](using Quotes)(
    view:     Expr[View[T]],
    selector: Expr[T => A]
  ): Expr[View[T]] =
    val name = extractFieldName(selector)
    '{ View.addTransform[T]($view, ViewTransform.Omit[T](${ Expr(name) })) }

  def mask[T: Type, A: Type](using Quotes)(
    view:     Expr[View[T]],
    selector: Expr[T => A],
    maskStr:  Expr[String]
  ): Expr[View[T]] =
    val name = extractFieldName(selector)
    '{ View.addTransform[T]($view, ViewTransform.Mask[T](${ Expr(name) }, $maskStr)) }

  // ── Field-name extractor (same pattern as PatchMacro) ─────────────────────

  private def extractFieldName[T: Type](using q: Quotes)(
    selector: Expr[T => ?]
  ): String =
    import q.reflect.*

    def extract(term: Term): String = term match
      case Select(_, name)                             => name
      case Block(List(DefDef(_, _, _, Some(body))), _) => extract(body)
      case Block(_, body)                              => extract(body)
      case Inlined(_, _, body)                         => extract(body)
      case Typed(inner, _)                             => extract(inner)
      case other =>
        report.errorAndAbort(
          s"View.omit / View.mask selector must be a simple field accessor " +
          s"like '_.fieldName', but got: ${other.show}"
        )

    extract(selector.asTerm)
