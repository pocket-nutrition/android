// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlin.math.floor

/**
 * The platform's one rounding rule: a tie always goes to the larger value.
 *
 * **Do not replace this with [kotlin.math.round].** That function breaks ties *towards even*, so
 * `round(2.5)` is 2 and `round(12.5)` is 12 — silently different from the web client and from iOS,
 * with nothing anywhere reporting an error.
 *
 * Not hypothetical. The portion label divides a weight by a portion size and rounds to one decimal:
 * 80 g against a 64 g portion is exactly 1.25, so the label comes from `roundHalfUp(12.5)`. Half-up
 * renders "≈ 1,3 portion"; ties-to-even renders "≈ 1,2 portion".
 *
 * Pinned by the `rounding` suite of the shared behavioural corpus, which the sandbox and iOS assert
 * against too — see RoundingVectorTest.
 */
fun roundHalfUp(value: Double): Int = floor(value + 0.5).toInt()
