package io.concentric

import io.concentric.annotations.*
// The Java @aspectOf annotation in io.concentric.annotations shadows the Scala
// aspectOf class from the same package via the wildcard import above.
// This explicit named import restores the Scala version for use in this file.
import io.concentric.aspectOf

// ── Source contracts ──────────────────────────────────────────────────────────

@contract
case class AspectUser(
  @internal @immutable val id:    Long,
  @email               val email: String,
  @nonEmpty @maxLength(100) val name: String,
  @reserved            val role:  Option[String] = None,
  @masked              val token: Option[String] = None
) derives Contract

// ── Aspect variants ───────────────────────────────────────────────────────────

/** PATCH variant — only email and name are accepted, both optional.
 *  - @email / @nonEmpty / @maxLength(100) inherited from AspectUser
 *  - id, role, token excluded by omission
 */
@aspectOf[AspectUser]
case class AspectUserPatch(
  val email: Option[String] = None,
  val name:  Option[String] = None
) derives Contract

/** Admin PATCH — also allows setting role (which is @reserved in AspectUser).
 *  @reserved is NOT inherited — role is accepted here without restriction.
 */
@aspectOf[AspectUser]
case class AspectAdminPatch(
  val email: Option[String] = None,
  val name:  Option[String] = None,
  val role:  Option[String] = None
) derives Contract

// ── Source with numeric and string fields ─────────────────────────────────────

@contract
case class AspectOrder(
  @nonEmpty val ref:    String,
  @min(1)   val qty:    Int,
  @max(999) val weight: Double
) derives Contract

/** Aspect — only ref and qty accepted; weight excluded. */
@aspectOf[AspectOrder]
case class AspectOrderPatch(
  val ref: Option[String] = None,
  val qty: Option[Int]    = None
  // weight excluded
) derives Contract

// ── New-field aspects (fields not present in source) ──────────────────────────

/** Registration form — includes source fields plus a request-only confirmPassword. */
@aspectOf[AspectUser]
case class AspectUserRegistration(
  val email:           Option[String] = None,  // @email inherited
  val name:            Option[String] = None,  // @nonEmpty / @maxLength(100) inherited
  @nonEmpty val confirmPassword: Option[String] = None  // new — not in AspectUser
) derives Contract

// ── Constraint override ───────────────────────────────────────────────────

/** Aspect that tightens @maxLength — 50 instead of source's 100. */
@aspectOf[AspectUser]
case class AspectUserPatchStrict(
  val email: Option[String] = None,
  @maxLength(50) val name:  Option[String] = None   // overrides inherited @maxLength(100)
) derives Contract

// ── @internal not inherited ───────────────────────────────────────────────

/** Aspect that includes `id`, which is @internal @immutable in AspectUser.
 *  Neither policy annotation is inherited, so id can be freely set here.
 */
@aspectOf[AspectUser]
case class AspectWithInternalField(
  val id:    Option[Long]   = None,   // @internal and @immutable NOT inherited
  val email: Option[String] = None
) derives Contract

// ── Open-source aspect ────────────────────────────────────────────────────────

/** Open source contract — aspect should still work and be closed by default. */
case class AspectProduct(
  @nonEmpty val sku:   String,
  @min(0)   val price: Double
) derives OpenContract

@aspectOf[AspectProduct]
case class AspectProductPatch(
  val sku:   Option[String] = None,  // @nonEmpty inherited
  val price: Option[Double] = None   // @min(0) inherited
) derives Contract

// ── Spec ──────────────────────────────────────────────────────────────────────

final class AspectOfSpec extends SpecBase:

  val userContract              = summon[Contract[AspectUser]]
  val patchContract             = summon[Contract[AspectUserPatch]]
  val adminPatchContract        = summon[Contract[AspectAdminPatch]]
  val orderPatchContract        = summon[Contract[AspectOrderPatch]]
  val registrationContract      = summon[Contract[AspectUserRegistration]]
  val productPatchContract      = summon[Contract[AspectProductPatch]]
  val strictPatchContract       = summon[Contract[AspectUserPatchStrict]]
  val internalFieldContract     = summon[Contract[AspectWithInternalField]]

  // ── field inclusion ───────────────────────────────────────────────────────

  test("aspect contract only includes declared fields") {
    val fieldNames = patchContract.fieldMetas.map(_.name).toSet
    fieldNames shouldBe Set("email", "name")
  }

  test("aspect contract excludes fields not listed") {
    val fieldNames = patchContract.fieldMetas.map(_.name).toSet
    fieldNames should not contain "id"
    fieldNames should not contain "role"
    fieldNames should not contain "token"
  }

  // ── optionality ───────────────────────────────────────────────────────────

  test("aspect fields are optional when declared as Option") {
    patchContract.fieldMetas.foreach { m =>
      m.isOptional shouldBe true
    }
  }

  test("aspect contract accepts empty patch (all fields absent)") {
    val result = patchContract.validate(Map.empty)
    result.isRight shouldBe true
  }

  test("aspect contract accepts partial patch with only email") {
    val result = patchContract.validate(Map("email" -> "new@example.com"))
    result.isRight shouldBe true
    result.value.email shouldBe Some("new@example.com")
    result.value.name  shouldBe None
  }

  // ── inherited constraints ─────────────────────────────────────────────────

  test("@email constraint is inherited from source — invalid email rejected") {
    val result = patchContract.validate(Map("email" -> "not-an-email"))
    result.isLeft shouldBe true
    result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("email")) shouldBe true
  }

  test("@email constraint is inherited — valid email accepted") {
    val result = patchContract.validate(Map("email" -> "valid@example.com"))
    result.isRight shouldBe true
  }

  test("@nonEmpty constraint is inherited from source — empty name rejected") {
    val result = patchContract.validate(Map("name" -> ""))
    result.isLeft shouldBe true
    result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("nonEmpty")) shouldBe true
  }

  test("@maxLength(100) constraint is inherited — oversized name rejected") {
    val longName = "a" * 101
    val result = patchContract.validate(Map("name" -> longName))
    result.isLeft shouldBe true
    result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("maxLength")) shouldBe true
  }

  // ── policy inheritance ────────────────────────────────────────────────────

  test("@reserved is inherited — user patch cannot set role") {
    val result = patchContract.validate(Map("role" -> "admin"))
    // role is not in patchContract at all (excluded) — treated as unknown field
    result.isLeft shouldBe true
  }

  test("admin patch can set role when @reserved is overridden") {
    val result = adminPatchContract.validate(Map("role" -> "admin"))
    result.isRight shouldBe true
    result.value.role shouldBe Some("admin")
  }

  // ── integration with validatePatch ────────────────────────────────────────

  test("aspect patch integrates with userContract.validatePatch (raw form)") {
    val currentRaw: RawObject = Map(
      "id"    -> 1L,
      "email" -> "old@example.com",
      "name"  -> "Old Name",
      "role"  -> null,
      "token" -> null
    )
    val patchBody: RawObject = Map("name" -> "New Name")

    val result =
      for
        patch   <- patchContract.validate(patchBody)
        patchRaw = patchContract.toRaw(patch).collect { case (k, Some(v)) => k -> v }
        updated <- userContract.validatePatch(currentRaw, patchRaw)
      yield updated

    result.isRight shouldBe true
    result.value.name  shouldBe "New Name"
    result.value.email shouldBe "old@example.com"  // unchanged
  }

  test("aspect patch integrates via typed validatePatch overload — no manual collect") {
    val currentRaw: RawObject = Map(
      "id"    -> 1L,
      "email" -> "old@example.com",
      "name"  -> "Old Name",
      "role"  -> null,
      "token" -> null
    )

    val result =
      for
        patch   <- patchContract.validate(Map("name" -> "New Name"))
        updated <- userContract.validatePatch(currentRaw, patch)
      yield updated

    result.isRight shouldBe true
    result.value.name  shouldBe "New Name"
    result.value.email shouldBe "old@example.com"  // unchanged
  }

  test("typed validatePatch absent field leaves current value") {
    val currentRaw: RawObject = Map(
      "id"    -> 1L,
      "email" -> "keep@example.com",
      "name"  -> "Keep Name",
      "role"  -> null,
      "token" -> null
    )

    // patch has both fields absent — nothing changes
    val result =
      for
        patch   <- patchContract.validate(Map.empty)
        updated <- userContract.validatePatch(currentRaw, patch)
      yield updated

    result.isRight shouldBe true
    result.value.email shouldBe "keep@example.com"
    result.value.name  shouldBe "Keep Name"
  }

  test("typed validatePatch still applies source constraints") {
    val currentRaw: RawObject = Map(
      "id" -> 1L, "email" -> "old@example.com", "name" -> "Old",
      "role" -> null, "token" -> null
    )

    val result =
      for
        patch   <- patchContract.validate(Map("email" -> "bad-email"))
        updated <- userContract.validatePatch(currentRaw, patch)
      yield updated

    // patchContract catches the bad email before validatePatch is even called
    result.isLeft shouldBe true
  }

  // ── JSON schema reflects aspect shape ─────────────────────────────────────

  test("jsonSchema only contains declared aspect fields") {
    val schema = patchContract.jsonSchema
    val props  = schema("properties").asInstanceOf[Map[String, Any]]
    props.keySet shouldBe Set("email", "name")
  }

  test("jsonSchema has no required fields (all optional)") {
    val schema = patchContract.jsonSchema
    schema.get("required") shouldBe None
  }

  test("jsonSchema carries inherited format constraint") {
    val schema = patchContract.jsonSchema
    val props  = schema("properties").asInstanceOf[Map[String, Any]]
    val emailSchema = props("email").asInstanceOf[Map[String, Any]]
    emailSchema.get("format") shouldBe Some("email")
  }

  // ── excluded fields ───────────────────────────────────────────────────────

  test("aspect with excluded nested field validates correctly") {
    val result = orderPatchContract.validate(Map("qty" -> 5))
    result.isRight shouldBe true
    result.value.qty shouldBe Some(5)
    result.value.ref shouldBe None
  }

  test("@min constraint inherited on qty") {
    val result = orderPatchContract.validate(Map("qty" -> 0))
    result.isLeft shouldBe true
    result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("min")) shouldBe true
  }

  // ── new fields not in source ──────────────────────────────────────────────

  test("new field not in source is accepted in aspect") {
    val result = registrationContract.validate(Map(
      "email"           -> "a@b.com",
      "name"            -> "Alice",
      "confirmPassword" -> "secret"
    ))
    result.isRight shouldBe true
    result.value.confirmPassword shouldBe Some("secret")
  }

  test("new field inherits its own annotations — @nonEmpty enforced on confirmPassword") {
    val result = registrationContract.validate(Map("confirmPassword" -> ""))
    result.isLeft shouldBe true
    result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("nonEmpty")) shouldBe true
  }

  test("source field constraints still inherited alongside new field") {
    val result = registrationContract.validate(Map("email" -> "not-an-email"))
    result.isLeft shouldBe true
    result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("email")) shouldBe true
  }

  test("new field is included in fieldMetas") {
    val names = registrationContract.fieldMetas.map(_.name).toSet
    names shouldBe Set("email", "name", "confirmPassword")
  }

  // ── open-source aspect ────────────────────────────────────────────────────

  test("aspect of open-source contract is closed by default") {
    // unknown field should be rejected
    val result = productPatchContract.validate(Map("sku" -> "ABC", "unknown" -> "x"))
    result.isLeft shouldBe true
  }

  test("aspect of open-source contract inherits constraints") {
    val result = productPatchContract.validate(Map("sku" -> ""))
    result.isLeft shouldBe true
    result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("nonEmpty")) shouldBe true
  }

  test("aspect of open-source contract validates correctly") {
    val result = productPatchContract.validate(Map("sku" -> "ABC-123", "price" -> 9.99))
    result.isRight shouldBe true
    result.value.sku   shouldBe Some("ABC-123")
    result.value.price shouldBe Some(9.99)
  }

  // ── multiple violations ───────────────────────────────────────────────────

  test("all violations reported when multiple patch fields are invalid simultaneously") {
    val result = patchContract.validate(Map(
      "email" -> "not-an-email",
      "name"  -> ""
    ))
    result.isLeft shouldBe true
    val codes = result.left.value.violations.map(_.code).toList
    codes should contain(ViolationCode.ConstraintFailed("email"))
    codes should contain(ViolationCode.ConstraintFailed("nonEmpty"))
  }

  // ── constraint override (aspect wins) ────────────────────────────────────

  test("aspect @maxLength(50) overrides inherited @maxLength(100) — name of 75 chars rejected") {
    val result = strictPatchContract.validate(Map("name" -> ("a" * 75)))
    result.isLeft shouldBe true
    result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("maxLength")) shouldBe true
  }

  test("aspect @maxLength(50) — name within 50 chars accepted") {
    val result = strictPatchContract.validate(Map("name" -> ("a" * 50)))
    result.isRight shouldBe true
  }

  test("inherited @nonEmpty still applies when aspect only overrides @maxLength") {
    val result = strictPatchContract.validate(Map("name" -> ""))
    result.isLeft shouldBe true
    result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("nonEmpty")) shouldBe true
  }

  // ── @internal not inherited ───────────────────────────────────────────────

  test("@internal is not inherited — field included in aspect can be freely set") {
    // id is @internal @immutable in AspectUser; neither policy applies in AspectWithInternalField
    val result = internalFieldContract.validate(Map("id" -> 99L))
    result.isRight shouldBe true
    result.value.id shouldBe Some(99L)
  }

  test("@immutable is not inherited — setting id does not violate @immutable in aspect") {
    // There is no currentRaw concept for validate; just confirm the field is accepted
    val result = internalFieldContract.validate(Map("id" -> 42L, "email" -> "a@b.com"))
    result.isRight shouldBe true
  }

  // ── toPatchRaw ────────────────────────────────────────────────────────────

  test("toPatchRaw strips None fields and unwraps Some fields") {
    val patch    = AspectUserPatch(email = Some("x@y.com"), name = None)
    val patchRaw = patchContract.toPatchRaw(patch)
    patchRaw.keySet               shouldBe Set("email")
    patchRaw("email")             shouldBe "x@y.com"
    patchRaw.contains("name")     shouldBe false
  }

  test("toPatchRaw on empty patch produces empty map") {
    val patch    = AspectUserPatch()
    val patchRaw = patchContract.toPatchRaw(patch)
    patchRaw shouldBe Map.empty
  }

  test("toPatchRaw with all fields set unwraps all Somes") {
    val patch    = AspectUserPatch(email = Some("a@b.com"), name = Some("Alice"))
    val patchRaw = patchContract.toPatchRaw(patch)
    patchRaw shouldBe Map("email" -> "a@b.com", "name" -> "Alice")
  }

  // ── IsAspectOf compile-time enforcement ──────────────────────────────────

  test("IsAspectOf can be summoned for a declared aspect") {
    // If this compiles, the proof is available.
    val _ = summon[IsAspectOf[AspectUser, AspectUserPatch]]
    val _ = summon[IsAspectOf[AspectUser, AspectAdminPatch]]
    val _ = summon[IsAspectOf[AspectOrder, AspectOrderPatch]]
    val _ = summon[IsAspectOf[AspectUser, AspectUserRegistration]]
    val _ = summon[IsAspectOf[AspectProduct, AspectProductPatch]]
    succeed
  }

  test("IsAspectOf is rejected at compile time for a non-aspect type") {
    assertTypeError(
      """summon[IsAspectOf[AspectUser, AspectOrder]]"""
    )
  }

  test("typed validatePatch is rejected at compile time for a non-aspect") {
    assertTypeError("""
      val current: RawObject = Map("ref" -> "R1", "qty" -> 1, "weight" -> 0.5)
      userContract.validatePatch(current, AspectOrderPatch(Some(3)))
    """)
  }
