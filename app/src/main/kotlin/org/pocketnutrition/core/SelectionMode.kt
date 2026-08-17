// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * Whether tapping a search row should *toggle a selection* rather than add the food immediately.
 *
 * Multi-term search is what makes this question exist. `GET /ingredients/search` reports how many
 * ingredient phrases it split the query into, in the `X-Segment-Count` response header; when that is
 * more than one, the person is composing a meal rather than picking a single food, and a tap should
 * accumulate a selection they confirm in one go.
 *
 * **The term count must come from that header, never from counting spaces.** The server segments with
 * greedy longest-match probes against the search index, so `pomme de terre` is *one* term while
 * `riz poulet pomme` is three. A client splitting on whitespace would offer checkboxes for a single
 * potato — which is why the count is a server concern and the header exists at all.
 *
 * **Recipes are the exception, and it is the reason this is pinned rather than written inline twice.** A
 * recipe is not one of the query's terms: it unpacks into a whole meal by itself, through [unpackRecipe].
 * Miss that branch and recipes become silently un-addable the moment a query has two words — no compiler
 * complains, no other test notices, and the highest-value row in the list stops working.
 *
 * Pinned by the `selection-mode` suite of the shared behavioural corpus.
 */
fun shouldToggleSelection(termCount: Int, productType: String?): Boolean {
    if (productType == "recipe") return false
    return termCount > 1
}
