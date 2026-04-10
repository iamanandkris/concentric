package io.dsentric

import io.dsentric.annotations.*
import io.dsentric.validators.NoWhitespaceValidator

final class ConstraintSpec extends SpecBase:

  // Product uses @validateWith(Array(classOf[X])).
  @contract
  case class Product(
    @nonEmpty                                            name:         String,
    @email                                               contactEmail: Option[String],
    @pattern("^[A-Z]{2}-\\d+$")                          code:         Option[String],
    @validateWith(Array(classOf[NoWhitespaceValidator])) slug:         String
  )
  given productContract: Contract[Product] = Contract.derived[Product]

  // ── @nonEmpty / @minLength / @maxLength ───────────────────────────────────

  test("@nonEmpty rejects an empty string") {
    val raw = Map("id" -> 1L, "name" -> "")
    val result = userContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("name") && v.code == ViolationCode.ConstraintFailed("nonEmpty")
    ) shouldBe true
  }

  test("@maxLength rejects a string that is too long") {
    val raw = Map("id" -> 1L, "name" -> ("x" * 101))
    val result = userContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("name") && v.code == ViolationCode.ConstraintFailed("maxLength")
    ) shouldBe true
  }

  test("@minLength rejects a string that is too short") {
    val raw = Map("key" -> "abc", "secret" -> "xyz")
    val result = apiKeyContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("key") && v.code == ViolationCode.ConstraintFailed("minLength")
    ) shouldBe true
  }

  test("@minLength accepts a string exactly at the minimum") {
    val raw = Map("key" -> "12345678", "secret" -> "xyz")
    val k = apiKeyContract.validate(raw).value
    k.key shouldBe "12345678"
  }

  // ── @min / @max ───────────────────────────────────────────────────────────

  test("@min rejects a value below the minimum") {
    val raw = Map("id" -> 1L, "name" -> "Dave", "age" -> -1)
    val result = userContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("age") && v.code == ViolationCode.ConstraintFailed("min")
    ) shouldBe true
  }

  test("@max rejects a value above the maximum") {
    val raw = Map("id" -> 1L, "name" -> "Eve", "age" -> 200)
    val result = userContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("age") && v.code == ViolationCode.ConstraintFailed("max")
    ) shouldBe true
  }

  test("@min/@max accumulate multiple violations") {
    val raw = Map("id" -> 1L, "name" -> "", "age" -> 200)
    val result = userContract.validate(raw).left.value
    result.violations.size should be >= 2
  }

  // ── @email ────────────────────────────────────────────────────────────────

  test("@email accepts a valid address") {
    val raw = Map("name" -> "Widget", "contactEmail" -> "support@example.com", "slug" -> "widget-pro")
    val p = productContract.validate(raw).value
    p.contactEmail shouldBe Some("support@example.com")
  }

  test("@email rejects malformed address") {
    val raw = Map("name" -> "Widget", "contactEmail" -> "not-an-email", "slug" -> "widget-pro")
    val result = productContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("contactEmail") && v.code == ViolationCode.ConstraintFailed("email")
    ) shouldBe true
  }

  test("@email allows absent optional field") {
    val raw = Map("name" -> "Widget", "slug" -> "widget-pro")
    val p = productContract.validate(raw).value
    p.contactEmail shouldBe None
  }

  // ── @pattern ──────────────────────────────────────────────────────────────

  test("@pattern accepts matching value") {
    val raw = Map("name" -> "Widget", "code" -> "AB-42", "slug" -> "widget-pro")
    productContract.validate(raw).value.code shouldBe Some("AB-42")
  }

  test("@pattern rejects non-matching value") {
    val raw = Map("name" -> "Widget", "code" -> "ab-42", "slug" -> "widget-pro")
    val result = productContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("code") && v.code == ViolationCode.ConstraintFailed("pattern")
    ) shouldBe true
  }

  test("@pattern allows absent optional field") {
    val raw = Map("name" -> "Widget", "slug" -> "widget-pro")
    productContract.validate(raw).value.code shouldBe None
  }

  // ── @validateWith ─────────────────────────────────────────────────────────

  test("@validateWith accepts valid value") {
    val raw = Map("name" -> "Widget", "slug" -> "widget-pro")
    productContract.validate(raw).value.slug shouldBe "widget-pro"
  }

  test("@validateWith rejects invalid value") {
    val raw = Map("name" -> "Widget", "slug" -> "has spaces")
    val result = productContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("slug") && v.code == ViolationCode.ConstraintFailed("validateWith")
    ) shouldBe true
  }

  test("@validateWith preserves custom error message") {
    val raw = Map("name" -> "Widget", "slug" -> "has\ttab")
    val result = productContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("slug") && v.message.contains("whitespace")
    ) shouldBe true
  }

  // ── @reserved ─────────────────────────────────────────────────────────────

  test("@reserved rejects provided value in validate") {
    val raw = Map("trackingId" -> "CUSTOM-001", "title" -> "Fix login bug")
    val result = ticketContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("trackingId") && v.code == ViolationCode.ReservedField
    ) shouldBe true
  }

  test("@reserved absent field uses default") {
    val raw = Map("title" -> "Fix login bug")
    val ticket = ticketContract.validate(raw).value
    ticket.trackingId shouldBe "PENDING"
    ticket.title shouldBe "Fix login bug"
  }

  test("@reserved rejects value in validatePatch") {
    val current = Map("title" -> "Fix login bug")
    val patch   = Map("trackingId" -> "CUSTOM-001")
    val result  = ticketContract.validatePatch(current, patch).left.value
    result.violations.toList.exists(_.code == ViolationCode.ReservedField) shouldBe true
  }

  // ── @url ──────────────────────────────────────────────────────────────────

  test("@url accepts http URL") {
    webLinkContract.validate(Map("href" -> "http://example.com/path?q=1")).value.href shouldBe
      "http://example.com/path?q=1"
  }

  test("@url accepts https URL") {
    webLinkContract.validate(Map("href" -> "https://api.example.com/v1/items")).value.href shouldBe
      "https://api.example.com/v1/items"
  }

  test("@url rejects non-URL string") {
    val result = webLinkContract.validate(Map("href" -> "not-a-url")).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("href") && v.code == ViolationCode.ConstraintFailed("url")
    ) shouldBe true
  }

  test("@url rejects ftp URL") {
    val result = webLinkContract.validate(Map("href" -> "ftp://files.example.com/file.txt")).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("href") && v.code == ViolationCode.ConstraintFailed("url")
    ) shouldBe true
  }

  // ── @uuid ─────────────────────────────────────────────────────────────────

  test("@uuid accepts valid UUID") {
    val raw = Map("id" -> "550e8400-e29b-41d4-a716-446655440000", "name" -> "Thing")
    resourceContract.validate(raw).value.id shouldBe "550e8400-e29b-41d4-a716-446655440000"
  }

  test("@uuid accepts uppercase UUID") {
    val raw = Map("id" -> "550E8400-E29B-41D4-A716-446655440000", "name" -> "Thing")
    resourceContract.validate(raw).value.id shouldBe "550E8400-E29B-41D4-A716-446655440000"
  }

  test("@uuid rejects short string") {
    val result = resourceContract.validate(Map("id" -> "not-a-uuid", "name" -> "Thing")).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("id") && v.code == ViolationCode.ConstraintFailed("uuid")
    ) shouldBe true
  }

  test("@uuid rejects wrong group lengths") {
    val result = resourceContract.validate(
      Map("id" -> "550e8400-e29b-41d4-a716-44665544000", "name" -> "Thing")
    ).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("id") && v.code == ViolationCode.ConstraintFailed("uuid")
    ) shouldBe true
  }

  // ── @future ───────────────────────────────────────────────────────────────

  test("@future accepts future timestamp") {
    val futureTs = System.currentTimeMillis() + 86_400_000L
    val pastTs   = System.currentTimeMillis() - 86_400_000L
    val raw = Map("title" -> "Meeting", "startsAt" -> futureTs, "createdAt" -> pastTs)
    scheduledEventContract.validate(raw).value.startsAt shouldBe futureTs
  }

  test("@future rejects past timestamp") {
    val pastTs1 = System.currentTimeMillis() - 86_400_000L
    val pastTs2 = System.currentTimeMillis() - 172_800_000L
    val raw = Map("title" -> "Meeting", "startsAt" -> pastTs1, "createdAt" -> pastTs2)
    val result = scheduledEventContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("startsAt") && v.code == ViolationCode.ConstraintFailed("future")
    ) shouldBe true
  }

  // ── @past ─────────────────────────────────────────────────────────────────

  test("@past accepts past timestamp") {
    val futureTs = System.currentTimeMillis() + 86_400_000L
    val pastTs   = System.currentTimeMillis() - 86_400_000L
    val raw = Map("title" -> "Meeting", "startsAt" -> futureTs, "createdAt" -> pastTs)
    scheduledEventContract.validate(raw).value.createdAt shouldBe pastTs
  }

  test("@past rejects future timestamp") {
    val futureTs1 = System.currentTimeMillis() + 86_400_000L
    val futureTs2 = System.currentTimeMillis() + 172_800_000L
    val raw = Map("title" -> "Meeting", "startsAt" -> futureTs1, "createdAt" -> futureTs2)
    val result = scheduledEventContract.validate(raw).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("createdAt") && v.code == ViolationCode.ConstraintFailed("past")
    ) shouldBe true
  }

  // ── @positive ─────────────────────────────────────────────────────────────

  test("@positive accepts positive Double") {
    val raw = Map("value" -> 3.14, "count" -> 1)
    measurementContract.validate(raw).value.value shouldBe 3.14
  }

  test("@positive accepts positive Int") {
    val raw = Map("value" -> 1.0, "count" -> 42)
    measurementContract.validate(raw).value.count shouldBe 42
  }

  test("@positive rejects zero") {
    val result = measurementContract.validate(Map("value" -> 0.0, "count" -> 1)).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("value") && v.code == ViolationCode.ConstraintFailed("positive")
    ) shouldBe true
  }

  test("@positive rejects negative") {
    val result = measurementContract.validate(Map("value" -> 1.0, "count" -> -5)).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("count") && v.code == ViolationCode.ConstraintFailed("positive")
    ) shouldBe true
  }

  // ── @multipleOf ───────────────────────────────────────────────────────────

  test("@multipleOf accepts Double multiple") {
    val raw = Map("amount" -> 19.99, "quantity" -> 5)
    paymentContract.validate(raw).value.amount shouldBe 19.99
  }

  test("@multipleOf accepts Int multiple") {
    val raw = Map("amount" -> 10.00, "quantity" -> 15)
    paymentContract.validate(raw).value.quantity shouldBe 15
  }

  test("@multipleOf rejects non-multiple double") {
    val result = paymentContract.validate(Map("amount" -> 19.999, "quantity" -> 5)).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("amount") && v.code == ViolationCode.ConstraintFailed("multipleOf")
    ) shouldBe true
  }

  test("@multipleOf rejects non-multiple int") {
    val result = paymentContract.validate(Map("amount" -> 10.00, "quantity" -> 7)).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("quantity") && v.code == ViolationCode.ConstraintFailed("multipleOf")
    ) shouldBe true
  }

  test("@positive combines with @multipleOf") {
    val result = paymentContract.validate(Map("amount" -> -0.01, "quantity" -> 5)).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("amount") && v.code == ViolationCode.ConstraintFailed("positive")
    ) shouldBe true
  }
