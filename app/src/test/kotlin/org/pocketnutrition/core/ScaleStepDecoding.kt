// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes the [ServingUnit] and [ScaleStep] shapes out of the untyped corpus.
 *
 * Shared by the build-anchors and step-label suites, and by the three still to come that speak the
 * same shapes — step-raw-weight, shortcut-profile and describe-quantity. It lives beside the tests
 * rather than in [VectorCorpus] because the reader is deliberately suite-agnostic: it knows the
 * corpus structure and nothing about any individual suite's payload.
 */
object ScaleStepDecoding {

    fun localizedText(element: JsonElement): LocalizedText {
        val raw = element.jsonObject
        return LocalizedText(
            lang = requireNotNull(raw["lang"]).jsonPrimitive.content,
            text = requireNotNull(raw["text"]).jsonPrimitive.content,
        )
    }

    /**
     * Tolerant of the same two shapes [ServingUnitSerializer] is — see that serializer's comment.
     * The pre-2.0.0 `build-anchors`/`shortcut-profile`/`step-label`/`step-raw-weight` suites still
     * carry `labelFr`/`labelEn`; the `-en` siblings added in 2.0.0 carry the generic `labels` array.
     */
    fun servingUnit(element: JsonElement): ServingUnit {
        val raw = element.jsonObject
        val labels = raw["labels"]?.jsonArray?.map(::localizedText)
            ?: buildList {
                raw["labelFr"]?.jsonPrimitive?.contentOrNull?.let { add(LocalizedText("fr", it)) }
                raw["labelEn"]?.jsonPrimitive?.contentOrNull?.let { add(LocalizedText("en", it)) }
            }
        return ServingUnit(
            unit = requireNotNull(raw["unit"]).jsonPrimitive.content,
            labels = labels,
            volumeMl = requireNotNull(raw["volumeMl"]).jsonPrimitive.double,
            grams = requireNotNull(raw["grams"]).jsonPrimitive.double,
        )
    }

    /**
     * `emoji` is ABSENT rather than null for an unrecognised unit, so a missing key decodes to null
     * and the two are indistinguishable here — which matches how the function models it.
     */
    fun shortcut(element: JsonElement): Shortcut {
        val raw = element.jsonObject
        return when (val kind = requireNotNull(raw["kind"]).jsonPrimitive.content) {
            "multiplier" -> Shortcut.Multiplier(
                label = requireNotNull(raw["label"]).jsonPrimitive.content,
                factor = requireNotNull(raw["factor"]).jsonPrimitive.double,
            )
            "increment" -> Shortcut.Increment(
                label = requireNotNull(raw["label"]).jsonPrimitive.content,
                emoji = nullableString(raw["emoji"]),
                grams = requireNotNull(raw["grams"]).jsonPrimitive.int,
            )
            else -> error("unknown shortcut kind '$kind' in the corpus")
        }
    }

    fun anchor(element: JsonElement): QuantityAnchor {
        val raw = element.jsonObject
        return QuantityAnchor(
            label = requireNotNull(raw["label"]).jsonPrimitive.content,
            grams = requireNotNull(raw["grams"]).jsonPrimitive.double,
        )
    }

    fun quantityContext(element: JsonElement): QuantityContext {
        val raw = element.jsonObject
        return QuantityContext(
            servingMode = nullableString(raw["servingMode"]),
            itemUnitG = raw["itemUnitG"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.double,
            itemLabel = nullableString(raw["itemLabel"]),
            portionG = requireNotNull(raw["portionG"]).jsonPrimitive.double,
            displayUnit = requireNotNull(raw["displayUnit"]).jsonPrimitive.content,
        )
    }

    fun step(element: JsonElement): ScaleStep {
        val raw = element.jsonObject
        return when (val kind = requireNotNull(raw["kind"]).jsonPrimitive.content) {
            "factor" -> ScaleStep.FactorStep(
                factor = requireNotNull(raw["factor"]).jsonPrimitive.double,
                label = requireNotNull(raw["label"]).jsonPrimitive.content,
            )
            "unit" -> ScaleStep.UnitStep(
                unit = servingUnit(requireNotNull(raw["unit"])),
                count = requireNotNull(raw["count"]).jsonPrimitive.double,
            )
            else -> error("unknown step kind '$kind' in the corpus")
        }
    }

    /**
     * `servingMode` is JSON null in two build-anchors cases and must stay null rather than becoming
     * the string "null" — a port that stringifies it branches on a mode that never matches "item",
     * which happens to give the right answer for those two cases and hides the bug.
     */
    fun nullableString(element: JsonElement?): String? =
        if (element == null || element is JsonNull) null else element.jsonPrimitive.content
}
