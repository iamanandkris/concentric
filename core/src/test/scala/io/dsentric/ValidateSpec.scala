package io.dsentric

import io.dsentric.annotations.internal
import io.dsentric.annotations.immutable

final class ValidateSpec extends SpecBase:

  // ── validate: happy path ──────────────────────────────────────────────────

  test("validate accepts a fully valid object") {
    val raw: RawObject = Map(
      "id"       -> 1L,
      "name"     -> "Alice",
      "email"    -> "alice@example.com",
      "age"      -> 30,
      "password" -> "s3cr3t"
    )
    val user = userContract.validate(raw).value
    user.id       shouldBe 1L
    user.name     shouldBe "Alice"
    user.email    shouldBe Some("alice@example.com")
    user.age      shouldBe 30
    user.password shouldBe Some("s3cr3t")
  }

  test("validate uses default when optional-with-default field is absent") {
    val raw: RawObject = Map("id" -> 42L, "name" -> "Bob")
    val user = userContract.validate(raw).value
    user.age      shouldBe 0
    user.email    shouldBe None
    user.password shouldBe None
  }

  test("validate accepts None when optional fields supplied as null") {
    val raw: RawObject = Map(
      "id"       -> 1L,
      "name"     -> "Carol",
      "email"    -> null,
      "age"      -> 25,
      "password" -> null
    )
    val user = userContract.validate(raw).value
    user.email shouldBe None
    user.password shouldBe None
  }

  // ── validate: required field violations ───────────────────────────────────

  test("validate fails with ExpectedMissing when required field absent") {
    val raw: RawObject = Map("id" -> 1L)
    val result = userContract.validate(raw).left.value
    result.violations.toList.exists(_.code == ViolationCode.ExpectedMissing) shouldBe true
    result.violations.toList.exists(_.path == FieldPath("name")) shouldBe true
  }

  test("validate accumulates all missing required fields") {
    val raw: RawObject = Map.empty
    val result = userContract.validate(raw).left.value
    val paths = result.violations.mapToList(_.path.toString).toSet
    paths should contain("id")
    paths should contain("name")
    result.violations.size should be >= 2
  }

  // ── validate: type mismatch ───────────────────────────────────────────────

  test("validate fails with TypeMismatch when field has wrong type") {
    val raw: RawObject = Map("id" -> 1L, "name" -> 12345)
    val result = userContract.validate(raw).left.value
    result.violations.toList.exists {
      case Violation(p, _: ViolationCode.TypeMismatch, _) => p == FieldPath("name")
      case _                                              => false
    } shouldBe true
  }

  test("null on required field produces TypeMismatch, not ExpectedMissing") {
    val raw: RawObject = Map("id" -> 1L, "name" -> null)
    val result = userContract.validate(raw).left.value
    result.violations.toList.exists {
      case Violation(p, _: ViolationCode.TypeMismatch, _) => p == FieldPath("name")
      case _                                              => false
    } shouldBe true
    result.violations.toList.exists(_.code == ViolationCode.ExpectedMissing) shouldBe false
  }

  // ── open / closed contract ────────────────────────────────────────────────

  test("open contract accepts unknown fields") {
    val raw: RawObject = Map(
      "title"         -> "Hello",
      "body"          -> "World",
      "extra_field"   -> "allowed",
      "another_extra" -> 42
    )
    val doc = openDocContract.validate(raw).value
    doc.title shouldBe "Hello"
  }

  test("closed contract rejects unknown fields") {
    val raw: RawObject = Map(
      "id"            -> 1L,
      "name"          -> "Alice",
      "unknown_field" -> "should fail"
    )
    val result = userContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("unknown_field") &&
        v.code == ViolationCode.UnknownField
    ) shouldBe true
  }

  // ── nested contract ───────────────────────────────────────────────────────

  test("nested contract validates and constructs nested case class field") {
    val raw: RawObject = Map(
      "userId"  -> 7L,
      "handle"  -> "alice_w",
      "address" -> Map("street" -> "123 Elm St", "city" -> "Springfield")
    )
    val profile = profileContract.validate(raw).value
    profile.userId shouldBe 7L
    profile.handle shouldBe "alice_w"
    profile.address.map(_.city) shouldBe Some("Springfield")
  }

  test("nested contract violation surfaces with correct path") {
    val raw: RawObject = Map(
      "userId"  -> 7L,
      "handle"  -> "alice_w",
      "address" -> Map("street" -> "", "city" -> "Springfield")
    )
    val result = profileContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path.toString.contains("address") &&
        v.path.toString.contains("street") &&
        v.code == ViolationCode.ConstraintFailed("nonEmpty")
    ) shouldBe true
  }

  test("nested empty map produces TypeMismatch for required inner fields") {
    val raw: RawObject = Map(
      "userId"  -> 7L,
      "handle"  -> "alice_w",
      "address" -> Map.empty[String, Any]
    )
    val result = profileContract.validate(raw).left.value
    result.violations.toList.exists {
      case Violation(p, _: ViolationCode.TypeMismatch, _) => p == FieldPath("address")
      case _                                              => false
    } shouldBe true
  }

  // ── mask / internal / reserved ────────────────────────────────────────────

  test("@masked replaces masked fields when sanitizing") {
    val raw: RawObject = Map(
      "id"       -> 1L,
      "name"     -> "Alice",
      "password" -> "topsecret"
    )
    userContract.sanitize(raw).get("password") shouldBe Some("***")
  }

  // S1.16 — Null supplied for an optional nested field is treated as
  // absent (None), not as a type error.
  test("null for an optional nested field is treated as absent") {
    val raw: RawObject = Map(
      "userId"  -> 7L,
      "handle"  -> "alice_w",
      "address" -> null
    )
    for profile <- profileContract.validate(raw)
    yield assertTrue(profile.address.isEmpty)
  }

  // ── nested collection ─────────────────────────────────────────────────────

  suite("nested collection contract")(

      test("validates each element of a List[T] nested contract field") {
        val raw: RawObject = Map(
          "id"    -> "ORD-1",
          "items" -> List(
            Map("name" -> "Widget", "qty" -> 2),
            Map("name" -> "Gadget", "qty" -> 1)
          )
        )
        for order <- orderContract.validate(raw)
        yield assertTrue(
          order.id              == "ORD-1",
          order.items.size      == 2,
          order.items.head.name == "Widget"
        )
      },

      test("surfaces violations from an invalid element with an indexed path") {
        val raw: RawObject = Map(
          "id"    -> "ORD-2",
          "items" -> List(
            Map("name" -> "Widget", "qty" -> 2),
            Map("name" -> "",       "qty" -> 0)
          )
        )
        for result <- orderContract.validate(raw).flip
        yield {
          val paths = result.violations.map(_.path.toString).toList
          assertTrue(
            paths.exists(p => p.contains("1") && p.contains("name")),
            paths.exists(p => p.contains("1") && p.contains("qty"))
          )
        }
      }
    )

    // ── Either[A, B] field ────────────────────────────────────────────────────

    suite("Either[A, B] field")(

      test("decodes Left branch from tagged wire format") {
        val raw: RawObject = Map(
          "label" -> "error-case",
          "value" -> Map("left" -> "something went wrong")
        )
        for h <- eitherContract.validate(raw)
        yield assertTrue(h.value == Left("something went wrong"))
      },

      test("decodes Right branch from tagged wire format") {
        val raw: RawObject = Map(
          "label" -> "ok-case",
          "value" -> Map("right" -> 42)
        )
        for h <- eitherContract.validate(raw)
        yield assertTrue(h.value == Right(42))
      },

      test("fails TypeMismatch when wire format is not a tagged map") {
        val raw: RawObject = Map("label" -> "bad", "value" -> "not-a-map")
        for result <- eitherContract.validate(raw).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("value") &&
            v.code.isInstanceOf[ViolationCode.TypeMismatch]
          )
        )
      }
    )

  // ── sanitize ──────────────────────────────────────────────────────────────

  suite("sanitize")(

      test("strips @internal fields from output") {
        val raw: RawObject = Map(
          "id"       -> 1L,
          "name"     -> "Alice",
          "email"    -> "alice@example.com",
          "age"      -> 30,
          "password" -> "s3cr3t"
        )
        val sanitized = userContract.sanitize(raw)
        assertTrue(!sanitized.contains("id"))
      },

      test("replaces @masked fields with the default mask string") {
        val raw: RawObject = Map(
          "id"       -> 1L,
          "name"     -> "Alice",
          "password" -> "s3cr3t"
        )
        val sanitized = userContract.sanitize(raw)
        assertTrue(sanitized.get("password") == Some("***"))
      },

      test("replaces @masked fields with a custom mask string") {
        val raw: RawObject = Map("key" -> "my-api-key", "secret" -> "my-secret")
        val sanitized = apiKeyContract.sanitize(raw)
        assertTrue(sanitized.get("secret") == Some("REDACTED"))
      },

      test("leaves non-masked, non-internal fields untouched") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice", "email" -> "alice@example.com")
        val sanitized = userContract.sanitize(raw)
        assertTrue(
          sanitized.get("name")  == Some("Alice"),
          sanitized.get("email") == Some("alice@example.com")
        )
      },

      test("open contract sanitize removes @internal fields but keeps extras") {
        val raw: RawObject = Map("title" -> "Test Doc", "extra_field" -> "kept")
        val sanitized = openDocContract.sanitize(raw)
        assertTrue(
          sanitized.get("title")       == Some("Test Doc"),
          sanitized.get("extra_field") == Some("kept")
        )
      },

      test("strips @internal field from the Left branch of an Either field") {
        val raw: RawObject = Map(
          "invoiceId" -> "INV-001",
          "payment"   -> Map(
            "left" -> Map(
              "cardNumber" -> "4111-1111-1111-1111",
              "cvv"        -> "123",
              "amount"     -> 49.99
            )
          )
        )
        val sanitized  = invoiceContract.sanitize(raw)
        val sanitizedJson = invoiceContract.sanitizeJson(raw)
        println(sanitizedJson)
        val leftBranch = sanitized("payment")
          .asInstanceOf[Map[String, Any]]("left")
          .asInstanceOf[Map[String, Any]]
        assertTrue(
          !leftBranch.contains("cvv"),
          leftBranch.contains("cardNumber"),
          leftBranch.contains("amount")
        )
      },

      test("masks @masked field from the Left branch of an Either field") {
        val raw: RawObject = Map(
          "invoiceId" -> "INV-002",
          "payment"   -> Map(
            "left" -> Map(
              "cardNumber" -> "4111-1111-1111-1111",
              "cvv"        -> "456",
              "amount"     -> 99.00
            )
          )
        )
        val sanitized  = invoiceContract.sanitize(raw)
        val leftBranch = sanitized("payment")
          .asInstanceOf[Map[String, Any]]("left")
          .asInstanceOf[Map[String, Any]]
        assertTrue(leftBranch("cardNumber") == "***")
      },

      test("strips @internal field from the Right branch of an Either field") {
        val raw: RawObject = Map(
          "invoiceId" -> "INV-003",
          "payment"   -> Map(
            "right" -> Map(
              "accountRef" -> "ACC-9876",
              "sortCode"   -> "12-34-56",
              "amount"     -> 200.00
            )
          )
        )
        val sanitized   = invoiceContract.sanitize(raw)
        val rightBranch = sanitized("payment")
          .asInstanceOf[Map[String, Any]]("right")
          .asInstanceOf[Map[String, Any]]
        assertTrue(
          !rightBranch.contains("sortCode"),
          rightBranch.contains("accountRef"),
          rightBranch.contains("amount")
        )
      },

      test("sanitize leaves non-sensitive fields in an Either branch untouched") {
        val raw: RawObject = Map(
          "invoiceId" -> "INV-004",
          "payment"   -> Map(
            "right" -> Map("accountRef" -> "ACC-1234", "sortCode" -> "99-88-77", "amount" -> 75.00)
          )
        )
        val sanitized   = invoiceContract.sanitize(raw)
        val rightBranch = sanitized("payment")
          .asInstanceOf[Map[String, Any]]("right")
          .asInstanceOf[Map[String, Any]]
        assertTrue(
          rightBranch("accountRef") == "ACC-1234",
          rightBranch("amount")     == 75.00
        )
      },

      test("Either field with primitive branches (no Contract) is a sanitize no-op") {
        val raw: RawObject = Map("label" -> "test", "value" -> Map("left" -> "hello"))
        val sanitized = eitherContract.sanitize(raw)
        assertTrue(sanitized == raw)
      },

      test("nested sanitize preserves nested Map values intact") {
        val rawProfile: RawObject = Map(
          "userId"  -> 7L,
          "handle"  -> "bob",
          "address" -> Map("street" -> "Main St", "city" -> "Townsville")
        )
        val sanitized = profileContract.sanitize(rawProfile)
        val addr = sanitized.get("address").map(_.asInstanceOf[Map[String, Any]])
        assertTrue(
          sanitized.get("handle")     == Some("bob"),
          sanitized.get("userId")     == Some(7L),
          addr.flatMap(_.get("city")) == Some("Townsville")
        )
      },

      // S6.1 — Sanitizing an empty raw object is a no-op.
      test("sanitize of an empty raw object returns an empty map") {
        assertTrue(userContract.sanitize(Map.empty) == Map.empty)
      },

      // S6.8 — When an optional nested field is absent, sanitize must not
      // introduce a phantom key for it in the output.
      test("sanitize does not add phantom key when optional nested field is absent") {
        val raw: RawObject = Map("userId" -> 7L, "handle" -> "alice")
        val sanitized = profileContract.sanitize(raw)
        assertTrue(!sanitized.contains("address"))
      },

      // S6.10 — sanitize recurses into each element of a List[T] field and
      // strips @internal fields from every element.
      test("sanitize strips @internal from each element of a List[T] field") {
        val raw: RawObject = Map(
          "cartId" -> "C-001",
          "items"  -> List(
            Map("productId" -> "P-1", "costPrice" -> 5.0, "quantity" -> 2),
            Map("productId" -> "P-2", "costPrice" -> 8.5, "quantity" -> 1)
          )
        )
        val sanitized = cartContract.sanitize(raw)
        val items = sanitized("items").asInstanceOf[List[Map[String, Any]]]
        assertTrue(
          !items(0).contains("costPrice"),          // @internal stripped
          !items(1).contains("costPrice"),
          items(0).get("productId") == Some("P-1"), // non-sensitive kept
          items(0).get("quantity")  == Some(2)
        )
      },

      // S6.11 — When no element in a List[T] field has @internal or @masked
      // annotations, sanitize leaves the list completely unchanged.
      test("sanitize leaves List[T] elements unchanged when no sensitive annotations present") {
        val raw: RawObject = Map(
          "id"    -> "ORD-1",
          "items" -> List(
            Map("name" -> "Widget", "qty" -> 2),
            Map("name" -> "Gadget", "qty" -> 1)
          )
        )
        val sanitized = orderContract.sanitize(raw)
        assertTrue(sanitized == raw)
      }
    )

    // ── validatePatch ─────────────────────────────────────────────────────────

    suite("validatePatch")(

      test("accepts a valid patch and merges it with current state") {
        val current: RawObject = Map("id" -> 1L, "name" -> "Alice", "age" -> 25)
        val patch:   RawObject = Map("name" -> "Alicia", "age" -> 26)
        for user <- userContract.validatePatch(current, patch)
        yield assertTrue(user.name == "Alicia", user.age == 26, user.id == 1L)
      },

      test("rejects patches containing @immutable fields") {
        val current: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val patch:   RawObject = Map("id" -> 99L)
        for result <- userContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(_.code == ViolationCode.ImmutableField)
        )
      },

      test("validates constraint annotations on patched values") {
        val current: RawObject = Map("id" -> 1L, "name" -> "Alice")
        val patch:   RawObject = Map("name" -> "")
        for result <- userContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("name") &&
            v.code == ViolationCode.ConstraintFailed("nonEmpty")
          )
        )
      },

      test("deep-merges nested Map values instead of replacing them") {
        val current: RawObject = Map(
          "userId"  -> 1L,
          "handle"  -> "alice",
          "address" -> Map("street" -> "123 Elm", "city" -> "Oldtown")
        )
        val patch: RawObject = Map("address" -> Map("street" -> "123 Elm", "city" -> "Newtown"))
        for profile <- profileContract.validatePatch(current, patch)
        yield assertTrue(
          profile.address.map(_.city)   == Some("Newtown"),
          profile.address.map(_.street) == Some("123 Elm")
        )
      }
    )

  // ── @include ──────────────────────────────────────────────────────────────

    suite("@include")(

      test("validates flat wire format and constructs nested type") {
        val raw: RawObject = Map(
          "title"     -> "Hello World",
          "body"      -> "content",
          "createdAt" -> 1000L,
          "updatedAt" -> 2000L
        )
        for doc <- documentContract.validate(raw)
        yield assertTrue(
          doc.title                == "Hello World",
          doc.body                 == Some("content"),
          doc.timestamps.createdAt == 1000L,
          doc.timestamps.updatedAt == 2000L
        )
      },

      test("included fields with defaults can be omitted") {
        val raw: RawObject = Map("title" -> "Minimal", "createdAt" -> 500L)
        for doc <- documentContract.validate(raw)
        yield assertTrue(
          doc.timestamps.createdAt == 500L,
          doc.timestamps.updatedAt == 0L
        )
      },

      test("included required field missing produces ExpectedMissing violation") {
        val raw: RawObject = Map("title" -> "No Timestamps")
        for result <- documentContract.validate(raw).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("createdAt") &&
            v.code == ViolationCode.ExpectedMissing
          )
        )
      },

      test("@immutable on included field is enforced in validatePatch") {
        val current: RawObject = Map(
          "title" -> "Original", "createdAt" -> 100L, "updatedAt" -> 200L
        )
        val patch: RawObject = Map("createdAt" -> 999L)
        for result <- documentContract.validatePatch(current, patch).flip
        yield assertTrue(
          result.violations.toList.exists(v =>
            v.path == FieldPath("createdAt") &&
            v.code == ViolationCode.ImmutableField
          )
        )
      },

      test("toRaw flattens @include fields back to wire format") {
        val raw: RawObject = Map(
          "title"     -> "Round-trip",
          "createdAt" -> 111L,
          "updatedAt" -> 222L
        )
        for doc <- documentContract.validate(raw)
        yield {
          val out = documentContract.toRaw(doc)
          assertTrue(
            out.get("title")     == Some("Round-trip"),
            out.get("createdAt") == Some(111L),
            out.get("updatedAt") == Some(222L),
            !out.contains("timestamps")
          )
        }
      },

      test("fieldMetas contains the flattened inner fields, not the @include param") {
        val metas = documentContract.fieldMetas.map(_.name)
        assertTrue(
          metas.contains("title"),
          metas.contains("createdAt"),
          metas.contains("updatedAt"),
          !metas.contains("timestamps")
        )
      }
    )

    // ── @discriminator ────────────────────────────────────────────────────────

    suite("@discriminator")(

      test("decodes the Left branch using the discriminator key") {
        val raw: RawObject = Map(
          "ownerId" -> "u1",
          "pet"     -> Map("kind" -> "cat", "name" -> "Whiskers")
        )
        for ph <- petHolderContract.validate(raw)
        yield assertTrue(ph.ownerId == "u1", ph.pet == Left(Cat("Whiskers")))
      },

      test("decodes the Right branch using the discriminator key") {
        val raw: RawObject = Map(
          "ownerId" -> "u2",
          "pet"     -> Map("kind" -> "dog", "name" -> "Rex", "age" -> 3)
        )
        for ph <- petHolderContract.validate(raw)
        yield assertTrue(ph.pet == Right(Dog("Rex", 3)))
      },

      test("fails TypeMismatch when discriminator key is missing") {
        val raw: RawObject = Map(
          "ownerId" -> "u3",
          "pet"     -> Map("name" -> "Buddy", "age" -> 2)
        )
        for result <- petHolderContract.validate(raw).flip
        yield assertTrue(
          result.violations.toList.exists {
            case Violation(p, _: ViolationCode.TypeMismatch, _) => p == FieldPath("pet")
            case _ => false
          }
        )
      },

      test("fails TypeMismatch when discriminator value is unknown") {
        val raw: RawObject = Map(
          "ownerId" -> "u4",
          "pet"     -> Map("kind" -> "fish", "name" -> "Nemo")
        )
        for result <- petHolderContract.validate(raw).flip
        yield assertTrue(
          result.violations.toList.exists {
            case Violation(p, _: ViolationCode.TypeMismatch, _) => p == FieldPath("pet")
            case _ => false
          }
        )
      },

      test("discriminator key is stripped — inner RawDecoder only sees its own fields") {
        val raw: RawObject = Map(
          "ownerId" -> "u5",
          "pet"     -> Map("kind" -> "dog", "name" -> "Max", "age" -> 5)
        )
        for ph <- petHolderContract.validate(raw)
        yield assertTrue(ph.pet == Right(Dog("Max", 5)))
      },

      test("toRaw round-trips discriminator-tagged Either for petHolder") {
        val ph       = PetHolder("u1", Left(Cat("Whiskers")))
        val raw      = petHolderContract.toRaw(ph)
        val petField = raw("pet").asInstanceOf[Map[String, Any]]
        assertTrue(
          petField.get("kind").contains("cat"),
          petField.get("name").contains("Whiskers")
        )
      },

      test("round-trips left envelope for PetHolder") {
        val raw: RawObject = Map(
          "ownerId" -> "u5",
          "pet"     -> Map("kind" -> "dog", "name" -> "Max", "age" -> 5)
        )
        val validated = petHolderContract.validate(raw).toOption.get
        val roundTrip = petHolderContract.toRaw(validated)
        assertTrue(roundTrip == raw)
      }
    )

    suite("toRaw default Either envelope (no discriminator)")(
      test("round-trips left envelope for Invoice") {
        val raw: RawObject = Map(
          "invoiceId" -> "INV-001",
          "payment"   -> Map(
            "left" -> Map(
              "cardNumber"  -> "4111-1111-1111-1111",
              "cvv"         -> "123",
              "amount"      -> 49.99
            )
          )
        )
        val validated = invoiceContract.validate(raw).toOption.get
        val roundTrip = invoiceContract.toRaw(validated)
        assertTrue(roundTrip == raw)
      }
    )
