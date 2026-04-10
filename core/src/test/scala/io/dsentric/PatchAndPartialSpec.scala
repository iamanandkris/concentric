package io.dsentric

final class PatchAndPartialSpec extends SpecBase:

  def spec: Unit = suite("PatchAndPartialSpec")(

    // ── Patch[T] typed builder ────────────────────────────────────────────────

    suite("Patch[T]")(

      test("Patch.empty starts with no fields") {
        val p = Patch.empty[User]
        assertTrue(p.isEmpty, p.size == 0)
      },

      test(".set captures field name and value at compile time") {
        val p = Patch.empty[User].set(_.name, "Alice").set(_.age, 25)
        assertTrue(
          p.fields.get("name") == Some("Alice"),
          p.fields.get("age")  == Some(25)
        )
      },

      test(".unset records None for an optional field") {
        val p = Patch.empty[User].unset(_.email)
        assertTrue(p.fields.get("email") == Some(None))
      },

      test("applyPatch with a valid Patch[T] merges and validates") {
        val current: RawObject = Map("id" -> 1L, "name" -> "Alice", "age" -> 25)
        val patch = Patch.empty[User].set(_.name, "Alicia").set(_.age, 26)
        for user <- userContract.applyPatch(current, patch)
        yield assertTrue(user.name == "Alicia", user.age == 26, user.id == 1L)
      },

      test("applyPatch rejects @immutable fields") {
        val current: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val patch = Patch.empty[User].set(_.id, 99L)
        for result <- userContract.applyPatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(_.code == ViolationCode.ImmutableField)
        )
      },

      test("applyPatch enforces constraint annotations on patched values") {
        val current: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val patch = Patch.empty[User].set(_.name, "")
        for result <- userContract.applyPatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("name") &&
            v.code == ViolationCode.ConstraintFailed("nonEmpty")
          )
        )
      },

      test("Patch.fromRaw produces a Patch from a raw map") {
        val raw: RawObject = Map("name" -> "Bob", "age" -> 40)
        val p = Patch.fromRaw[User](raw)
        assertTrue(p.fields == raw)
      },

      test("Patch.toRaw round-trips the accumulated fields") {
        val p = Patch.empty[User].set(_.name, "Carol").set(_.age, 22)
        val raw = p.toRaw
        assertTrue(
          raw.get("name") == Some("Carol"),
          raw.get("age")  == Some(22),
          raw.size        == 2
        )
      },

      test("chained .set calls accumulate independently") {
        val p = Patch.empty[User]
          .set(_.name, "First")
          .set(_.age, 10)
          .set(_.name, "Second")
        assertTrue(
          p.fields.get("name") == Some("Second"),
          p.fields.get("age")  == Some(10),
          p.size               == 2
        )
      }
    ),

    // ── validatePartial / Draft[T] ────────────────────────────────────────────

    suite("validatePartial / Draft[T]")(

      test("partial validation succeeds with only a subset of required fields") {
        val raw: RawObject = Map("id" -> 1L)
        for draft <- userContract.validatePartial(raw)
        yield assertTrue(
          draft.validatedFields.get("id") == Some(1L),
          !draft.validatedFields.contains("name")
        )
      },

      test("partial validation still rejects constraint violations") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "")
        for result <- userContract.validatePartial(raw).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("name") &&
            v.code == ViolationCode.ConstraintFailed("nonEmpty")
          )
        )
      },

      test("Draft.finalize succeeds when all required fields are present") {
        val draft = new Draft[User](Map("id" -> 2L, "name" -> "Bob"))
        for user <- draft.finalize(userContract)
        yield assertTrue(user.id == 2L, user.name == "Bob")
      },

      test("Draft.finalize fails with ExpectedMissing for remaining required fields") {
        val draft = new Draft[User](Map("id" -> 3L))
        for result <- draft.finalize(userContract).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("name") &&
            v.code == ViolationCode.ExpectedMissing
          )
        )
      },

      test("all-optional contract accepts empty input in validatePartial") {
        for draft <- prefsContract.validatePartial(Map.empty)
        yield assertTrue(draft.isEmpty)
      },

      test("Draft.merge(Draft) combines two partially-validated drafts") {
        for
          draftA <- userContract.validatePartial(Map("id" -> 1L))
          draftB <- userContract.validatePartial(Map("name" -> "Alice"))
          merged  = draftA.merge(draftB)
        yield assertTrue(
          merged.validatedFields.get("id")   == Some(1L),
          merged.validatedFields.get("name") == Some("Alice"),
          merged.size == 2
        )
      },

      test("Draft.merge(Draft) is right-biased — other's fields win on collision") {
        for
          draftA <- userContract.validatePartial(Map("id" -> 1L, "name" -> "Alice"))
          draftB <- userContract.validatePartial(Map("name" -> "Alicia"))
          merged  = draftA.merge(draftB)
        yield assertTrue(
          merged.validatedFields.get("name") == Some("Alicia"),
          merged.validatedFields.get("id")   == Some(1L)
        )
      },

      test("Draft.merge(Draft) followed by finalize succeeds when all required fields covered") {
        for
          draftA <- userContract.validatePartial(Map("id" -> 7L))
          draftB <- userContract.validatePartial(Map("name" -> "Bob"))
          user   <- draftA.merge(draftB).finalize(userContract)
        yield assertTrue(user.id == 7L, user.name == "Bob")
      },

      test("Draft.merge(Draft) followed by finalize fails when required fields still missing") {
        for
          draftA <- userContract.validatePartial(Map("id" -> 7L))
          draftB <- userContract.validatePartial(Map("age" -> 25))
          result <- draftA.merge(draftB).finalize(userContract).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("name") &&
            v.code == ViolationCode.ExpectedMissing
          )
        )
      },

      test("merging an empty draft with a populated draft is identity for the populated one") {
        for
          populated <- userContract.validatePartial(Map("id" -> 1L, "name" -> "Carol"))
          empty     <- userContract.validatePartial(Map.empty)
          merged     = empty.merge(populated)
        yield assertTrue(
          merged.validatedFields == populated.validatedFields
        )
      }
    ),

    // ── validate (Either-returning) ───────────────────────────────────────────

    suite("validate (Either)")(

      test("returns Right when the object is valid") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val result = userContract.validate(raw)
        assertTrue(result.isRight)
      },

      test("returns Left with violations when invalid") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "")
        val result = userContract.validate(raw)
        assertTrue(
          result.isLeft,
          result.left.exists(cv =>
            cv.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("nonEmpty"))
          )
        )
      }
    ),

    // ── collectViolations ─────────────────────────────────────────────────────

    suite("collectViolations")(

      test("returns an empty list for a valid raw object") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice")
        assertTrue(userContract.collectViolations(raw).isEmpty)
      },

      test("returns violations without constructing T") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "")
        val violations = userContract.collectViolations(raw)
        assertTrue(
          violations.toList.exists(v =>
            v.path == FieldPath("name") &&
            v.code == ViolationCode.ConstraintFailed("nonEmpty")
          )
        )
      }
    ),

    // ── toRaw ─────────────────────────────────────────────────────────────────

    suite("toRaw")(

      test("serialises a validated T back to a RawObject") {
        for user <- userContract.validate(Map("id" -> 1L, "name" -> "Alice", "age" -> 30))
        yield {
          val raw = userContract.toRaw(user)
          assertTrue(
            raw.get("id")   == Some(1L),
            raw.get("name") == Some("Alice"),
            raw.get("age")  == Some(30)
          )
        }
      },

      test("toRaw round-trips through validate") {
        val original: RawObject = Map("id" -> 5L, "name" -> "Tester")
        for
          user  <- userContract.validate(original)
          raw    = userContract.toRaw(user)
          user2 <- userContract.validate(raw)
        yield assertTrue(user2.id == 5L, user2.name == "Tester")
      }
    ),

    // ── RawObjectOps ──────────────────────────────────────────────────────────

    suite("RawObjectOps")(

      test("rightDifference returns changed and new keys") {
        val base    = Map[String, Any]("a" -> 1, "b" -> 2)
        val updated = Map[String, Any]("a" -> 1, "b" -> 99, "c" -> 3)
        val diff = RawObjectOps.rightDifference(base, updated)
        assertTrue(
          !diff.contains("a"),
          diff.get("b") == Some(99),
          diff.get("c") == Some(3)
        )
      },

      test("rightDifference returns empty when no changes") {
        val base = Map[String, Any]("x" -> "hello")
        assertTrue(RawObjectOps.rightDifference(base, base).isEmpty)
      },

      test("differenceDelta captures additions and modifications") {
        val before = Map[String, Any]("name" -> "Alice", "age" -> 25)
        val after  = Map[String, Any]("name" -> "Alice", "age" -> 26, "email" -> "a@b.com")
        val delta = RawObjectOps.differenceDelta(before, after)
        assertTrue(
          !delta.contains("name"),
          delta.get("age")   == Some(26),
          delta.get("email") == Some("a@b.com")
        )
      },

      test("deltaTraverseConcat deep-merges nested maps") {
        val base = Map[String, Any](
          "name"    -> "Alice",
          "address" -> Map[String, Any]("city" -> "Old York", "zip" -> "12345")
        )
        val delta = Map[String, Any](
          "address" -> Map[String, Any]("city" -> "New York")
        )
        val result = RawObjectOps.deltaTraverseConcat(base, delta)
        val addr = result("address").asInstanceOf[Map[String, Any]]
        assertTrue(
          addr.get("city")   == Some("New York"),
          addr.get("zip")    == Some("12345"),
          result.get("name") == Some("Alice")
        )
      },

      test("deltaTraverseConcat replaces non-Map values (delta wins)") {
        val base   = Map[String, Any]("count" -> 5)
        val delta  = Map[String, Any]("count" -> 10)
        val result = RawObjectOps.deltaTraverseConcat(base, delta)
        assertTrue(result.get("count") == Some(10))
      },

      test("deltaTraverseConcat adds keys absent in base") {
        val base   = Map[String, Any]("a" -> 1)
        val delta  = Map[String, Any]("b" -> 2)
        val result = RawObjectOps.deltaTraverseConcat(base, delta)
        assertTrue(result.get("a") == Some(1), result.get("b") == Some(2))
      }
    ),

    // ── Patch.modify ──────────────────────────────────────────────────────────

    suite("Patch.modify")(

      test("modify increments an Int field") {
        val current: RawObject = Map(
          "id" -> 1L, "name" -> "Alice", "age" -> 25, "email" -> None, "password" -> None
        )
        val patch = Patch.empty[User].modify(_.age, _ + 1)
        for user <- userContract.applyPatch(current, patch)
        yield assertTrue(user.age == 26)
      },

      test("modify doubles an Int field") {
        val current: RawObject = Map(
          "id" -> 1L, "name" -> "Bob", "age" -> 10, "email" -> None, "password" -> None
        )
        val patch = Patch.empty[User].modify(_.age, (n: Int) => n * 2)
        for user <- userContract.applyPatch(current, patch)
        yield assertTrue(user.age == 20)
      },

      test("modify on a String field appends a suffix") {
        val current: RawObject = Map(
          "id" -> 1L, "name" -> "Alice", "age" -> 30, "email" -> None, "password" -> None
        )
        val patch = Patch.empty[User].modify(_.name, _ + " Smith")
        for user <- userContract.applyPatch(current, patch)
        yield assertTrue(user.name == "Alice Smith")
      },

      test("modify is silently skipped when the field is absent from current raw") {
        val current: RawObject = Map(
          "id" -> 1L, "name" -> "Carol", "email" -> None, "password" -> None
          // age absent — modifier skipped, default 0 applies
        )
        val patch = Patch.empty[User].modify(_.age, _ + 5)
        for user <- userContract.applyPatch(current, patch)
        yield assertTrue(user.age == 0)
      },

      test("set wins over modify when both target the same field") {
        val current: RawObject = Map(
          "id" -> 1L, "name" -> "Dave", "age" -> 20, "email" -> None, "password" -> None
        )
        val patch = Patch.empty[User].modify(_.age, _ + 1).set(_.age, 99)
        for user <- userContract.applyPatch(current, patch)
        yield assertTrue(user.age == 99)
      },

      test("modify and set can target different fields in the same patch") {
        val current: RawObject = Map(
          "id" -> 1L, "name" -> "Eve", "age" -> 28, "email" -> None, "password" -> None
        )
        val patch = Patch.empty[User].modify(_.age, _ + 2).set(_.name, "Eve Updated")
        for user <- userContract.applyPatch(current, patch)
        yield assertTrue(user.age == 30, user.name == "Eve Updated")
      },

      test("Patch.isEmpty is true only when no fields or modifiers are set") {
        val empty      = Patch.empty[User]
        val withSet    = empty.set(_.age, 5)
        val withModify = empty.modify(_.age, _ + 1)
        assertTrue(empty.isEmpty, !withSet.isEmpty, !withModify.isEmpty)
      },


      test("Patch.size counts unique fields across set and modify") {
        val patch = Patch.empty[User].set(_.name, "X").modify(_.age, _ + 1)
        assertTrue(patch.size == 2)
      },

      test("modify enforces constraint annotations on the transformed value") {
        // @min(0) on age — modifying 0 → -1 should produce a violation
        val current: RawObject = Map(
          "id" -> 1L, "name" -> "Frank", "age" -> 0, "email" -> None, "password" -> None
        )
        val patch = Patch.empty[User].modify(_.age, _ - 1)
        for result <- userContract.applyPatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("age") &&
            v.code == ViolationCode.ConstraintFailed("min")
          )
        )
      }
    ),

    // ── validatePatch — edge cases (S2.4) ────────────────────────────────────
    //
    // Raw-map validatePatch edge cases that are hard to exercise via the typed
    // Patch builder (e.g. passing null as a field value) or that verify correct
    // handling of fields that are entirely absent from both patch and currentRaw.

    suite("validatePatch — edge cases")(

      test("null on a required field in a patch produces TypeMismatch") {
        // Sending null for 'name' is a type failure, not an absence failure.
        val current: RawObject = Map("id" -> 1L)
        val patch: RawObject   = Map("name" -> null)
        for result <- userContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists {
            case Violation(p, _: ViolationCode.TypeMismatch, _) => p == FieldPath("name")
            case _                                               => false
          }
        )
      },

      test("required field absent from both patch and currentRaw produces ExpectedMissing") {
        // 'name' is required; it is in neither the patch nor currentRaw.
        // runPatchValidation must surface ExpectedMissing in this situation.
        val current: RawObject = Map("id" -> 1L)   // no 'name'
        val patch: RawObject   = Map("age" -> 25)  // patch does not include 'name' either
        for result <- userContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("name") &&
            v.code == ViolationCode.ExpectedMissing
          )
        )
      }
    )
  )

  spec
