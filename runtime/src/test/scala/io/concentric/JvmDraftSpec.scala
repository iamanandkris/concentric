package io.concentric

import scala.jdk.CollectionConverters.*

/**
 * Tests for [[JvmDraft]], [[JvmDraftResult]], and
 * [[JvmContract.validatePartialAsDraft]].
 *
 * Uses [[TestKotlinSimUser]] (plain Java class, Java-11-compatible).
 * Fields: id @immutable Long, name @nonEmpty String,
 *         age @min(0) @max(150) Int, email @email String.
 * All four fields are required.
 */
final class JvmDraftSpec extends SpecBase:

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

  // ── validatePartialAsDraft — result shape ─────────────────────────────────

  suite("validatePartialAsDraft — result shape")(

    test("valid partial map → isValid=true, draft is present") {
      val raw    = jmap("name" -> "Alice", "age" -> Int.box(30))
      val result = userContract.validatePartialAsDraft(raw)
      assertTrue(
        result.isValid,
        result.errors.isEmpty,
        result.draft.isPresent
      )
    },

    test("invalid partial map → isValid=false, errors non-empty, draft absent") {
      val raw    = jmap("name" -> "", "age" -> Int.box(-1))  // @nonEmpty + @min(0) fail
      val result = userContract.validatePartialAsDraft(raw)
      assertTrue(
        !result.isValid,
        !result.errors.isEmpty,
        result.draft.isEmpty
      )
    },

    test("empty partial map → isValid=true, draft is present and isEmpty") {
      val result = userContract.validatePartialAsDraft(jmap())
      assertTrue(
        result.isValid,
        result.draft.isPresent,
        result.draft.get().isEmpty
      )
    },

    test("getErrors and errors are the same object (Kotlin alias)") {
      val raw    = jmap("name" -> "")
      val result = userContract.validatePartialAsDraft(raw)
      assertTrue(result.getErrors eq result.errors)
    },

    test("getDraft and draft are equal (Kotlin alias)") {
      val raw    = jmap("name" -> "Alice")
      val result = userContract.validatePartialAsDraft(raw)
      assertTrue(result.getDraft == result.draft)
    },

    test("@immutable field in partial map is accepted — only rejected during patch") {
      val raw    = jmap("id" -> Long.box(42L))
      val result = userContract.validatePartialAsDraft(raw)
      assertTrue(result.isValid)
    },

    test("type mismatch on a present field → invalid result with path in errors") {
      val raw    = jmap("age" -> "not-a-number")
      val result = userContract.validatePartialAsDraft(raw)
      assertTrue(
        !result.isValid,
        result.errors.asScala.exists(_.path == "age")
      )
    },

    test("constraint violation on present field is reported") {
      val raw    = jmap("age" -> Int.box(200))  // @max(150)
      val result = userContract.validatePartialAsDraft(raw)
      assertTrue(
        !result.isValid,
        result.errors.asScala.exists(v => v.path == "age" && v.code.startsWith("CONSTRAINT"))
      )
    }
  )

  // ── JvmDraft.empty ────────────────────────────────────────────────────────

  suite("JvmDraft.empty")(

    test("empty draft has isEmpty=true and fieldCount=0") {
      val d = JvmDraft.empty[TestKotlinSimUser]
      assertTrue(d.isEmpty, d.fieldCount == 0)
    }
  )

  // ── JvmDraft.merge ────────────────────────────────────────────────────────

  suite("JvmDraft.merge")(

    test("merge accumulates fields from two separate drafts") {
      val r1 = userContract.validatePartialAsDraft(jmap("name" -> "Alice", "age" -> Int.box(30)))
      val r2 = userContract.validatePartialAsDraft(jmap("id" -> Long.box(1L), "email" -> "alice@example.com"))
      val merged = r1.draft.get().merge(r2.draft.get())
      assertTrue(merged.fieldCount == 4)
    },

    test("merge is right-biased: later draft's value wins on overlapping field") {
      val r1 = userContract.validatePartialAsDraft(jmap("name" -> "Alice"))
      val r2 = userContract.validatePartialAsDraft(jmap("name" -> "Bob"))
      val merged = r1.draft.get().merge(r2.draft.get())
      // only one field, Bob wins — finalize will use Bob
      assertTrue(merged.fieldCount == 1)
    },

    test("merge with JvmDraft.empty is identity") {
      val r1    = userContract.validatePartialAsDraft(jmap("name" -> "Alice", "age" -> Int.box(30)))
      val empty = JvmDraft.empty[TestKotlinSimUser]
      val merged = r1.draft.get().merge(empty)
      assertTrue(merged.fieldCount == r1.draft.get().fieldCount)
    },

    test("merge empty with non-empty returns the non-empty draft's fields") {
      val r1    = userContract.validatePartialAsDraft(jmap("name" -> "Alice"))
      val empty = JvmDraft.empty[TestKotlinSimUser]
      val merged = empty.merge(r1.draft.get())
      assertTrue(merged.fieldCount == 1)
    }
  )

  // ── JvmDraft.finalize ─────────────────────────────────────────────────────

  suite("JvmDraft.finalize")(

    test("finalize with all required fields succeeds and constructs T") {
      val r1 = userContract.validatePartialAsDraft(jmap("id" -> Long.box(1L), "name" -> "Alice"))
      val r2 = userContract.validatePartialAsDraft(jmap("age" -> Int.box(30), "email" -> "alice@example.com"))
      val result = r1.draft.get().merge(r2.draft.get()).finalize(userContract)
      assertTrue(
        result.isValid,
        result.value.isPresent,
        result.value.get().name()  == "Alice",
        result.value.get().email() == "alice@example.com"
      )
    },

    test("finalize with missing required fields fails with MISSING violation") {
      // Only name provided — id, age, email still required
      val r1     = userContract.validatePartialAsDraft(jmap("name" -> "Alice"))
      val result = r1.draft.get().finalize(userContract)
      assertTrue(
        !result.isValid,
        result.errors.asScala.exists(_.code == "MISSING")
      )
    },

    test("finalize an empty draft fails with multiple MISSING violations") {
      val result = JvmDraft.empty[TestKotlinSimUser].finalize(userContract)
      assertTrue(
        !result.isValid,
        result.errors.size > 1
      )
    }
  )

  // ── Multi-step form workflow (integration) ────────────────────────────────

  suite("multi-step form workflow")(

    test("two-step form accumulates fields and finalises correctly") {
      val step1 = userContract.validatePartialAsDraft(jmap("id" -> Long.box(7L), "name" -> "Carol"))
      val step2 = userContract.validatePartialAsDraft(jmap("age" -> Int.box(28), "email" -> "carol@example.com"))
      assertTrue(step1.isValid, step2.isValid)
      val result = step1.draft.get().merge(step2.draft.get()).finalize(userContract)
      assertTrue(
        result.isValid,
        result.value.get().name()  == "Carol",
        result.value.get().age()   == 28,
        result.value.get().email() == "carol@example.com"
      )
    },

    test("invalid step keeps its draft absent — cannot be merged into workflow") {
      val good = userContract.validatePartialAsDraft(jmap("name" -> "Dave", "age" -> Int.box(40)))
      val bad  = userContract.validatePartialAsDraft(jmap("name" -> ""))   // @nonEmpty fails
      assertTrue(good.isValid, !bad.isValid, bad.draft.isEmpty)
    },

    test("three-step accumulation with right-biased field update") {
      val step1 = userContract.validatePartialAsDraft(jmap("id" -> Long.box(9L), "name" -> "Eve"))
      val step2 = userContract.validatePartialAsDraft(jmap("age" -> Int.box(22)))
      val step3 = userContract.validatePartialAsDraft(jmap("email" -> "eve@example.com"))
      val result = step1.draft.get()
        .merge(step2.draft.get())
        .merge(step3.draft.get())
        .finalize(userContract)
      assertTrue(result.isValid, result.value.get().name() == "Eve")
    }
  )
