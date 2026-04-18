package io.concentric

import java.util.{Map => JMap, HashMap => JHashMap, Optional}
import scala.jdk.CollectionConverters.*

/**
 * Test suite for the JVM aspect feature:
 *   - @aspectOf Java annotation
 *   - JvmContractDeriver.deriveAspect (constraint inheritance, policy exclusion)
 *   - JvmContract.ofAspect / ofAspectPrimary
 *   - JvmContract.validatePatch(current, aspect, aspectContract) typed overload
 *
 * Uses only Java records (TestJvmUser, TestJvmUserPatch, TestJvmAdminPatch)
 * so no Kotlin compiler plugin is required for these tests.
 */
final class JvmAspectSpec extends SpecBase:

  // ── Contracts ────────────────────────────────────────────────────────────

  val userContract: JvmContract[TestJvmUser] = JvmContract.of(
    classOf[TestJvmUser],
    fields =>
      new TestJvmUser(
        fields.get("id").asInstanceOf[Long],
        fields.get("name").asInstanceOf[String],
        fields.get("age").asInstanceOf[Int],
        fields.get("email").asInstanceOf[String],
        fields.get("password").asInstanceOf[String]
      )
  )

  val patchContract: JvmContract[TestJvmUserPatch] =
    JvmContract.ofAspect(classOf[TestJvmUserPatch], classOf[TestJvmUser])

  val adminPatchContract: JvmContract[TestJvmAdminPatch] =
    JvmContract.ofAspect(classOf[TestJvmAdminPatch], classOf[TestJvmUser])

  val strictPatchContract: JvmContract[TestJvmUserPatchStrict] =
    JvmContract.ofAspect(classOf[TestJvmUserPatchStrict], classOf[TestJvmUser])

  val openDocPatchContract: JvmContract[TestJvmOpenDocPatch] =
    JvmContract.ofAspect(classOf[TestJvmOpenDocPatch], classOf[TestJvmOpenDoc])

  // ── Helper ────────────────────────────────────────────────────────────────

  def jmap(pairs: (String, AnyRef)*): JMap[String, AnyRef] =
    val m = new JHashMap[String, AnyRef]()
    pairs.foreach { (k, v) => m.put(k, v) }
    m

  def currentRaw: JMap[String, AnyRef] = jmap(
    "id"       -> Long.box(1L),
    "name"     -> "Old Name",
    "age"      -> Int.box(30),
    "email"    -> "old@example.com",
    "password" -> "s3cr3t"
  )

  // ── field inclusion ───────────────────────────────────────────────────────

  test("aspect contract only includes declared fields") {
    val names = patchContract.fieldMetas.map(_.name).toSet
    names shouldBe Set("name", "email")
  }

  test("aspect contract excludes source fields not listed") {
    val names = patchContract.fieldMetas.map(_.name).toSet
    names should not contain "id"
    names should not contain "age"
    names should not contain "password"
  }

  // ── optionality ───────────────────────────────────────────────────────────

  test("aspect fields are optional when declared as Optional") {
    patchContract.fieldMetas.foreach { m =>
      m.isOptional shouldBe true
    }
  }

  test("aspect contract accepts empty patch (all fields absent)") {
    val result = patchContract.validate(jmap())
    assertTrue(result.isValid)
  }

  test("aspect contract accepts partial patch with only email") {
    val result = patchContract.validate(jmap("email" -> "new@example.com"))
    assertTrue(
      result.isValid,
      result.getValue.get.email() == Optional.of("new@example.com"),
      result.getValue.get.name()  == Optional.empty[String]()
    )
  }

  // ── inherited constraints ─────────────────────────────────────────────────

  test("@email constraint is inherited from source — invalid email rejected") {
    val result = patchContract.validate(jmap("email" -> "not-an-email"))
    assertTrue(
      !result.isValid,
      result.getErrors.asScala.exists(v =>
        v.path == "email" && v.code == "CONSTRAINT(email)"
      )
    )
  }

  test("@email constraint is inherited — valid email accepted") {
    val result = patchContract.validate(jmap("email" -> "valid@example.com"))
    assertTrue(result.isValid)
  }

  test("@nonEmpty constraint is inherited from source — empty name rejected") {
    val result = patchContract.validate(jmap("name" -> ""))
    assertTrue(
      !result.isValid,
      result.getErrors.asScala.exists(v =>
        v.path == "name" && v.code == "CONSTRAINT(nonEmpty)"
      )
    )
  }

  test("@maxLength(50) constraint is inherited — oversized name rejected") {
    val longName = "a" * 51
    val result   = patchContract.validate(jmap("name" -> longName))
    assertTrue(
      !result.isValid,
      result.getErrors.asScala.exists(v =>
        v.path == "name" && v.code == "CONSTRAINT(maxLength)"
      )
    )
  }

  // ── policy annotations not inherited ─────────────────────────────────────

  test("@masked is not inherited — password field is simply excluded from aspect") {
    // password is not in the aspect at all — not just unmasked
    val names = patchContract.fieldMetas.map(_.name).toSet
    names should not contain "password"
  }

  test("@immutable is not inherited — id field excluded from aspect") {
    val names = patchContract.fieldMetas.map(_.name).toSet
    names should not contain "id"
  }

  // ── closed aspect ─────────────────────────────────────────────────────────

  test("aspect is always closed — unknown field rejected") {
    val result = patchContract.validate(jmap("name" -> "Alice", "unknown" -> "x"))
    assertTrue(!result.isValid)
  }

  // ── new fields not in source ──────────────────────────────────────────────

  test("admin patch includes role as a new field not in source") {
    val names = adminPatchContract.fieldMetas.map(_.name).toSet
    names shouldBe Set("name", "email", "role")
  }

  test("admin patch accepts role value") {
    val result = adminPatchContract.validate(jmap("role" -> "admin"))
    assertTrue(
      result.isValid,
      result.getValue.get.role() == Optional.of("admin")
    )
  }

  // ── typed validatePatch overload (aspect instance) ───────────────────────

  test("validatePatch(current, aspect, patchContract) updates only present fields") {
    val patchResult = patchContract.validate(jmap("name" -> "New Name"))
    val result      = userContract.validatePatch(currentRaw, patchResult.getValue.get(), patchContract)
    assertTrue(
      result.isValid,
      result.getValue.get.name()  == "New Name",
      result.getValue.get.email() == "old@example.com"  // unchanged
    )
  }

  // ── typed validatePatch overload (ValidationResult passthrough) ───────────

  test("validatePatch(current, patchResult, patchContract) updates only present fields") {
    val patchResult = patchContract.validate(jmap("name" -> "New Name"))
    val result      = userContract.validatePatch(currentRaw, patchResult, patchContract)
    assertTrue(
      result.isValid,
      result.getValue.get.name()  == "New Name",
      result.getValue.get.email() == "old@example.com"  // unchanged
    )
  }

  test("validatePatch propagates patch violations without consulting source contract") {
    val patchResult = patchContract.validate(jmap("email" -> "bad-email"))
    val result      = userContract.validatePatch(currentRaw, patchResult, patchContract)
    assertTrue(
      !result.isValid,
      result.getErrors.asScala.exists(v =>
        v.path == "email" && v.code == "CONSTRAINT(email)"
      )
    )
  }

  test("validatePatch with empty patch leaves current values unchanged") {
    val patchResult = patchContract.validate(jmap())
    val result      = userContract.validatePatch(currentRaw, patchResult, patchContract)
    assertTrue(
      result.isValid,
      result.getValue.get.name()  == "Old Name",
      result.getValue.get.email() == "old@example.com"
    )
  }

  // ── multiple violations ───────────────────────────────────────────────────

  test("all violations are reported when multiple fields are invalid simultaneously") {
    val result = patchContract.validate(jmap(
      "name"  -> "",              // @nonEmpty fails
      "email" -> "not-an-email"   // @email fails
    ))
    val codes = result.getErrors.asScala.map(_.code).toSet
    assertTrue(
      !result.isValid,
      codes.contains("CONSTRAINT(nonEmpty)"),
      codes.contains("CONSTRAINT(email)")
    )
  }

  test("validatePatch propagates multiple patch violations") {
    val patchResult = patchContract.validate(jmap(
      "name"  -> "",
      "email" -> "bad"
    ))
    val result = userContract.validatePatch(currentRaw, patchResult, patchContract)
    assertTrue(
      !result.isValid,
      result.getErrors.size() >= 2
    )
  }

  // ── constraint override (aspect wins) ────────────────────────────────────

  test("aspect @maxLength(20) overrides inherited @maxLength(50) — name of 30 chars rejected") {
    val result = strictPatchContract.validate(jmap("name" -> ("a" * 30)))
    assertTrue(
      !result.isValid,
      result.getErrors.asScala.exists(v =>
        v.path == "name" && v.code == "CONSTRAINT(maxLength)"
      )
    )
  }

  test("aspect @maxLength(20) — name within 20 chars accepted") {
    val result = strictPatchContract.validate(jmap("name" -> ("a" * 20)))
    assertTrue(result.isValid)
  }

  test("inherited constraint still applies alongside aspect constraint") {
    // @nonEmpty inherited; aspect only overrides @maxLength — empty name still fails
    val result = strictPatchContract.validate(jmap("name" -> ""))
    assertTrue(
      !result.isValid,
      result.getErrors.asScala.exists(_.code == "CONSTRAINT(nonEmpty)")
    )
  }

  // ── type mismatch ──────────────────────────────────────────────────────────

  test("type mismatch on Optional patch field produces TYPE_MISMATCH violation") {
    // name is Optional<String> but we pass an Integer
    val result = patchContract.validate(jmap("name" -> Int.box(42)))
    assertTrue(
      !result.isValid,
      result.getErrors.asScala.exists(v =>
        v.path == "name" && v.code.startsWith("TYPE_MISMATCH")
      )
    )
  }

  // ── null for Optional treated as absent ───────────────────────────────────

  test("null for Optional patch field is treated as absent — no violation") {
    val result = patchContract.validate(jmap("name" -> null))
    assertTrue(result.isValid)
  }

  test("null for Optional patch field leaves field absent in result") {
    val result = patchContract.validate(jmap("name" -> null))
    assertTrue(
      result.isValid,
      result.getValue.get.name() == Optional.empty[String]()
    )
  }

  // ── aspect of open-source contract ────────────────────────────────────────

  test("aspect of open contract is closed — unknown field rejected") {
    val result = openDocPatchContract.validate(jmap("key" -> "k", "extra" -> "x"))
    assertTrue(!result.isValid)
  }

  test("aspect of open contract inherits constraints from source") {
    // @nonEmpty on key, inherited from TestJvmOpenDoc
    val result = openDocPatchContract.validate(jmap("key" -> ""))
    assertTrue(
      !result.isValid,
      result.getErrors.asScala.exists(v =>
        v.path == "key" && v.code == "CONSTRAINT(nonEmpty)"
      )
    )
  }

  test("aspect of open contract validates correctly") {
    val result = openDocPatchContract.validate(jmap("key" -> "cfg", "value" -> "true"))
    assertTrue(
      result.isValid,
      result.getValue.get.key()   == Optional.of("cfg"),
      result.getValue.get.value() == Optional.of("true")
    )
  }

  // ── JSON schema reflects aspect shape ─────────────────────────────────────

  test("jsonSchema only contains declared aspect fields") {
    val schema = patchContract.jsonSchema()
    val props  = schema.get("properties").asInstanceOf[JMap[String, AnyRef]]
    props.keySet() shouldBe java.util.Set.of("name", "email")
  }

  test("jsonSchema has no required fields (all optional)") {
    val schema = patchContract.jsonSchema()
    schema.containsKey("required") shouldBe false
  }

  test("jsonSchema carries inherited format constraint (email)") {
    val schema    = patchContract.jsonSchema()
    val props     = schema.get("properties").asInstanceOf[JMap[String, AnyRef]]
    val emailProp = props.get("email").asInstanceOf[JMap[String, AnyRef]]
    emailProp.get("format") shouldBe "email"
  }

  // ── ofAspect construction-time validation ────────────────────────────────

  test("ofAspect throws when @aspectOf annotation is absent") {
    an[IllegalArgumentException] should be thrownBy
      JvmContract.ofAspect(classOf[TestJvmUser], classOf[TestJvmUser])
  }

  test("ofAspect throws when @aspectOf points to a different class") {
    // TestJvmUserPatch is @aspectOf(TestJvmUser) — passing TestJvmTicket as source should fail
    an[IllegalArgumentException] should be thrownBy
      JvmContract.ofAspect(classOf[TestJvmUserPatch], classOf[TestJvmTicket])
  }

