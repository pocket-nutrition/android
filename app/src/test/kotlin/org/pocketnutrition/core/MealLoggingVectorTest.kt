// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts the meal-logging helpers ([mealSlotForMinutes], [defaultMinutes], [scaleDishQuantity],
 * [parseQuickAddKcal], [rankRecents]) against their five suites of the shared corpus — the same JSON the
 * sandbox and iOS assert against. Mismatches are collected per suite, as in the other vector tests.
 */
class MealLoggingVectorTest {

    private fun cases(name: String): List<VectorCorpus.Case> {
        val suite = VectorCorpus.load(name)
        assertEquals("$name case count disagrees with index.json", VectorCorpus.index().suites[name], suite.cases.size)
        assertTrue("the $name suite is empty", suite.cases.isNotEmpty())
        return suite.cases
    }

    private fun slot(id: String): MealSlot = requireNotNull(MealSlot.fromId(id)) { "unknown slot '$id'" }

    private fun check(name: String, actual: (VectorCorpus.Case) -> Any?, expected: (VectorCorpus.Case) -> Any?) {
        val mismatches = cases(name).mapNotNull { case ->
            val got = actual(case)
            val want = expected(case)
            if (got == want) null else "$name/${case.id}: got $got, expected $want — ${case.why}"
        }
        assertEquals("$name vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `meal-slot`() = check(
        "meal-slot",
        actual = { mealSlotForMinutes(requireNotNull(it.input["minutes"]).jsonPrimitive.double).id },
        expected = { it.expected.jsonPrimitive.content },
    )

    @Test
    fun `meal-slot-default-time`() = check(
        "meal-slot-default-time",
        actual = { case ->
            val slot = slot(requireNotNull(case.input["slot"]).jsonPrimitive.content)
            val minutes = defaultMinutes(slot)
            // Round trip: the default time of a slot falls inside that slot's own band.
            if (mealSlotForMinutes(minutes.toDouble()) == slot) minutes else "out of band: $minutes"
        },
        expected = { it.expected.jsonPrimitive.int },
    )

    @Test
    fun `scale-dish-quantity`() = check(
        "scale-dish-quantity",
        actual = {
            scaleDishQuantity(
                requireNotNull(it.input["quantityG"]).jsonPrimitive.double,
                requireNotNull(it.input["servings"]).jsonPrimitive.double,
                requireNotNull(it.input["parts"]).jsonPrimitive.double,
            )
        },
        expected = { it.expected.jsonPrimitive.double },
    )

    @Test
    fun `parse-quick-add-kcal`() = check(
        "parse-quick-add-kcal",
        actual = { parseQuickAddKcal(requireNotNull(it.input["query"]).jsonPrimitive.content) },
        expected = { if (it.expected is JsonNull) null else it.expected.jsonPrimitive.int },
    )

    @Test
    fun `rank-recents`() = check(
        "rank-recents",
        actual = { case ->
            val uses = requireNotNull(case.input["uses"]).jsonArray.map { element ->
                val use = element.jsonObject
                LoggedUse(
                    key = requireNotNull(use["key"]).jsonPrimitive.content,
                    slot = slot(requireNotNull(use["slot"]).jsonPrimitive.content),
                    at = requireNotNull(use["at"]).jsonPrimitive.double,
                )
            }
            rankRecents(
                uses,
                slot(requireNotNull(case.input["slot"]).jsonPrimitive.content),
                requireNotNull(case.input["limit"]).jsonPrimitive.int,
            )
        },
        expected = { case -> case.expected.jsonArray.map { it.jsonPrimitive.content } },
    )
}
