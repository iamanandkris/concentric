package io.concentric

import java.util.{Map => JMap, HashMap => JHashMap, Optional}
import scala.jdk.CollectionConverters.*

/**
 * Tests for the `inherit = ALL` + `exclude` feature of @aspectOf.
 *
 * Uses Java 16+ record fixtures (TestJvmUser* family) — excluded on JVM < 16
 * by the same build.sbt filter that excludes JvmAspectSpec.
 *
 * Scenarios:
 *   1. inherit = ALL basic exhaustiveness — public profile (id/name/email; age+password excluded)
 *   2. inherit = ALL with extra field not in source
 *   3. inherit = ALL constraint inheritance and override behaviour
 *   4. Error cases detected at ofAspect creation time
 */
final class JvmAspectInheritSpec extends SpecBase:

  // ── helpers ──────────────────────────────────────────────────────────────

  def jmap(pairs: (String, AnyRef)*): JMap[String, AnyRef] =
    val m = new JHashMap[String, AnyRef]()
    pairs.foreach { (k, v) => m.put(k, v) }
    m

  // ── suite 1: inherit = ALL basic exhaustiveness ──────────────────────────

  suite("inherit = ALL — basic exhaustiveness") {

    lazy val publicProfileContract: JvmContract[TestJvmUserPublicProfile] =
      JvmContract.ofAspect(classOf[TestJvmUserPublicProfile], classOf[TestJvmUser])

    test("inherit = ALL aspect contract is created without error") {
      // creation must not throw
      val c = publicProfileContract
      assertTrue(c != null)
    }

    test("inherit = ALL aspect includes exactly the declared fields (id, name, email)") {
      val names = publicProfileContract.fieldMetas.map(_.name).toSet
      names shouldBe Set("id", "name", "email")
    }

    test("inherit = ALL aspect excludes age (listed in exclude)") {
      val names = publicProfileContract.fieldMetas.map(_.name).toSet
      names should not contain "age"
    }

    test("inherit = ALL aspect excludes password (listed in exclude)") {
      val names = publicProfileContract.fieldMetas.map(_.name).toSet
      names should not contain "password"
    }

    test("valid map with id/name/email passes validation") {
      val result = publicProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "name"  -> "Alice",
        "email" -> "alice@example.com"
      ))
      assertTrue(result.isValid)
    }

    test("aspect is closed — passing age as input is rejected (unknown field)") {
      val result = publicProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "name"  -> "Alice",
        "email" -> "alice@example.com",
        "age"   -> Int.box(30)
      ))
      assertTrue(!result.isValid)
    }

    test("@nonEmpty inherited on name — empty name rejected") {
      val result = publicProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "name"  -> "",
        "email" -> "alice@example.com"
      ))
      assertTrue(
        !result.isValid,
        result.getErrors.asScala.exists(v =>
          v.path == "name" && v.code == "CONSTRAINT(nonEmpty)"
        )
      )
    }

    test("@email inherited on email — invalid email rejected") {
      val result = publicProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "name"  -> "Alice",
        "email" -> "not-an-email"
      ))
      assertTrue(
        !result.isValid,
        result.getErrors.asScala.exists(v =>
          v.path == "email" && v.code == "CONSTRAINT(email)"
        )
      )
    }

    test("@maxLength(50) inherited on name — oversized name rejected") {
      val result = publicProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "name"  -> ("a" * 51),
        "email" -> "alice@example.com"
      ))
      assertTrue(
        !result.isValid,
        result.getErrors.asScala.exists(v =>
          v.path == "name" && v.code == "CONSTRAINT(maxLength)"
        )
      )
    }
  }

  // ── suite 2: inherit = ALL with extra field ────────────────────────────

  suite("inherit = ALL — extra fields not in source") {

    lazy val profileWithExtraContract: JvmContract[TestJvmUserProfileWithExtra] =
      JvmContract.ofAspect(classOf[TestJvmUserProfileWithExtra], classOf[TestJvmUser])

    test("inherit = ALL with extra field creates contract successfully") {
      val c = profileWithExtraContract
      assertTrue(c != null)
    }

    test("extra field (displayName) is included in field list") {
      val names = profileWithExtraContract.fieldMetas.map(_.name).toSet
      names shouldBe Set("name", "email", "displayName")
    }

    test("extra field @nonEmpty constraint is enforced") {
      val result = profileWithExtraContract.validate(jmap(
        "name"        -> "Alice",
        "email"       -> "alice@example.com",
        "displayName" -> ""
      ))
      assertTrue(
        !result.isValid,
        result.getErrors.asScala.exists(v =>
          v.path == "displayName" && v.code == "CONSTRAINT(nonEmpty)"
        )
      )
    }

    test("extra field accepts valid value") {
      val result = profileWithExtraContract.validate(jmap(
        "name"        -> "Alice",
        "email"       -> "alice@example.com",
        "displayName" -> "Alice A."
      ))
      assertTrue(result.isValid)
    }

    test("inherited constraint on name still applies alongside extra field") {
      val result = profileWithExtraContract.validate(jmap(
        "name"        -> "",
        "email"       -> "alice@example.com",
        "displayName" -> "Alice A."
      ))
      assertTrue(
        !result.isValid,
        result.getErrors.asScala.exists(v =>
          v.path == "name" && v.code == "CONSTRAINT(nonEmpty)"
        )
      )
    }
  }

  // ── suite 3a: optional fields (gap #7) ───────────────────────────────────

  suite("inherit = ALL — Optional fields") {

    lazy val optionalProfileContract: JvmContract[TestJvmUserOptionalProfile] =
      JvmContract.ofAspect(classOf[TestJvmUserOptionalProfile], classOf[TestJvmUser])

    test("contract with Optional fields is created without error") {
      assertTrue(optionalProfileContract != null)
    }

    test("field list contains id, name, age, email (password excluded)") {
      val names = optionalProfileContract.fieldMetas.map(_.name).toSet
      names shouldBe Set("id", "name", "age", "email")
    }

    test("absent optional field is accepted (name absent → Optional.empty)") {
      val result = optionalProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "email" -> "alice@example.com",
        "age"   -> Int.box(25)
        // name absent
      ))
      assertTrue(result.isValid)
      assertTrue(result.getValue.orElseThrow().name().isEmpty)
    }

    test("@nonEmpty inherited on name — empty string rejected even through Optional") {
      val result = optionalProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "name"  -> "",
        "email" -> "alice@example.com",
        "age"   -> Int.box(25)
      ))
      assertTrue(
        !result.isValid,
        result.getErrors.asScala.exists(v =>
          v.path == "name" && v.code == "CONSTRAINT(nonEmpty)"
        )
      )
    }

    test("@email inherited on email — invalid email rejected through Optional") {
      val result = optionalProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "name"  -> "Alice",
        "email" -> "bad-email",
        "age"   -> Int.box(25)
      ))
      assertTrue(
        !result.isValid,
        result.getErrors.asScala.exists(v =>
          v.path == "email" && v.code == "CONSTRAINT(email)"
        )
      )
    }

    test("@min(0) @max(150) inherited on age — value -1 rejected through Optional") {
      val result = optionalProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "name"  -> "Alice",
        "email" -> "alice@example.com",
        "age"   -> Int.box(-1)
      ))
      assertTrue(
        !result.isValid,
        result.getErrors.asScala.exists(v =>
          v.path == "age" && v.code == "CONSTRAINT(min)"
        )
      )
    }

    test("valid complete input passes") {
      val result = optionalProfileContract.validate(jmap(
        "id"    -> Long.box(1L),
        "name"  -> "Alice",
        "email" -> "alice@example.com",
        "age"   -> Int.box(30)
      ))
      assertTrue(result.isValid)
    }
  }

  // ── suite 3b: open-source base + no exclusions (gaps #8 and #10) ─────────

  suite("inherit = ALL — open-source contract base") {

    lazy val openDocFullContract: JvmContract[TestJvmOpenDocFull] =
      JvmContract.ofAspect(classOf[TestJvmOpenDocFull], classOf[TestJvmOpenDoc])

    test("inherit = ALL on open-source base creates contract without error") {
      assertTrue(openDocFullContract != null)
    }

    test("field list contains exactly key and value (no exclusions)") {
      val names = openDocFullContract.fieldMetas.map(_.name).toSet
      names shouldBe Set("key", "value")
    }

    test("aspect is CLOSED even though source is open — unknown field rejected") {
      val result = openDocFullContract.validate(jmap(
        "key"     -> "k1",
        "value"   -> "v1",
        "unknown" -> "x"
      ))
      assertTrue(!result.isValid)
    }

    test("@nonEmpty inherited from open-source field — empty key rejected") {
      val result = openDocFullContract.validate(jmap(
        "key"   -> "",
        "value" -> "v1"
      ))
      assertTrue(
        !result.isValid,
        result.getErrors.asScala.exists(v =>
          v.path == "key" && v.code == "CONSTRAINT(nonEmpty)"
        )
      )
    }

    test("valid input passes") {
      val result = openDocFullContract.validate(jmap(
        "key"   -> "myKey",
        "value" -> "myValue"
      ))
      assertTrue(result.isValid)
    }
  }

  // ── suite 3c: multiple missing fields in error message (gap #9) ──────────

  suite("inherit = ALL — multiple unaccounted fields in error") {

    test("two missing fields cause IllegalArgumentException") {
      // TestJvmUserBadMissingTwo declares id/name/email, no excludes,
      // leaving age and password both unaccounted.
      an[IllegalArgumentException] should be thrownBy
        JvmContract.ofAspect(classOf[TestJvmUserBadMissingTwo], classOf[TestJvmUser])
    }

    test("error message names all unaccounted fields") {
      val ex = intercept[IllegalArgumentException] {
        JvmContract.ofAspect(classOf[TestJvmUserBadMissingTwo], classOf[TestJvmUser])
      }
      // Both age and password must appear in the message
      assertTrue(ex.getMessage.contains("age"))
      assertTrue(ex.getMessage.contains("password"))
    }

    test("single missing field still names that field (regression)") {
      val ex = intercept[IllegalArgumentException] {
        JvmContract.ofAspect(classOf[TestJvmUserBadMissingField], classOf[TestJvmUser])
      }
      assertTrue(ex.getMessage.contains("password"))
    }
  }

  // ── suite 4: error cases at ofAspect creation time ────────────────────

  suite("inherit = ALL — error cases at contract creation") {

    test("source field neither declared nor excluded causes IllegalArgumentException") {
      // TestJvmUserBadMissingField declares id/name/email, excludes age,
      // but omits password — should fail.
      an[IllegalArgumentException] should be thrownBy
        JvmContract.ofAspect(classOf[TestJvmUserBadMissingField], classOf[TestJvmUser])
    }

    test("error message for missing field names the unaccounted field") {
      val ex = intercept[IllegalArgumentException] {
        JvmContract.ofAspect(classOf[TestJvmUserBadMissingField], classOf[TestJvmUser])
      }
      assertTrue(ex.getMessage.contains("password"))
    }

    test("exclude name not in source causes IllegalArgumentException") {
      // TestJvmUserBadExcludeNonExistent has exclude = {"age","password","nonExistentField"}
      an[IllegalArgumentException] should be thrownBy
        JvmContract.ofAspect(classOf[TestJvmUserBadExcludeNonExistent], classOf[TestJvmUser])
    }

    test("error message for bad exclude name mentions the unknown name") {
      val ex = intercept[IllegalArgumentException] {
        JvmContract.ofAspect(classOf[TestJvmUserBadExcludeNonExistent], classOf[TestJvmUser])
      }
      assertTrue(ex.getMessage.contains("nonExistentField"))
    }

    test("all source fields excluded with no extras causes IllegalArgumentException") {
      // TestJvmUserBadAllExcluded has exclude = all 5 source fields, no constructor params
      an[IllegalArgumentException] should be thrownBy
        JvmContract.ofAspect(classOf[TestJvmUserBadAllExcluded], classOf[TestJvmUser])
    }

    test("exclude without inherit = ALL causes IllegalArgumentException") {
      // TestJvmUserBadExcludeWithoutInheritAll uses default EXPLICIT mode but has exclude
      an[IllegalArgumentException] should be thrownBy
        JvmContract.ofAspect(classOf[TestJvmUserBadExcludeWithoutInheritAll], classOf[TestJvmUser])
    }

    test("error message for exclude without inherit = ALL mentions inherit = ALL") {
      val ex = intercept[IllegalArgumentException] {
        JvmContract.ofAspect(classOf[TestJvmUserBadExcludeWithoutInheritAll], classOf[TestJvmUser])
      }
      assertTrue(ex.getMessage.toLowerCase.contains("inherit"))
    }
  }
