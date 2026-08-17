// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * Pluralises a French ingredient label for a count.
 *
 * The threshold is 2, not 1, and that is the substance of the function rather than an oversight:
 * French keeps the singular below two, so "1,5 carotte" is correct and "1,5 carottes" — what an
 * English rule would produce — is not. An English-shaped port passes four of the six vectors; only the
 * 1.5 and 2 cases tell the two rules apart.
 *
 * **This is deliberately naive, and every client is required to be naive in the same way.** Appending
 * an "s" is wrong for a good number of French words; the real forms belong in Knowledge, under
 * `set_ingredient_plural`. Until those exist the clients must agree with each other rather than each
 * improvise, because a label that differs between the phone and the web is a bug a user can see,
 * whereas a label that is uniformly a bit crude is a gap already known about. The `ananas` case exists
 * only to stop the crude rule from producing "ananass".
 *
 * Pinned by the `french-plural` suite of the shared behavioural corpus — see FrenchPluralVectorTest.
 */
fun frenchPlural(label: String, count: Double): String =
    if (count >= 2 && !label.endsWith("s")) label + "s" else label
