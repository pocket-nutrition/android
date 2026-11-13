// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts the daily-targets port ([dailyTargets], [weightForDay], [profileForDay], [applyOverrides],
 * [dayProgress], [selfComparison]) against its six suites of the shared corpus — the same JSON the
 * sandbox and iOS assert against.
 *
 * Every case is run (the count is checked against index.json, so a skipped id fails), and results are
 * compared as JSON trees: numbers by value, objects by key set, arrays in order. An absent optional
 * field and an explicit null are therefore different, which `profile-for-day` relies on.
 */
class DailyTargetsVectorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun check(name: String, actual: (VectorCorpus.Case) -> JsonElement) {
        val suite = VectorCorpus.load(name)
        assertEquals("$name case count disagrees with index.json", VectorCorpus.index().suites[name], suite.cases.size)
        assertTrue("the $name suite is empty", suite.cases.isNotEmpty())
        var ran = 0
        val mismatches = suite.cases.mapNotNull { case ->
            ran++
            val got = actual(case)
            if (sameJson(got, case.expected)) null else "$name/${case.id}:\n  got      $got\n  expected ${case.expected}\n  — ${case.why}"
        }
        assertEquals("$name: every case must run", suite.cases.size, ran)
        assertEquals("$name vectors that do not hold", emptyList<String>(), mismatches)
    }

    // ── decoding ─────────────────────────────────────────────────────────────

    private fun JsonObject.str(key: String): String = requireNotNull(this[key]) { "missing $key" }.jsonPrimitive.content
    private fun JsonObject.dbl(key: String): Double = requireNotNull(this[key]) { "missing $key" }.jsonPrimitive.double
    private fun JsonObject.optDbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.optInt(key: String): Int? = optDbl(key)?.toInt()
    private fun JsonObject.optBool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.optStr(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun profile(o: JsonObject) = TargetProfile(
        ageYears = o.dbl("ageYears").toInt(),
        basis = requireNotNull(CalculationBasis.fromId(o.str("basis"))),
        situation = requireNotNull(Situation.fromId(o.str("situation"))),
        trimester = o.optInt("trimester"),
        lactation = o.optStr("lactation")?.let { requireNotNull(LactationPeriod.fromId(it)) },
        postmenopausal = o.optBool("postmenopausal"),
        pal = o.dbl("pal"),
        weightKg = o.dbl("weightKg"),
        phytateMgPerDay = o.optDbl("phytateMgPerDay"),
    )

    private fun table(e: JsonElement): List<ReferenceRow> = e.jsonArray.map { json.decodeFromJsonElement(ReferenceRow.serializer(), it) }

    private fun weights(e: JsonElement) = e.jsonArray.map { val o = it.jsonObject; WeightEntry(o.str("date"), o.dbl("kg")) }

    private fun versions(e: JsonElement) = e.jsonArray.map {
        val o = it.jsonObject
        ProfileVersion(
            effectiveFrom = o.str("effectiveFrom"),
            birthYear = o.dbl("birthYear").toInt(),
            basis = requireNotNull(CalculationBasis.fromId(o.str("basis"))),
            situation = requireNotNull(Situation.fromId(o.str("situation"))),
            trimester = o.optInt("trimester"),
            lactation = o.optStr("lactation")?.let { l -> requireNotNull(LactationPeriod.fromId(l)) },
            postmenopausal = o.optBool("postmenopausal"),
            pal = o.dbl("pal"),
            weightKg = o.dbl("weightKg"),
            phytateMgPerDay = o.optDbl("phytateMgPerDay"),
        )
    }

    private fun target(o: JsonObject) = NutrientTarget(
        nutrientKey = o.str("nutrientKey"),
        unit = requireNotNull(TargetUnit.fromSymbol(o.str("unit"))),
        min = o.optDbl("min"),
        max = o.optDbl("max"),
        upperLimit = o.optDbl("upperLimit"),
        sources = (o["sources"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
    )

    private fun overrideVersions(e: JsonElement) = e.jsonArray.map {
        val o = it.jsonObject
        OverrideVersion(
            effectiveFrom = o.str("effectiveFrom"),
            values = requireNotNull(o["values"]).jsonObject.mapValues { (_, v) ->
                TargetOverride(v.jsonObject.optDbl("min"), v.jsonObject.optDbl("max"))
            },
        )
    }

    // ── encoding ─────────────────────────────────────────────────────────────

    private fun NutrientTarget.toJson() = buildJsonObject {
        put("nutrientKey", nutrientKey); put("unit", unit.symbol); put("min", min); put("max", max)
        put("upperLimit", upperLimit); put("sources", JsonArray(sources.map(::JsonPrimitive)))
    }

    private fun DayTarget.toJson() = buildJsonObject {
        put("nutrientKey", nutrientKey); put("unit", unit.symbol); put("min", min); put("max", max)
        put("upperLimit", upperLimit); put("sources", JsonArray(sources.map(::JsonPrimitive))); put("overridden", overridden)
    }

    private fun TargetProfile.toJson() = buildJsonObject {
        put("ageYears", ageYears); put("basis", basis.id); put("situation", situation.id)
        trimester?.let { put("trimester", it) }
        lactation?.let { put("lactation", it.id) }
        postmenopausal?.let { put("postmenopausal", it) }
        put("pal", pal); put("weightKg", weightKg)
        phytateMgPerDay?.let { put("phytateMgPerDay", it) }
    }

    // ── suites ───────────────────────────────────────────────────────────────

    @Test
    fun `daily-targets`() = check("daily-targets") { case ->
        when (val result = dailyTargets(table(requireNotNull(case.input["table"])), profile(requireNotNull(case.input["profile"]).jsonObject))) {
            is TargetsResult.Ok -> buildJsonObject {
                put("status", "ok")
                put("targets", JsonArray(result.targets.map { it.toJson() }))
            }
            is TargetsResult.Unsupported -> buildJsonObject {
                put("status", "unsupported")
                put("reason", result.reason.id)
            }
        }
    }

    @Test
    fun `weight-for-day`() = check("weight-for-day") { case ->
        JsonPrimitive(weightForDay(weights(requireNotNull(case.input["entries"])), case.input.str("day")))
    }

    @Test
    fun `profile-for-day`() = check("profile-for-day") { case ->
        profileForDay(
            versions(requireNotNull(case.input["versions"])),
            weights(requireNotNull(case.input["weights"])),
            case.input.str("day"),
        )?.toJson() ?: JsonNull
    }

    @Test
    fun `apply-target-overrides`() = check("apply-target-overrides") { case ->
        val targets = requireNotNull(case.input["targets"]).jsonArray.map { target(it.jsonObject) }
        JsonArray(applyOverrides(targets, overrideVersions(requireNotNull(case.input["versions"])), case.input.str("day")).map { it.toJson() })
    }

    @Test
    fun `day-progress`() = check("day-progress") { case ->
        val targets = requireNotNull(case.input["targets"]).jsonArray.map { target(it.jsonObject) }
        val meals = requireNotNull(case.input["meals"]).jsonArray.map { meal ->
            requireNotNull(meal.jsonObject["nutrients"]).jsonObject.mapValues { it.value.jsonPrimitive.double }
        }
        val keys = requireNotNull(case.input["keys"]).jsonArray.map { it.jsonPrimitive.content }
        JsonArray(
            dayProgress(targets, meals, keys).map { p ->
                buildJsonObject {
                    put("nutrientKey", p.nutrientKey); put("unit", p.unit.symbol); put("total", p.total)
                    put("knownMeals", p.knownMeals); put("totalMeals", p.totalMeals); put("percent", p.percent)
                    put("status", p.status.id); put("aboveUpperLimit", p.aboveUpperLimit)
                }
            },
        )
    }

    @Test
    fun `self-comparison`() = check("self-comparison") { case ->
        val history = requireNotNull(case.input["history"]).jsonArray.map {
            val o = it.jsonObject
            DayTotal(o.str("date"), o.optDbl("total"))
        }
        val result = selfComparison(history, case.input.str("day"), case.input.optDbl("total"))
        buildJsonObject {
            put("average", result.average); put("daysWithData", result.daysWithData); put("percentOfAverage", result.percentOfAverage)
        }
    }

    @Test
    fun `the comparator itself tells numbers, nulls and absent keys apart`() {
        assertTrue(sameJson(JsonPrimitive(2), JsonPrimitive(2.0)))
        assertTrue(!sameJson(JsonPrimitive(2), JsonPrimitive(2.1)))
        assertTrue(!sameJson(buildJsonObject { put("a", null as Int?) }, buildJsonObject { }))
        assertTrue(!sameJson(JsonNull, JsonPrimitive(0)))
        assertTrue(sameJson(JsonArray(listOf(JsonPrimitive(1))), JsonArray(listOf(JsonPrimitive(1.0)))))
        assertEquals(0, JsonPrimitive(0).int)
    }
}

/** JSON equality with numbers compared by value (2 == 2.0); strings and booleans exactly. */
internal fun sameJson(a: JsonElement, b: JsonElement): Boolean = when {
    a is JsonNull || b is JsonNull -> a is JsonNull && b is JsonNull
    a is JsonObject && b is JsonObject -> a.keys == b.keys && a.keys.all { sameJson(a.getValue(it), b.getValue(it)) }
    a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { sameJson(a[it], b[it]) }
    a is JsonPrimitive && b is JsonPrimitive -> when {
        a.isString || b.isString -> a.isString && b.isString && a.content == b.content
        a.doubleOrNull != null && b.doubleOrNull != null -> a.doubleOrNull == b.doubleOrNull
        else -> a.content == b.content
    }
    else -> false
}
