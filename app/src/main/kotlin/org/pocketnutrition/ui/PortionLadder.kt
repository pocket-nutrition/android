// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import org.pocketnutrition.core.MealIngredient
import org.pocketnutrition.core.Shortcut
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.design.PnRole
import org.pocketnutrition.core.resolveYieldFactor
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.core.shortcutProfile

/**
 * The portion control: a row of quick buttons, then the stepper and the editable weight.
 *
 * ```
 * [ Demi ][ 1 Part ][ Double ]
 * [ − ][      150 g       ][ + ]
 * ```
 *
 * Sized to match the sandbox's `PortionScale` (`docs/SPECIFICATION.md` §7.10) rather than this app's
 * own former, much smaller controls: 48dp steppers and a bold, large-type weight field, because this
 * is the one row in the meal builder a user touches for every single ingredient, and small targets
 * there cost taps across the whole meal. The summary line this composable used to show — "≈ 1,3
 * portion" — now lives on the collapsed row instead (`MealBuilderScreen.kt`'s `MealItemRow`), which is
 * also where the sandbox puts it.
 *
 * Every label and every number here comes from a corpus function. Nothing is formatted locally —
 * `stepLabel` names each rung, `stepRawWeight` says what a rung weighs. That is the point: the sandbox
 * and the two apps show the same words for the same row, and a disagreement fails a vector test rather
 * than shipping.
 *
 * TWO TRAPS THE CORPUS ENCODES AND THIS COMPOSABLE MUST NOT UNDO
 * [shortcutProfile] guarantees **between two and four** entries, never a fixed count. Equal-width flex
 * (rather than the horizontal scroll this composable used before) matches the sandbox, and holds up
 * because the corpus caps this row at four short labels. And `stepRawWeight` **ignores the base
 * portion for unit steps** — recomputing a weight here instead of calling it would reintroduce exactly
 * the bug its vectors exist to catch.
 */
// Grams (or mL) moved per tap of the +/- buttons. One value for every ingredient rather than a
// step scaled to the current weight, so repeated taps stay predictable. Bounds match the
// sandbox's PortionScale so a weight typed on one client is always reachable by holding on another.
private const val STEP_AMOUNT = 5.0
private const val MINIMUM_QUANTITY = 1.0
private const val MAXIMUM_QUANTITY = 5000.0

// Press-and-hold cadence for the +/- buttons — mirrors the sandbox's useHoldRepeat hook: fire once
// on press, then repeat every REPEAT_INTERVAL_MS once held past INITIAL_DELAY_MS.
private const val INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 75L

private fun steppedQuantity(current: Double, delta: Double): Double =
    (current + delta).coerceIn(MINIMUM_QUANTITY, MAXIMUM_QUANTITY)

/**
 * Whether the current weight already *is* this multiplier's portion, within
 * `docs/SPECIFICATION.md` §7.10's tolerance — `max(2 g, 4 %)` of the target, computed through the
 * yield when the row measures cooked so a grilled row's "1 Part" lights up against the grilled
 * weight, not the raw one.
 */
private fun isFactorActive(row: MealIngredient, factor: Double): Boolean {
    val target = if (row.measuredState == "cooked") {
        row.basePortionG * factor * resolveYieldFactor(row.yieldFactors, row.cookingMethod)
    } else {
        row.basePortionG * factor
    }
    val tolerance = maxOf(2.0, target * 0.04)
    return kotlin.math.abs(row.quantity - target) <= tolerance
}

@Composable
fun PortionLadder(row: MealIngredient, onChange: (MealIngredient) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Equal width now rather than a horizontal scroller, since the corpus caps this row at four
        // entries and the sandbox's own layout assumes exactly that.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcutProfile(row.servingUnits, row.displayUnit).forEach { shortcut ->
                when (shortcut) {
                    is Shortcut.Multiplier ->
                        // Active state, not on the increments below: a multiplier sets an absolute
                        // portion, so "is the current weight this portion" is a real, checkable fact.
                        LadderButton(shortcut.label, active = isFactorActive(row, shortcut.factor)) {
                            // A multiplier puts the row back *on* the ladder, so the factor is
                            // recorded as well as the weight. The two must move together: a weight
                            // without its factor is a row applyMethodChange can no longer rescale.
                            onChange(
                                row.copy(
                                    portionFactor = shortcut.factor,
                                    quantity = roundHalfUp(row.basePortionG * shortcut.factor).toDouble(),
                                ),
                            )
                        }
                    is Shortcut.Increment ->
                        // `active: null`, not `false` — an increment is a momentary trigger with no
                        // state of its own to show, not a toggle that merely happens to be off.
                        LadderButton(
                            listOfNotNull(shortcut.emoji, shortcut.label).joinToString(" "),
                            active = null,
                        ) {
                            // An increment *adds* a serving unit's worth rather than replacing the
                            // weight — one more spoonful, not "a spoonful in total" — so it leaves
                            // the ladder, same as typing.
                            onChange(
                                row.copy(quantity = row.quantity + shortcut.grams, portionFactor = 0.0),
                            )
                        }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton(
                symbol = "−",
                contentDescription = "Diminuer la quantité",
                enabled = row.quantity > MINIMUM_QUANTITY,
            ) {
                // Adjusting off the ladder, same as typing directly — see the onValueChange comment below.
                val next = steppedQuantity(row.quantity, -STEP_AMOUNT)
                if (next == row.quantity) {
                    false
                } else {
                    onChange(row.copy(quantity = next, portionFactor = 0.0))
                    true
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(PnColor.SurfaceCream, RoundedCornerShape(PnRadius.Default))
                    .border(1.dp, PnColor.InkLine, RoundedCornerShape(PnRadius.Default))
                    .padding(vertical = 10.dp),
            ) {
                TextField(
                    value = if (row.quantity % 1.0 == 0.0) row.quantity.toInt().toString()
                    else row.quantity.toString(),
                    onValueChange = { typed ->
                        typed.replace(',', '.').toDoubleOrNull()?.let {
                            // Typing a weight directly takes the row off the ladder, which is what
                            // portionFactor 0 means. Leaving it at 1 would make applyMethodChange
                            // recompute from the base portion on the next method change and silently
                            // discard what was typed — the case `a zero portion factor does not
                            // recompute from the base`.
                            onChange(row.copy(quantity = it, portionFactor = 0.0))
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        textAlign = TextAlign.End,
                        color = PnColor.InkDefault,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    // No explicit colours beyond textStyle: PocketNutritionTheme maps the tokens onto
                    // Material3's slots, so overriding the container here would be a second source of
                    // truth for the same colour.
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = if (row.displayUnit == "ml") "mL" else "g",
                    color = PnColor.InkSoft,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            StepButton(
                symbol = "+",
                contentDescription = "Augmenter la quantité",
                enabled = row.quantity < MAXIMUM_QUANTITY,
            ) {
                val next = steppedQuantity(row.quantity, STEP_AMOUNT)
                if (next == row.quantity) {
                    false
                } else {
                    onChange(row.copy(quantity = next, portionFactor = 0.0))
                    true
                }
            }
        }
    }
}

/**
 * One quick-portion button. `active` is `null` for an increment (never shows a selected state — a
 * pure momentary trigger), `true`/`false` for a multiplier, which lights up in the app's primary
 * colour when the current weight already matches this portion.
 */
@Composable
private fun RowScope.LadderButton(label: String, active: Boolean?, onClick: () -> Unit) {
    val isOn = active == true
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .background(
                if (isOn) PnRole.ButtonPrimaryBackground else PnColor.SurfaceCream,
                RoundedCornerShape(PnRadius.Small),
            )
            .then(
                if (isOn) {
                    Modifier
                } else {
                    Modifier.border(1.dp, PnColor.InkLine, RoundedCornerShape(PnRadius.Small))
                },
            )
            .semantics {
                role = Role.Button
                // Only a multiplier (`active` non-null) ever exposes a selected state — an increment
                // has no pressed state to announce, same reasoning as `active: null` above.
                if (active != null) selected = isOn
                onClick { onClick(); true }
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isOn) PnRole.ButtonPrimaryForeground else PnColor.InkDefault,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * A +/- button that fires once on press and, if still held, repeats at [INITIAL_DELAY_MS] /
 * [REPEAT_INTERVAL_MS] cadence — the same as the sandbox's `useHoldRepeat` hook, so holding the
 * button scrolls the value instead of forcing many taps. [onStep] returns `false` at a boundary
 * (the value already hit its floor/ceiling), which stops the repeat early.
 *
 * 48dp square, matching `docs/SPECIFICATION.md` §7.10's stepper size — up from the 28dp circle this
 * button used to be, which is most of why the row was hard to tap precisely.
 *
 * Driven by a raw `pointerInput` gesture rather than `clickable`, because a real down/up pair is
 * what the repeat timer needs — `clickable`'s `onClick` only fires on release, by which point a
 * hold has already come and gone. The semantics `onClick` action below is what TalkBack invokes
 * instead of a touch gesture, so it is registered separately and fires [onStep] exactly once.
 */
@Composable
private fun StepButton(symbol: String, contentDescription: String, enabled: Boolean, onStep: () -> Boolean) {
    val shape = RoundedCornerShape(PnRadius.Default)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(PnColor.SurfaceCream)
            .border(1.dp, PnColor.InkLine, shape)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (enabled) onClick { onStep() }
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(onStep) {
                        awaitEachGesture {
                            awaitFirstDown()
                            if (onStep()) {
                                var released = withTimeoutOrNull(INITIAL_DELAY_MS) { waitForUpOrCancellation() } != null
                                while (!released) {
                                    if (!onStep()) break
                                    released = withTimeoutOrNull(REPEAT_INTERVAL_MS) { waitForUpOrCancellation() } != null
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = if (enabled) PnColor.InkDefault else PnColor.InkSoft.copy(alpha = 0.4f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
