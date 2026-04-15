package io.dsentric

import io.dsentric.annotations.*

final class JsonSchemaSpec extends SpecBase:

  @contract
  case class ProductSchemaSample(
    @nonEmpty                                            name:         String,
    @email                                               contactEmail: Option[String],
    @pattern("^[A-Z]{2}-\\d+$")                          code:         Option[String],
    @validateWith(Array(classOf[io.dsentric.validators.NoWhitespaceValidator])) slug:         String
  ) derives Contract

  def spec: Unit = suite("JsonSchemaSpec")(

    // ── top-level structure ───────────────────────────────────────────────────

    suite("jsonSchema — top-level structure")(

      test("schema has $schema, type=object, properties") {
        val schema = userContract.jsonSchema
        assertTrue(
          schema("$schema") == "http://json-schema.org/draft-07/schema#",
          schema("type")    == "object",
          schema.contains("properties")
        )
      },

      test("closed contract has additionalProperties=false") {
        val schema = userContract.jsonSchema
        assertTrue(schema("additionalProperties") == false)
      },

      test("open contract omits additionalProperties") {
        val schema = openDocContract.jsonSchema
        assertTrue(!schema.contains("additionalProperties"))
      },

      test("required contains non-optional, no-default fields only") {
        val schema   = userContract.jsonSchema
        val required = schema("required").asInstanceOf[List[String]]
        assertTrue(
          required.contains("id"),
          required.contains("name"),
          !required.contains("email"),
          !required.contains("age"),
          !required.contains("password")
        )
      }
    ),

    // ── primitive types ───────────────────────────────────────────────────────

    suite("jsonSchema — primitive types")(

      test("String field → type=string") {
        val props = userContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("name").asInstanceOf[Map[String, Any]]("type") == "string")
      },

      test("Long field → type=integer") {
        val props = userContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("id").asInstanceOf[Map[String, Any]]("type") == "integer")
      },

      test("Int field → type=integer") {
        val props = userContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("age").asInstanceOf[Map[String, Any]]("type") == "integer")
      },

      test("Option[String] field → type=string, absent from required") {
        val schema   = userContract.jsonSchema
        val props    = schema("properties").asInstanceOf[Map[String, Any]]
        val required = schema.get("required").map(_.asInstanceOf[List[String]]).getOrElse(Nil)
        assertTrue(
          props("email").asInstanceOf[Map[String, Any]]("type") == "string",
          !required.contains("email")
        )
      }
    ),

    // ── constraint annotations ────────────────────────────────────────────────

    suite("jsonSchema — constraint annotations")(

      test("@maxLength → maxLength in property schema") {
        val props = userContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("name").asInstanceOf[Map[String, Any]]("maxLength") == 100)
      },

      test("@nonEmpty on String adds minLength=1 (when no explicit minLength)") {
        val props = userContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("name").asInstanceOf[Map[String, Any]]("minLength") == 1)
      },

      test("@minLength(n) takes precedence over @nonEmpty") {
        val props = apiKeyContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("key").asInstanceOf[Map[String, Any]]("minLength") == 8)
      },

      test("@min/@max → minimum/maximum in property schema") {
        val props = userContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        val age   = props("age").asInstanceOf[Map[String, Any]]
        assertTrue(age("minimum") == 0L, age("maximum") == 150L)
      },

      test("@email → format=email") {
        val props = summon[Contract[ProductSchemaSample]].jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(
          props("contactEmail").asInstanceOf[Map[String, Any]]("format") == "email"
        )
      },

      test("@pattern → pattern key in property schema") {
        val props = summon[Contract[ProductSchemaSample]].jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(
          props("code").asInstanceOf[Map[String, Any]]("pattern") == "^[A-Z]{2}-\\d+$"
        )
      }
    ),

    // ── extension annotations ─────────────────────────────────────────────────

    suite("jsonSchema — extension annotations")(

      test("@immutable → x-immutable=true") {
        val props = userContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("id").asInstanceOf[Map[String, Any]]("x-immutable") == true)
      },

      test("@internal → x-internal=true") {
        val props = userContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("id").asInstanceOf[Map[String, Any]]("x-internal") == true)
      },

      test("@masked (default) → x-masked=***") {
        val props = userContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("password").asInstanceOf[Map[String, Any]]("x-masked") == "***")
      },

      test("@masked(custom) → x-masked with that value") {
        val props = apiKeyContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("secret").asInstanceOf[Map[String, Any]]("x-masked") == "REDACTED")
      },

      test("@reserved → x-reserved=true") {
        val props = ticketContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("trackingId").asInstanceOf[Map[String, Any]]("x-reserved") == true)
      }
    ),

    // ── nested contracts ──────────────────────────────────────────────────────

    suite("jsonSchema — nested contracts")(

      test("Option[Address] field inlines nested object schema") {
        val props      = profileContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        val addrSchema = props("address").asInstanceOf[Map[String, Any]]
        assertTrue(addrSchema("type") == "object", addrSchema.contains("properties"))
        val nested = addrSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(nested.contains("street"), nested.contains("city"))
      },

      test("nested object schema has no top-level $schema key") {
        val props      = profileContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        val addrSchema = props("address").asInstanceOf[Map[String, Any]]
        assertTrue(!addrSchema.contains("$schema"))
      },

      test("List[OrderItem] → type=array with items schema inlined") {
        val props       = orderContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        val itemsSchema = props("items").asInstanceOf[Map[String, Any]]
        assertTrue(itemsSchema("type") == "array")
        val items = itemsSchema("items").asInstanceOf[Map[String, Any]]
        assertTrue(items("type") == "object", items.contains("properties"))
        val nested = items("properties").asInstanceOf[Map[String, Any]]
        assertTrue(nested.contains("name"), nested.contains("qty"))
      }
    ),

    // ── @include flattening ───────────────────────────────────────────────────

    suite("jsonSchema — @include flattening")(

      test("@include fields appear flat at top level of properties") {
        val props = documentContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(
          props.contains("title"),
          props.contains("createdAt"),
          props.contains("updatedAt")
        )
      },

      test("@include inner Long fields → type=integer") {
        val props = documentContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(
          props("createdAt").asInstanceOf[Map[String, Any]]("type") == "integer",
          props("updatedAt").asInstanceOf[Map[String, Any]]("type") == "integer"
        )
      }
    ),

    // ── @extract emits pattern ────────────────────────────────────────────────

    suite("jsonSchema — @extract")(

      test("@extract on a contract field appears as pattern in JSON Schema") {
        val props   = bookingContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        val checkIn = props("checkIn").asInstanceOf[Map[String, Any]]
        assertTrue(checkIn("pattern") == "\\d{4}-\\d{2}-\\d{2}")
      }
    ),

    // ── Format and numeric constraint annotations ──────────────────────────────

    suite("jsonSchema — format and numeric constraint annotations")(

      test("@url → format=uri") {
        val props = webLinkContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("href").asInstanceOf[Map[String, Any]]("format") == "uri")
      },

      test("@uuid → format=uuid") {
        val props = resourceContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("id").asInstanceOf[Map[String, Any]]("format") == "uuid")
      },

      test("@future → x-future=true") {
        val props = scheduledEventContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("startsAt").asInstanceOf[Map[String, Any]]("x-future") == true)
      },

      test("@past → x-past=true") {
        val props = scheduledEventContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("createdAt").asInstanceOf[Map[String, Any]]("x-past") == true)
      },

      test("@positive → exclusiveMinimum=0") {
        val props = measurementContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(
          props("value").asInstanceOf[Map[String, Any]]("exclusiveMinimum") == 0,
          props("count").asInstanceOf[Map[String, Any]]("exclusiveMinimum") == 0
        )
      },

      test("@multipleOf(0.01) → multipleOf=0.01") {
        val props = paymentContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("amount").asInstanceOf[Map[String, Any]]("multipleOf") == 0.01)
      },

      test("@multipleOf(5) → multipleOf=5.0") {
        val props = paymentContract.jsonSchema("properties").asInstanceOf[Map[String, Any]]
        assertTrue(props("quantity").asInstanceOf[Map[String, Any]]("multipleOf") == 5.0)
      }
    )
  )

  spec
