package io.concentric

final class CrossFeatureSpec extends SpecBase:

/**
 * Cross-feature interaction tests.
 *
 * Each suite exercises a pair (or triple) of features working together to
 * make sure one feature does not silently bypass the other.  The motivating
 * principle: if feature A works and feature B works, their composition must
 * also work correctly — especially in the negative case.
 */
  def spec: Unit = suite("CrossFeatureSpec")(

    // ── Patch.modify × @immutable ─────────────────────────────────────────────
    //
    // Patch.set on an @immutable field is already tested in PatchAndPartialSpec.
    // Patch.modify is a separate code path (modifier map → applyTo → validatePatch)
    // that must also be caught by the @immutable guard.

    suite("Patch.modify × @immutable")(

      test("modify on an @immutable field produces ImmutableField violation") {
        // User.id is @immutable — incrementing it via .modify must be rejected
        val current: RawObject = Map(
          "id" -> 1L, "name" -> "Alice", "age" -> 30, "email" -> None, "password" -> None
        )
        val patch = Patch.empty[User].modify(_.id, _ + 1L)
        for result <- userContract.applyPatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("id") &&
            v.code == ViolationCode.ImmutableField
          )
        )
      },

      test("modify on a mutable field is unaffected by @immutable check") {
        // age is NOT @immutable — modify should succeed
        val current: RawObject = Map(
          "id" -> 1L, "name" -> "Alice", "age" -> 29, "email" -> None, "password" -> None
        )
        val patch = Patch.empty[User].modify(_.age, _ + 1)
        for user <- userContract.applyPatch(current, patch)
        yield assertTrue(user.age == 30)
      }
    ),

    // ── Patch (typed builder) × @reserved ────────────────────────────────────
    //
    // The existing @reserved tests in ConstraintSpec use raw-map validatePatch.
    // Here we confirm the typed Patch builder also triggers the @reserved guard,
    // both for .set and .modify — two different code paths through applyPatch.

    suite("Patch × @reserved")(

      test("Patch.set on a @reserved field produces ReservedField violation") {
        val current: RawObject = Map("title" -> "Fix login bug")
        val patch = Patch.empty[Ticket].set(_.trackingId, "CUSTOM-001")
        for result <- ticketContract.applyPatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("trackingId") &&
            v.code == ViolationCode.ReservedField
          )
        )
      },

      test("Patch.modify on a @reserved field produces ReservedField violation") {
        val current: RawObject = Map("title" -> "Fix login bug", "trackingId" -> "PENDING")
        val patch = Patch.empty[Ticket].modify(_.trackingId, _ + "-MODIFIED")
        for result <- ticketContract.applyPatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("trackingId") &&
            v.code == ViolationCode.ReservedField
          )
        )
      },

      test("Patch with only non-reserved fields succeeds even when @reserved field is in current") {
        val current: RawObject = Map("title" -> "Old title", "trackingId" -> "TKT-001")
        val patch = Patch.empty[Ticket].set(_.title, "New title")
        for ticket <- ticketContract.applyPatch(current, patch)
        yield assertTrue(ticket.title == "New title", ticket.trackingId == "TKT-001")
      }
    ),

    // ── validatePatch field-origin semantics ─────────────────────────────────
    //
    // A patch is only responsible for the fields it explicitly touches.
    // Fields that come from currentRaw (not the patch) are decoded and trusted
    // without re-running constraint checks.  This matches the original concentric
    // design and avoids two categories of false failures:
    //
    //  1. @reserved fields written by the system into currentRaw would
    //     permanently block all future patches on that record (the original bug).
    //
    //  2. A stored value that predates a tightened constraint (e.g. age = -1
    //     before @min(0) was added) would break every subsequent patch that
    //     doesn't also fix that field — even though the patch is unrelated.

    suite("validatePatch — currentRaw fields are trusted, not re-validated")(

      test("patch succeeds when currentRaw contains a value that violates a current constraint") {
        // 'age' = -1 violates @min(0), but the patch only changes 'name'.
        // The patch must not be held responsible for data it did not touch.
        val currentRaw: RawObject = Map("id" -> 1L, "name" -> "Alice", "age" -> -1)
        val patch: RawObject      = Map("name" -> "Alice Updated")
        for user <- userContract.validatePatch(currentRaw, patch)
        yield assertTrue(user.name == "Alice Updated", user.age == -1)
      },

      test("patch correctly rejects a NEW constraint violation introduced by the patch itself") {
        // The patch changes 'age' to an invalid value — this must still be caught.
        val currentRaw: RawObject = Map("id" -> 1L, "name" -> "Alice", "age" -> 30)
        val patch: RawObject      = Map("age" -> -5)
        for result <- userContract.validatePatch(currentRaw, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("age") &&
            v.code == ViolationCode.ConstraintFailed("min")
          )
        )
      },

      test("patch that fixes a constraint violation in currentRaw succeeds (S4.18)") {
        // currentRaw has 'name: ""' which violates @nonEmpty.  The patch supplies a
        // valid name — the field is now in the patch so it IS fully validated, and
        // the result is correct.  This shows the system converges toward a valid
        // state: a patch can repair bad stored data.
        val currentRaw: RawObject = Map("id" -> 1L, "name" -> "", "age" -> 25)
        val patch: RawObject      = Map("name" -> "Alice")
        for user <- userContract.validatePatch(currentRaw, patch)
        yield assertTrue(user.name == "Alice", user.age == 25)
      }
    ),

    // ── validatePatch × @validateContract (S5.12 / S12.13) ───────────────────
    //
    // Cross-field @validateContract validators run on the fully assembled result,
    // not just on the individual fields that the patch touched.  This means:
    //  - A patch that passes all field-level checks can still fail cross-field.
    //  - A patch can also *fix* a cross-field violation that existed in currentRaw.

    suite("validatePatch × @validateContract")(

      test("patch that individually passes field validation but violates @validateContract fails") {
        // Patching checkOut to before checkIn — the Int value 20240515 is valid on
        // its own, but the cross-field rule (checkOut > checkIn) is violated.
        val currentRaw: RawObject = Map(
          "name"      -> "Beach House",
          "checkIn"   -> 20240601,
          "checkOut"  -> 20240610,
          "minGuests" -> 1,
          "maxGuests" -> 4
        )
        val patch: RawObject = Map("checkOut" -> 20240515)
        for result <- stayContract.validatePatch(currentRaw, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath(Nil) &&
            v.code == ViolationCode.ConstraintFailed("validateContract") &&
            v.message.contains("checkOut must be after checkIn")
          )
        )
      },

      test("patch that corrects a cross-field violation in currentRaw succeeds") {
        // currentRaw has checkOut < checkIn — the stored data is invalid.
        // The patch corrects checkOut; the assembled result satisfies the
        // cross-field rule and validation succeeds.
        val currentRaw: RawObject = Map(
          "name"      -> "Cabin",
          "checkIn"   -> 20240610,
          "checkOut"  -> 20240601, // invalid stored value — before checkIn
          "minGuests" -> 1,
          "maxGuests" -> 4
        )
        val patch: RawObject = Map("checkOut" -> 20240620)
        for stay <- stayContract.validatePatch(currentRaw, patch)
        yield assertTrue(stay.checkOut == 20240620, stay.checkIn == 20240610)
      }
    ),

    // ── New constraint annotations × validatePatch ────────────────────────────
    //
    // The constraint tests in ConstraintSpec cover validate().  Here we confirm
    // that the newer annotations (@url, @uuid, @positive, @multipleOf) are
    // equally enforced when the value arrives via a patch — i.e. they survive
    // the validatePatch → runPatchValidation path.

    suite("new constraint annotations × validatePatch")(

      test("@url constraint is enforced on a patched value") {
        val current: RawObject = Map("href" -> "https://example.com")
        val patch: RawObject   = Map("href" -> "not-a-url")
        for result <- webLinkContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("href") &&
            v.code == ViolationCode.ConstraintFailed("url")
          )
        )
      },

      test("patching a non-uuid field succeeds when the uuid field is unchanged") {
        // Positive case: patching 'name' (no uuid constraint) leaves the valid
        // uuid in 'id' untouched — the patch should succeed.
        val current: RawObject = Map("id" -> "550e8400-e29b-41d4-a716-446655440000", "name" -> "Thing")
        val patch: RawObject   = Map("name" -> "Updated")
        for r <- resourceContract.validatePatch(current, patch)
        yield assertTrue(r.name == "Updated")
      },

      test("@uuid constraint rejects an invalid uuid in a patch") {
        val current: RawObject = Map("id" -> "550e8400-e29b-41d4-a716-446655440000", "name" -> "Thing")
        val patch: RawObject   = Map("id" -> "not-a-uuid")
        for result <- resourceContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("id") &&
            v.code == ViolationCode.ConstraintFailed("uuid")
          )
        )
      },

      test("@positive constraint is enforced on a patched value") {
        val current: RawObject = Map("value" -> 3.14, "count" -> 5)
        val patch: RawObject   = Map("value" -> -1.0)
        for result <- measurementContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("value") &&
            v.code == ViolationCode.ConstraintFailed("positive")
          )
        )
      },

      test("@multipleOf constraint is enforced on a patched value") {
        val current: RawObject = Map("amount" -> 10.00, "quantity" -> 5)
        val patch: RawObject   = Map("quantity" -> 7) // not a multiple of 5
        for result <- paymentContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("quantity") &&
            v.code == ViolationCode.ConstraintFailed("multipleOf")
          )
        )
      }
    ),

    // ── validatePartial × annotations ────────────────────────────────────────
    //
    // validatePartial skips ExpectedMissing for absent fields, but every other
    // check — @reserved, type, constraints, unknown fields — must still apply.

    suite("validatePartial × annotations")(

      test("@reserved field in validatePartial produces ReservedField violation") {
        val raw: RawObject = Map("trackingId" -> "CUSTOM-001")
        for result <- ticketContract.validatePartial(raw).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("trackingId") &&
            v.code == ViolationCode.ReservedField
          )
        )
      },

      test("@url constraint is enforced in validatePartial") {
        val raw: RawObject = Map("href" -> "not-a-url")
        for result <- webLinkContract.validatePartial(raw).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("href") &&
            v.code == ViolationCode.ConstraintFailed("url")
          )
        )
      },

      test("unknown field in validatePartial on a closed contract is rejected") {
        val raw: RawObject = Map("name" -> "Alice", "unknownField" -> "surprise")
        for result <- userContract.validatePartial(raw).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("unknownField") &&
            v.code == ViolationCode.UnknownField
          )
        )
      },

      test("validatePartial accepts a subset of valid fields with no violations") {
        // Only providing 'href', which satisfies @url — label is optional so no error
        val raw: RawObject = Map("href" -> "https://example.com/api")
        for draft <- webLinkContract.validatePartial(raw)
        yield assertTrue(draft.validatedFields.get("href") == Some("https://example.com/api"))
      },

      test("@nonEmpty constraint is enforced in validatePartial") {
        val raw: RawObject = Map("name" -> "")
        for result <- userContract.validatePartial(raw).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("name") &&
            v.code == ViolationCode.ConstraintFailed("nonEmpty")
          )
        )
      }
    ),

    // ── collectViolations × @validateContract ─────────────────────────────────
    //
    // collectViolations is a summary method that skips constructing T.
    // It calls the same runValidation pipeline, so cross-field @validateContract
    // validators must still fire — but only when all field-level checks pass.

    suite("collectViolations × @validateContract")(

      test("collectViolations surfaces cross-field violations when fields are individually valid") {
        val raw: RawObject = Map(
          "name"      -> "Beach House",
          "checkIn"   -> 20240610, // checkIn > checkOut → cross-field failure
          "checkOut"  -> 20240601,
          "minGuests" -> 1,
          "maxGuests" -> 4
        )
        val violations = stayContract.collectViolations(raw)
        assertTrue(
          violations.toList.exists(v =>
            v.path == FieldPath(Nil) &&
            v.code == ViolationCode.ConstraintFailed("validateContract") &&
            v.message.contains("checkOut must be after checkIn")
          )
        )
      },

      test("collectViolations returns empty for a fully valid object") {
        val raw: RawObject = Map(
          "name"      -> "Cabin",
          "checkIn"   -> 20240601,
          "checkOut"  -> 20240610,
          "minGuests" -> 1,
          "maxGuests" -> 4
        )
        assertTrue(stayContract.collectViolations(raw).isEmpty)
      },

      test("field-level violations prevent @validateContract from running in collectViolations") {
        // minGuests = 0 fails @min(1) — cross-field validators should NOT run
        val raw: RawObject = Map(
          "name"      -> "Tent",
          "checkIn"   -> 20240601,
          "checkOut"  -> 20240610,
          "minGuests" -> 0, // @min(1) violation
          "maxGuests" -> 2
        )
        val violations = stayContract.collectViolations(raw)
        assertTrue(
          violations.toList.exists(_.path == FieldPath("minGuests")),
          !violations.toList.exists(_.code == ViolationCode.ConstraintFailed("validateContract"))
        )
      },

      test("collectViolations accumulates multiple cross-field failures") {
        val raw: RawObject = Map(
          "name"      -> "Hut",
          "checkIn"   -> 20240610,
          "checkOut"  -> 20240601, // checkOut < checkIn
          "minGuests" -> 5,
          "maxGuests" -> 2          // maxGuests < minGuests
        )
        val violations = stayContract.collectViolations(raw)
        assertTrue(
          violations.toList.exists(_.message.contains("checkOut must be after checkIn")),
          violations.toList.exists(_.message.contains("maxGuests must be >= minGuests"))
        )
      }
    ),

    // ── sanitize × direct contract invocation ─────────────────────────────────
    //
    // The invoiceContract tests already verify that sanitize recurses into
    // Either branches.  Here we test the CardPayment contract in isolation
    // to confirm @internal removal and @masked replacement without the Either
    // wrapper — a "unit" test for the sanitize transformation.

    suite("sanitize × direct contract (CardPayment)")(

      test("sanitize strips @internal cvv from CardPayment") {
        val raw: RawObject = Map(
          "cardNumber" -> "4111-1111-1111-1111",
          "cvv"        -> "123",
          "amount"     -> 49.99
        )
        val sanitized = cardPaymentContract.sanitize(raw)
        assertTrue(!sanitized.contains("cvv"))
      },

      test("sanitize masks @masked cardNumber in CardPayment") {
        val raw: RawObject = Map(
          "cardNumber" -> "4111-1111-1111-1111",
          "cvv"        -> "123",
          "amount"     -> 49.99
        )
        val sanitized = cardPaymentContract.sanitize(raw)
        assertTrue(sanitized.get("cardNumber") == Some("***"))
      },

      test("sanitize leaves non-sensitive amount field unchanged in CardPayment") {
        val raw: RawObject = Map(
          "cardNumber" -> "4111-1111-1111-1111",
          "cvv"        -> "123",
          "amount"     -> 49.99
        )
        val sanitized = cardPaymentContract.sanitize(raw)
        assertTrue(sanitized.get("amount") == Some(49.99))
      }
    ),

    // ── Draft.finalize × @validateContract ───────────────────────────────────
    //
    // Draft.finalize delegates to contract.validate, so cross-field validators
    // must fire during finalization.  This tests that the partial-build workflow
    // (validatePartial → Draft.merge → Draft.finalize) does not silently skip
    // contract-level validation.

    suite("Draft.finalize × @validateContract")(

      test("Draft.finalize fails when @validateContract validator is violated") {
        // Build a draft from individually-valid fields, then finalize with an
        // invalid cross-field relationship (checkOut <= checkIn).
        val draft = new Draft[Stay](Map(
          "name"      -> "Cabin",
          "checkIn"   -> 20240610,
          "checkOut"  -> 20240601, // checkOut < checkIn → cross-field failure
          "minGuests" -> 1,
          "maxGuests" -> 4
        ))
        for result <- draft.finalize(stayContract).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath(Nil) &&
            v.code == ViolationCode.ConstraintFailed("validateContract") &&
            v.message.contains("checkOut must be after checkIn")
          )
        )
      },

      test("Draft.finalize succeeds when @validateContract validators all pass") {
        val draft = new Draft[Stay](Map(
          "name"      -> "Beach House",
          "checkIn"   -> 20240601,
          "checkOut"  -> 20240610,
          "minGuests" -> 2,
          "maxGuests" -> 6
        ))
        for stay <- draft.finalize(stayContract)
        yield assertTrue(
          stay.name      == "Beach House",
          stay.checkIn   == 20240601,
          stay.checkOut  == 20240610,
          stay.maxGuests == 6
        )
      },

      test("Draft built from validatePartial + merge(Draft) finalizes correctly") {
        // Each batch of fields is validated immediately via validatePartial,
        // then the two clean drafts are combined before finalization.
        for
          draft1 <- stayContract.validatePartial(Map("name" -> "Villa", "checkIn" -> 20240601))
          draft2 <- stayContract.validatePartial(Map("checkOut" -> 20240615, "minGuests" -> 1, "maxGuests" -> 8))
          stay   <- draft1.merge(draft2).finalize(stayContract)
        yield assertTrue(stay.name == "Villa", stay.minGuests == 1)
      },

      test("Draft built from validatePartial + merge(Draft) fails on cross-field violation at finalize") {
        // Each batch of fields passes validatePartial individually — checkOut = 20240601
        // is a valid positive Int on its own, so step 2 succeeds.  The cross-field
        // violation (checkOut < checkIn) is only detectable once both halves are
        // assembled at finalize, where @validateContract fires.
        for
          draft1 <- stayContract.validatePartial(Map("name" -> "Tent", "checkIn" -> 20240610))
          draft2 <- stayContract.validatePartial(Map("checkOut" -> 20240601, "minGuests" -> 1, "maxGuests" -> 4))
          result <- draft1.merge(draft2).finalize(stayContract).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath(Nil) &&
            v.code == ViolationCode.ConstraintFailed("validateContract") &&
            v.message.contains("checkOut must be after checkIn")
          )
        )
      }
    ),

    // ── View × sanitize — orthogonality ──────────────────────────────────────
    //
    // View.omit and contract.sanitize are independent operations.  A field that
    // sanitize would strip can also be independently controlled by View, and
    // vice versa.  These tests confirm the two features do not interfere.

    suite("View × sanitize — orthogonality")(

      test("View.omit removes @internal field independently of sanitize") {
        // User.id is @internal (sanitize would strip it), but View.omit also
        // removes it — the two should produce the same result for that field.
        val raw: RawObject = Map(
          "id" -> 1L, "name" -> "Alice", "password" -> "s3cr3t", "age" -> 30
        )
        val viewOut     = View[User].omit(_.id)(raw)
        val sanitizeOut = userContract.sanitize(raw)
        assertTrue(
          !viewOut.contains("id"),       // View removes it
          !sanitizeOut.contains("id")    // sanitize also removes it
        )
      },

      test("View.mask and sanitize @masked produce the same outcome for password") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "password" -> "s3cr3t")
        // sanitize uses the contract's @masked annotation
        val sanitized = userContract.sanitize(raw)
        // View.mask achieves the same effect explicitly
        val viewed = View[User].mask(_.password)(raw)
        assertTrue(
          sanitized.get("password") == Some("***"),
          viewed.get("password")    == Some("***")
        )
      },

      test("View.omit on a non-sensitive field does not affect sanitize output") {
        // Omitting 'name' in a view does not alter what sanitize produces
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "password" -> "s3cr3t")
        val viewOut     = View[User].omit(_.name)(raw)
        val sanitizeOut = userContract.sanitize(raw)
        // sanitize keeps 'name'; view removes it — they are independent
        assertTrue(
          !viewOut.contains("name"),
          sanitizeOut.get("name") == Some("Alice")
        )
      },

      test("transforms compose: omit then sanitize gives narrower result") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Bob", "password" -> "s3cr3t", "age" -> 25)
        // First apply View to remove 'name', then sanitize (strips id, masks password)
        val viewOut   = View[User].omit(_.name)(raw)
        val finalOut  = userContract.sanitize(viewOut)
        assertTrue(
          !finalOut.contains("id"),          // stripped by sanitize
          !finalOut.contains("name"),        // stripped by view before sanitize
          finalOut.get("password") == Some("***"), // masked by sanitize
          finalOut.get("age")      == Some(25)     // unchanged
        )
      }
    ),

    // ── Draft × aspectOf (inherit = ALL) ─────────────────────────────────────
    //
    // AspectOrderFull uses inherit = true with no exclusions — all three source
    // fields (ref, qty, weight) are declared as Option[T], each with an inherited
    // constraint.  These tests confirm that the Draft/validatePartial workflow
    // integrates correctly with inherit = ALL aspects:
    //
    //  - partial input is accepted (Option fields are genuinely optional mid-draft)
    //  - inherited constraints still fire when a present value is invalid
    //  - multi-step merge+finalize assembles a valid object
    //  - validatePatch on the SOURCE contract still works while the ASPECT is used
    //    for the creation path (the two contracts coexist without interfering)

    suite("Draft × aspectOf (inherit = ALL)")(

      test("validatePartial on inherit=ALL aspect accepts partial input (only ref provided)") {
        // AspectOrderFull has all Option fields — providing only ref must succeed.
        val aspectOrderFullContract = summon[Contract[AspectOrderFull]]
        for draft <- aspectOrderFullContract.validatePartial(Map("ref" -> "ORD-001"))
        yield assertTrue(draft.toMap.get("ref") == Some("ORD-001"))
      },

      test("validatePartial on inherit=ALL aspect catches inherited constraint violation") {
        // qty has inherited @min(1) — sending 0 must fail even in a partial batch.
        val aspectOrderFullContract = summon[Contract[AspectOrderFull]]
        for result <- aspectOrderFullContract.validatePartial(Map("qty" -> 0)).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("qty") &&
            v.code == ViolationCode.ConstraintFailed("min")
          )
        )
      },

      test("multi-step draft merge+finalize on inherit=ALL aspect produces valid object") {
        // Simulate a two-step form: first step provides ref+qty, second adds weight.
        val aspectOrderFullContract = summon[Contract[AspectOrderFull]]
        for
          draft1 <- aspectOrderFullContract.validatePartial(Map("ref" -> "ORD-002", "qty" -> 5))
          draft2 <- aspectOrderFullContract.validatePartial(Map("weight" -> 12.5))
          order  <- draft1.merge(draft2).finalize(aspectOrderFullContract)
        yield assertTrue(
          order.ref    == Some("ORD-002"),
          order.qty    == Some(5),
          order.weight == Some(12.5)
        )
      },

      test("inherit=ALL draft finalize rejects inherited constraint on merged value") {
        // First step passes — weight 999.9 is an individually valid Double.
        // But @max(999) inherited from AspectOrder must reject it at finalization.
        val aspectOrderFullContract = summon[Contract[AspectOrderFull]]
        for
          draft1 <- aspectOrderFullContract.validatePartial(Map("ref" -> "ORD-003", "qty" -> 2))
          draft2 <- aspectOrderFullContract.validatePartial(Map("weight" -> 1000.0))
          result <- draft1.merge(draft2).finalize(aspectOrderFullContract).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("weight") &&
            v.code == ViolationCode.ConstraintFailed("max")
          )
        )
      },

      test("source contract validatePatch is unaffected when aspect contract coexists") {
        // Using AspectOrderFull (inherit=ALL) for the creation path does not
        // interfere with validatePatch on the source AspectOrder contract.
        val aspectOrderContract     = summon[Contract[AspectOrder]]
        val aspectOrderFullContract = summon[Contract[AspectOrderFull]]
        val current: RawObject = Map("ref" -> "ORD-004", "qty" -> 3, "weight" -> 10.0)
        for
          // aspect contract: create a draft, finalize OK
          draft <- aspectOrderFullContract.validatePartial(Map("ref" -> "ORD-005", "qty" -> 1, "weight" -> 5.0))
          _     <- draft.finalize(aspectOrderFullContract)
          // source contract: patch current — independent of the aspect
          order <- aspectOrderContract.validatePatch(current, Map("qty" -> 7))
        yield assertTrue(order.qty == 7, order.ref == "ORD-004")
      }
    ),

    // ── View × aspectOf (inherit = ALL) ──────────────────────────────────────
    //
    // AspectUserPublicProfile uses inherit = true, exclude = Seq("role","token")
    // — fields are id (Long), email (String), name (String).
    // AspectUserProfileStrict is the tighter variant with @maxLength(20) on name.
    //
    // These tests confirm:
    //  - View.omit on an inherit=ALL aspect output correctly removes the field
    //  - View.mask on an inherit=ALL aspect output correctly masks the field
    //  - View chaining respects order on aspect-validated output
    //  - A valid inherit=ALL validation result feeds into a View pipeline unchanged

    suite("View × aspectOf (inherit = ALL)")(

      test("View.omit on inherit=ALL aspect output removes the named field") {
        val publicProfileContract = summon[Contract[AspectUserPublicProfile]]
        val raw: RawObject        = Map("id" -> 1L, "name" -> "Alice", "email" -> "alice@example.com")
        for profile <- publicProfileContract.validate(raw)
        yield
          val viewOut = View[AspectUserPublicProfile].omit(_.name)(raw)
          assertTrue(
            !viewOut.contains("name"),
            viewOut.get("id")    == Some(1L),
            viewOut.get("email") == Some("alice@example.com")
          )
      },

      test("View.mask on inherit=ALL aspect output masks the named field") {
        val publicProfileContract = summon[Contract[AspectUserPublicProfile]]
        val raw: RawObject        = Map("id" -> 1L, "name" -> "Alice", "email" -> "alice@example.com")
        for _ <- publicProfileContract.validate(raw)
        yield
          val viewOut = View[AspectUserPublicProfile].mask(_.email)(raw)
          assertTrue(
            viewOut.get("email") == Some("***"),
            viewOut.get("name")  == Some("Alice")
          )
      },

      test("View chaining respects order on inherit=ALL aspect output") {
        // omit(name) then mask(email) — name gone, email masked, id survives
        val publicProfileContract = summon[Contract[AspectUserPublicProfile]]
        val raw: RawObject        = Map("id" -> 42L, "name" -> "Bob", "email" -> "bob@example.com")
        for _ <- publicProfileContract.validate(raw)
        yield
          val viewOut = View[AspectUserPublicProfile].omit(_.name).mask(_.email)(raw)
          assertTrue(
            !viewOut.contains("name"),
            viewOut.get("email") == Some("***"),
            viewOut.get("id")    == Some(42L)
          )
      },

      test("validated output of inherit=ALL aspect feeds into View correctly") {
        // Validate with the strict profile (tighter @maxLength(20)) — result is valid.
        // Then apply a View to the same raw map; the constraint-tightening on the
        // aspect must not interfere with the View's independent field projection.
        val strictProfileContract = summon[Contract[AspectUserProfileStrict]]
        val raw: RawObject        = Map("id" -> 5L, "email" -> "carol@example.com", "name" -> "Carol")
        for profile <- strictProfileContract.validate(raw)
        yield
          val viewOut = View[AspectUserProfileStrict].omit(_.id)(raw)
          assertTrue(
            !viewOut.contains("id"),
            viewOut.get("name")  == Some("Carol"),
            viewOut.get("email") == Some("carol@example.com"),
            profile.id    == 5L,
            profile.name  == "Carol",
            profile.email == "carol@example.com"
          )
      },

      test("inherit=ALL aspect validation failure does not affect View output") {
        // Validation fails (name too long for strict profile @maxLength(20)), but
        // View.omit is an independent transformation — it still produces its output
        // correctly from the raw map regardless of what validate returns.
        val strictProfileContract = summon[Contract[AspectUserProfileStrict]]
        val raw: RawObject        = Map("id" -> 9L, "email" -> "dave@example.com", "name" -> ("x" * 25))
        for result <- strictProfileContract.validate(raw).flip
        yield
          val viewOut = View[AspectUserProfileStrict].omit(_.id)(raw)
          assertTrue(
            result.violations.toList.exists(v =>
              v.path == FieldPath("name") &&
              v.code == ViolationCode.ConstraintFailed("maxLength")
            ),
            !viewOut.contains("id"),      // View still works
            viewOut.get("name") == Some("x" * 25)  // raw value, unmodified by validation
          )
      }
    )
  )

  spec
