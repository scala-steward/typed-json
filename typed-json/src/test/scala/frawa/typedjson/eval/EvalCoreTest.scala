/*
 * Copyright 2021 Frank Wagner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package frawa.typedjson.eval

import frawa.typedjson.eval.CacheState.R
import frawa.typedjson.keywords.EvaluatedIndices
import frawa.typedjson.keywords.EvaluatedProperties
import frawa.typedjson.output.FlagOutput
import frawa.typedjson.output.FlagOutput.given
import frawa.typedjson.output.SimpleOutput
import frawa.typedjson.output.SimpleOutput.given
import frawa.typedjson.parser.Value.BoolValue
import frawa.typedjson.parser.Value.NullValue
import frawa.typedjson.parser.Value.StringValue
import frawa.typedjson.pointer.Pointer
import frawa.typedjson.testutil.TestSchemas._
import frawa.typedjson.testutil.TestUtil.{_, given}
import frawa.typedjson.util.WithPointer
import frawa.typedjson.validation._
import munit.FunSuite

class EvalCoreTest extends FunSuite:

  import Util.*

  private val evalBasic       = Eval[R, SimpleOutput]
  given Eval[R, SimpleOutput] = evalBasic

  private val evalFlag = Eval[R, FlagOutput]

  test("null") {
    given Eval[R, FlagOutput] = evalFlag

    withCompiledSchema(nullSchema) { fun =>
      assertEquals(doApply(fun, NullValue), FlagOutput(true))
      assertEquals(doApply(fun, BoolValue(true)), FlagOutput(false))
    }
  }

  test("true") {
    given Eval[R, FlagOutput] = evalFlag

    withCompiledSchema(trueSchema) { fun =>
      assertEquals(doApply(fun, BoolValue(true)), FlagOutput(true))
      assertEquals(doApply(fun, NullValue), FlagOutput(true))
    }
  }

  test("null with errors") {
    withCompiledSchema(nullSchema) { fun =>
      assertEquals(doApply(fun, NullValue), SimpleOutput(true))
      assertEquals(
        doApply(fun, BoolValue(true)),
        SimpleOutput(
          false,
          Seq(WithPointer(TypeMismatch("null")))
        )
      )
    }
  }

  test("false") {
    given Eval[R, FlagOutput] = evalFlag

    withCompiledSchema(falseSchema) { fun =>
      assertEquals(doApply(fun, BoolValue(true)), FlagOutput(false))
      assertEquals(doApply(fun, NullValue), FlagOutput(false))
      assertEquals(doApply(fun, parseJsonValue("13")), FlagOutput(false))
    }
  }

  test("false with errors") {
    withCompiledSchema(falseSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("{}")),
        SimpleOutput(false, Seq(WithPointer(FalseSchemaReason())))
      )
    }
  }

  test("boolean") {
    withCompiledSchema(boolSchema) { fun =>
      assertEquals(doApply(fun, parseJsonValue("true")), SimpleOutput(true))
      assertEquals(
        doApply(fun, parseJsonValue("13")),
        SimpleOutput(false, Seq(WithPointer(TypeMismatch("boolean"))))
      )
    }
  }

  test("true schema") {
    withCompiledSchema(trueSchema) { fun =>
      assertEquals(doApply(fun, parseJsonValue("null")), SimpleOutput(true))
      assertEquals(doApply(fun, parseJsonValue("13")), SimpleOutput(true))
      assertEquals(doApply(fun, parseJsonValue("{}")), SimpleOutput(true))
    }
  }

  test("false schema") {
    withCompiledSchema(falseSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(false, Seq(WithPointer(FalseSchemaReason())))
      )
      assertEquals(
        doApply(fun, parseJsonValue("13")),
        SimpleOutput(false, Seq(WithPointer(FalseSchemaReason())))
      )
      assertEquals(
        doApply(fun, parseJsonValue("{}")),
        SimpleOutput(false, Seq(WithPointer(FalseSchemaReason())))
      )
    }
  }

  test("not false") {
    given Eval[R, FlagOutput] = evalFlag

    withCompiledSchema(notFalseSchema) { fun =>
      assertEquals(doApply(fun, parseJsonValue("null")), FlagOutput(true))
      assertEquals(doApply(fun, parseJsonValue("13")), FlagOutput(true))
      assertEquals(doApply(fun, parseJsonValue("{}")), FlagOutput(true))
    }
  }

  test("empty schema") {
    withCompiledSchema(emtpySchema) { fun =>
      assertEquals(doApply(fun, parseJsonValue("null")), SimpleOutput(true))
      assertEquals(doApply(fun, parseJsonValue("13")), SimpleOutput(true))
      assertEquals(doApply(fun, parseJsonValue("{}")), SimpleOutput(true))
    }
  }

  test("not empty schema") {
    withCompiledSchema("""{"not": {}}""") { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(false, Seq(WithPointer(NotInvalid())))
      )
      assertEquals(
        doApply(fun, parseJsonValue("13")),
        SimpleOutput(false, Seq(WithPointer(NotInvalid())))
      )
      assertEquals(
        doApply(fun, parseJsonValue("{}")),
        SimpleOutput(false, Seq(WithPointer(NotInvalid())))
      )
    }
  }

  test("string") {
    withCompiledSchema(stringSchema) { fun =>
      assertEquals(doApply(fun, parseJsonValue(""""hello"""")), SimpleOutput(true))
      assertEquals(
        doApply(fun, parseJsonValue("13")),
        SimpleOutput(false, Seq(WithPointer(TypeMismatch("string"))))
      )
    }
  }

  test("number") {
    withCompiledSchema(numberSchema) { fun =>
      assertEquals(doApply(fun, parseJsonValue("13")), SimpleOutput(true))
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(false, Seq(WithPointer(TypeMismatch("number"))))
      )
    }
  }

  test("array") {
    withCompiledSchema(numberArraySchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("[13]")),
        SimpleOutput(true, annotations = Seq(EvaluatedIndices(Set(0))))
      )
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(false, Seq(WithPointer(TypeMismatch("array"))))
      )
    }
  }

  test("array items") {
    withCompiledSchema(numberArraySchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(false, Seq(WithPointer(TypeMismatch("array"))))
      )
      assertEquals(
        doApply(fun, parseJsonValue("[13]")),
        SimpleOutput(true, annotations = Seq(EvaluatedIndices(Set(0))))
      )
      assertEquals(
        doApply(fun, parseJsonValue("[true]")),
        SimpleOutput(
          false,
          Seq(WithPointer(TypeMismatch("number"), Pointer.empty / 0))
        )
      )
    }
  }

  test("object") {
    withCompiledSchema(totoObjectSchema) { fun =>
      assertEquals(
        doApply(
          fun,
          parseJsonValue("""{
                           |"toto": 13,
                           |"titi": "hello"
                           |}
                           |""".stripMargin)
        ),
        SimpleOutput(true, annotations = Seq(EvaluatedProperties(Set("toto", "titi"))))
      )
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(false, Seq(WithPointer(TypeMismatch("object"))))
      )
    }
  }

  test("object with pointer") {
    withCompiledSchema(totoObjectSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("""{"toto": 13,"titi": true}""")),
        SimpleOutput(
          false,
          Seq(WithPointer(TypeMismatch("string"), Pointer.empty / "titi"))
        )
      )
    }
  }

  test("object missing property") {
    withCompiledSchema(totoObjectSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("""{"toto": 13}""")),
        SimpleOutput(true, annotations = Seq(EvaluatedProperties(Set("toto"))))
      )
    }
  }

  test("object missing required property") {
    withCompiledSchema("""{
                         |"type": "object",
                         |"properties": {
                         |  "toto": { "type": "number" },
                         |  "titi": { "type": "string" }
                         |},
                         |"required": ["titi"]
                         |}
                         |""".stripMargin) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("""{"toto": 13}""")),
        SimpleOutput(
          false,
          Seq(WithPointer(MissingRequiredProperties(Seq("titi"))))
        )
      )
    }
  }

  test("all of") {
    withCompiledSchema(allOfSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("13")),
        SimpleOutput(true)
      )
    }
  }

  test("impossible all of") {
    withCompiledSchema("""{
                         |"allOf": [
                         |  { "type": "number" },
                         |  { "type": "string" }
                         |]
                         |}
                         |""".stripMargin) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(false, Seq(WithPointer(TypeMismatch("string"))))
      )
    }
  }

  test("any of") {
    withCompiledSchema(anyOfSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue("true")),
        SimpleOutput(
          false,
          Seq(
            WithPointer(TypeMismatch("number")),
            WithPointer(TypeMismatch("string"))
          )
        )
      )
    }
  }

  test("one of") {
    withCompiledSchema(oneOfSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(true)
      )
    }
  }

  test("failed one of: none") {
    withCompiledSchema("""{
                         |"oneOf": [
                         |  { "type": "string" },
                         |  { "type": "boolean" }
                         |]
                         |}
                         |""".stripMargin) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(
          false,
          Seq(WithPointer(TypeMismatch("string")), WithPointer(TypeMismatch("boolean")))
        )
      )
    }
  }

  test("failed one of: two") {
    withCompiledSchema("""{
                         |"oneOf": [
                         |  { "type": "number" },
                         |  { "type": "number" }
                         |]
                         |}
                         |""".stripMargin) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(false, Seq(WithPointer(NotOneOf(2))))
      )
    }
  }

  test("not") {
    withCompiledSchema("""{"not": { "type": "number" }}""") { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("true")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(false, Seq(WithPointer(NotInvalid())))
      )
    }
  }

  test("if/then/else") {
    withCompiledSchema(ifThenElseSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue(""""string"""")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(false, Seq(WithPointer(TypeMismatch("string"))))
      )
    }
  }

  test("if/else") {
    withCompiledSchema("""{
                         |"if": { "type": "number" },
                         |"else": { "type": "string" }
                         |}
                         |""".stripMargin) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue(""""string"""")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(false, Seq(WithPointer(TypeMismatch("string"))))
      )
    }
  }

  test("if/then") {
    withCompiledSchema("""{
                         |"if": { "type": "number" },
                         |"then": { "type": "number" }
                         |}
                         |""".stripMargin) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue(""""string"""")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(true)
      )
    }
  }

  test("then/else") {
    withCompiledSchema("""{
                         |"then": { "type": "number" },
                         |"else": { "type": "string" }
                         |}
                         |""".stripMargin) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("1313")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue(""""string"""")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(true)
      )
    }
  }

  test("null or string") {
    withCompiledSchema(nullOrStringSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue("null")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue(""""hello"""")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue("13")),
        SimpleOutput(
          false,
          Seq(WithPointer(TypeMismatch("null")), WithPointer(TypeMismatch("string")))
        )
      )
    }
  }

  test("enum") {
    withCompiledSchema(enumSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue(""""foo"""")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue(""""bar"""")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue(""""hello"""")),
        SimpleOutput(
          false,
          Seq(
            WithPointer(NotInEnum(Seq(StringValue("foo"), StringValue("bar"))))
          )
        )
      )
    }
  }

  test("const") {
    withCompiledSchema(constSchema) { fun =>
      assertEquals(
        doApply(fun, parseJsonValue(""""first"""")),
        SimpleOutput(true)
      )
      assertEquals(
        doApply(fun, parseJsonValue("{}")),
        SimpleOutput(
          false,
          Seq(
            WithPointer(NotInEnum(Seq(StringValue("first")))),
            WithPointer(TypeMismatch("string"))
          )
        )
      )
      assertEquals(
        doApply(fun, parseJsonValue(""""second"""")),
        SimpleOutput(
          false,
          Seq(
            WithPointer(NotInEnum(Seq(StringValue("first"))))
          )
        )
      )
    }
  }
