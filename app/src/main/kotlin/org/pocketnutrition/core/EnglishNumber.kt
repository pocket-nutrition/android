// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlin.math.floor

/**
 * Renders a number the way the English UI writes it: a decimal point, and no decimal part at all on
 * a whole number — the mirror image of [formatFrenchNumber].
 *
 * **Do not reach for a locale-aware formatter here**, for the same reason [formatFrenchNumber] does
 * not: it would insert thousands separators that vary by platform and by the user's chosen locale.
 * The rule is pinned as "stringify, then leave the dot alone", which needs no formatter at all.
 *
 * Pinned by the `english-number` suite of the shared behavioural corpus.
 */
fun formatEnglishNumber(value: Double): String {
    if (value == floor(value)) return value.toInt().toString()
    return value.toString()
}

/**
 * Formats [value] in whichever language [lang] names, defaulting to French for anything that is not
 * `"en"`. The one dispatcher every call site routes through — adding a third language means adding a
 * case here, not hunting down every [formatFrenchNumber] call across the app a second time.
 */
fun formatNumber(value: Double, lang: String): String =
    if (lang == "en") formatEnglishNumber(value) else formatFrenchNumber(value)
