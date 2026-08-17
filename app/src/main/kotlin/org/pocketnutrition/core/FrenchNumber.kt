// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlin.math.floor

/**
 * Renders a number the way the French UI writes it: a decimal comma, and no decimal part at all on a
 * whole number.
 *
 * **Do not reach for [java.text.NumberFormat] or a locale-aware `String.format` here.** A locale-aware
 * formatter is the obvious tool and the wrong one: it inserts thousands separators, and which
 * separator it picks — a space, a narrow no-break space, nothing — varies by platform and by OS
 * version. On Android it additionally depends on the user's chosen locale, which would make the label
 * differ between two phones showing the same meal. The contract pins the rule as literally "stringify,
 * then replace the dot with a comma".
 *
 * One JVM detail worth knowing, because it is invisible until it bites: from JDK 19 onwards
 * [Double.toString] emits the shortest decimal that round-trips, which is what Swift already does, so
 * the two clients agree digit for digit. On an older JDK some values render with extra digits. CI runs
 * JDK 21, and every value in this suite is short enough to be unaffected either way — so the risk is
 * latent rather than live, but it is the reason a JDK downgrade is not a neutral change.
 *
 * Pinned by the `french-number` suite of the shared behavioural corpus, which the sandbox and iOS
 * assert against too.
 */
fun formatFrenchNumber(value: Double): String {
    // A whole number renders "2" and not "2,0", so the integer branch comes first. The Int conversion
    // matches roundHalfUp, which makes the same assumption: these are human food quantities, nowhere
    // near the range where the conversion would saturate.
    if (value == floor(value)) return value.toInt().toString()
    return value.toString().replace('.', ',')
}
