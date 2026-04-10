package io.dsentric

final class ValidateContractSpec extends SpecBase:

  def spec: Unit = suite("ValidateContractSpec")(

    suite("@validateContract — cross-field validators")(

      test("valid Stay passes all cross-field validators") {
        val raw: RawObject = Map(
          "name"      -> "Beach House",
          "checkIn"   -> 20240601,
          "checkOut"  -> 20240610,
          "minGuests" -> 1,
          "maxGuests" -> 4
        )
        for s <- stayContract.validate(raw)
        yield assertTrue(
          s.name      == "Beach House",
          s.checkIn   == 20240601,
          s.checkOut  == 20240610,
          s.minGuests == 1,
          s.maxGuests == 4
        )
      },

      test("checkOut == checkIn fails CheckOutAfterCheckIn") {
        val raw: RawObject = Map(
          "name"      -> "Cabin",
          "checkIn"   -> 20240601,
          "checkOut"  -> 20240601,
          "minGuests" -> 1,
          "maxGuests" -> 2
        )
        for violations <- stayContract.validate(raw).flip
        yield assertTrue(
          violations.violations.toList.exists(v =>
            v.path == FieldPath(Nil) &&
            v.code == ViolationCode.ConstraintFailed("validateContract") &&
            v.message.contains("checkOut must be after checkIn")
          )
        )
      },

      test("checkOut < checkIn fails CheckOutAfterCheckIn") {
        val raw: RawObject = Map(
          "name"      -> "Cabin",
          "checkIn"   -> 20240610,
          "checkOut"  -> 20240601,
          "minGuests" -> 1,
          "maxGuests" -> 2
        )
        for violations <- stayContract.validate(raw).flip
        yield assertTrue(
          violations.violations.toList.exists(v =>
            v.path == FieldPath(Nil) &&
            v.message.contains("checkOut must be after checkIn")
          )
        )
      },

      test("maxGuests < minGuests fails GuestRangeValid") {
        val raw: RawObject = Map(
          "name"      -> "Villa",
          "checkIn"   -> 20240601,
          "checkOut"  -> 20240610,
          "minGuests" -> 5,
          "maxGuests" -> 3
        )
        for violations <- stayContract.validate(raw).flip
        yield assertTrue(
          violations.violations.toList.exists(v =>
            v.path == FieldPath(Nil) &&
            v.message.contains("maxGuests must be >= minGuests")
          )
        )
      },

      test("both validators can fail in a single validation run") {
        val raw: RawObject = Map(
          "name"      -> "Hut",
          "checkIn"   -> 20240610,
          "checkOut"  -> 20240601,
          "minGuests" -> 5,
          "maxGuests" -> 2
        )
        for violations <- stayContract.validate(raw).flip
        yield assertTrue(
          violations.violations.toList.exists(_.message.contains("checkOut must be after checkIn")),
          violations.violations.toList.exists(_.message.contains("maxGuests must be >= minGuests"))
        )
      },

      test("field-level violation prevents contract validators from running") {
        // minGuests is 0, which fails @min(1) — contract validators should not run
        val raw: RawObject = Map(
          "name"      -> "Tent",
          "checkIn"   -> 20240601,
          "checkOut"  -> 20240610,
          "minGuests" -> 0,
          "maxGuests" -> 2
        )
        for violations <- stayContract.validate(raw).flip
        yield assertTrue(
          violations.violations.toList.exists(_.path == FieldPath("minGuests")),
          !violations.violations.toList.exists(_.code == ViolationCode.ConstraintFailed("validateContract"))
        )
      },

      test("contract without @validateContract has no cross-field errors on valid input") {
        val raw: RawObject = Map(
          "id"      -> 1L,
          "handle"  -> "alice",
          "contact" -> "alice@example.com"
        )
        for m <- memberContract.validate(raw)
        yield assertTrue(m.handle == "alice")
      }
    )
  )

  spec
