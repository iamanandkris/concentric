package io.dsentric

import java.util.{Map => JMap, HashMap => JHashMap}
import scala.jdk.CollectionConverters.*

/**
 * Verifies that [[JvmContract]] correctly handles the two distinct annotation-
 * placement strategies used by Kotlin data classes.
 *
 * == Background ==
 *
 * Kotlin places annotations differently depending on whether the `@field:`
 * use-site target is specified:
 *
 *  - '''Without `@field:`''' (Kotlin default):
 *    annotations go to the primary constructor parameter.  The JVM backing
 *    field carries no annotations.  [[TestKotlinSimUser]] simulates this.
 *
 *  - '''With `@field:`''':
 *    annotations go to the JVM backing field (identical to a Java field
 *    annotation).  [[TestKotlinFieldUser]] simulates this.
 *
 * [[JvmContractDeriver.effectiveAnnotations]] merges both sources, so both
 * placement styles are detected.  This spec proves that through genuine
 * compiled Java bytecode without requiring the Kotlin compiler in the build.
 *
 * The actual Kotlin source lives at:
 *   `src/test/kotlin/io/dsentric/TestKotlinUser.kt`
 * It is compiled and exercised automatically once the sbt-kotlin-plugin is
 * enabled (see TestKotlinUser.kt for setup instructions).
 */
final class KotlinInteropSpec extends SpecBase:

  // ── Contracts ─────────────────────────────────────────────────────────────

  /** Simulates a Kotlin data class with parameter-site annotations (no @field:). */
  val simContract: JvmContract[TestKotlinSimUser] = JvmContract.of(
    classOf[TestKotlinSimUser],
    fields =>
      new TestKotlinSimUser(
        fields.get("id").asInstanceOf[Long],
        fields.get("name").asInstanceOf[String],
        fields.get("age").asInstanceOf[Int],
        fields.get("email").asInstanceOf[String]
      )
  )

  /** Simulates a Kotlin data class with @field: use-site annotations. */
  val fieldContract: JvmContract[TestKotlinFieldUser] = JvmContract.of(
    classOf[TestKotlinFieldUser],
    fields =>
      new TestKotlinFieldUser(
        fields.get("id").asInstanceOf[Long],
        fields.get("name").asInstanceOf[String],
        fields.get("age").asInstanceOf[Int],
        fields.get("email").asInstanceOf[String]
      )
  )

  // ── Helper ────────────────────────────────────────────────────────────────

  def jmap(pairs: (String, AnyRef)*): JMap[String, AnyRef] =
    val m = new JHashMap[String, AnyRef]()
    pairs.foreach { (k, v) => m.put(k, v) }
    m

  // ── Spec ──────────────────────────────────────────────────────────────────

  def spec: Unit = suite("KotlinInteropSpec")(

    // ── Parameter-site annotations (no @field:) ──────────────────────────

    suite("Kotlin-style: annotations on constructor parameters (no @field:)")(

      test("valid input constructs T correctly (field annotations)") {
        val raw    = jmap("id" -> Long.box(1L), "name" -> "Alice",
                          "age" -> Int.box(30), "email" -> "a@b.com")
        val result = simContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.name() == "Alice",
          result.getValue.get.age()  == 30
        )
      },

      test("@immutable on constructor param is detected → IMMUTABLE violation on patch") {
        val current = jmap("id" -> Long.box(1L), "name" -> "Alice",
                           "age" -> Int.box(30), "email" -> "a@b.com")
        val patch   = jmap("id" -> Long.box(2L))
        val result  = simContract.validatePatch(current, patch)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v => v.path == "id" && v.code == "IMMUTABLE")
        )
      },

      test("@nonEmpty on constructor param is detected → CONSTRAINT violation") {
        val raw    = jmap("id" -> Long.box(1L), "name" -> "",
                          "age" -> Int.box(30), "email" -> "a@b.com")
        val result = simContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "name" && v.code == "CONSTRAINT(nonEmpty)"
          )
        )
      },

      test("@min(0) on constructor param is detected → CONSTRAINT violation") {
        val raw    = jmap("id" -> Long.box(1L), "name" -> "Alice",
                          "age" -> Int.box(-1), "email" -> "a@b.com")
        val result = simContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "age" && v.code == "CONSTRAINT(min)"
          )
        )
      },

      test("@email on constructor param is detected → CONSTRAINT violation") {
        val raw    = jmap("id" -> Long.box(1L), "name" -> "Alice",
                          "age" -> Int.box(30), "email" -> "not-an-email")
        val result = simContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "email" && v.code == "CONSTRAINT(email)"
          )
        )
      },

      test("metadata: @immutable is true for id (field annotations)") {
        assertTrue(simContract.fieldMetas.find(_.name == "id").exists(_.isImmutable))
      },

      test("metadata: @nonEmpty is true for name") {
        assertTrue(simContract.fieldMetas.find(_.name == "name").exists(_.isNonEmpty))
      }
    ),

    // ── Field-site annotations (@field:) ─────────────────────────────────

    suite("Kotlin-style: annotations on backing fields (@field:)")(

      test("valid input constructs T correctly") {
        val raw    = jmap("id" -> Long.box(1L), "name" -> "Bob",
                          "age" -> Int.box(25), "email" -> "b@b.com")
        val result = fieldContract.validate(raw)
        assertTrue(
          result.isValid,
          result.getValue.get.name() == "Bob",
          result.getValue.get.age()  == 25
        )
      },

      test("@immutable on field is detected → IMMUTABLE violation on patch") {
        val current = jmap("id" -> Long.box(1L), "name" -> "Bob",
                           "age" -> Int.box(25), "email" -> "b@b.com")
        val patch   = jmap("id" -> Long.box(9L))
        val result  = fieldContract.validatePatch(current, patch)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v => v.path == "id" && v.code == "IMMUTABLE")
        )
      },

      test("@nonEmpty on field is detected → CONSTRAINT violation") {
        val raw    = jmap("id" -> Long.box(1L), "name" -> "",
                          "age" -> Int.box(25), "email" -> "b@b.com")
        val result = fieldContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "name" && v.code == "CONSTRAINT(nonEmpty)"
          )
        )
      },

      test("@min(0) on field is detected → CONSTRAINT violation") {
        val raw    = jmap("id" -> Long.box(1L), "name" -> "Bob",
                          "age" -> Int.box(-5), "email" -> "b@b.com")
        val result = fieldContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "age" && v.code == "CONSTRAINT(min)"
          )
        )
      },

      test("@email on field is detected → CONSTRAINT violation") {
        val raw    = jmap("id" -> Long.box(1L), "name" -> "Bob",
                          "age" -> Int.box(25), "email" -> "nope")
        val result = fieldContract.validate(raw)
        assertTrue(
          !result.isValid,
          result.getErrors.asScala.exists(v =>
            v.path == "email" && v.code == "CONSTRAINT(email)"
          )
        )
      },

      test("metadata: @immutable is true for id") {
        assertTrue(fieldContract.fieldMetas.find(_.name == "id").exists(_.isImmutable))
      },

      test("metadata: @email is true for email") {
        assertTrue(fieldContract.fieldMetas.find(_.name == "email").exists(_.isEmail))
      }
    )
  )

  spec
