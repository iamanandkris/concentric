package io.concentric

import scala.jdk.CollectionConverters.*

/**
 * Tests for [[JvmFilter]], [[JvmFieldFilter]], [[JvmFilterExpr]], and the
 * [[JvmContract.filter]] accessor.
 *
 * Uses [[TestKotlinSimUser]] (plain Java class, Java-11-compatible).
 * Fields: id @immutable Long, name @nonEmpty String,
 *         age @min(0) @max(150) Int, email @email String.
 *
 * Tests cover every operator, logical combinators, in-memory apply, and the
 * MongoDB query document shape produced by toMongoQuery.
 */
final class JvmFilterSpec extends SpecBase:

  val userContract: JvmContract[TestKotlinSimUser] = JvmContract.of(
    classOf[TestKotlinSimUser],
    fields => new TestKotlinSimUser(
      fields.get("id").asInstanceOf[Long],
      fields.get("name").asInstanceOf[String],
      fields.get("age").asInstanceOf[Int],
      fields.get("email").asInstanceOf[String]
    )
  )

  private def jmap(pairs: (String, AnyRef)*): java.util.Map[String, AnyRef] =
    val m = new java.util.LinkedHashMap[String, AnyRef]()
    pairs.foreach { case (k, v) => m.put(k, v) }
    m

  private val alice = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30), "email" -> "alice@example.com")
  private val bob   = jmap("id" -> Long.box(2L), "name" -> "Bob",   "age" -> Int.box(17), "email" -> "bob@example.com")
  private val carol = jmap("id" -> Long.box(3L), "name" -> "Carol", "age" -> Int.box(25), "email" -> "carol@example.com")
  private val all   = java.util.List.of(alice, bob, carol)

  // Shorthand — always build from the contract's filter accessor
  private def f = userContract.filter

  // ── JvmContract.filter accessor ───────────────────────────────────────────

  suite("JvmContract.filter accessor")(
    test("filter returns a non-null JvmFilter") {
      assertTrue(userContract.filter != null)
    }
  )

  // ── exists / notExists ────────────────────────────────────────────────────

  suite("exists / notExists")(

    test("exists — field present → true") {
      assertTrue(f.field("name").exists().test(alice))
    },

    test("exists — field absent → false") {
      val sparse = jmap("name" -> "Alice")
      assertTrue(!f.field("age").exists().test(sparse))
    },

    test("notExists — field absent → true") {
      val sparse = jmap("name" -> "Alice")
      assertTrue(f.field("age").notExists().test(sparse))
    },

    test("notExists — field present → false") {
      assertTrue(!f.field("name").notExists().test(alice))
    }
  )

  // ── Numeric comparisons ───────────────────────────────────────────────────

  suite("numeric comparisons")(

    test("gte — value equals threshold") {
      assertTrue(f.field("age").gte(30).test(alice))
    },

    test("gte — value above threshold") {
      assertTrue(f.field("age").gte(18).test(alice))
    },

    test("gte — value below threshold → false") {
      assertTrue(!f.field("age").gte(31).test(alice))
    },

    test("gt — strictly greater passes") {
      assertTrue(f.field("age").gt(29).test(alice))
    },

    test("gt — equal value fails") {
      assertTrue(!f.field("age").gt(30).test(alice))
    },

    test("lt — strictly less passes") {
      assertTrue(f.field("age").lt(31).test(alice))
    },

    test("lt — equal value fails") {
      assertTrue(!f.field("age").lt(30).test(alice))
    },

    test("lte — equal value passes") {
      assertTrue(f.field("age").lte(30).test(alice))
    },

    test("lte — greater value fails") {
      assertTrue(!f.field("age").lte(29).test(alice))
    },

    test("Long field comparison works (id is Long)") {
      assertTrue(f.field("id").gte(1).test(alice))
      assertTrue(!f.field("id").gte(2).test(alice))
    }
  )

  // ── String operations ────────────────────────────────────────────────────

  suite("string operations")(

    test("startsWith — matching prefix → true") {
      assertTrue(f.field("name").startsWith("Ali").test(alice))
    },

    test("startsWith — non-matching prefix → false") {
      assertTrue(!f.field("name").startsWith("Bob").test(alice))
    },

    test("contains — substring present → true") {
      assertTrue(f.field("name").contains("lic").test(alice))
    },

    test("contains — substring absent → false") {
      assertTrue(!f.field("name").contains("xyz").test(alice))
    },

    test("matches — regex matches → true") {
      assertTrue(f.field("name").matches("A.*e").test(alice))
    },

    test("matches — regex no match → false") {
      assertTrue(!f.field("name").matches("B.*").test(alice))
    },

    test("string gte — alphabetical ordering") {
      assertTrue(f.field("name").gte("A").test(alice))
      assertTrue(!f.field("name").gte("Z").test(alice))
    },

    test("string lt — alphabetical ordering") {
      assertTrue(f.field("name").lt("Z").test(alice))
      assertTrue(!f.field("name").lt("A").test(alice))
    }
  )

  // ── equalTo / notEqualTo ──────────────────────────────────────────────────

  suite("equalTo / notEqualTo")(

    test("equalTo — matching value → true") {
      assertTrue(f.field("name").equalTo("Alice").test(alice))
    },

    test("equalTo — non-matching value → false") {
      assertTrue(!f.field("name").equalTo("Bob").test(alice))
    },

    test("notEqualTo — non-matching value → true") {
      assertTrue(f.field("name").notEqualTo("Bob").test(alice))
    },

    test("notEqualTo — matching value → false") {
      assertTrue(!f.field("name").notEqualTo("Alice").test(alice))
    }
  )

  // ── in / notIn ────────────────────────────────────────────────────────────

  suite("in / notIn")(

    test("in — value is a member → true") {
      assertTrue(f.field("name").in(java.util.List.of("Alice", "Charlie")).test(alice))
    },

    test("in — value not a member → false") {
      assertTrue(!f.field("name").in(java.util.List.of("Bob", "Dave")).test(alice))
    },

    test("notIn — value not a member → true") {
      assertTrue(f.field("name").notIn(java.util.List.of("Bob", "Dave")).test(alice))
    },

    test("notIn — value is a member → false") {
      assertTrue(!f.field("name").notIn(java.util.List.of("Alice")).test(alice))
    }
  )

  // ── Logical combinators ───────────────────────────────────────────────────

  suite("logical combinators")(

    test("and — both sides true → true") {
      val expr = f.field("age").gte(18).and(f.field("name").startsWith("A"))
      assertTrue(expr.test(alice))
    },

    test("and — left side false → false") {
      val expr = f.field("age").gte(31).and(f.field("name").startsWith("A"))
      assertTrue(!expr.test(alice))
    },

    test("and — right side false → false") {
      val expr = f.field("age").gte(18).and(f.field("name").startsWith("B"))
      assertTrue(!expr.test(alice))
    },

    test("or — first side true → true") {
      val expr = f.field("name").startsWith("A").or(f.field("name").startsWith("B"))
      assertTrue(expr.test(alice))
    },

    test("or — second side true → true") {
      val expr = f.field("name").startsWith("X").or(f.field("name").startsWith("A"))
      assertTrue(expr.test(alice))
    },

    test("or — both sides false → false") {
      val expr = f.field("name").startsWith("X").or(f.field("name").startsWith("Y"))
      assertTrue(!expr.test(alice))
    },

    test("not — negates a passing expression") {
      assertTrue(!f.field("name").startsWith("A").not().test(alice))
    },

    test("not — negates a failing expression") {
      assertTrue(f.field("name").startsWith("B").not().test(alice))
    },

    test("chained and — adults whose name starts with A") {
      val expr = f.field("age").gte(18).and(f.field("name").startsWith("A"))
      assertTrue(expr.test(alice))
      assertTrue(!expr.test(bob))   // bob is 17
    }
  )

  // ── apply — in-memory collection filter ───────────────────────────────────

  suite("apply — in-memory collection filter")(

    test("filter adults (age >= 18) returns alice and carol") {
      val results = f.field("age").gte(18).apply(all)
      assertTrue(results.size == 2)
    },

    test("filter by name prefix returns only alice") {
      val results = f.field("name").startsWith("A").apply(all)
      assertTrue(results.size == 1, results.get(0).get("name") == "Alice")
    },

    test("filter that matches no rows returns empty list") {
      val results = f.field("age").gte(100).apply(all)
      assertTrue(results.isEmpty)
    },

    test("filter that matches all rows returns all") {
      val results = f.field("age").gte(0).apply(all)
      assertTrue(results.size == all.size)
    },

    test("combined and filter — adult whose name starts with C") {
      val results = f.field("age").gte(18).and(f.field("name").startsWith("C")).apply(all)
      assertTrue(results.size == 1, results.get(0).get("name") == "Carol")
    }
  )

  // ── toMongoQuery ──────────────────────────────────────────────────────────

  suite("toMongoQuery — MongoDB document shape")(

    test("exists → { field: { $exists: true } }") {
      val q = f.field("email").exists().toMongoQuery()
      val emailDoc = q.get("email").asInstanceOf[java.util.Map[?, ?]]
      assertTrue(emailDoc != null, emailDoc.get("$exists") == Boolean.box(true))
    },

    test("notExists → { field: { $exists: false } }") {
      val q = f.field("email").notExists().toMongoQuery()
      val emailDoc = q.get("email").asInstanceOf[java.util.Map[?, ?]]
      assertTrue(emailDoc.get("$exists") == Boolean.box(false))
    },

    test("gte → { field: { $gte: value } }") {
      val q = f.field("age").gte(18).toMongoQuery()
      val ageDoc = q.get("age").asInstanceOf[java.util.Map[?, ?]]
      assertTrue(ageDoc != null, ageDoc.containsKey("$gte"))
    },

    test("lt → { field: { $lt: value } }") {
      val q = f.field("age").lt(18).toMongoQuery()
      val ageDoc = q.get("age").asInstanceOf[java.util.Map[?, ?]]
      assertTrue(ageDoc.containsKey("$lt"))
    },

    test("startsWith → { field: { $regex: ^prefix } }") {
      val q = f.field("name").startsWith("Al").toMongoQuery()
      val nameDoc = q.get("name").asInstanceOf[java.util.Map[?, ?]]
      assertTrue(
        nameDoc != null,
        nameDoc.containsKey("$regex"),
        nameDoc.get("$regex").toString.startsWith("^")
      )
    },

    test("and with non-overlapping fields → flat merge (no $and wrapper)") {
      val q = f.field("age").gte(18).and(f.field("name").startsWith("A")).toMongoQuery()
      assertTrue(!q.containsKey("$and"), q.containsKey("age"), q.containsKey("name"))
    },

    test("or → { $or: [...] }") {
      val q = f.field("name").startsWith("A").or(f.field("name").startsWith("B")).toMongoQuery()
      assertTrue(q.containsKey("$or"))
    },

    test("not → { $nor: [...] }") {
      val q = f.field("name").startsWith("X").not().toMongoQuery()
      assertTrue(q.containsKey("$nor"))
    },

    test("in → { field: { $in: [...] } }") {
      val q = f.field("name").in(java.util.List.of("Alice", "Bob")).toMongoQuery()
      val nameDoc = q.get("name").asInstanceOf[java.util.Map[?, ?]]
      assertTrue(nameDoc != null, nameDoc.containsKey("$in"))
    },

    test("notIn → { field: { $nin: [...] } }") {
      val q = f.field("name").notIn(java.util.List.of("Alice")).toMongoQuery()
      val nameDoc = q.get("name").asInstanceOf[java.util.Map[?, ?]]
      assertTrue(nameDoc.containsKey("$nin"))
    }
  )
