// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts [buildAnchors] against the `build-anchors` suite of the shared corpus.
 *
 * The suite's real subject is the branch condition. Three of its six cases are the same weight ladder
 * reached three different ways — no serving units at all, an item unit the mode does not authorise,
 * and a mode that authorises counting with nothing countable available. A port that branches on
 * `servingMode` alone passes the first and fails the other two.
 */
class BuildAnchorsVectorTest {

    @Test
    fun `every build-anchors vector holds`() {
        val suite = VectorCorpus.load("build-anchors")

        assertEquals(
            "build-anchors suite case count disagrees with index.json",
            VectorCorpus.index().suites["build-anchors"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            val basePortionG = requireNotNull(case.input["basePortionG"]).jsonPrimitive.double
            val servingUnits = requireNotNull(case.input["servingUnits"]).jsonArray
                .map(ScaleStepDecoding::servingUnit)
            val servingMode = ScaleStepDecoding.nullableString(case.input["servingMode"])
            val expected = case.expected.jsonArray.map(ScaleStepDecoding::step)

            val actual = buildAnchors(basePortionG, servingUnits, servingMode)
            if (actual == expected) {
                null
            } else {
                "build-anchors/${case.id}: got $actual, expected $expected — ${case.why}"
            }
        }
        assertEquals("build-anchors vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `counting needs the item mode and an item unit, not either alone`() {
        // Either half alone is not enough, and the failure mode of getting this wrong is an empty
        // ladder rather than a visibly wrong one.
        val carrot = ServingUnit("item", "carotte", 0.0, 74.0)
        val spoon = ServingUnit("tablespoon", "c. à soupe", 15.0, 15.0)

        assertTrue(
            "mode item + an item unit must count",
            buildAnchors(74.0, listOf(carrot), "item").all { it is ScaleStep.UnitStep },
        )
        assertTrue(
            "mode item with nothing countable must fall back to factors",
            buildAnchors(80.0, listOf(spoon), "item").all { it is ScaleStep.FactorStep },
        )
        assertTrue(
            "a countable unit the mode does not authorise must not be counted",
            buildAnchors(74.0, listOf(carrot), "weight").all { it is ScaleStep.FactorStep },
        )
    }

    @Test
    fun `the drop thresholds are inclusive`() {
        // A 10 g item: the 0.5 rung resolves to exactly 5 g and is kept. Writing > rather than >=
        // loses it and breaks nothing else.
        val tenGramItem = ServingUnit("item", "noix", 0.0, 10.0)
        assertEquals(
            "0.5 x 10 g is exactly the 5 g floor and must survive",
            4,
            buildAnchors(10.0, listOf(tenGramItem), "item").size,
        )

        // A 4 g base portion: the 0.25 rung resolves to exactly 1 g and is kept.
        assertEquals(
            "0.25 x 4 g is exactly the 1 g floor and must survive",
            5,
            buildAnchors(4.0, emptyList(), null).size,
        )
    }
}
