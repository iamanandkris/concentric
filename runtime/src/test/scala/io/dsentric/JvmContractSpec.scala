package io.dsentric

import java.util.{Map => JMap, HashMap => JHashMap}
import scala.jdk.CollectionConverters.*

/**
 * Test suite for [[JvmContract]] — the synchronous, reflection-based contract
 * API for Java and Kotlin consumers.
 *
 * All test models are Java records (Java 16+) defined in src/test/java,
 * so annotation reflection goes through genuine Java bytecode without any
 * Scala macro involvement.  Record accessor methods follow the naming
 * convention `field()` (no `get` prefix).
 */
final class JvmContractSpec extends SpecBase:

  // ── Optional-field contract ───────────────────────────────────────────────

  val profileContract: JvmContract[TestJvmProfile] = JvmContract.of(
    classOf[TestJvmProfile],
    fields =>
      new TestJvmProfile(
        fields.get("username").asInstanceOf[String],
        fields.get("bio").asInstanceOf[java.util.Optional[String]],
        fields.get("score").asInstanceOf[java.util.Optional[Integer]]
      )
  )

  // ── Contract instances ────────────────────────────────────────────────────

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

  val ticketContract: JvmContract[TestJvmTicket] = JvmContract.of(
    classOf[TestJvmTicket],
    fields =>
      new TestJvmTicket(
        fields.get("trackingId").asInstanceOf[String],
        fields.get("title").asInstanceOf[String]
      )
  )

  val openDocContract: JvmContract[TestJvmOpenDoc] = JvmContract.ofRecord(classOf[TestJvmOpenDoc])
  val documentContract: JvmContract[TestJvmDocument] = JvmContract.ofRecord(classOf[TestJvmDocument])
  val contactContract: JvmContract[TestJvmContact] = JvmContract.ofRecord(classOf[TestJvmContact])
  val bookingRuleContract: JvmContract[TestJvmBooking] = JvmContract.ofRecord(classOf[TestJvmBooking])
  val eventContract: JvmContract[TestJvmEvent] = JvmContract.ofRecord(classOf[TestJvmEvent])
  val memberContract: JvmContract[TestJvmMember] = JvmContract.ofRecord(classOf[TestJvmMember])
  val productContract: JvmContract[TestJvmProduct] = JvmContract.ofRecord(classOf[TestJvmProduct])
  val optionalProductContract: JvmContract[TestJvmOptionalProduct] = JvmContract.ofRecord(classOf[TestJvmOptionalProduct])
  val orderContract: JvmContract[TestJvmOrder] = JvmContract.ofRecord(classOf[TestJvmOrder])
  val javaProductPayloadContract: JvmContract[TestJvmJavaProductPayload] = JvmContract.ofRecord(classOf[TestJvmJavaProductPayload])
  val javaUserPayloadContract: JvmContract[TestJvmJavaUserPayload] = JvmContract.ofRecord(classOf[TestJvmJavaUserPayload])
  val javaOrderPayloadContract: JvmContract[TestJvmJavaOrderPayload] = JvmContract.ofRecord(classOf[TestJvmJavaOrderPayload])
  val nullableProfileContract: JvmContract[TestJvmNullableProfile] = JvmContract.ofRecord(classOf[TestJvmNullableProfile])
  val kotlinOptionalUserContract: JvmContract[TestKotlinOptionalUserPayload] = JvmContract.ofPrimary(classOf[TestKotlinOptionalUserPayload])
  val kotlinOptionalOrderContract: JvmContract[TestKotlinOptionalOrderPayload] = JvmContract.ofPrimary(classOf[TestKotlinOptionalOrderPayload])

  // ── Helper ────────────────────────────────────────────────────────────────

  def jmap(pairs: (String, AnyRef)*): JMap[String, AnyRef] =
    val m = new JHashMap[String, AnyRef]()
    pairs.foreach { (k, v) => m.put(k, v) }
    m

  // ── Specs ─────────────────────────────────────────────────────────────────

  def spec: Unit = suite("JvmContractSpec")(

    // ── validate — happy path ─────────────────────────────────────────────

    suite("validate — happy path")(

      test("valid raw map produces a correctly constructed T") {
        val raw = jmap(
          "id"       -> Long.box(1L),
          "name"     -> "Alice",
          "age"      -> Int.box(30),
          "email"    -> "alice@example.com",
          "password" -> "s3cr3t"
        )
        val result = userContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.id()   == 1L,
          result.getValue.get.name() == "Alice",
          result.getValue.get.age()  == 30
        )
      },

      test("getErrors is empty on success") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "Bob", "age" -> Int.box(25),
                       "email" -> "bob@example.com", "password" -> "pw")
        assertTrue(userContract.validate(raw).getErrors.isEmpty)
      }
    ),

    // ── validate — constraint violations ─────────────────────────────────

    suite("validate — constraint violations")(

      test("@nonEmpty violation on empty name") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "", "age" -> Int.box(25),
                       "email" -> "a@b.com", "password" -> "pw")
        val result = userContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "name" && v.code == "CONSTRAINT(nonEmpty)"
          )
        )
      },

      test("@maxLength violation on name > 50 chars") {
        val raw = jmap("id" -> Long.box(1L), "name" -> ("A" * 51), "age" -> Int.box(25),
                       "email" -> "a@b.com", "password" -> "pw")
        val result = userContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(_.code == "CONSTRAINT(maxLength)")
        )
      },

      test("@min violation on negative age") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(-1),
                       "email" -> "a@b.com", "password" -> "pw")
        val result = userContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "age" && v.code == "CONSTRAINT(min)"
          )
        )
      },

      test("@max violation on age > 150") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(151),
                       "email" -> "a@b.com", "password" -> "pw")
        assertTrue(!userContract.validate(raw).isValid)
      },

      test("@email violation on malformed email") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(25),
                       "email" -> "not-an-email", "password" -> "pw")
        val result = userContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "email" && v.code == "CONSTRAINT(email)"
          )
        )
      },

      test("missing required field produces MISSING violation") {
        // 'name' omitted from the map
        val raw = jmap("id" -> Long.box(1L), "age" -> Int.box(25),
                       "email" -> "a@b.com", "password" -> "pw")
        val result = userContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "name" && v.code == "MISSING"
          )
        )
      },

      test("wrong type produces TYPE_MISMATCH violation") {
        // age should be int, but a String is supplied
        val raw = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> "thirty",
                       "email" -> "a@b.com", "password" -> "pw")
        val result = userContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "age" && v.code.startsWith("TYPE_MISMATCH")
          )
        )
      },

      test("multiple violations are all accumulated") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "", "age" -> Int.box(-5),
                       "email" -> "bad", "password" -> "pw")
        val result = userContract.validate(raw)
        assertTrue(!result.isValid, result.getErrors.size >= 3)
      }
    ),

    // ── validate — policy annotations ─────────────────────────────────────

    suite("validate — policy annotations")(

      test("@reserved field in validate produces RESERVED violation") {
        val raw = jmap("trackingId" -> "TKT-001", "title" -> "Fix bug")
        val result = ticketContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "trackingId" && v.code == "RESERVED"
          )
        )
      }
    ),

    // ── validatePatch ─────────────────────────────────────────────────────

    suite("validatePatch")(

      test("patch on a non-@immutable field succeeds") {
        val current = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                           "email" -> "a@b.com", "password" -> "pw")
        val patch   = jmap("name" -> "Alicia")
        val result  = userContract.validatePatch(current, patch)
        assertTrue(result.isValid, result.getValue.get.name() == "Alicia")
      },

      test("patch on @immutable field produces IMMUTABLE violation") {
        val current = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                           "email" -> "a@b.com", "password" -> "pw")
        val patch   = jmap("id" -> Long.box(99L))
        val result  = userContract.validatePatch(current, patch)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "id" && v.code == "IMMUTABLE"
          )
        )
      },

      test("patch on @reserved field produces RESERVED violation") {
        val current = jmap("title" -> "Old title", "trackingId" -> "TKT-001")
        val patch   = jmap("trackingId" -> "CUSTOM-001")
        val result  = ticketContract.validatePatch(current, patch)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(_.code == "RESERVED")
        )
      },

      test("@reserved field in currentRaw is trusted, not re-checked") {
        // trackingId is system-assigned in currentRaw — patch only changes title
        val current = jmap("title" -> "Old title", "trackingId" -> "TKT-001")
        val patch   = jmap("title" -> "New title")
        val result  = ticketContract.validatePatch(current, patch)
        assertTrue(result.isValid, result.getValue.get.title() == "New title")
      },

      test("currentRaw fields that violate a constraint are trusted (not re-validated)") {
        // age = -1 violates @min(0) but lives in currentRaw; patch doesn't touch it
        val current = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(-1),
                           "email" -> "a@b.com", "password" -> "pw")
        val patch   = jmap("name" -> "Alice Updated")
        val result  = userContract.validatePatch(current, patch)
        assertTrue(result.isValid, result.getValue.get.age() == -1)
      },

      test("patch introducing a new constraint violation is rejected") {
        val current = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                           "email" -> "a@b.com", "password" -> "pw")
        val patch   = jmap("age" -> Int.box(-5))
        val result  = userContract.validatePatch(current, patch)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "age" && v.code == "CONSTRAINT(min)"
          )
        )
      },

      test("JvmPatch builder can be passed directly to validatePatch") {
        val current = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                           "email" -> "a@b.com", "password" -> "pw")
        val patch = JvmPatch.empty()
          .set("name", "Alicia")
          .set("age", Int.box(31))
        val result = userContract.validatePatch(current, patch)
        assertTrue(
          result.isValid,
          result.getValue.get.name() == "Alicia",
          result.getValue.get.age() == 31
        )
      }
    ),

    // ── sanitize ─────────────────────────────────────────────────────────

    suite("sanitize")(

      test("@masked field is replaced with '***'") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                       "email" -> "a@b.com", "password" -> "s3cr3t")
        val sanitized = userContract.sanitize(raw)
        assertTrue(
          sanitized.get("password") == "***",
          sanitized.get("name")     == "Alice"
        )
      },

      test("sanitize does not crash when an optional field is absent") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "Bob", "age" -> Int.box(25),
                       "email" -> "b@b.com")
        // password not in raw — sanitize should not throw
        val sanitized = userContract.sanitize(raw)
        assertTrue(!sanitized.containsKey("password"))
      }
    ),

    // ── collectViolations ────────────────────────────────────────────────

    suite("collectViolations")(

      test("returns empty list for valid input") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                       "email" -> "a@b.com", "password" -> "pw")
        assertTrue(userContract.collectViolations(raw).isEmpty)
      },

      test("returns violations without constructing T") {
        val raw = jmap("id" -> Long.box(1L), "name" -> "", "age" -> Int.box(-1),
                       "email" -> "bad", "password" -> "pw")
        assertTrue(userContract.collectViolations(raw).size >= 3)
      }
    ),

    // ── JvmContractDeriver metadata ──────────────────────────────────────

    suite("JvmContractDeriver — field metadata")(

      test("derives the correct number of fields from the Java record") {
        assertTrue(userContract.fieldMetas.length == 5)
      },

      test("@immutable flag is read from the record component annotation") {
        val idMeta = userContract.fieldMetas.find(_.name == "id")
        assertTrue(idMeta.exists(_.isImmutable))
      },

      test("@nonEmpty flag is read from the record component annotation") {
        val nameMeta = userContract.fieldMetas.find(_.name == "name")
        assertTrue(nameMeta.exists(_.isNonEmpty))
      },

      test("@min and @max are read from the record component annotation") {
        val ageMeta = userContract.fieldMetas.find(_.name == "age")
        assertTrue(
          ageMeta.exists(_.min.contains(0L)),
          ageMeta.exists(_.max.contains(150L))
        )
      },

      test("@email flag is read from the record component annotation") {
        val emailMeta = userContract.fieldMetas.find(_.name == "email")
        assertTrue(emailMeta.exists(_.isEmail))
      },

      test("@masked flag is read from the record component annotation") {
        val pwMeta = userContract.fieldMetas.find(_.name == "password")
        assertTrue(pwMeta.exists(_.masked.isDefined))
      },

      test("@reserved flag is read for TestJvmTicket") {
        val trackMeta = ticketContract.fieldMetas.find(_.name == "trackingId")
        assertTrue(trackMeta.exists(_.isReserved))
      }
    ),

    // ── Optional<T> fields ────────────────────────────────────────────────

    suite("Optional<T> fields")(

      test("present optional field arrives as Optional.of(value) in constructed T") {
        val raw = jmap("username" -> "alice", "bio" -> "Loves Scala",
                       "score" -> Int.box(42))
        val result = profileContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.bio()   == java.util.Optional.of("Loves Scala"),
          result.getValue.get.score() == java.util.Optional.of(42)
        )
      },

      test("absent optional field arrives as Optional.empty() in constructed T") {
        // bio and score both omitted — should succeed with Optional.empty()
        val raw = jmap("username" -> "alice")
        val result = profileContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.bio()   == java.util.Optional.empty[String](),
          result.getValue.get.score() == java.util.Optional.empty[Integer]()
        )
      },

      test("null for an optional field is treated as Optional.empty()") {
        val raw = jmap("username" -> "alice", "bio" -> null)
        val result = profileContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.bio() == java.util.Optional.empty[String]()
        )
      },

      test("constraint on optional field is enforced when the field is present") {
        // score must be >= 0; supplying -1 should fail even though it's optional
        val raw = jmap("username" -> "alice", "score" -> Int.box(-1))
        val result = profileContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "score" && v.code == "CONSTRAINT(min)"
          )
        )
      },

      test("constraint on optional field is NOT enforced when the field is absent") {
        // score omitted entirely — @min(0) should not fire
        val raw = jmap("username" -> "alice")
        assertTrue(profileContract.validate(raw).isValid)
      },

      test("absent optional field is isOptional=true in FieldMeta") {
        val bioMeta = profileContract.fieldMetas.find(_.name == "bio")
        assertTrue(bioMeta.exists(_.isOptional))
      },

      test("required field is still required when optional fields are absent") {
        val raw = jmap("bio" -> "no username here")
        val result = profileContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "username" && v.code == "MISSING"
          )
        )
      }
    ),

    // ── validatePartial ───────────────────────────────────────────────────

    suite("validatePartial")(

      test("all fields valid → empty violation list") {
        val raw = jmap("name" -> "Alice", "age" -> Int.box(30))
        assertTrue(userContract.validatePartial(raw).isEmpty)
      },

      test("absent required field does NOT produce MISSING violation") {
        // Only 'age' present; 'name', 'email', 'password' all missing — should be fine
        val raw = jmap("age" -> Int.box(25))
        assertTrue(userContract.validatePartial(raw).isEmpty)
      },

      test("constraint violation on a present field IS reported") {
        val raw = jmap("name" -> "")   // @nonEmpty fails
        val violations = userContract.validatePartial(raw).asScala
        assertTrue(
          violations.nonEmpty,
          violations.toList.exists(v => v.path == "name" && v.code == "CONSTRAINT(nonEmpty)")
        )
      },

      test("type mismatch on a present field IS reported") {
        val raw = jmap("age" -> "not-a-number")
        val violations = userContract.validatePartial(raw).asScala
        assertTrue(
          violations.nonEmpty,
          violations.toList.exists(v => v.path == "age" && v.code.startsWith("TYPE_MISMATCH"))
        )
      },

      test("@immutable field in a partial map is ACCEPTED (only rejected in validatePatch)") {
        // @immutable is enforced only during patch operations — the field can
        // legitimately be set on initial creation via validate or validatePartial.
        val raw = jmap("id" -> Long.box(99L))
        val violations = userContract.validatePartial(raw).asScala
        assertTrue(!violations.toList.exists(v => v.path == "id"))
      },

      test("multiple violations on present fields are all accumulated") {
        val raw = jmap("name" -> "", "age" -> Int.box(-1), "email" -> "bad")
        val violations = userContract.validatePartial(raw).asScala
        assertTrue(violations.size >= 3)
      },

      test("empty partial map produces no violations") {
        assertTrue(userContract.validatePartial(jmap()).isEmpty)
      }
    ),

    suite("@include")(

      test("validates flat wire format and constructs nested type") {
        val raw = jmap(
          "title" -> "Hello World",
          "body" -> "content",
          "createdAt" -> Long.box(1000L),
          "updatedAt" -> Long.box(2000L)
        )
        val result = documentContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.title() == "Hello World",
          result.getValue.get.body() == "content",
          result.getValue.get.timestamps().createdAt() == 1000L,
          result.getValue.get.timestamps().updatedAt() == 2000L
        )
      },

      test("included required field missing produces MISSING violation") {
        val raw = jmap("title" -> "No Timestamps")
        val result = documentContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v => v.path == "createdAt" && v.code == "MISSING")
        )
      },

      test("@immutable on included field is enforced in validatePatch") {
        val current = jmap(
          "title" -> "Original",
          "createdAt" -> Long.box(100L),
          "updatedAt" -> Long.box(200L)
        )
        val patch = jmap("createdAt" -> Long.box(999L))
        val result = documentContract.validatePatch(current, patch)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v => v.path == "createdAt" && v.code == "IMMUTABLE")
        )
      },

      test("toRaw flattens @include fields back to wire format") {
        val raw = jmap(
          "title" -> "Round-trip",
          "body" -> "content",
          "createdAt" -> Long.box(111L),
          "updatedAt" -> Long.box(222L)
        )
        val result = documentContract.validate(raw)
        val out = documentContract.toRaw(result.getValue.get)
        assertTrue(
          out.get("title") == "Round-trip",
          out.get("body") == "content",
          out.get("createdAt") == Long.box(111L),
          out.get("updatedAt") == Long.box(222L),
          !out.containsKey("timestamps")
        )
      },

      test("jsonSchema properties are flat for included fields") {
        val schema = documentContract.jsonSchema()
        val props = schema.get("properties").asInstanceOf[java.util.Map[String, AnyRef]]
        assertTrue(
          props.containsKey("title"),
          props.containsKey("createdAt"),
          props.containsKey("updatedAt"),
          !props.containsKey("timestamps")
        )
      }
    ),

    suite("nested JVM derivation")( 

      test("ofRecord decodes a nested Java record from a Scala map value") {
        val raw = jmap(
          "sku" -> "SKU-1",
          "inventory" -> Map(
            "warehouseId" -> "WH-1",
            "available" -> 8,
            "reserved" -> 3
          )
        )
        val result = productContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.inventory().isInstanceOf[TestJvmInventory],
          result.getValue.get.inventory().warehouseId() == "WH-1",
          result.getValue.get.inventory().available() == 8,
          result.getValue.get.inventory().reserved() == 3
        )
      },

      test("nested Optional record is Optional.empty when omitted") {
        val result = optionalProductContract.validate(jmap("sku" -> "SKU-2"))
        assertTrue(
          result.isValid,
          result.getValue.get.inventory() == java.util.Optional.empty[TestJvmInventory]()
        )
      },

      test("nested @validateContract violations are reported with the nested path") {
        val raw = jmap(
          "sku" -> "SKU-3",
          "inventory" -> Map(
            "warehouseId" -> "WH-2",
            "available" -> 2,
            "reserved" -> 5
          )
        )
        val result = productContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "inventory" &&
            v.code == "CONSTRAINT(validateContract)"
          )
        )
      },

      test("nested @reserved fields are rejected on validate") {
        val raw = jmap(
          "orderNumber" -> "ORD-1",
          "paymentInfo" -> Map(
            "cardNumber" -> "4111111111111111",
            "gatewayToken" -> "tok_123",
            "internalSegment" -> "vip"
          )
        )
        val result = orderContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "paymentInfo.internalSegment" && v.code == "RESERVED"
          )
        )
      },

      test("nested sanitize applies @internal and @masked recursively") {
        val sanitized = orderContract.sanitize(jmap(
          "orderNumber" -> "ORD-2",
          "paymentInfo" -> Map(
            "cardNumber" -> "4111111111111111",
            "gatewayToken" -> "tok_456"
          )
        ))
        val paymentInfo = sanitized.get("paymentInfo").asInstanceOf[java.util.Map[String, AnyRef]]
        assertTrue(
          paymentInfo.get("cardNumber") == "***",
          !paymentInfo.containsKey("gatewayToken")
        )
      },

      test("nested validatePatch trusts current reserved fields and still applies nested validators") {
        val current = jmap(
          "orderNumber" -> "ORD-3",
          "paymentInfo" -> Map(
            "cardNumber" -> "4111111111111111",
            "gatewayToken" -> "tok_789",
            "internalSegment" -> "system"
          )
        )
        val patch = jmap(
          "paymentInfo" -> Map(
            "cardNumber" -> "5555555555554444"
          )
        )
        val result = orderContract.validatePatch(current, patch)
        assertTrue(
          result.isValid,
          result.getValue.get.paymentInfo().cardNumber() == "5555555555554444",
          result.getValue.get.paymentInfo().internalSegment() == "system"
        )
      },

      test("nested validatePatch rejects nested immutable updates") {
        val current = jmap(
          "sku" -> "SKU-4",
          "inventory" -> Map(
            "warehouseId" -> "WH-9",
            "available" -> 4,
            "reserved" -> 1
          )
        )
        val patch = jmap(
          "inventory" -> Map(
            "warehouseId" -> "WH-10"
          )
        )
        val result = productContract.validatePatch(current, patch)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "inventory.warehouseId" && v.code == "IMMUTABLE"
          )
        )
      },

      test("java-record payload decodes nested records and java lists") {
        val raw = jmap(
          "sku" -> "JAVA-001",
          "name" -> "Java Product",
          "description" -> "Nested decode check",
          "category" -> "test",
          "price" -> Map("amount" -> 19.99d, "currency" -> "USD"),
          "inventory" -> Map("available" -> 10, "reserved" -> 2),
          "tags" -> java.util.List.of("java", "nested")
        )
        val result = javaProductPayloadContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.price().amount() == 19.99d,
          result.getValue.get.inventory().available() == 10,
          result.getValue.get.tags() == java.util.List.of("java", "nested")
        )
      },

      test("java-record payload rejects nested reserved fields") {
        val raw = jmap(
          "email" -> "reserved@example.com",
          "name" -> "Reserved User",
          "preferences" -> Map("internalSegment" -> "secret")
        )
        val result = javaUserPayloadContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "preferences.internalSegment" && v.code == "RESERVED"
          )
        )
      },

      test("java-record payload sanitizes nested masked and internal fields") {
        val sanitized = javaOrderPayloadContract.sanitize(jmap(
          "userId" -> Long.box(1L),
          "orderNumber" -> "ORD-JAVA-001",
          "status" -> "pending",
          "items" -> java.util.List.of(Map("productId" -> 1L, "sku" -> "SKU-1", "quantity" -> 2, "unitPrice" -> 10.0d)),
          "totals" -> Map("subtotal" -> 20.0d, "tax" -> 2.0d, "shipping" -> 1.0d, "total" -> 23.0d),
          "paymentInfo" -> Map("method" -> "credit_card", "last4" -> "1234", "gatewayReference" -> "gw-123")
        ))
        val paymentInfo = sanitized.get("paymentInfo").asInstanceOf[java.util.Map[String, AnyRef]]
        assertTrue(
          paymentInfo.get("last4") == "****",
          !paymentInfo.containsKey("gatewayReference")
        )
      },

      test("list of nested JVM contracts is materialized as typed elements") {
        val raw = jmap(
          "userId" -> Long.box(1L),
          "orderNumber" -> "ORD-JAVA-002",
          "status" -> "pending",
          "items" -> java.util.List.of(Map("productId" -> 1L, "sku" -> "SKU-1", "quantity" -> 2, "unitPrice" -> 10.0d)),
          "totals" -> Map("subtotal" -> 20.0d, "tax" -> 2.0d, "shipping" -> 1.0d, "total" -> 23.0d),
          "paymentInfo" -> Map("method" -> "credit_card", "last4" -> "1234")
        )
        val result = javaOrderPayloadContract.validate(raw)
        val first = result.getValue.get.items().get(0)
        assertTrue(
          result.isValid,
          first.isInstanceOf[TestJvmJavaOrderItem],
          first.productId() == 1L,
          first.quantity() == 2
        )
      },

      test("nullable reference fields can be omitted when marked @Nullable") {
        val result = nullableProfileContract.validate(jmap("username" -> "alice"))
        assertTrue(
          result.isValid,
          result.getValue.get.bio() == null,
          result.getValue.get.favoritePrice() == null
        )
      },

      test("nullable nested contract fields decode when provided") {
        val result = nullableProfileContract.validate(jmap(
          "username" -> "alice",
          "favoritePrice" -> Map("amount" -> 19.99d, "currency" -> "USD")
        ))
        assertTrue(
          result.isValid,
          result.getValue.get.favoritePrice().amount() == 19.99d,
          result.getValue.get.favoritePrice().currency() == "USD"
        )
      }
    ),

    // ── ofRecord — zero-boilerplate auto-constructor ──────────────────────

    suite("ofRecord — auto-constructor for Java records")( 

      test("produces the same validated T as the explicit JvmContract.of version") {
        val autoContract = JvmContract.ofRecord(classOf[TestJvmUser])
        val raw = jmap(
          "id"       -> Long.box(1L),
          "name"     -> "Alice",
          "age"      -> Int.box(30),
          "email"    -> "alice@example.com",
          "password" -> "s3cr3t"
        )
        val auto     = autoContract.validate(raw)
        val explicit = userContract.validate(raw)
        assertTrue(
          auto.isValid,
          auto.getValue.get.id()    == explicit.getValue.get.id(),
          auto.getValue.get.name()  == explicit.getValue.get.name(),
          auto.getValue.get.age()   == explicit.getValue.get.age(),
          auto.getValue.get.email() == explicit.getValue.get.email()
        )
      },

      test("all constraint annotations are still enforced") {
        val autoContract = JvmContract.ofRecord(classOf[TestJvmUser])
        val raw = jmap(
          "id"    -> Long.box(1L),
          "name"  -> "",             // @nonEmpty violation
          "age"   -> Int.box(-1),    // @min(0) violation
          "email" -> "not-an-email", // @email violation
          "password" -> "pw"
        )
        val result = autoContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(_.code == "CONSTRAINT(nonEmpty)"),
          result.getErrors.asScala.exists(_.code == "CONSTRAINT(min)"),
          result.getErrors.asScala.exists(_.code == "CONSTRAINT(email)")
        )
      },

      test("@immutable field is enforced in validatePatch") {
        val autoContract = JvmContract.ofRecord(classOf[TestJvmUser])
        val current = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                           "email" -> "a@b.com", "password" -> "pw")
        val patch = jmap("id" -> Long.box(999L))
        val result = autoContract.validatePatch(current, patch)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v => v.path == "id" && v.code == "IMMUTABLE")
        )
      },

      test("Optional<T> fields work correctly via ofRecord") {
        val autoProfile = JvmContract.ofRecord(classOf[TestJvmProfile])

        // present optional
        val withBio = jmap("username" -> "alice", "bio" -> "Loves Scala", "score" -> Int.box(10))
        val r1 = autoProfile.validate(withBio)
        assertTrue(
          r1.isValid,
          r1.getValue.get.bio()   == java.util.Optional.of("Loves Scala"),
          r1.getValue.get.score() == java.util.Optional.of(10)
        )
        // absent optional
        val noBio = jmap("username" -> "alice")
        val r2 = autoProfile.validate(noBio)
        assertTrue(
          r2.isValid,
          r2.getValue.get.bio()   == java.util.Optional.empty[String](),
          r2.getValue.get.score() == java.util.Optional.empty[Integer]()
        )
      },

      test("@reserved annotation is enforced via ofRecord") {
        val autoTicket = JvmContract.ofRecord(classOf[TestJvmTicket])
        val raw = jmap("trackingId" -> "TKT-001", "title" -> "Fix bug")
        val result = autoTicket.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "trackingId" && v.code == "RESERVED"
          )
        )
      },

      test("throws IllegalArgumentException for a non-record class") {
        try
          JvmContract.ofRecord(classOf[TestKotlinSimUser])
          assertTrue(false) // should not reach here
        catch
          case e: IllegalArgumentException =>
            assertTrue(e.getMessage.contains("not a Java record"))
      }
    ),

    // ── ofPrimary — zero-boilerplate auto-constructor for POJOs / Kotlin ──

    suite("ofPrimary — auto-constructor for POJOs and Kotlin-style classes")(

      test("constructs T correctly from a Kotlin-param-annotated class") {
        val autoContract = JvmContract.ofPrimary(classOf[TestKotlinSimUser])
        val raw = jmap(
          "id"    -> Long.box(7L),
          "name"  -> "Bob",
          "age"   -> Int.box(28),
          "email" -> "bob@example.com"
        )
        val result = autoContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.id()    == 7L,
          result.getValue.get.name()  == "Bob",
          result.getValue.get.age()   == 28,
          result.getValue.get.email() == "bob@example.com"
        )
      },

      test("constraint annotations are enforced via ofPrimary") {
        val autoContract = JvmContract.ofPrimary(classOf[TestKotlinSimUser])
        val raw = jmap(
          "id"    -> Long.box(1L),
          "name"  -> "",            // @nonEmpty
          "age"   -> Int.box(-5),   // @min(0)
          "email" -> "bad"          // @email
        )
        val result = autoContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(_.code == "CONSTRAINT(nonEmpty)"),
          result.getErrors.asScala.exists(_.code == "CONSTRAINT(min)"),
          result.getErrors.asScala.exists(_.code == "CONSTRAINT(email)")
        )
      },

      test("validatePatch works correctly via ofPrimary") {
        val autoContract = JvmContract.ofPrimary(classOf[TestKotlinSimUser])
        val current = jmap("id" -> Long.box(1L), "name" -> "Alice",
                           "age" -> Int.box(30), "email" -> "a@b.com")
        val patch   = jmap("name" -> "Alicia")
        val result  = autoContract.validatePatch(current, patch)
        assertTrue(result.isValid, result.getValue.get.name() == "Alicia")
      },

      test("constructs nested Kotlin-style classes from nested raw maps") {
        val autoContract = JvmContract.ofPrimary(classOf[TestKotlinNestedUser])
        val raw = jmap(
          "id" -> Long.box(11L),
          "name" -> "Casey",
          "address" -> Map(
            "city" -> "London",
            "postcode" -> "E1 6AN"
          )
        )
        val result = autoContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.address().isInstanceOf[TestKotlinNestedAddress],
          result.getValue.get.address().city() == "London",
          result.getValue.get.address().postcode() == "E1 6AN"
        )
      },

      test("kotlin-style optional nested fields can be omitted") {
        val result = kotlinOptionalUserContract.validate(jmap(
          "email" -> "optional@example.com",
          "name" -> "Optional Kotlin User"
        ))
        assertTrue(
          result.isValid,
          result.getValue.get.address() == null,
          result.getValue.get.preferences() == null,
          result.getValue.get.internalNotes() == java.util.Optional.empty[String]()
        )
      },

      test("kotlin-style nested data classes decode when provided") {
        val result = kotlinOptionalUserContract.validate(jmap(
          "email" -> "kotlin@example.com",
          "name" -> "Kotlin User",
          "address" -> Map("street" -> "1 Kotlin St", "city" -> "Leeds", "zipCode" -> "LS11AA"),
          "preferences" -> Map("newsletter" -> java.lang.Boolean.TRUE)
        ))
        assertTrue(
          result.isValid,
          result.getValue.get.address().street() == "1 Kotlin St",
          result.getValue.get.preferences().newsletter() == java.lang.Boolean.TRUE
        )
      },

      test("kotlin-style nested reserved field is rejected") {
        val result = kotlinOptionalUserContract.validate(jmap(
          "email" -> "reserved@example.com",
          "name" -> "Reserved Kotlin User",
          "preferences" -> Map("internalSegment" -> "secret")
        ))
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v => v.path.contains("internalSegment"))
        )
      },

      test("kotlin-style sanitize masks and drops nested sensitive fields") {
        val sanitized = kotlinOptionalOrderContract.sanitize(jmap(
          "userId" -> Long.box(1L),
          "orderNumber" -> "ORD-KOTLIN-001",
          "status" -> "pending",
          "items" -> java.util.List.of(Map("productId" -> 1L, "sku" -> "SKU-1", "quantity" -> 2, "unitPrice" -> 10.0d)),
          "totals" -> Map("subtotal" -> 20.0d, "tax" -> 2.0d, "shipping" -> 1.0d, "total" -> 23.0d),
          "paymentInfo" -> Map("method" -> "credit_card", "last4" -> "1234", "gatewayReference" -> "gw-123")
        ))
        val paymentInfo = sanitized.get("paymentInfo").asInstanceOf[java.util.Map[String, AnyRef]]
        assertTrue(
          paymentInfo.get("last4") == "****",
          !paymentInfo.containsKey("gatewayReference")
        )
      }
    ),

    // ── toJson / sanitizeJson ─────────────────────────────────────────────

    suite("toJson and sanitizeJson")(

      test("toJson round-trips a constructed TestJvmUser") {
        val raw    = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                          "email" -> "alice@example.com", "password" -> "s3cr3t")
        val user   = userContract.validate(raw).getValue.get
        val json   = userContract.toJson(user)
        assertTrue(
          json.contains("\"name\":\"Alice\""),
          json.contains("\"email\":\"alice@example.com\""),
          json.contains("\"id\":1"),
          json.contains("\"age\":30")
        )
      },

      test("sanitizeJson masks @masked fields (TestJvmUser has no @internal fields)") {
        // Note: TestJvmUser.id is @immutable (blocks patch changes) — NOT @internal.
        // Only @internal strips a field from sanitize output; @immutable does not.
        val raw  = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                        "email" -> "alice@example.com", "password" -> "s3cr3t")
        val json = userContract.sanitizeJson(raw)
        assertTrue(
          json.contains("\"id\":1"),             // @immutable — still present in sanitize output
          json.contains("\"name\":\"Alice\""),
          json.contains("\"password\":\"***\"")  // @masked → replaced with mask string
        )
      },

      test("toJson with indent produces indented output") {
        val raw  = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                        "email" -> "alice@example.com", "password" -> "pw")
        val user = userContract.validate(raw).getValue.get
        val json = userContract.toJson(user, 2)
        assertTrue(json.contains("\n"), json.contains("  \"name\""))
      },

      test("sanitizeJson with indent produces indented output") {
        val raw  = jmap("id" -> Long.box(1L), "name" -> "Alice", "age" -> Int.box(30),
                        "email" -> "alice@example.com", "password" -> "pw")
        val json = userContract.sanitizeJson(raw, 2)
        assertTrue(json.contains("\n"), json.contains("  \"name\""))
      },

      test("absent Optional field is omitted from toJson output") {
        val raw = jmap("username" -> "alice")
        val profile = JvmContract.ofRecord(classOf[TestJvmProfile]).validate(raw).getValue.get
        val json = JvmContract.ofRecord(classOf[TestJvmProfile]).toJson(profile)
        // bio and score are Optional.empty() → should not appear
        assertTrue(json.contains("\"username\""), !json.contains("\"bio\""), !json.contains("\"score\""))
      }
    ),

    // ── jsonSchemaJson / jsonSchema ───────────────────────────────────────

    suite("jsonSchemaJson and jsonSchema")(

      test("jsonSchemaJson returns compact draft-07 schema JSON") {
        val json = userContract.jsonSchemaJson()
        assertTrue(
          json.contains("\"$schema\":\"http://json-schema.org/draft-07/schema#\""),
          json.contains("\"properties\""),
          json.contains("\"name\""),
          json.contains("\"additionalProperties\":false")
        )
      },

      test("jsonSchemaJson with indent produces indented output") {
        val json = userContract.jsonSchemaJson(2)
        assertTrue(
          json.contains("\n"),
          json.contains("  \"$schema\""),
          json.contains("  \"properties\"")
        )
      },

      test("jsonSchema returns deeply converted Java collections") {
        val schema = userContract.jsonSchema()
        val props = schema.get("properties")
        val required = schema.get("required")
        val nameSchema =
          props.asInstanceOf[java.util.Map[String, AnyRef]].get("name")

        assertTrue(
          schema.isInstanceOf[java.util.Map[?, ?]],
          props.isInstanceOf[java.util.Map[?, ?]],
          required.isInstanceOf[java.util.List[?]],
          nameSchema.isInstanceOf[java.util.Map[?, ?]]
        )
      },

      test("closed contract schema includes additionalProperties=false") {
        val schema = userContract.jsonSchema()
        assertTrue(schema.get("additionalProperties") == java.lang.Boolean.FALSE)
      },

      test("open contract schema omits additionalProperties") {
        val schema = openDocContract.jsonSchema()
        assertTrue(!schema.containsKey("additionalProperties"))
      },

      test("ofRecord infers openness from @contract(open = true)") {
        val raw = jmap("key" -> "theme", "value" -> "dark", "source" -> "ui-settings")
        val result = openDocContract.validate(raw)
        assertTrue(result.isValid, result.getValue.get.key() == "theme")
      },

      test("explicit open=false overrides @contract(open = true)") {
        val closedOverride = JvmContract.ofRecord(classOf[TestJvmOpenDoc], false)
        val schema = closedOverride.jsonSchema()
        assertTrue(schema.get("additionalProperties") == java.lang.Boolean.FALSE)
      },

      test("explicit open=true overrides @contract default false") {
        val openOverride = JvmContract.ofRecord(classOf[TestJvmUser], true)
        val schema = openOverride.jsonSchema()
        assertTrue(!schema.containsKey("additionalProperties"))
      }
    ),

    suite("extraFields")(

      test("open contract returns only unknown top-level fields") {
        val raw = jmap("key" -> "theme", "value" -> "dark", "source" -> "ui-settings")
        val extra = openDocContract.extraFields(raw)
        assertTrue(
          extra.size == 1,
          extra.get("source") == "ui-settings",
          !extra.containsKey("key"),
          !extra.containsKey("value")
        )
      },

      test("closed contract returns unknown fields from the raw map helper") {
        val raw = jmap(
          "id" -> Long.box(1L),
          "name" -> "Alice",
          "age" -> Int.box(30),
          "email" -> "alice@example.com",
          "password" -> "pw",
          "source" -> "ui-settings"
        )
        val extra = userContract.extraFields(raw)
        assertTrue(extra.size == 1, extra.get("source") == "ui-settings")
      },

      test("extraFields returns nested Java collections") {
        val nested = jmap("flag" -> java.lang.Boolean.TRUE)
        val raw = jmap(
          "key" -> "theme",
          "value" -> "dark",
          "meta" -> nested,
          "tags" -> java.util.List.of("a", "b")
        )
        val extra = openDocContract.extraFields(raw)
        assertTrue(
          extra.get("meta").isInstanceOf[java.util.Map[?, ?]],
          extra.get("tags").isInstanceOf[java.util.List[?]]
        )
      }
    ),

    suite("JvmPatch")(

      test("set accumulates fields and preserves insertion order in toMap") {
        val patch = JvmPatch.empty()
          .set("name", "Alicia")
          .set("age", Int.box(31))
        val it = patch.toMap().keySet().iterator()
        assertTrue(
          patch.size() == 2,
          !patch.isEmpty(),
          it.next() == "name",
          it.next() == "age"
        )
      },

      test("from copies an existing raw map") {
        val patch = JvmPatch.from(jmap("name" -> "Alicia"))
        patch.set("age", Int.box(31))
        val raw = patch.toMap()
        assertTrue(raw.get("name") == "Alicia", raw.get("age") == Int.box(31))
      },

      test("putAll merges raw map entries") {
        val patch = JvmPatch.empty().set("name", "Alicia")
        patch.putAll(jmap("age" -> Int.box(31), "email" -> "a@b.com"))
        val raw = patch.toMap()
        assertTrue(
          raw.get("name") == "Alicia",
          raw.get("age") == Int.box(31),
          raw.get("email") == "a@b.com"
        )
      }
    ),

    suite("JVM feature parity gaps")( 

      test("@validateWith is enforced on JvmContract") {
        val raw = jmap("phone" -> "not-a-phone", "name" -> "Alice")
        val result = contactContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "phone" && v.code == "CONSTRAINT(validateWith)"
          )
        )
      },

      test("@validateContract is enforced on JvmContract") {
        val raw = jmap(
          "checkIn" -> Long.box(2000L),
          "checkOut" -> Long.box(1000L),
          "guestId" -> "guest-123"
        )
        val result = bookingRuleContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "" && v.code == "CONSTRAINT(validateContract)"
          )
        )
      },

      test("@extract on a field is enforced on JvmContract") {
        val raw = jmap("date" -> "not-a-date")
        val result = eventContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "date" && v.code == "CONSTRAINT(extract)"
          )
        )
      },

      test("@decodable wrapper fields are currently unsupported on JvmContract") {
        val raw = jmap("email" -> "alice@example.com", "name" -> "Alice")
        assertThrows[IllegalArgumentException] {
          memberContract.validate(raw)
        }
      }
    )
  )

  spec
