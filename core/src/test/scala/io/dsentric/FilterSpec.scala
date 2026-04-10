package io.dsentric

final class FilterSpec extends SpecBase:

  def spec: Unit = suite("FilterSpec")(

    // ── FieldFilter.is / isNot ────────────────────────────────────────────────

    suite("Filter[T] — equality")(

      test(".is matches a field exactly") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "age" -> 30)
        val f = userContract.filter.field(_.name).is("Alice")
        assertTrue(f.test(raw))
      },

      test(".is does not match a different value") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Bob", "age" -> 30)
        val f = userContract.filter.field(_.name).is("Alice")
        assertTrue(!f.test(raw))
      },

      test(".isNot passes when field has a different value") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Bob")
        val f = userContract.filter.field(_.name).isNot("Alice")
        assertTrue(f.test(raw))
      },

      test(".isNot fails when field matches the excluded value") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val f = userContract.filter.field(_.name).isNot("Alice")
        assertTrue(!f.test(raw))
      }
    ),

    // ── ordered comparison ────────────────────────────────────────────────────

    suite("Filter[T] — ordered comparison")(

      test(".gt passes when field value is greater") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "X", "age" -> 25)
        val f = userContract.filter.field(_.age).gt(18)
        assertTrue(f.test(raw))
      },

      test(".gt fails when field value equals the bound") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "X", "age" -> 18)
        val f = userContract.filter.field(_.age).gt(18)
        assertTrue(!f.test(raw))
      },

      test(".gte passes when field value equals the bound") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "X", "age" -> 18)
        val f = userContract.filter.field(_.age).gte(18)
        assertTrue(f.test(raw))
      },

      test(".lt passes when field value is less than bound") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "X", "age" -> 17)
        val f = userContract.filter.field(_.age).lt(18)
        assertTrue(f.test(raw))
      },

      test(".lte passes when field value equals the bound") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "X", "age" -> 18)
        val f = userContract.filter.field(_.age).lte(18)
        assertTrue(f.test(raw))
      },

      test("range: gte AND lt narrows correctly") {
        val inRange:  RawObject = Map("id" -> 1L, "name" -> "X", "age" -> 30)
        val tooYoung: RawObject = Map("id" -> 1L, "name" -> "X", "age" -> 17)
        val tooOld:   RawObject = Map("id" -> 1L, "name" -> "X", "age" -> 65)
        val f = userContract.filter.field(_.age).gte(18) && userContract.filter.field(_.age).lt(65)
        assertTrue(f.test(inRange), !f.test(tooYoung), !f.test(tooOld))
      }
    ),

    // ── set membership ────────────────────────────────────────────────────────

    suite("Filter[T] — set membership")(

      test(".in passes when value is in the set") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val f = userContract.filter.field(_.name).in(List("Alice", "Bob"))
        assertTrue(f.test(raw))
      },

      test(".in fails when value is not in the set") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Carol")
        val f = userContract.filter.field(_.name).in(List("Alice", "Bob"))
        assertTrue(!f.test(raw))
      },

      test(".notIn passes when value is absent from the set") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Carol")
        val f = userContract.filter.field(_.name).notIn(List("Alice", "Bob"))
        assertTrue(f.test(raw))
      }
    ),

    // ── exists / notExists ────────────────────────────────────────────────────

    suite("Filter[T] — exists / notExists")(

      test(".exists passes when field is present and non-null") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "email" -> "a@b.com")
        val f = userContract.filter.field(_.email).exists
        assertTrue(f.test(raw))
      },

      test(".exists fails when optional field is absent") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val f = userContract.filter.field(_.email).exists
        assertTrue(!f.test(raw))
      },

      test(".notExists passes when optional field is absent") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val f = userContract.filter.field(_.email).notExists
        assertTrue(f.test(raw))
      }
    ),

    // ── string predicates ─────────────────────────────────────────────────────

    suite("Filter[T] — string predicates")(

      test(".startsWith passes for matching prefix") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val f = userContract.filter.field(_.name).startsWith("Ali")
        assertTrue(f.test(raw))
      },

      test(".startsWith fails for non-matching prefix") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Bob")
        val f = userContract.filter.field(_.name).startsWith("Ali")
        assertTrue(!f.test(raw))
      },

      test(".contains passes when substring is present") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice Wonderland")
        val f = userContract.filter.field(_.name).contains("Wonder")
        assertTrue(f.test(raw))
      },

      test(".contains fails when substring is absent") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Bob")
        val f = userContract.filter.field(_.name).contains("Wonder")
        assertTrue(!f.test(raw))
      },

      test(".matches passes for a regex pattern") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val f = userContract.filter.field(_.name).matches("[A-Z][a-z]+")
        assertTrue(f.test(raw))
      },

      test(".matches fails when pattern does not match") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "123")
        val f = userContract.filter.field(_.name).matches("[A-Z][a-z]+")
        assertTrue(!f.test(raw))
      },

      test(".isSome matches an optional field's inner value") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "email" -> "alice@example.com")
        val f = userContract.filter.field(_.email).isSome("alice@example.com")
        assertTrue(f.test(raw))
      },

      test(".isNone matches when the optional field is absent") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val f = userContract.filter.field(_.email).isNone
        assertTrue(f.test(raw))
      },

      test(".isSome does not match when the optional field is absent") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val f = userContract.filter.field(_.email).isSome("alice@example.com")
        assertTrue(!f.test(raw))
      }
    ),

    // ── logical combinators ───────────────────────────────────────────────────

    suite("Filter[T] — logical combinators")(

      test("&& passes when both sides match") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "age" -> 30)
        val f = userContract.filter.field(_.name).is("Alice") &&
                userContract.filter.field(_.age).gte(18)
        assertTrue(f.test(raw))
      },

      test("&& fails when one side does not match") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Bob", "age" -> 30)
        val f = userContract.filter.field(_.name).is("Alice") &&
                userContract.filter.field(_.age).gte(18)
        assertTrue(!f.test(raw))
      },

      test("|| passes when at least one side matches") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "age" -> 5)
        val f = userContract.filter.field(_.name).is("Alice") ||
                userContract.filter.field(_.age).gte(18)
        assertTrue(f.test(raw))
      },

      test("|| fails when neither side matches") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Carol", "age" -> 5)
        val f = userContract.filter.field(_.name).is("Alice") ||
                userContract.filter.field(_.age).gte(18)
        assertTrue(!f.test(raw))
      },

      test("! (unary not) inverts the expression") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Bob")
        val f = !userContract.filter.field(_.name).is("Alice")
        assertTrue(f.test(raw))
      },

      test("complex three-way expression") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "age" -> 25, "email" -> "a@b.com")
        val f = userContract.filter.field(_.age).gte(18)          &&
                userContract.filter.field(_.name).startsWith("A") &&
                userContract.filter.field(_.email).exists
        assertTrue(f.test(raw))
      }
    ),

    // ── .apply collection filtering ───────────────────────────────────────────

    suite("Filter[T] — .apply collection filtering")(

      test(".apply filters a list of raw objects") {
        val items: List[RawObject] = List(
          Map("id" -> 1L, "name" -> "Alice", "age" -> 30),
          Map("id" -> 2L, "name" -> "Bob",   "age" -> 17),
          Map("id" -> 3L, "name" -> "Carol", "age" -> 25)
        )
        val f = userContract.filter.field(_.age).gte(18)
        val result = f(items)
        assertTrue(
          result.size == 2,
          result.exists(_("name") == "Alice"),
          result.exists(_("name") == "Carol")
        )
      }
    ),

    // ── toMongoQuery ──────────────────────────────────────────────────────────

    suite("Filter[T] — toMongoQuery")(

      test("equality produces flat field map") {
        val f = userContract.filter.field(_.name).is("Alice")
        val q = f.toMongoQuery
        assertTrue(q == Map("name" -> "Alice"))
      },

      test("gt produces $gt operator") {
        val f = userContract.filter.field(_.age).gt(18)
        val q = f.toMongoQuery
        assertTrue(q == Map("age" -> Map("$gt" -> 18)))
      },

      test("$and smart-merges distinct fields into flat map") {
        val f = userContract.filter.field(_.age).gte(18) &&
                userContract.filter.field(_.name).is("Alice")
        val q = f.toMongoQuery
        assertTrue(q.contains("age"), q.contains("name"), !q.contains("$and"))
      },

      test("$and falls back to explicit $and for same-field range") {
        val f = userContract.filter.field(_.age).gte(18) &&
                userContract.filter.field(_.age).lt(65)
        val q = f.toMongoQuery
        assertTrue(q.contains("$and"))
      },

      test("$or produces $or wrapper") {
        val f = userContract.filter.field(_.name).is("Alice") ||
                userContract.filter.field(_.name).is("Bob")
        val q = f.toMongoQuery
        assertTrue(q.contains("$or"))
      },

      test("! produces $nor") {
        val f = !userContract.filter.field(_.name).is("Alice")
        val q = f.toMongoQuery
        assertTrue(q.contains("$nor"))
      },

      test("$in produces correct array") {
        val f = userContract.filter.field(_.name).in(List("Alice", "Bob"))
        val q = f.toMongoQuery
        assertTrue(q == Map("name" -> Map("$in" -> List("Alice", "Bob"))))
      },

      test("$exists: true produces correct document") {
        val f = userContract.filter.field(_.email).exists
        val q = f.toMongoQuery
        assertTrue(q == Map("email" -> Map("$exists" -> true)))
      }
    ),

    // ── View[T] aspect pipeline ───────────────────────────────────────────────

    suite("View[T]")(

      test("empty view is identity") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val view = View[User]
        assertTrue(view(raw) == raw)
      },

      test(".omit removes the field from the output") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "password" -> "s3cr3t")
        val view = View[User].omit(_.id).omit(_.password)
        val out = view(raw)
        assertTrue(!out.contains("id"), !out.contains("password"), out.contains("name"))
      },

      test(".mask replaces the field value with the mask string") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "password" -> "s3cr3t")
        val view = View[User].mask(_.password)
        val out = view(raw)
        assertTrue(out.get("password") == Some("***"))
      },

      test(".mask accepts a custom mask string") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "password" -> "s3cr3t")
        val view = View[User].mask(_.password, "••••")
        val out = view(raw)
        assertTrue(out.get("password") == Some("••••"))
      },

      test(".compute adds a derived field") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "email" -> "alice@example.com")
        val view = View[User].compute("displayName", r =>
          s"${r.getOrElse("name", "?")} <${r.getOrElse("email", "no-reply")}>"
        )
        val out = view(raw)
        assertTrue(out.get("displayName") == Some("Alice <alice@example.com>"))
      },

      test("transforms compose in order: omit then compute does not see omitted field") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "password" -> "s3cr3t")
        val view = View[User]
          .omit(_.password)
          .compute("hint", r => if r.contains("password") then "has-pw" else "no-pw")
        val out = view(raw)
        assertTrue(out.get("hint") == Some("no-pw"))
      },

      test("View.size reflects number of added transforms") {
        val view = View[User].omit(_.id).mask(_.password).compute("x", _ => 1)
        assertTrue(view.size == 3)
      }
    ),

    // ── extraFields ───────────────────────────────────────────────────────────

    suite("extraFields")(

      test("returns empty map when no unknown fields are present") {
        val raw: RawObject = Map("title" -> "Doc", "body" -> "Content")
        val extra = openDocContract.extraFields(raw)
        assertTrue(extra.isEmpty)
      },

      test("returns only the keys not declared in the contract") {
        val raw: RawObject = Map(
          "title"  -> "Doc",
          "body"   -> "Content",
          "source" -> "API",
          "tags"   -> List("a", "b")
        )
        val extra = openDocContract.extraFields(raw)
        assertTrue(
          !extra.contains("title"),
          !extra.contains("body"),
          extra.get("source") == Some("API"),
          extra.contains("tags")
        )
      },

      test("extraFields returns all unknown fields from a fully-extra map") {
        val raw: RawObject = Map("x" -> 1, "y" -> 2)
        val extra = openDocContract.extraFields(raw)
        assertTrue(extra.size == 2, extra.contains("x"), extra.contains("y"))
      }
    )
  )

  spec
