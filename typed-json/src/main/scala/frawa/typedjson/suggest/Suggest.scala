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

package frawa.typedjson.suggest

import frawa.typedjson.pointer.Pointer
import frawa.typedjson.parser.Value
import frawa.typedjson.parser.Value.*
import frawa.typedjson.keywords.Keyword
import frawa.typedjson.keywords.DynamicScope
import java.net.URI
import frawa.typedjson.keywords.*
import frawa.typedjson.suggest.SuggestOutput

object Suggest:
  def suggestAt[R[_], O](at: Pointer)(compiled: Value => R[O]): Value => R[O] =
    // TODO stop evaluation as soon as 'at' is reached
    compiled

  def isAt(at: Pointer): Pointer => Boolean = pointer => (at.isInsideKey && at.outer == pointer) || at == pointer

  def suggestions(at: Pointer, output: SuggestOutput): Seq[Value] =
    val all = output.keywords.flatMap(suggestFor).distinct
    // TODO use more precise replaceAt for better suggestions
    if at.isInsideKey then onlyKeys(all)
    else all

  private def suggestFor(keyword: Keyword): Set[Value] =
    keyword match
      case NullTypeKeyword    => Set(NullValue)
      case BooleanTypeKeyword => Set(BoolValue(true))
      case StringTypeKeyword  => Set(StringValue(""))
      case NumberTypeKeyword  => Set(NumberValue(0))
      case ArrayTypeKeyword   => Set(ArrayValue(Seq()))
      case ObjectTypeKeyword  => Set(ObjectValue(Map()))
      case ObjectPropertiesKeyword(properties, _, _) =>
        properties.flatMap { case (prop, keywords) =>
          keywords
            .flatMap(keyword => suggestFor(keyword).toSet)
            .map(v => ObjectValue(Map(prop -> v)))
        }.toSet
      case ObjectRequiredKeyword(required) => Set(ObjectValue(Map.from(required.map((_, NullValue)))))
      case TrivialKeyword(v)               => Set(BoolValue(v))
      case IfThenElseKeyword(ifChecks, thenChecks, elseChecks) =>
        Set(ifChecks, thenChecks, elseChecks).flatten
          .flatMap(_.flatMap(keyword => suggestFor(keyword).toSet))
      case OneOfKeyword(keywords) =>
        keywords
          .flatMap(_.flatMap(keyword => suggestFor(keyword)))
          .toSet
      case AnyOfKeyword(keywords) =>
        keywords
          .flatMap(_.flatMap(keyword => suggestFor(keyword)))
          .toSet
      case AllOfKeyword(keywords) =>
        // TODO intersect?
        keywords
          .flatMap(_.flatMap(keyword => suggestFor(keyword)))
          .toSet
      case EnumKeyword(values) => values.toSet
      case ArrayItemsKeyword(items, prefixItems) =>
        val itemArrays = Seq(items).flatten
          .flatMap(_.flatMap(keyword => suggestFor(keyword).toSet))
          .map(v => ArrayValue(Seq(v)))
        // TODO combinations? might explode?
        val tuplesOfHeads = ArrayValue(
          prefixItems
            .map(_.flatMap(keyword => suggestFor(keyword).toSet))
            .map(_.headOption)
            .map(_.getOrElse(NullValue))
        )
        (itemArrays :+ tuplesOfHeads).toSet
      case UnionTypeKeyword(keywords) =>
        keywords
          .flatMap(keyword => suggestFor(keyword))
          .toSet
      case WithLocation(keyword, _) => suggestFor(keyword)
      case _                        =>
        // useful for debugging:
        // Seq(StringValue(keyword.getClass.getSimpleName))
        Set(NullValue)

  private def onlyKeys(suggestions: Seq[Value]): Seq[StringValue] =
    suggestions.flatMap(Value.asObject).flatMap(_.keys).map(StringValue.apply).distinct