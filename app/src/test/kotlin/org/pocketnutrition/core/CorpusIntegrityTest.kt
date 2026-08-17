// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the vendored corpus is present, parseable and internally consistent from Kotlin.
 *
 * **Every suite is implemented and asserted — 144 of 144 cases across 17 suites.** The counts are not
 * restated in the assertions below: those read index.json, so this comment is the only thing that can go
 * stale, and a suite added upstream without a port here fails `required` rather than this prose.
 *
 * This file therefore no longer covers for any gap. It still earns its place, and deliberately asserts
 * no values — that would duplicate the per-suite tests. What it asserts is that the corpus this app
 * ships against is the corpus upstream published, which is what fails loudly if a half-finished
 * `sync-contracts.sh --update` leaves a truncated tree behind.
 */
class CorpusIntegrityTest {

    @Test
    fun `every declared suite loads and matches its declared case count`() {
        val index = VectorCorpus.index()
        assertTrue("index.json declares no suites", index.suites.isNotEmpty())

        index.suites.forEach { (name, declaredCount) ->
            val suite = VectorCorpus.load(name)
            assertEquals("$name: suite field disagrees with its filename", name, suite.name)
            assertEquals(
                "$name: case count disagrees with index.json",
                declaredCount,
                suite.cases.size,
            )
            assertTrue("$name: description too short to guide a port", suite.description.length > 60)
        }
    }

    @Test
    fun `case ids are unique within each suite`() {
        // Consumers key on the id, so a duplicate would make one case silently unreachable.
        VectorCorpus.index().suites.keys.forEach { name ->
            val ids = VectorCorpus.load(name).cases.map { it.id }
            assertEquals("$name has duplicate case ids", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `every case explains itself`() {
        // The `why` is the only guidance this side of the port has: the Kotlin author cannot see the
        // TypeScript tables. Upstream CI enforces 40 characters; it is asserted here too, because the
        // vendored copy is what this app actually builds against.
        val thin = VectorCorpus.index().suites.keys.flatMap { name ->
            VectorCorpus.load(name).cases
                .filter { it.why.length < 40 }
                .map { "$name/${it.id} (${it.why.length} chars)" }
        }
        assertEquals("cases without a usable explanation", emptyList<String>(), thin)
    }

    @Test
    fun `the corpus covers the suites this app will need`() {
        // Named explicitly so dropping one upstream turns red here rather than quietly reducing
        // coverage. Each is a function the meal builder cannot be written without.
        val required = listOf(
            "apply-method-change",
            "switch-measured-state",
            "build-anchors",
            "build-meal-ingredient",
            "deduplicate-suggestions",
            "selection-mode",
            "describe-quantity",
            "food-group-icon",
            "french-number",
            "french-plural",
            "rounding",
            "serving-unit-emoji",
            "shortcut-profile",
            "step-label",
            "step-raw-weight",
            "unpack-recipe",
            "yield-factor",
        )
        val present = VectorCorpus.index().suites.keys
        required.forEach { name ->
            assertTrue("required suite '$name' is missing from the vendored corpus", name in present)
        }
    }
}
