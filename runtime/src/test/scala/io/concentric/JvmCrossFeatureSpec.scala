package io.concentric

import java.util.{Map => JMap, HashMap => JHashMap, Optional}
import scala.jdk.CollectionConverters.*

/**
 * Cross-feature interaction tests for the JVM runtime layer.
 *
 * Covers feature pairs that are orthogonal in their own unit tests but whose
 * interaction is only exercised here:
 *
 *   Suite 1 — JvmDraft × inherit=ALL aspect
 *             Multi-step form validation using an inherit=ALL aspect contract
 *             (TestJvmUserPublicProfile — Java 16+ record, JVM ≥ 16 only).
 *
 *   Suite 2 — JvmView × inherit=ALL aspect
 *             JvmView transforms (omit/mask/chain) applied to output that was
 *             produced by an inherit=ALL aspect contract, confirming the two
 *             features compose correctly without interfering.
 *
 *   Suite 3 — JvmView × JvmDraft combined workflow
 *             Uses TestKotlinSimUser (plain Java class, JVM 11+).
 *             Finalise a multi-step draft then pipe the result through a
 *             JvmView transform pipeline — the most complete JVM workflow test.
 *
 * Suites 1 and 2 use Java 16+ records; the build.sbt filter excludes this
 * entire file on JVM < 16 (same filter that excludes JvmAspectSpec, etc.).
 */
final class JvmCrossFeatureSpec extends SpecBase:

  // ── shared helpers ───────────────────────────────────────────────────────

  def jmap(pairs: (String, AnyRef)*): JMap[String, AnyRef] =
    val m = new JHashMap[String, AnyRef]()
    pairs.foreach { (k, v) => m.put(k, v) }
    m

  // ── contracts ────────────────────────────────────────────────────────────

  /** inherit=ALL aspect: id/name/email from TestJvmUser, age+password excluded. */
  lazy val publicProfileContract: JvmContract[TestJvmUserPublicProfile] =
    JvmContract.ofAspect(classOf[TestJvmUserPublicProfile], classOf[TestJvmUser])

  /** Plain contract for TestKotlinSimUser (JVM 11+). */
  lazy val simUserContract: JvmContract[TestKotlinSimUser] =
    JvmContract.of(
      classOf[TestKotlinSimUser],
      fields => new TestKotlinSimUser(
        fields.get("id").asInstanceOf[Long],
        fields.get("name").asInstanceOf[String],
        fields.get("age").asInstanceOf[Int],
        fields.get("email").asInstanceOf[String]
      )
    )

  // ── suite 1: JvmDraft × inherit=ALL aspect ───────────────────────────────

  suite("JvmDraft × inherit=ALL aspect") {

    test("validatePartialAsDraft on inherit=ALL aspect accepts a partial input") {
      // Providing only name (id and email absent) must produce a valid draft.
      val result = publicProfileContract.validatePartialAsDraft(jmap("name" -> "Alice"))
      assertTrue(
        result.isValid,
        result.draft.isPresent
      )
    }

    test("validatePartialAsDraft on inherit=ALL aspect catches inherited constraint") {
      // @nonEmpty is inherited on 'name' — an empty string must be rejected.
      val result = publicProfileContract.validatePartialAsDraft(jmap("name" -> ""))
      assertTrue(
        !result.isValid,
        result.errors.asScala.exists(v =>
          v.path == "name" && v.code == "CONSTRAINT(nonEmpty)"
        )
      )
    }

    test("multi-step draft merge+finalize on inherit=ALL aspect produces valid object") {
      // Step 1: name only.  Step 2: id + email.
      // Both must be valid sub-batches before merging.
      val r1 = publicProfileContract.validatePartialAsDraft(jmap("name" -> "Alice"))
      val r2 = publicProfileContract.validatePartialAsDraft(
        jmap("id" -> Long.box(1L), "email" -> "alice@example.com")
      )
      assertTrue(r1.isValid, r2.isValid)
      val finalResult = r1.draft.get().merge(r2.draft.get()).finalize(publicProfileContract)
      assertTrue(finalResult.isValid)
      val profile = finalResult.getValue.orElseThrow()
      assertTrue(
        profile.id()    == 1L,
        profile.name()  == "Alice",
        profile.email() == "alice@example.com"
      )
    }

    test("finalize on inherit=ALL aspect fails when inherited @email constraint is violated") {
      // Each batch is valid in isolation (email constraint cannot fire until
      // finalize runs full validation).  A bad email introduced in step 2 must
      // be caught at finalization.
      val r1 = publicProfileContract.validatePartialAsDraft(jmap("name" -> "Bob"))
      assertTrue(r1.isValid)
      // Pass a syntactically-bad email — validatePartialAsDraft will already
      // catch it before we even merge.
      val r2 = publicProfileContract.validatePartialAsDraft(
        jmap("id" -> Long.box(2L), "email" -> "not-an-email")
      )
      assertTrue(
        !r2.isValid,
        r2.errors.asScala.exists(v =>
          v.path == "email" && v.code == "CONSTRAINT(email)"
        )
      )
    }

    test("excluded field (age) sent to inherit=ALL aspect draft is rejected as unknown") {
      // TestJvmUserPublicProfile excludes 'age' — the contract is closed so
      // 'age' must trigger an unknown-field violation.
      val result = publicProfileContract.validatePartialAsDraft(
        jmap("name" -> "Carol", "age" -> Int.box(25))
      )
      assertTrue(!result.isValid)
    }

    test("source contract validatePatch is unaffected while aspect draft workflow runs") {
      // Create a draft via the aspect, then separately run a validatePatch on the
      // source (TestJvmUser) contract — the two must not interfere.
      val sourceContract: JvmContract[TestJvmUser] = JvmContract.ofRecord(classOf[TestJvmUser])
      val draft = publicProfileContract.validatePartialAsDraft(
        jmap("id" -> Long.box(3L), "name" -> "Dave", "email" -> "dave@example.com")
      )
      assertTrue(draft.isValid)
      val current: JMap[String, AnyRef] = jmap(
        "id"       -> Long.box(3L),
        "name"     -> "Dave",
        "age"      -> Int.box(30),
        "email"    -> "dave@example.com",
        "password" -> "s3cr3t"
      )
      val patchResult = sourceContract.validatePatch(current, jmap("name" -> "David"))
      assertTrue(
        patchResult.isValid,
        patchResult.getValue.orElseThrow().name() == "David"
      )
    }
  }

  // ── suite 2: JvmView × inherit=ALL aspect ────────────────────────────────

  suite("JvmView × inherit=ALL aspect") {

    test("JvmView.omit on inherit=ALL aspect output removes the named field") {
      val raw: JMap[String, AnyRef] = jmap(
        "id"    -> Long.box(1L),
        "name"  -> "Alice",
        "email" -> "alice@example.com"
      )
      // Validate to confirm the data is sound, then apply a View independently.
      assertTrue(publicProfileContract.validate(raw).isValid)
      val view   = JvmView.of(classOf[TestJvmUserPublicProfile]).omit("name")
      val result = view.apply(raw)
      assertTrue(
        !result.containsKey("name"),
        result.containsKey("id"),
        result.containsKey("email")
      )
    }

    test("JvmView.mask on inherit=ALL aspect output masks the named field") {
      val raw: JMap[String, AnyRef] = jmap(
        "id"    -> Long.box(2L),
        "name"  -> "Bob",
        "email" -> "bob@example.com"
      )
      val view   = JvmView.of(classOf[TestJvmUserPublicProfile]).mask("email")
      val result = view.apply(raw)
      assertTrue(
        result.get("email") == "***",
        result.get("name")  == "Bob"
      )
    }

    test("JvmView chain: omit then mask on inherit=ALL aspect output") {
      // omit(name) → mask(email) — name gone, email masked, id survives.
      val raw: JMap[String, AnyRef] = jmap(
        "id"    -> Long.box(3L),
        "name"  -> "Carol",
        "email" -> "carol@example.com"
      )
      val view   = JvmView.of(classOf[TestJvmUserPublicProfile]).omit("name").mask("email")
      val result = view.apply(raw)
      assertTrue(
        !result.containsKey("name"),
        result.get("email") == "***",
        result.get("id")    == Long.box(3L)
      )
    }

    test("validation failure on inherit=ALL aspect does not affect JvmView output") {
      // Validation fails (@email constraint), but JvmView is an independent
      // transformation over the raw map — it must still produce correct output.
      val raw: JMap[String, AnyRef] = jmap(
        "id"    -> Long.box(4L),
        "name"  -> "Dave",
        "email" -> "not-valid"
      )
      val validationResult = publicProfileContract.validate(raw)
      assertTrue(!validationResult.isValid)
      // View still works regardless of validation outcome.
      val view   = JvmView.of(classOf[TestJvmUserPublicProfile]).omit("id")
      val result = view.apply(raw)
      assertTrue(
        !result.containsKey("id"),
        result.get("name")  == "Dave",
        result.get("email") == "not-valid"   // raw value — View doesn't validate
      )
    }

    test("validated output serialised to raw then piped through JvmView round-trips correctly") {
      // Full cycle: validate → toRaw → JvmView transform.
      val raw: JMap[String, AnyRef] = jmap(
        "id"    -> Long.box(5L),
        "name"  -> "Eve",
        "email" -> "eve@example.com"
      )
      val validationResult = publicProfileContract.validate(raw)
      assertTrue(validationResult.isValid)
      val profile = validationResult.getValue.orElseThrow()
      val rawBack = publicProfileContract.toRaw(profile)
      val view    = JvmView.of(classOf[TestJvmUserPublicProfile]).omit("email")
      val result  = view.apply(rawBack)
      assertTrue(
        result.get("id")   == Long.box(5L),
        result.get("name") == "Eve",
        !result.containsKey("email")
      )
    }
  }

  // ── suite 3: JvmView × JvmDraft combined workflow ────────────────────────
  //
  // Uses TestKotlinSimUser (plain Java class, JVM 11+).
  // Simulates the most complete real-world pattern:
  //   1. Build a complete object via multi-step draft validation.
  //   2. Finalise the draft into a typed T.
  //   3. Serialise T back to a raw map.
  //   4. Pipe the raw map through a JvmView transform pipeline.

  suite("JvmView × JvmDraft combined workflow") {

    test("multi-step draft → finalize → JvmView.omit pipeline produces correct output") {
      val r1 = simUserContract.validatePartialAsDraft(
        jmap("id" -> Long.box(10L), "name" -> "Frank")
      )
      val r2 = simUserContract.validatePartialAsDraft(
        jmap("age" -> Int.box(28), "email" -> "frank@example.com")
      )
      assertTrue(r1.isValid, r2.isValid)
      val finalResult = r1.draft.get().merge(r2.draft.get()).finalize(simUserContract)
      assertTrue(finalResult.isValid)
      val user   = finalResult.getValue.orElseThrow()
      val rawOut = simUserContract.toRaw(user)
      val view   = JvmView.of(classOf[TestKotlinSimUser]).omit("age")
      val result = view.apply(rawOut)
      assertTrue(
        result.get("id")    == Long.box(10L),
        result.get("name")  == "Frank",
        result.get("email") == "frank@example.com",
        !result.containsKey("age")
      )
    }

    test("multi-step draft → finalize → JvmView.mask pipeline masks the target field") {
      val r1 = simUserContract.validatePartialAsDraft(
        jmap("id" -> Long.box(11L), "name" -> "Grace", "age" -> Int.box(35))
      )
      val r2 = simUserContract.validatePartialAsDraft(
        jmap("email" -> "grace@example.com")
      )
      assertTrue(r1.isValid, r2.isValid)
      val finalResult = r1.draft.get().merge(r2.draft.get()).finalize(simUserContract)
      assertTrue(finalResult.isValid)
      val user   = finalResult.getValue.orElseThrow()
      val rawOut = simUserContract.toRaw(user)
      val view   = JvmView.of(classOf[TestKotlinSimUser]).mask("email")
      val result = view.apply(rawOut)
      assertTrue(
        result.get("email") == "***",
        result.get("name")  == "Grace"
      )
    }

    test("finalize failure propagates correctly — JvmView receives no output") {
      // Step 2 provides an invalid age — @min(0) is violated.
      // validatePartialAsDraft must report the error immediately; we never reach finalize.
      val r1 = simUserContract.validatePartialAsDraft(
        jmap("id" -> Long.box(12L), "name" -> "Hank")
      )
      assertTrue(r1.isValid)
      val r2 = simUserContract.validatePartialAsDraft(
        jmap("age" -> Int.box(-5), "email" -> "hank@example.com")
      )
      assertTrue(
        !r2.isValid,
        r2.errors.asScala.exists(v =>
          v.path == "age" && v.code == "CONSTRAINT(min)"
        )
      )
      // Draft is absent — no finalize, no View.
      assertTrue(r2.draft.isEmpty)
    }

    test("multi-step draft → finalize → chained JvmView transforms all apply in order") {
      val r1 = simUserContract.validatePartialAsDraft(
        jmap("id" -> Long.box(13L), "name" -> "Ivy", "age" -> Int.box(22))
      )
      val r2 = simUserContract.validatePartialAsDraft(
        jmap("email" -> "ivy@example.com")
      )
      assertTrue(r1.isValid, r2.isValid)
      val finalResult = r1.draft.get().merge(r2.draft.get()).finalize(simUserContract)
      assertTrue(finalResult.isValid)
      val user   = finalResult.getValue.orElseThrow()
      val rawOut = simUserContract.toRaw(user)
      // omit(id) → mask(email) — id gone, email masked, name and age survive
      val view   = JvmView.of(classOf[TestKotlinSimUser]).omit("id").mask("email")
      val result = view.apply(rawOut)
      assertTrue(
        !result.containsKey("id"),
        result.get("email") == "***",
        result.get("name")  == "Ivy",
        result.get("age")   == Int.box(22)
      )
    }
  }
