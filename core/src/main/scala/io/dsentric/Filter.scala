package io.dsentric

/**
 * Entry point for building type-safe filter expressions over contract type T.
 *
 * Obtain a [[Filter]][T] via [[Contract.filter]] or `Filter[T]`:
 *
 * {{{
 *   // Via contract (recommended — ties the filter to a specific contract)
 *   val f: FilterExpr[User] =
 *     userContract.filter.field(_.age).gte(18) &&
 *     userContract.filter.field(_.name).startsWith("A")
 *
 *   // Via Filter companion (standalone)
 *   val f2: FilterExpr[User] = Filter[User].field(_.age).lt(30)
 *
 *   // In-memory filtering
 *   val matching: List[RawObject] = f.apply(rawItems)
 *
 *   // MongoDB query document
 *   val q: RawObject = f.toMongoQuery
 *   // → Map("age" -> Map("$gte" -> 18), "name" -> Map("$regex" -> "^A"))
 * }}}
 *
 * The `field` method is a compile-time macro:
 *  - It extracts the field name from the lambda at compile time — no runtime
 *    string literals to mistype.
 *  - It summons `RawDecoder[A]` at compile time — missing decoders are a
 *    compile error.
 *  - For `Option[A]` fields the `Option` wrapper is automatically unwrapped,
 *    so you write `.is("value")` not `.is(Some("value"))`.
 *
 * ==Building complex expressions==
 *
 * {{{
 *   import io.dsentric.FieldFilter   // for string extension methods
 *
 *   val nameF  = userContract.filter.field(_.name)
 *   val ageF   = userContract.filter.field(_.age)
 *   val emailF = userContract.filter.field(_.email)  // Option[String] → String decoder
 *
 *   val expr =
 *     (nameF.startsWith("A") || nameF.startsWith("B")) &&
 *     ageF.gte(18) &&
 *     emailF.exists
 *
 *   expr.test(raw)         // Boolean
 *   expr.toMongoQuery      // RawObject
 *   expr(rawItems)         // List[RawObject]
 * }}}
 */
final class Filter[T]:

  /**
   * Build a [[FieldFilter]] for a single field of T.
   *
   * This is a compile-time macro — the field name is extracted from the
   * selector lambda at compile time, and the appropriate [[RawDecoder]] is
   * summoned automatically.
   *
   * @param selector  A simple field accessor of the form `_.fieldName`.
   * @return          A [[FieldFilter]][T, A] with operators for the field type.
   */
  inline def field[A](inline selector: T => A): FieldFilter[T, A] =
    ${ internal.FilterMacro.field[T, A]('this, 'selector) }


object Filter:

  /**
   * Create a new [[Filter]][T] builder.
   *
   * The type parameter can be inferred from context or supplied explicitly:
   *
   * {{{
   *   val f = Filter[User]
   *   f.field(_.name).is("Alice")
   * }}}
   */
  def apply[T]: Filter[T] = new Filter[T]
