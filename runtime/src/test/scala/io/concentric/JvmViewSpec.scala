package io.concentric

import scala.jdk.CollectionConverters.*

/**
 * Tests for [[JvmView]] — the string-keyed JVM wrapper around [[View]][T].
 *
 * Uses [[TestKotlinSimUser]] (plain Java class, Java-11-compatible) as the
 * contract model.  Fields: id @immutable Long, name @nonEmpty String,
 * age @min(0) @max(150) Int, email @email String.
 *
 * Tests cover every transform type (omit, mask, compute), transform ordering,
 * absent-field edge cases, and the factory method.
 */
final class JvmViewSpec extends SpecBase:

  val userContract: JvmContract[TestKotlinSimUser] = JvmContract.of(
    classOf[TestKotlinSimUser],
    fields => new TestKotlinSimUser(
      fields.get("id").asInstanceOf[Long],
      fields.get("name").asInstanceOf[String],
      fields.get("age").asInstanceOf[Int],
      fields.get("email").asInstanceOf[String]
    )
  )

  private def jmap(pairs: (String, AnyRef)*): java.util.Map[String, AnyRef] =
    val m = new java.util.LinkedHashMap[String, AnyRef]()
    pairs.foreach { case (k, v) => m.put(k, v) }
    m

  private val fullRaw = jmap(
    "id"    -> Long.box(1L),
    "name"  -> "Alice",
    "age"   -> Int.box(30),
    "email" -> "alice@example.com"
  )

  // ── omit ─────────────────────────────────────────────────────────────────

  suite("JvmView — omit")(

    test("omit removes the named field") {
      val view   = JvmView.of(classOf[TestKotlinSimUser]).omit("email")
      val result = view.apply(fullRaw)
      assertTrue(
        !result.containsKey("email"),
        result.containsKey("name"),
        result.containsKey("age")
      )
    },

    test("omit multiple fields — all are gone") {
      val view   = JvmView.of(classOf[TestKotlinSimUser]).omit("email").omit("id")
      val result = view.apply(fullRaw)
      assertTrue(
        !result.containsKey("email"),
        !result.containsKey("id"),
        result.containsKey("name")
      )
    },

    test("omit a field that is absent is a no-op — no exception") {
      val view   = JvmView.of(classOf[TestKotlinSimUser]).omit("nonExistentField")
      val result = view.apply(fullRaw)
      assertTrue(result.size == fullRaw.size)
    }
  )

  // ── mask ─────────────────────────────────────────────────────────────────

  suite("JvmView — mask")(

    test("mask with no mask string replaces value with ***") {
      val view   = JvmView.of(classOf[TestKotlinSimUser]).mask("email")
      val result = view.apply(fullRaw)
      assertTrue(result.get("email") == "***")
    },

    test("mask with custom string uses the given mask") {
      val view   = JvmView.of(classOf[TestKotlinSimUser]).mask("email", "REDACTED")
      val result = view.apply(fullRaw)
      assertTrue(result.get("email") == "REDACTED")
    },

    test("mask leaves other fields untouched") {
      val view   = JvmView.of(classOf[TestKotlinSimUser]).mask("email")
      val result = view.apply(fullRaw)
      assertTrue(
        result.get("name") == "Alice",
        result.get("age")  == Int.box(30)
      )
    },

    test("mask a field that is absent is a no-op") {
      val raw    = jmap("name" -> "Alice")
      val view   = JvmView.of(classOf[TestKotlinSimUser]).mask("email")
      val result = view.apply(raw)
      assertTrue(!result.containsKey("email"))
    }
  )

  // ── compute ───────────────────────────────────────────────────────────────

  suite("JvmView — compute")(

    test("compute adds a brand-new synthetic field") {
      val view = JvmView.of(classOf[TestKotlinSimUser])
        .compute("displayName", raw => (raw.get("name").toString + " (verified)").asInstanceOf[AnyRef])
      val result = view.apply(fullRaw)
      assertTrue(
        result.containsKey("displayName"),
        result.get("displayName").toString.contains("Alice")
      )
    },

    test("compute can replace an existing field") {
      val view = JvmView.of(classOf[TestKotlinSimUser])
        .compute("name", _ => "ANONYMISED".asInstanceOf[AnyRef])
      val result = view.apply(fullRaw)
      assertTrue(result.get("name") == "ANONYMISED")
    },

    test("compute sees the raw map AFTER preceding transforms have been applied") {
      // omit email first, then compute reads it — should be absent
      val view = JvmView.of(classOf[TestKotlinSimUser])
        .omit("email")
        .compute("hadEmail", raw => Boolean.box(raw.containsKey("email")))
      val result = view.apply(fullRaw)
      assertTrue(result.get("hadEmail") == Boolean.box(false))
    }
  )

  // ── chaining and ordering ─────────────────────────────────────────────────

  suite("JvmView — chaining and ordering")(

    test("transforms are applied in declaration order") {
      // mask email first, then compute copies the (already masked) value
      val view = JvmView.of(classOf[TestKotlinSimUser])
        .mask("email", "***")
        .compute("emailCopy", raw => raw.get("email"))
      val result = view.apply(fullRaw)
      assertTrue(result.get("emailCopy") == "***")
    },

    test("public-profile pipeline: omit id, mask email") {
      val view = JvmView.of(classOf[TestKotlinSimUser])
        .omit("id")
        .mask("email")
      val result = view.apply(fullRaw)
      assertTrue(
        !result.containsKey("id"),
        result.get("email") == "***",
        result.get("name")  == "Alice"
      )
    }
  )

  // ── factory and identity ──────────────────────────────────────────────────

  suite("JvmView — factory and identity")(

    test("JvmView.of produces an identity view") {
      val view   = JvmView.of(classOf[TestKotlinSimUser])
      val result = view.apply(fullRaw)
      assertTrue(
        result.get("name")  == fullRaw.get("name"),
        result.get("email") == fullRaw.get("email"),
        result.size         == fullRaw.size
      )
    },

    test("applying any view to an empty map produces an empty map") {
      val view   = JvmView.of(classOf[TestKotlinSimUser]).omit("name").mask("email")
      val result = view.apply(jmap())
      assertTrue(result.isEmpty)
    }
  )
