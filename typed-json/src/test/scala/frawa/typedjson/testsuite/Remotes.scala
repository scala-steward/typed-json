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

package frawa.typedjson.testsuite

import frawa.typedjson.keywords.LoadedSchemasResolver
import frawa.typedjson.keywords.RootSchemaValue
import frawa.typedjson.keywords.SchemaValue
import frawa.typedjson.parser._
import frawa.typedjson.schematestsuite.data.Draft202012
import frawa.typedjson.util.UriUtil

import java.net.URI

object Remotes:
  val remotesUri: URI = UriUtil.uri("http://localhost:1234")

  def lazyResolver(using Parser): LoadedSchemasResolver.LazyResolver = { uri =>
    if uri.getSchemeSpecificPart.startsWith(remotesUri.getSchemeSpecificPart) then
      resolveRemotes(remotesUri.relativize(uri))
    else None
  }

  private def resolveRemotes(relative: URI)(using parser: Parser): Option[RootSchemaValue] =
    if relative.getFragment != null then None
    else
      val name = relative.getSchemeSpecificPart
      val json = Draft202012.remotesFiles.get(name)
      json
        .map(parser.parse)
        .map {
          case Left(error)  => throw IllegalArgumentException(s"broken remote ${error}")
          case Right(value) => value
        }
        .map(SchemaValue.root)
      // remotesFiles.get(name).map(SchemaValue.root)

  import frawa.typedjson.macros.Macros.*

  // private val remotesFiles = inlineJsonContents("./JSON-Schema-Test-Suite/remotes", ".json")
