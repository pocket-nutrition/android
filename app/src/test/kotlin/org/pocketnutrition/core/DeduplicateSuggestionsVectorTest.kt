// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [deduplicateByLabel] against the `deduplicate-suggestions` suite.
 *
 * This client shipped without any of these rules, which is why a search for "poulet" rendered four rows
 * all reading "Poulet / 100 g / Produit". The corpus cases carry only the four fields the function reads,
 * so they decode into [IngredientSuggestion] with everything else defaulted.
 */
class DeduplicateSuggestionsVectorTest {

    @Test
    fun `every deduplicate-suggestions vector holds`() {
        val suite = VectorCorpus.load("deduplicate-suggestions")
        assertEquals(
            "case count disagrees with index.json",
            VectorCorpus.index().suites["deduplicate-suggestions"],
            suite.cases.size,
        )

        val failures = suite.cases.mapNotNull { case ->
            val items = SuggestionDecoding.decode<List<IngredientSuggestion>>(
                requireNotNull(case.input["items"]) { "${case.id}: input has no items" },
            )
            val preferLang = requireNotNull(case.input["preferLang"]) {
                "${case.id}: input has no preferLang"
            }.jsonPrimitive.content
            val expected = SuggestionDecoding.decode<List<IngredientSuggestion>>(case.expected)

            // maxResults is absent in most cases, and absent must mean "the default" rather than zero —
            // a zero cap would return only the recipes and pass nothing else.
            val cap = case.input["maxResults"]?.jsonPrimitive?.int
            val actual =
                if (cap == null) deduplicateByLabel(items, preferLang)
                else deduplicateByLabel(items, preferLang, cap)

            if (actual.map { it.ingredientId } == expected.map { it.ingredientId }) {
                null
            } else {
                "${case.id}: ${actual.map { it.ingredientId }} != " +
                    "${expected.map { it.ingredientId }} — ${case.why}"
            }
        }
        assertEquals("deduplicate-suggestions vectors that do not hold", emptyList<String>(), failures)
    }

    /**
     * The threshold, asserted from both sides. Two same-language results keep the server order whole;
     * three flip it. A port that always partitions by language passes the three case and fails the two.
     */
    @Test
    fun `the language preference switches on at three, not below`() {
        fun s(id: String, label: String, lang: String) =
            IngredientSuggestion(id, label, lang, productType = "off_product")

        val twoFrench = listOf(s("f1", "Pain", "fr"), s("e1", "Bread", "en"), s("f2", "Baguette", "fr"))
        assertEquals(
            "below the threshold the server order must be kept whole",
            listOf("f1", "e1", "f2"),
            deduplicateByLabel(twoFrench, "fr").map { it.ingredientId },
        )

        val threeFrench = twoFrench + s("f3", "Brioche", "fr")
        assertEquals(
            "at the threshold the same-language results must lead",
            listOf("f1", "f2", "f3", "e1"),
            deduplicateByLabel(threeFrench, "fr").map { it.ingredientId },
        )
    }

    /**
     * Trimming, which is easy to omit when porting because lowercasing is the obvious half. OFF labels
     * frequently carry a trailing space, so an untrimmed key lets the same food through twice.
     */
    @Test
    fun `the label key is trimmed as well as lowercased`() {
        val items = listOf(
            IngredientSuggestion("a", "Tofu", productType = "off_product"),
            IngredientSuggestion("b", "  tofu ", productType = "off_product"),
            IngredientSuggestion("c", "\tTOFU\n", productType = "off_product"),
        )
        assertEquals(listOf("a"), deduplicateByLabel(items, "fr").map { it.ingredientId })
    }

    /**
     * Never by ingredientId. Stated by name because that is the shape the bug took: every row has a
     * unique id, so keying on it deduplicates nothing and the list looks untouched.
     */
    @Test
    fun `deduplication is by label, never by ingredientId`() {
        val items = (1..5).map {
            IngredientSuggestion("off:$it", "Yaourt", productType = "off_product")
        }
        assertEquals(1, deduplicateByLabel(items, "fr").size)
    }
}
