package io.concentric

import io.concentric.annotations.*

final class OpenContractSpec extends SpecBase:

  @contract
  case class Metadata(
    @nonEmpty key:   String,
    @nonEmpty value: String
  ) derives OpenContract

  @contract
  case class SensitiveMetadata(
    @internal           sourceId: String,
    @masked("REDACTED") token:    String,
                        key:      String
  ) derives OpenContract

  @contract
  case class Envelope(
    meta:  Metadata,
    label: String
  ) derives OpenContract

  given RawDecoder[Metadata] with
    def decode(raw: Any): Option[Metadata] = raw match
      case m: Map[?, ?] =>
        val sm = m.asInstanceOf[Map[String, Any]]
        for
          key   <- sm.get("key").flatMap(RawDecoder[String].decode)
          value <- sm.get("value").flatMap(RawDecoder[String].decode)
        yield Metadata(key, value)
      case _ => None

  test("OpenContract validates declared fields and preserves extras") {
    val raw = Map(
      "key"    -> "theme",
      "value"  -> "dark",
      "source" -> "ui-settings",
      "region" -> "eu-west"
    )

    val (value, extras) = OpenContract[Metadata].validate(raw).value
    value shouldBe Metadata("theme", "dark")
    extras shouldBe Map(
      "source" -> "ui-settings",
      "region" -> "eu-west"
    )
  }

  test("OpenContract named-tuple result can be pattern matched") {
    val raw = Map(
      "key"    -> "theme",
      "value"  -> "dark",
      "source" -> "ui-settings"
    )

    val decision =
      OpenContract[Metadata].validate(raw) match
        case Right((Metadata("theme", _), extras))
            if extras.get("source").contains("ui-settings") =>
          "theme-from-ui"
        case Right((Metadata(key, _), _)) =>
          s"other-$key"
        case Left(_) =>
          "invalid"

    decision shouldBe "theme-from-ui"
  }

  test("OpenContract does not treat extras as declared case class fields") {
    val raw = Map(
      "key"    -> "theme",
      "value"  -> "dark",
      "source" -> "ui-settings"
    )

    val validated = OpenContract[Metadata].validate(raw).value._1
    validated shouldBe Metadata("theme", "dark")
  }

  test("OpenContract still enforces missing required declared fields") {
    val raw = Map("value" -> "dark", "source" -> "ui-settings")

    val result = OpenContract[Metadata].validate(raw)
    assertTrue(
      result.isLeft,
      result.left.toOption.get.violations.exists(v =>
        v.path == FieldPath("key") && v.code == ViolationCode.ExpectedMissing
      )
    )
  }

  test("OpenContract still enforces declared-field constraints") {
    val raw = Map("key" -> "", "value" -> "dark", "source" -> "ui-settings")

    val result = OpenContract[Metadata].validate(raw)
    assertTrue(
      result.isLeft,
      result.left.toOption.get.violations.exists(v =>
        v.path == FieldPath("key") && v.code == ViolationCode.ConstraintFailed("nonEmpty")
      )
    )
  }

  test("OpenContract validates nested declared fields and still preserves top-level extras") {
    val raw = Map(
      "meta"   -> Map("key" -> "theme", "value" -> "dark"),
      "label"  -> "ui",
      "source" -> "ui-settings"
    )

    val (value, extras) = OpenContract[Envelope].validate(raw).value
    value shouldBe Envelope(Metadata("theme", "dark"), "ui")
    extras shouldBe Map("source" -> "ui-settings")
  }

  test("OpenContract sanitize keeps extras while applying declared-field internal/masked rules") {
    val raw = Map(
      "sourceId" -> "src-1",
      "token"    -> "secret-token",
      "key"      -> "theme",
      "source"   -> "ui-settings"
    )

    OpenContract[SensitiveMetadata].sanitize(raw) shouldBe Map(
      "token"  -> "REDACTED",
      "key"    -> "theme",
      "source" -> "ui-settings"
    )
  }

  test("OpenContract jsonSchema remains open") {
    val schema = OpenContract[Metadata].jsonSchema
    assertTrue(!schema.contains("additionalProperties"))
  }
