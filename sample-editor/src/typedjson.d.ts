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
declare module "typedjson" {

    export class TypedJsonFactory {
        static create(): TypedJson
        static withMetaSchema(): TypedJson
    }

    export class TypedJson {
        withSchema(typedJson: TypedJson): TypedJson
        forValue(json: string): TypedJson
        markers(): Marker[]
        suggestionsAt(offset: number): SuggestsResult | undefined
    }

    export interface Marker {
        start: number
        end: number
        pointer: string
        message: string
        severity: string
    }

    export interface SuggestsResult {
        range: TextRange
        pointer: string
        suggestions: Suggestion[]
    }

    export interface Suggestion {
        value: unknown
        replace: TextRange
        seperator?: string
        documentationMarkdown?: string
    }

    export interface TextRange {
        start: number
        end: number
    }
}