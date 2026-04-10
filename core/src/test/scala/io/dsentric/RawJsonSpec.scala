package io.dsentric

final class RawJsonSpec extends SpecBase:

/**
 * Tests for [[RawJson]] — the built-in zero-dependency JSON serialiser.
 *
 * Also covers the [[Contract.sanitizeJson]] and [[Contract.toJson]]
 * convenience methods which delegate here.
 */
  // ── Helpers ────────────────────────────────────────────────────────────────

  /** Strip all whitespace for compact comparison. */
  def compact(s: String): String = s.replaceAll("\\s+", "")

  def spec: Unit = suite("RawJsonSpec")(

    // ── Scalar values ────────────────────────────────────────────────────────

    suite("scalar values")(

      test("String is quoted and escaped") {
        val raw = Map[String, Any]("k" -> "hello \"world\"\nnewline")
        val json = RawJson.stringify(raw)
        assertTrue(json == """{"k":"hello \"world\"\nnewline"}""")
      },

      test("Int serialises as an integer literal") {
        assertTrue(RawJson.stringify(Map("n" -> 42)) == """{"n":42}""")
      },

      test("Long serialises as an integer literal") {
        assertTrue(RawJson.stringify(Map("n" -> 9999999999L)) == """{"n":9999999999}""")
      },

      test("Double serialises without trailing zeros") {
        assertTrue(RawJson.stringify(Map("n" -> 3.14)) == """{"n":3.14}""")
      },

      test("Boolean true and false") {
        val raw = Map[String, Any]("a" -> true, "b" -> false)
        val json = RawJson.stringify(raw)
        assertTrue(json.contains("\"a\":true"), json.contains("\"b\":false"))
      },

      test("NaN Double serialises as null") {
        assertTrue(RawJson.stringify(Map("n" -> Double.NaN)) == """{"n":null}""")
      },

      test("Infinite Double serialises as null") {
        assertTrue(RawJson.stringify(Map("n" -> Double.PositiveInfinity)) == """{"n":null}""")
      }
    ),

    // ── Optional / null handling ──────────────────────────────────────────────

    suite("None / null / Some handling")(

      test("None value is omitted from the object") {
        val raw = Map[String, Any]("a" -> "present", "b" -> None)
        val json = RawJson.stringify(raw)
        assertTrue(json.contains("\"a\""), !json.contains("\"b\""))
      },

      test("null value is omitted from the object") {
        val raw = Map[String, Any]("a" -> "present", "b" -> null)
        val json = RawJson.stringify(raw)
        assertTrue(!json.contains("\"b\""))
      },

      test("Some(v) unwraps and serialises v") {
        val raw = Map[String, Any]("bio" -> Some("Loves Scala"))
        assertTrue(RawJson.stringify(raw) == """{"bio":"Loves Scala"}""")
      },

      test("None inside an array serialises as null") {
        val raw = Map[String, Any]("items" -> List("a", None, "b"))
        val json = RawJson.stringify(raw)
        assertTrue(json == """{"items":["a",null,"b"]}""")
      },

      test("java.util.Optional.empty() serialises as null") {
        val raw = Map[String, Any]("opt" -> java.util.Optional.empty[String]())
        assertTrue(RawJson.stringify(raw) == """{"opt":null}""")
      },

      test("java.util.Optional.of(v) unwraps and serialises v") {
        val raw = Map[String, Any]("opt" -> java.util.Optional.of("hello"))
        assertTrue(RawJson.stringify(raw) == """{"opt":"hello"}""")
      }
    ),

    // ── Collections ───────────────────────────────────────────────────────────

    suite("collections")(

      test("List[Int] serialises as JSON array") {
        val raw = Map[String, Any]("scores" -> List(1, 2, 3))
        assertTrue(RawJson.stringify(raw) == """{"scores":[1,2,3]}""")
      },

      test("empty List serialises as []") {
        val raw = Map[String, Any]("tags" -> List.empty[String])
        assertTrue(RawJson.stringify(raw) == """{"tags":[]}""")
      },

      test("nested Map serialises as a JSON object") {
        val raw = Map[String, Any](
          "address" -> Map("city" -> "London", "zip" -> "EC1A")
        )
        val json = RawJson.stringify(raw)
        assertTrue(
          json.contains("\"address\""),
          json.contains("\"city\":\"London\""),
          json.contains("\"zip\":\"EC1A\"")
        )
      }
    ),

    // ── Key ordering ──────────────────────────────────────────────────────────

    suite("key ordering")(

      test("keys are sorted alphabetically for deterministic output") {
        val raw = Map[String, Any]("z" -> 3, "a" -> 1, "m" -> 2)
        assertTrue(RawJson.stringify(raw) == """{"a":1,"m":2,"z":3}""")
      }
    ),

    // ── Pretty print ──────────────────────────────────────────────────────────

    suite("pretty print")(

      test("indent=2 produces multi-line output") {
        val raw  = Map[String, Any]("name" -> "Alice", "age" -> 30)
        val json = RawJson.stringify(raw, indent = 2)
        assertTrue(
          json.contains("\n"),
          json.contains("  \"age\""),
          json.contains("  \"name\"")
        )
      },

      test("compact and pretty produce equivalent data (whitespace stripped)") {
        val raw     = Map[String, Any]("name" -> "Alice", "age" -> 30, "scores" -> List(1, 2, 3))
        val compact = RawJson.stringify(raw)
        val pretty  = RawJson.stringify(raw, indent = 2)
        assertTrue(compact == pretty.replaceAll("\\s+", "").replace("\n", ""))
      }
    ),

    // ── Empty map ─────────────────────────────────────────────────────────────

    suite("edge cases")(

      test("empty RawObject produces {}") {
        assertTrue(RawJson.stringify(Map.empty[String, Any]) == "{}")
      },

      test("object with all-None values produces {}") {
        val raw = Map[String, Any]("a" -> None, "b" -> null)
        assertTrue(RawJson.stringify(raw) == "{}")
      }
    ),

    // ── Contract convenience methods ──────────────────────────────────────────

    suite("Contract.toJson and Contract.sanitizeJson")(

      test("toJson round-trips a constructed User") {
        // User: id: Long, name: String, email: Option[String], age: Int, password: Option[String]
        val user = User(id = 1L, name = "Alice", email = Some("alice@example.com"),
                        age = 30, password = None)
        val json = userContract.toJson(user)
        assertTrue(
          json.contains("\"name\":\"Alice\""),
          json.contains("\"email\":\"alice@example.com\""),
          json.contains("\"id\":1"),
          !json.contains("\"password\"")  // None → omitted
        )
      },

      test("sanitizeJson strips @internal and masks @masked") {
        val raw: RawObject = Map(
          "id"       -> 1L,
          "name"     -> "Alice",
          "email"    -> Some("alice@example.com"),
          "age"      -> 30,
          "password" -> Some("s3cr3t")
        )
        val json = userContract.sanitizeJson(raw)
        assertTrue(
          !json.contains("\"id\""),               // @internal → stripped
          json.contains("\"name\""),
          json.contains("\"password\":\"***\"")   // @masked → replaced
        )
      },

      test("toJson with indent produces indented output") {
        val user = User(id = 1L, name = "Alice", email = Some("alice@example.com"),
                        age = 30, password = None)
        val json = userContract.toJson(user, 2)
        assertTrue(json.contains("\n"), json.contains("  \"name\""))
      },

      test("sanitizeJson with indent produces indented output") {
        val raw: RawObject = Map("id" -> 1L, "name" -> "Alice",
                                 "email" -> Some("alice@example.com"), "age" -> 30)
        val json = userContract.sanitizeJson(raw, 2)
        assertTrue(json.contains("\n"), json.contains("  \"name\""))
      }
    )
  )

  spec
