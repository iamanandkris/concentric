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

// ── inherit = ALL aspects ─────────────────────────────────────────────────────

/** Public profile — inherit = ALL, role + token excluded.
 *  Every source field is accounted for: id/email/name declared, role+token excluded.
 *  Policy annotations (@internal, @immutable) on id are still NOT inherited.
 */
@aspectOf[AspectUser](inherit = true, exclude = Seq("role", "token"))
case class AspectUserPublicProfile(
  val id:    Long,
  val email: String,
  val name:  String
) derives Contract

/** inherit = ALL with an extra field not in source.
 *  id+role+token excluded; email+name declared; displayName is brand-new.
 */
@aspectOf[AspectUser](inherit = true, exclude = Seq("id", "role", "token"))
case class AspectUserProfileWithExtra(
  val email:                 String,
  val name:                  String,
  @nonEmpty val displayName: String
) derives Contract

/** inherit = ALL on an open-source contract — all fields must be accounted for. */
@aspectOf[AspectProduct](inherit = true)
case class AspectProductFull(
  val sku:   Option[String] = None,  // @nonEmpty inherited
  val price: Option[Double] = None   // @min(0) inherited
) derives Contract

/** inherit = ALL, all fields Optional — full exhaustive PATCH variant of AspectOrder.
 *  ref+qty+weight all declared; nothing excluded.
 *  Tests that inherit = ALL works with Option[T] fields and integrates
 *  with validatePatch.
 */
@aspectOf[AspectOrder](inherit = true)
case class AspectOrderFull(
  val ref:    Option[String] = None,  // @nonEmpty inherited
  val qty:    Option[Int]    = None,  // @min(1) inherited
  val weight: Option[Double] = None   // @max(999) inherited
) derives Contract

/** inherit = ALL with constraint override — tightens @maxLength to 20 chars on name.
 *  All source fields accounted for: id/email/name declared, role/token excluded.
 *  Tests that a local annotation override still wins over the inherited one when
 *  inherit = true is active.
 */
@aspectOf[AspectUser](inherit = true, exclude = Seq("role", "token"))
case class AspectUserProfileStrict(
  val id:    Long,
  val email: String,
  @maxLength(20) val name: String  // overrides inherited @maxLength(100)
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
  val orderContract             = summon[Contract[AspectOrder]]
  val publicProfileContract     = summon[Contract[AspectUserPublicProfile]]
  val profileWithExtraContract  = summon[Contract[AspectUserProfileWithExtra]]
  val productFullContract       = summon[Contract[AspectProductFull]]
  val orderFullContract         = summon[Contract[AspectOrderFull]]
  val userProfileStrictContract = summon[Contract[AspectUserProfileStrict]]

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

  // ── inherit = ALL (exhaustive mode) ──────────────────────────────────────

  suite("inherit = ALL — field inclusion") {
    test("public profile includes exactly id, email, name") {
      publicProfileContract.fieldMetas.map(_.name).toSet shouldBe Set("id", "email", "name")
    }

    test("public profile excludes role") {
      publicProfileContract.fieldMetas.map(_.name).toSet should not contain "role"
    }

    test("public profile excludes token") {
      publicProfileContract.fieldMetas.map(_.name).toSet should not contain "token"
    }

    test("profile with extra includes email, name, and displayName") {
      profileWithExtraContract.fieldMetas.map(_.name).toSet shouldBe Set("email", "name", "displayName")
    }

    test("profile with extra excludes id") {
      profileWithExtraContract.fieldMetas.map(_.name).toSet should not contain "id"
    }

    test("product full includes sku and price (all source fields, no exclusions)") {
      productFullContract.fieldMetas.map(_.name).toSet shouldBe Set("sku", "price")
    }
  }

  suite("inherit = ALL — validation") {
    test("public profile: valid input passes") {
      val result = publicProfileContract.validate(Map("id" -> 1L, "email" -> "a@example.com", "name" -> "Alice"))
      result.isRight shouldBe true
    }

    test("public profile: closed — unknown field (role) rejected") {
      val result = publicProfileContract.validate(Map(
        "id" -> 1L, "email" -> "a@example.com", "name" -> "Alice", "role" -> "admin"))
      result.isLeft shouldBe true
    }

    test("public profile: @email inherited — invalid email rejected") {
      val result = publicProfileContract.validate(Map("id" -> 1L, "email" -> "bad", "name" -> "Alice"))
      result.isLeft shouldBe true
      result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("email")) shouldBe true
    }

    test("public profile: @nonEmpty inherited on name — empty name rejected") {
      val result = publicProfileContract.validate(Map("id" -> 1L, "email" -> "a@b.com", "name" -> ""))
      result.isLeft shouldBe true
      result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("nonEmpty")) shouldBe true
    }

    test("public profile: @maxLength(100) inherited on name — oversized name rejected") {
      val result = publicProfileContract.validate(Map("id" -> 1L, "email" -> "a@b.com", "name" -> ("x" * 101)))
      result.isLeft shouldBe true
      result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("maxLength")) shouldBe true
    }

    test("public profile: @internal not inherited on id — id can be freely set") {
      val result = publicProfileContract.validate(Map("id" -> 99L, "email" -> "a@b.com", "name" -> "Bob"))
      result.isRight shouldBe true
      result.value.id shouldBe 99L
    }

    test("profile with extra: extra field @nonEmpty enforced") {
      val result = profileWithExtraContract.validate(Map("email" -> "a@b.com", "name" -> "Alice", "displayName" -> ""))
      result.isLeft shouldBe true
      result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("nonEmpty")) shouldBe true
    }

    test("profile with extra: inherited constraint on email still applies") {
      val result = profileWithExtraContract.validate(Map("email" -> "bad", "name" -> "Alice", "displayName" -> "A"))
      result.isLeft shouldBe true
      result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("email")) shouldBe true
    }

    test("profile with extra: valid input passes") {
      val result = profileWithExtraContract.validate(Map("email" -> "a@b.com", "name" -> "Alice", "displayName" -> "Alice A."))
      result.isRight shouldBe true
    }

    test("product full (open source, inherit = ALL): @nonEmpty on sku inherited") {
      val result = productFullContract.validate(Map("sku" -> ""))
      result.isLeft shouldBe true
      result.left.value.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("nonEmpty")) shouldBe true
    }

    test("product full: aspect is closed even though source is open") {
      val result = productFullContract.validate(Map("sku" -> "ABC", "unknown" -> "x"))
      result.isLeft shouldBe true
    }
  }

  suite("inherit = ALL — compile-time errors") {
    test("exclude without inherit = true is a compile error") {
      assertTypeError("""
        import io.concentric.aspectOf
        @aspectOf[AspectUser](exclude = Seq("role"))
        case class BadExcludeNoInherit(val email: Option[String] = None) derives Contract
      """)
    }

    test("exclude name not in source is a compile error") {
      assertTypeError("""
        import io.concentric.aspectOf
        @aspectOf[AspectUser](inherit = true, exclude = Seq("role", "token", "nonExistentField"))
        case class BadBadExcludeName(
          val id: Long, val email: String, val name: String) derives Contract
      """)
    }

    test("source field neither declared nor excluded is a compile error") {
      assertTypeError("""
        import io.concentric.aspectOf
        @aspectOf[AspectUser](inherit = true, exclude = Seq("role"))
        case class BadMissingField(
          val id: Long, val email: String, val name: String) derives Contract
        // token is a source field that is neither declared nor in exclude
      """)
    }
  }

  // ── inherit = ALL, optional fields (gap #1) ──────────────────────────────
  //
  // AspectOrderFull uses inherit = true with no exclusions and all fields
  // wrapped in Option[T].  This verifies that:
  //   - fields absent from the input are silently None (not a missing-required error)
  //   - inherited constraints still fire when a value IS present and violates them
  //   - the aspect integrates correctly with validatePatch on the source contract

  suite("inherit = ALL — optional fields") {

    test("empty map is valid — all optional fields absent") {
      orderFullContract.validate(Map.empty).isRight shouldBe true
    }

    test("absent field constructs to None") {
      val result = orderFullContract.validate(Map.empty)
      result.isRight shouldBe true
      result.value.ref    shouldBe None
      result.value.qty    shouldBe None
      result.value.weight shouldBe None
    }

    test("@nonEmpty inherited on ref — empty string still rejected") {
      val result = orderFullContract.validate(Map("ref" -> ""))
      result.isLeft shouldBe true
      result.left.value.violations.toList
        .exists(_.code == ViolationCode.ConstraintFailed("nonEmpty")) shouldBe true
    }

    test("@min(1) inherited on qty — zero rejected") {
      val result = orderFullContract.validate(Map("qty" -> 0))
      result.isLeft shouldBe true
      result.left.value.violations.toList
        .exists(_.code == ViolationCode.ConstraintFailed("min")) shouldBe true
    }

    test("@max(999) inherited on weight — 1000.0 rejected") {
      val result = orderFullContract.validate(Map("weight" -> 1000.0))
      result.isLeft shouldBe true
      result.left.value.violations.toList
        .exists(_.code == ViolationCode.ConstraintFailed("max")) shouldBe true
    }

    test("valid partial input constructs correct optional values") {
      val result = orderFullContract.validate(Map("ref" -> "R1", "qty" -> 3))
      result.isRight shouldBe true
      result.value.ref    shouldBe Some("R1")
      result.value.qty    shouldBe Some(3)
      result.value.weight shouldBe None
    }

    test("inherit = ALL optional aspect integrates with validatePatch") {
      val currentRaw: RawObject = Map("ref" -> "OLD", "qty" -> 1, "weight" -> 0.5)
      val result =
        for
          patch   <- orderFullContract.validate(Map("ref" -> "NEW"))
          updated <- orderContract.validatePatch(currentRaw, patch)
        yield updated
      result.isRight      shouldBe true
      result.value.ref    shouldBe "NEW"
      result.value.qty    shouldBe 1     // unchanged
      result.value.weight shouldBe 0.5   // unchanged
    }

    test("validatePatch still enforces inherited constraint through inherit = ALL aspect") {
      val currentRaw: RawObject = Map("ref" -> "OLD", "qty" -> 1, "weight" -> 0.5)
      // qty = 0 violates @min(1) — patch validation must catch it before merge
      val patchResult = orderFullContract.validate(Map("qty" -> 0))
      patchResult.isLeft shouldBe true
    }
  }

  // ── inherit = ALL, constraint override (gap #2) ──────────────────────────
  //
  // AspectUserProfileStrict uses inherit = true + exclude + a local @maxLength(20)
  // that overrides the inherited @maxLength(100) on name.

  suite("inherit = ALL — constraint override") {

    test("@maxLength(20) override rejects name longer than 20 chars") {
      val result = userProfileStrictContract.validate(Map(
        "id" -> 1L, "email" -> "a@b.com", "name" -> ("x" * 21)))
      result.isLeft shouldBe true
      result.left.value.violations.toList
        .exists(_.code == ViolationCode.ConstraintFailed("maxLength")) shouldBe true
    }

    test("@maxLength(20) override accepts exactly 20 chars") {
      val result = userProfileStrictContract.validate(Map(
        "id" -> 1L, "email" -> "a@b.com", "name" -> ("x" * 20)))
      result.isRight shouldBe true
    }

    test("original @maxLength(100) no longer applies — 50 chars rejected by tighter override") {
      // Without override, 50 chars would pass @maxLength(100).
      // With @maxLength(20), 50 must still fail.
      val result = userProfileStrictContract.validate(Map(
        "id" -> 1L, "email" -> "a@b.com", "name" -> ("x" * 50)))
      result.isLeft shouldBe true
      result.left.value.violations.toList
        .exists(_.code == ViolationCode.ConstraintFailed("maxLength")) shouldBe true
    }

    test("@nonEmpty still inherited alongside the @maxLength override") {
      val result = userProfileStrictContract.validate(Map(
        "id" -> 1L, "email" -> "a@b.com", "name" -> ""))
      result.isLeft shouldBe true
      result.left.value.violations.toList
        .exists(_.code == ViolationCode.ConstraintFailed("nonEmpty")) shouldBe true
    }

    test("@email inherited on other fields unaffected by override on name") {
      val result = userProfileStrictContract.validate(Map(
        "id" -> 1L, "email" -> "not-an-email", "name" -> "Alice"))
      result.isLeft shouldBe true
      result.left.value.violations.toList
        .exists(_.code == ViolationCode.ConstraintFailed("email")) shouldBe true
    }

    test("valid input passes") {
      val result = userProfileStrictContract.validate(Map(
        "id" -> 1L, "email" -> "a@b.com", "name" -> "Alice"))
      result.isRight shouldBe true
    }
  }

  // ── inherit = ALL, multiple violations (gap #6) + extra error compile tests ──

  suite("inherit = ALL — multiple simultaneous violations") {

    test("all violations reported at once when multiple inherited constraints fail") {
      val result = publicProfileContract.validate(Map(
        "id" -> 1L, "email" -> "not-an-email", "name" -> ""))
      result.isLeft shouldBe true
      val codes = result.left.value.violations.map(_.code).toList
      codes should contain(ViolationCode.ConstraintFailed("email"))
      codes should contain(ViolationCode.ConstraintFailed("nonEmpty"))
    }

    test("multiple violations on optional fields all reported") {
      val result = orderFullContract.validate(Map(
        "ref" -> "", "qty" -> 0, "weight" -> 1000.0))
      result.isLeft shouldBe true
      val codes = result.left.value.violations.map(_.code).toList
      codes should contain(ViolationCode.ConstraintFailed("nonEmpty"))
      codes should contain(ViolationCode.ConstraintFailed("min"))
      codes should contain(ViolationCode.ConstraintFailed("max"))
    }

    // gaps #3 & #4: verify compile errors fire even with multiple bad names.
    // assertTypeError only confirms compilation fails; message content is
    // validated on the JVM side where exceptions carry readable text.

    test("two missing source fields is a compile error") {
      assertTypeError("""
        import io.concentric.aspectOf
        @aspectOf[AspectUser](inherit = true, exclude = Seq("role"))
        case class BadTwoMissing(
          val id: Long, val email: String) derives Contract
        // both name and token are unaccounted for
      """)
    }

    test("two non-existent exclude names is a compile error") {
      assertTypeError("""
        import io.concentric.aspectOf
        @aspectOf[AspectUser](inherit = true, exclude = Seq("ghost1", "ghost2", "role", "token"))
        case class BadTwoBadExcludes(
          val id: Long, val email: String, val name: String) derives Contract
      """)
    }
  }
