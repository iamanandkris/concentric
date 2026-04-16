package io.concentric

final class DecodableSpec extends SpecBase:

  def spec: Unit = suite("DecodableSpec")(

    // ── RawDecoder.derived — single-field @decodable wrappers ─────────────────

    suite("RawDecoder.derived — single-field wrappers")(

      test("decodes a String wrapper from a raw string") {
        assertTrue(RawDecoder[EmailAddr].decode("alice@example.com") == Some(EmailAddr("alice@example.com")))
      },

      test("returns None for a wrong-type raw value") {
        assertTrue(RawDecoder[EmailAddr].decode(42) == None)
      },

      test("decodes an Int wrapper from a raw int") {
        assertTrue(RawDecoder[Score].decode(99) == Some(Score(99)))
      },

      test("Int wrapper applies Long→Int coercion from the inner RawDecoder") {
        assertTrue(RawDecoder[Score].decode(42L) == Some(Score(42)))
      },

      test("decodes a Long wrapper from a raw long") {
        assertTrue(RawDecoder[UserId].decode(123L) == Some(UserId(123L)))
      },

      test("Long wrapper accepts raw Int (widening)") {
        assertTrue(RawDecoder[UserId].decode(7) == Some(UserId(7L)))
      },

      test("derived decoder integrates with Contract.derived — validates Member") {
        val raw: RawObject = Map(
          "id"      -> 1L,
          "handle"  -> "alice",
          "contact" -> "alice@example.com"
        )
        for m <- memberContract.validate(raw)
        yield assertTrue(
          m.id      == UserId(1L),
          m.handle  == "alice",
          m.contact == EmailAddr("alice@example.com"),
          m.score   == Score(0)
        )
      },

      test("@email constraint on a wrapper field still validates the inner string") {
        val raw: RawObject = Map(
          "id"      -> 1L,
          "handle"  -> "alice",
          "contact" -> "not-an-email"
        )
        for result <- memberContract.validate(raw).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("contact") &&
            v.code == ViolationCode.ConstraintFailed("email")
          )
        )
      },

      test("@immutable on a wrapper field is enforced in validatePatch") {
        val current: RawObject = Map("id" -> 1L, "handle" -> "alice", "contact" -> "a@b.com")
        val patch:   RawObject = Map("id" -> 2L)
        for result <- memberContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("id") &&
            v.code == ViolationCode.ImmutableField
          )
        )
      },

      test("toRaw round-trips wrapper types through validate") {
        val raw: RawObject = Map(
          "id" -> 1L, "handle" -> "bob", "contact" -> "bob@example.com", "score" -> 50
        )
        for m <- memberContract.validate(raw)
        yield {
          val out = memberContract.toRaw(m)
          assertTrue(
            out.get("id")      == Some(UserId(1L)),
            out.get("handle")  == Some("bob"),
            out.get("contact") == Some(EmailAddr("bob@example.com")),
            out.get("score")   == Some(Score(50))
          )
        }
      },

      test("filter works with wrapper-type fields") {
        val raw: RawObject = Map(
          "id" -> 1L, "handle" -> "alice", "contact" -> "a@b.com", "score" -> 80
        )
        val f = memberContract.filter.field(_.handle).is("alice")
        assertTrue(f.test(raw))
      },

      test("validatePartial validates constraint annotations on @decodable wrapper fields (S12.14)") {
        // validatePartial skips ExpectedMissing for absent fields, but constraint
        // violations on the fields that ARE present must still be caught.
        // Member.contact is an EmailAddr wrapper with an @email constraint — an
        // invalid email in a partial must be rejected.
        val raw: RawObject = Map("contact" -> "not-an-email")
        for result <- memberContract.validatePartial(raw).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("contact") &&
            v.code == ViolationCode.ConstraintFailed("email")
          )
        )
      }
    ),

    // ── @extract — RawDecoder.derived multi-field extraction ──────────────────

    suite("@extract — RawDecoder.derived multi-field extraction")(

      test("three-group regex extracts year/month/day as Int") {
        assertTrue(
          RawDecoder[IsoDate].decode("2024-03-15") == Some(IsoDate(2024, 3, 15)),
          RawDecoder[IsoDate].decode("1999-12-31") == Some(IsoDate(1999, 12, 31))
        )
      },

      test("non-matching string returns None") {
        assertTrue(
          RawDecoder[IsoDate].decode("not-a-date") == None,
          RawDecoder[IsoDate].decode("2024-3-5")   == None
        )
      },

      test("non-String raw value returns None") {
        assertTrue(RawDecoder[IsoDate].decode(20240315L) == None)
      },

      test("two-group regex extracts display name and address") {
        val result = RawDecoder[NamedEmail].decode("Alice <alice@example.com>")
        assertTrue(result == Some(NamedEmail("Alice", "alice@example.com")))
      },

      test("NamedEmail returns None when format does not match") {
        assertTrue(RawDecoder[NamedEmail].decode("alice@example.com") == None)
      },

      test("group decoded to wrong type returns None (non-numeric group for Int field)") {
        assertTrue(RawDecoder[IsoDate].decode("abcd-ef-gh") == None)
      }
    ),

    // ── @extract — validate-only mode (0 capture groups) ─────────────────────

    suite("@extract — RawDecoder.derived validate-only mode (0 groups)")(

      test("valid ProductCode string is wrapped") {
        assertTrue(RawDecoder[ProductCode].decode("AB-1234") == Some(ProductCode("AB-1234")))
      },

      test("invalid ProductCode string returns None") {
        assertTrue(
          RawDecoder[ProductCode].decode("ab-1234") == None,
          RawDecoder[ProductCode].decode("AB-12")   == None
        )
      },

      test("non-String returns None") {
        assertTrue(RawDecoder[ProductCode].decode(1234) == None)
      }
    ),

    // ── @extract — @decodable types used as Contract field types ──────────────

    suite("@extract — @decodable types inside Contract.derived")(

      test("IsoDate field decoded via @extract in a contract") {
        val raw: RawObject = Map(
          "id"      -> 1L,
          "ref"     -> "BK-001",
          "checkIn" -> "2024-06-01",
          "event"   -> "2024-06-15"
        )
        for b <- bookingContract.validate(raw)
        yield assertTrue(b.event == IsoDate(2024, 6, 15))
      },

      test("NamedEmail option field decoded via @extract") {
        val raw: RawObject = Map(
          "id"      -> 1L,
          "ref"     -> "BK-002",
          "checkIn" -> "2024-06-01",
          "event"   -> "2024-06-15",
          "host"    -> "Bob <bob@example.com>"
        )
        for b <- bookingContract.validate(raw)
        yield assertTrue(b.host == Some(NamedEmail("Bob", "bob@example.com")))
      },

      test("ProductCode option field decoded via validate-only @extract") {
        val raw: RawObject = Map(
          "id"      -> 1L,
          "ref"     -> "BK-003",
          "checkIn" -> "2024-06-01",
          "event"   -> "2024-06-15",
          "code"    -> "UK-9999"
        )
        for b <- bookingContract.validate(raw)
        yield assertTrue(b.code == Some(ProductCode("UK-9999")))
      },

      test("invalid event string fails decoding with TypeMismatch violation") {
        val raw: RawObject = Map(
          "id"      -> 1L,
          "ref"     -> "BK-004",
          "checkIn" -> "2024-06-01",
          "event"   -> "not-a-date"
        )
        for violations <- bookingContract.validate(raw).flip
        yield assertTrue(
          violations.violations.toList.exists(_.path == FieldPath("event"))
        )
      }
    ),

    // ── @extract — contract field format constraint ────────────────────────────

    suite("@extract — contract field format validation")(

      test("valid checkIn date passes @extract constraint") {
        val raw: RawObject = Map(
          "id"      -> 1L,
          "ref"     -> "BK-005",
          "checkIn" -> "2024-06-01",
          "event"   -> "2024-06-15"
        )
        for b <- bookingContract.validate(raw)
        yield assertTrue(b.checkIn == "2024-06-01")
      },

      test("invalid checkIn date fails with ConstraintFailed(extract) violation") {
        val raw: RawObject = Map(
          "id"      -> 1L,
          "ref"     -> "BK-006",
          "checkIn" -> "June 1st 2024",
          "event"   -> "2024-06-15"
        )
        for violations <- bookingContract.validate(raw).flip
        yield assertTrue(
          violations.violations.toList.exists(v =>
            v.path == FieldPath("checkIn") &&
            v.code == ViolationCode.ConstraintFailed("extract")
          )
        )
      },

      test("@extract constraint is enforced in validatePatch (S10.15)") {
        // Patching checkIn with an invalid format string — the @extract regex must
        // still be evaluated for fields that appear in the patch.
        val current: RawObject = Map(
          "id"      -> 1L,
          "ref"     -> "BK-010",
          "checkIn" -> "2024-06-01",
          "event"   -> "2024-06-15"
        )
        val patch: RawObject = Map("checkIn" -> "June 1st 2024")
        for violations <- bookingContract.validatePatch(current, patch).flip
        yield assertTrue(
          violations.violations.toList.exists(v =>
            v.path == FieldPath("checkIn") &&
            v.code == ViolationCode.ConstraintFailed("extract")
          )
        )
      }
    )
  )

  spec
