package io.dsentric.internal

import scala.quoted.*
import io.dsentric.{FieldFilter, Filter, RawDecoder}

/**
 * Compile-time macro backing [[Filter.field]].
 *
 * Extracts the field name from a selector lambda (identical logic to
 * [[PatchMacro.extractFieldName]]) and summons the appropriate
 * [[RawDecoder]] at compile time.  The return type is always
 * `FieldFilter[T, A]` — the full field type including any `Option` wrapper.
 *
 * For `Option[A]` fields use the `isSome` / `isNone` extension methods
 * defined in [[io.dsentric.FieldFilter]] rather than `.is(Some(...))`.
 */
object FilterMacro:

  /** Entry point called from the inline `Filter.field` method. */
  def field[T: Type, A: Type](using Quotes)(
    filter:   Expr[Filter[T]],
    selector: Expr[T => A]
  ): Expr[FieldFilter[T, A]] =
    import quotes.reflect.*

    val fieldName = extractFieldName(selector)

    Expr.summon[RawDecoder[A]] match
      case Some(d) => '{ new FieldFilter[T, A](${ Expr(fieldName) }, $d) }
      case None    =>
        report.errorAndAbort(
          s"Filter.field: no RawDecoder[${TypeRepr.of[A].show}] for field '$fieldName'. " +
          "Provide: given RawDecoder[...] = ..."
        )

  // ── Field-name extractor — mirrors PatchMacro.extractFieldName ────────────

  private def extractFieldName[T: Type](using q: Quotes)(
    selector: Expr[T => ?]
  ): String =
    import q.reflect.*

    def extract(term: Term): String = term match
      case Select(_, name)                                    => name
      case Block(List(DefDef(_, _, _, Some(body))), _)        => extract(body)
      case Block(_, body)                                     => extract(body)
      case Inlined(_, _, body)                                => extract(body)
      case Typed(inner, _)                                    => extract(inner)
      case other =>
        report.errorAndAbort(
          s"Filter.field selector must be a simple field accessor like '_.fieldName', " +
          s"but got: ${other.show}"
        )

    extract(selector.asTerm)
