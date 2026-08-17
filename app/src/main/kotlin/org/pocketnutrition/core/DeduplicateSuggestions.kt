// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * Minimum same-language results before the language preference applies at all.
 *
 * Below this the server's order is kept **whole** — not same-language-first — so a French query that
 * matches only one or two French entries does not push them above better English matches. Three is the
 * point at which the same-language set is a usable list on its own.
 */
private const val MIN_SAME_LANG = 3

/** Default cap. The caller over-fetches and this decides how much of it is offered. */
const val MAX_SUGGESTIONS = 30

/**
 * Collapses duplicate labels, preferring the viewer's language, with recipes pinned on top.
 *
 * Four rules, and the order between them is the contract:
 *
 * 1. **Recipes are never deduplicated away and always lead.** They are the highest-value result — one
 *    tap expands into a whole meal — so they are lifted out before anything else runs.
 * 2. **Language preference, but only above a threshold.** With at least [MIN_SAME_LANG] results in the
 *    requested language, those come first and the rest follow; below it the server's own order is kept
 *    whole. This is the rule most likely to be ported wrongly as "always sort by language", and the
 *    `two-french-results-keep-server-order` vector is the one that catches it.
 * 3. **Deduplicate by lowercased, trimmed label *and* brand.** The API returns genuinely distinct
 *    records — one curated ingredient plus N Open Food Facts barcodes — that a user could not tell apart
 *    when the row showed only a name and a weight. Brand joins the key because it is now on the row: two
 *    products of the same name from different brands are separate things to add, while the same name
 *    *and* brand is still noise. A brandless product keys on the label alone, so it collapses into the
 *    curated ingredient of that name — the winner that carries yield factors, serving units and a food
 *    group. Never by `ingredientId`: that is unique per row and would deduplicate nothing, which is
 *    exactly the bug this client shipped with.
 * 4. **Cap the result.** Recipes count toward the cap but are never dropped by it, so a response of
 *    nothing but recipes can exceed it. Dropping a recipe to honour a display cap would lose the best
 *    result to make room for a worse one.
 *
 * Recipe labels are deliberately absent from the seen set, so a recipe and an ingredient sharing a name
 * both survive — they are two different things to add.
 *
 * Pinned by the `deduplicate-suggestions` suite of the shared behavioural corpus.
 */
fun deduplicateByLabel(
    items: List<IngredientSuggestion>,
    preferLang: String,
    maxResults: Int = MAX_SUGGESTIONS,
): List<IngredientSuggestion> {
    val recipes = items.filter { it.isRecipe }
    val rest = items.filter { !it.isRecipe }

    val sameLang = rest.filter { it.lang == preferLang }
    val otherLang = rest.filter { it.lang != preferLang }
    val ordered = if (sameLang.size >= MIN_SAME_LANG) sameLang + otherLang else rest

    val seen = mutableSetOf<String>()
    val result = recipes.toMutableList()
    for (item in ordered) {
        val key = identityKey(item)
        if (!seen.add(key)) continue
        result.add(item)
        if (result.size == maxResults) break
    }
    return result
}

/**
 * The identity a viewer can actually perceive: name plus brand.
 *
 * Trimming matters as much as lowercasing — Open Food Facts labels frequently carry a trailing space,
 * and an untrimmed key would let " tofu " and "Tofu" through as separate rows. Brand gets the same
 * treatment, because contributors enter "Bonduelle", "BONDUELLE" and " bonduelle " for the same company.
 *
 * The separator is U+001F (unit separator), chosen because trimming cannot produce it in either half:
 * ("Jus", "Orange") and ("Jus Orange", null) are different products and must not collide. The
 * `the-key-separator-cannot-be-forged-across-the-two-halves` vector catches a port that joins the two
 * halves with nothing, or with a space it later trims.
 */
private fun identityKey(item: IngredientSuggestion): String =
    "${item.label.lowercase().trim()}\u001F${(item.brand ?: "").lowercase().trim()}"
