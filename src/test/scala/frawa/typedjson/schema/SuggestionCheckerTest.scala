package frawa.typedjson.schema

import munit.FunSuite
import frawa.typedjson.parser.ZioParser
import frawa.typedjson.parser.Parser
import frawa.typedjson.parser.{ObjectValue, NullValue, NumberValue, StringValue, ArrayValue, BoolValue}
import TestUtil._
import TestSchemas._
import frawa.typedjson.parser.Value

class SuggestCheckerTest extends FunSuite {
  implicit val zioParser = new ZioParser();

  private def assertSuggest(text: String, at: Pointer = Pointer.empty)(schema: SchemaValue)(
      f: Seq[Value] => Unit
  ) = {
    val withParsed = for {
      value     <- Parser(text)
      processor <- Processor(schema)(SuggestionChecker(at))
      result = processor.process(InnerValue(value, Pointer.empty))
    } yield {
      // TODO avoid the flattening
      f(result.results.flatMap(_.suggestions).distinct)
    }
    withParsed.swap
      .map(message => fail("parsing failed", clues(clue(message))))
      .swap
  }

  test("suggest property") {
    withSchema(totoObjectSchema) { schema =>
      assertSuggest(
        """{
          |"toto": 13
          |}
          |""".stripMargin
      )(
        schema
      ) { result =>
        assertEquals(
          result,
          Seq(
            ObjectValue(Map()),
            ObjectValue(Map("toto" -> NumberValue(0))),
            ObjectValue(Map("titi" -> StringValue("")))
          )
        )
      }
    }
  }

  test("suggest deep") {
    withSchema("""{
                 |"$id": "testme",
                 |"type": "object", 
                 |"properties": { 
                 |  "foo": { 
                 |    "type": "object", 
                 |    "properties": { 
                 |      "bar": { "type": "number" }
                 |    }
                 |  }
                 |} 
                 |}
                 |""".stripMargin) { schema =>
      assertSuggest(
        """{
          |"foo": {}
          |}
          |""".stripMargin
      )(
        schema
      ) { result =>
        assertEquals(
          result,
          Seq(
            ObjectValue(Map()),
            ObjectValue(
              properties = Map(
                "foo" -> ObjectValue(
                  properties = Map()
                )
              )
            ),
            ObjectValue(Map("foo" -> ObjectValue(Map("bar" -> NumberValue(0)))))
          )
        )
      }
    }
  }

  test("suggest several values") {
    withSchema("""{
                 |"$id": "testme",
                 |"type": "object", 
                 |"properties": { 
                 |  "foo": { 
                 |    "type": "object", 
                 |    "properties": { 
                 |      "bar": { "type": "number" }
                 |    }
                 |  },
                 |  "gnu": { 
                 |    "type": "object", 
                 |    "properties": { 
                 |      "toto": { "type": "string" }
                 |    }
                 |  }
                 |} 
                 |}
                 |""".stripMargin) { schema =>
      assertSuggest(
        """{
          |"foo": {}
          |}
          |""".stripMargin
      )(
        schema
      ) { result =>
        assertEquals(
          result,
          Seq(
            ObjectValue(Map()),
            ObjectValue(
              properties = Map(
                "foo" -> ObjectValue(
                  properties = Map()
                )
              )
            ),
            ObjectValue(
              Map(
                "foo" -> ObjectValue(
                  Map(
                    "bar" ->
                      NumberValue(0)
                  )
                )
              )
            ),
            ObjectValue(
              Map(
                "gnu" ->
                  ObjectValue(Map())
              )
            ),
            ObjectValue(
              properties = Map(
                "gnu" -> ObjectValue(
                  properties = Map(
                    "toto" -> StringValue(
                      value = ""
                    )
                  )
                )
              )
            )
          )
        )
      }
    }
  }

  test("suggestions for several properties") {
    withSchema("""{
                 |"$id": "testme",
                 |"type": "object", 
                 |"properties": { 
                 |  "foo": { 
                 |    "type": "object", 
                 |    "properties": { 
                 |      "bar": { "type": "number" },
                 |      "gnu": { "type": "number" }
                 |    }
                 |  }
                 |} 
                 |}
                 |""".stripMargin) { schema =>
      assertSuggest(
        """{
          |"foo": {}
          |}
          |""".stripMargin
      )(
        schema
      ) { result =>
        assertEquals(
          result,
          Seq(
            ObjectValue(
              properties = Map()
            ),
            ObjectValue(
              properties = Map(
                "foo" -> ObjectValue(
                  properties = Map()
                )
              )
            ),
            ObjectValue(
              Map(
                "foo" -> ObjectValue(
                  Map(
                    "bar" ->
                      NumberValue(0)
                  )
                )
              )
            ),
            ObjectValue(
              Map(
                "foo" -> ObjectValue(
                  Map(
                    "gnu" ->
                      NumberValue(0)
                  )
                )
              )
            )
          )
        )
      }
    }
  }

  test("null or number") {
    withSchema("""{
                 |"$id": "testme",
                 |"type": ["null","number"]
                 |}""".stripMargin) { schema =>
      assertSuggest("""13""")(schema) { result =>
        assertEquals(
          result,
          Seq(NullValue, NumberValue(0))
        )
      }
      assertSuggest("""true""")(schema) { result =>
        assertEquals(
          result,
          Seq(NullValue, NumberValue(0))
        )
      }
    }
  }

  test("enum") {
    withSchema("""{
                 |"$id": "testme",
                 |"type": "number",
                 |"enum": [13, 14]                
                 |}""".stripMargin) { schema =>
      assertSuggest("""true""")(schema) { result =>
        assertEquals(
          result,
          Seq(NumberValue(0), NumberValue(13), NumberValue(14))
        )
      }
      assertSuggest("""13""")(schema) { result =>
        assertEquals(
          result,
          Seq(NumberValue(0), NumberValue(13), NumberValue(14))
        )
      }
    }
  }

  test("const") {
    withSchema("""{
                 |"$id": "testme",
                 |"type": "boolean",
                 |"const": true               
                 |}""".stripMargin) { schema =>
      assertSuggest("""true""")(schema) { result =>
        assertEquals(
          result,
          Seq(BoolValue(true))
        )
      }
      assertSuggest("""13""")(schema) { result =>
        assertEquals(
          result,
          Seq(BoolValue(true))
        )
      }
    }
  }

  test("discriminator") {
    withSchema("""{
                 |"$id": "testme",
                 |"oneOf": [{
                 |  "if": { 
                 |    "type": "object",
                 |    "properties": { 
                 |      "kind": { "type": "string", "const": "first" }
                 |    }
                 |  },
                 |  "then": {
                 |    "type": "object",
                 |    "properties": { 
                 |      "gnu": { "type": "number" }
                 |    }
                 |  }
                 |},{
                 |  "if": { 
                 |    "type": "object",
                 |    "properties": { 
                 |      "kind": { "type": "string", "const": "second" }
                 |    }
                 |  },
                 |  "then": {
                 |    "type": "object",
                 |    "properties": { 
                 |      "foo": { "type": "boolean" }
                 |    }
                 |  }
                 |}]
                 |}""".stripMargin) { schema =>
      assertSuggest("""{}""".stripMargin)(schema) { result =>
        assertEquals(
          result,
          Seq(
            ObjectValue(
              properties = Map()
            ),
            ObjectValue(
              properties = Map(
                "kind" -> StringValue(
                  value = ""
                )
              )
            ),
            ObjectValue(
              properties = Map(
                "kind" -> StringValue(
                  value = "first"
                )
              )
            ),
            ObjectValue(
              properties = Map(
                "gnu" -> NumberValue(
                  value = 0
                )
              )
            ),
            ObjectValue(
              properties = Map(
                "kind" -> StringValue(
                  value = "second"
                )
              )
            ),
            ObjectValue(
              properties = Map(
                "foo" -> BoolValue(
                  value = true
                )
              )
            )
          )
        )
      }
      assertSuggest("""{"kind":"first"}""")(schema) { result =>
        assertEquals(
          result,
          Seq(
            ObjectValue(
              properties = Map()
            ),
            ObjectValue(
              properties = Map(
                "kind" -> StringValue(
                  value = ""
                )
              )
            ),
            ObjectValue(
              properties = Map(
                "kind" -> StringValue(
                  value = "first"
                )
              )
            ),
            ObjectValue(
              properties = Map(
                "gnu" -> NumberValue(
                  value = 0
                )
              )
            ),
            ObjectValue(
              properties = Map(
                "kind" -> StringValue(
                  value = "second"
                )
              )
            ),
            ObjectValue(
              properties = Map(
                "foo" -> BoolValue(
                  value = true
                )
              )
            )
          )
        )
      }
    }
  }

  test("suggestions enum properties") {
    withSchema("""{
                 |"$id": "testme",
                 |"type": "object", 
                 |"properties": { 
                 |  "foo": { 
                 |    "type": "object", 
                 |    "properties": { 
                 |      "bar": { "type": "number", "enum": [13, 14] },
                 |      "gnu": { "type": "string", "enum": ["toto", "titi"] }
                 |    }
                 |  }
                 |} 
                 |}
                 |""".stripMargin) { schema =>
      assertSuggest(
        """{
          |"foo": {}
          |}
          |""".stripMargin
      )(
        schema
      ) { result =>
        assertEquals(
          result,
          Seq(
            ObjectValue(
              properties = Map()
            ),
            ObjectValue(Map("foo" -> ObjectValue(Map()))),
            ObjectValue(Map("foo" -> ObjectValue(Map("bar" -> NumberValue(0))))),
            ObjectValue(Map("foo" -> ObjectValue(Map("bar" -> NumberValue(13))))),
            ObjectValue(Map("foo" -> ObjectValue(Map("bar" -> NumberValue(14))))),
            ObjectValue(Map("foo" -> ObjectValue(Map("gnu" -> StringValue(""))))),
            ObjectValue(Map("foo" -> ObjectValue(Map("gnu" -> StringValue("toto"))))),
            ObjectValue(Map("foo" -> ObjectValue(Map("gnu" -> StringValue("titi")))))
          )
        )
      }
    }
  }

  test("suggest required property") {
    withSchema(totoRequiredObjectSchema) { schema =>
      assertSuggest(
        """{
          |"toto": 13
          |}
          |""".stripMargin
      )(
        schema
      ) { result =>
        assertEquals(
          result,
          Seq(
            ObjectValue(Map()),
            ObjectValue(
              properties = Map(
                "toto" -> NullValue,
                "gnu"  -> NullValue
              )
            ),
            ObjectValue(Map("toto" -> NumberValue(0))),
            ObjectValue(Map("gnu" -> BoolValue(true))),
            ObjectValue(Map("titi" -> StringValue("")))
          )
        )
      }
    }
  }

  test("suggest array") {
    withSchema(numberArraySchema) { schema =>
      assertSuggest(
        """[]""".stripMargin
      )(
        schema
      ) { result =>
        assertEquals(
          result,
          Seq(
            ArrayValue(Seq())
          )
        )
      }
    }
  }

  test("suggest at inside object") {
    withSchema(totoObjectSchema) { schema =>
      assertSuggest(
        """{
          |"toto": 13
          |}
          |""".stripMargin,
        Pointer.empty / "toto"
      )(
        schema
      ) { result =>
        assertEquals(
          result,
          Seq(
            NumberValue(0)
          )
        )
      }
    }
  }

  test("suggest item inside array") {
    withSchema(numberArraySchema) { schema =>
      assertSuggest(
        """[ 13, 14 ]""".stripMargin,
        Pointer.empty / 1
      )(
        schema
      ) { result =>
        assertEquals(
          result,
          Seq(
            NumberValue(0)
          )
        )
      }
    }
  }
}