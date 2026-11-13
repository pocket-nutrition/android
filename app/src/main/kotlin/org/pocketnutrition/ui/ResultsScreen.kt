// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.formatNumber
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.model.Macros
import org.pocketnutrition.model.MealItem

/**
 * What the API returned for a composed meal, and the chance to keep it.
 *
 * The total is summed here rather than requested: `POST /nutrition` answers per item, and summing on
 * the client keeps the screen honest when one row fails to resolve — the visible rows always add up to
 * the total shown. An item the server could not compute (`available` false) is shown as not recognised
 * and left out of the total: its nutrients are unknown, and its placeholder zeros are not real values.
 * Removing it goes through the existing "edit the meal" action.
 */
@Composable
fun ResultsScreen(
    profiles: List<NutritionalProfile>,
    items: List<MealItem>,
    onSave: (Macros) -> Unit,
    onEdit: () -> Unit,
) {
    val totals = remember(profiles) { Macros.sumOf(profiles) }
    val notCounted = remember(profiles) { profiles.count { !it.available } }
    var hasSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(PnColor.SurfacePaper)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TotalCard(totals, notCounted, hasNoCountable = profiles.isNotEmpty() && notCounted == profiles.size)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(profiles) { _, profile -> ProfileCard(profile) }
        }

        PrimaryButton(
            text = Strings.active.RESULTS_SAVE,
            enabled = !hasSaved,
            onClick = {
                // Guarded rather than merely disabled: a double tap landing before recomposition would
                // otherwise write the meal twice, and a duplicated diary entry is worse than a missed
                // one.
                if (!hasSaved) {
                    hasSaved = true
                    onSave(totals)
                }
            },
        )
        TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.active.RESULTS_EDIT, color = PnColor.BrandLeafDeep, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TotalCard(totals: Macros, notCounted: Int, hasNoCountable: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = Strings.active.RESULTS_TOTAL,
            color = PnColor.InkSoft,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // roundHalfUp, not toInt() and not Math.round: the corpus rule that keeps this number
            // identical to what the web client shows for the same meal.
            // Nothing in the meal could be computed: the total is unknown, never 0.
            Text(
                text = if (hasNoCountable) Strings.active.RESULTS_TOTAL_UNKNOWN else "${roundHalfUp(totals.energyKcal)}",
                color = if (hasNoCountable) PnColor.InkSoft else PnColor.BrandLeafDeep,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("kcal", color = PnColor.InkSoft, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (!hasNoCountable) {
            Row(modifier = Modifier.fillMaxWidth()) {
                MacroColumn(Strings.active.NUTRIENT_PROTEIN, totals.proteinG, Modifier.weight(1f))
                MacroColumn(Strings.active.NUTRIENT_CARBS, totals.carbohydratesG, Modifier.weight(1f))
                MacroColumn(Strings.active.NUTRIENT_FAT, totals.fatG, Modifier.weight(1f))
            }
        }
        if (notCounted > 0) {
            Text(Strings.active.resultsNotCounted(notCounted), color = PnColor.InkSoft, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MacroColumn(label: String, grams: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${formatNumber(Math.round(grams * 10) / 10.0, LanguageSettings.effective)} g",
            color = PnColor.InkDefault,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, color = PnColor.InkSoft, fontSize = 11.sp)
    }
}

@Composable
private fun ProfileCard(profile: NutritionalProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.name,
                color = PnColor.InkDefault,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            // No kcal figure for an item the server could not compute: its 0 is a placeholder.
            if (profile.available) {
                Text(
                    text = "${roundHalfUp(profile.energyKcal)} kcal",
                    color = PnColor.BrandLeafDeep,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = rowSummary(profile),
            color = PnColor.InkSoft,
            fontSize = 12.sp,
        )
    }
}

/**
 * contract: results.rowSummary reads "{quantity} {unit} · {method} · pesé {state}", but for a raw item
 * `method` already says "Cru" — appending "· pesé cru" repeats the same fact in different words.
 * Dropped for exactly that one case, the same guard the meal builder's `MethodPill` text applies to its
 * own weighed suffix.
 */
private fun rowSummary(profile: NutritionalProfile): String {
    // A quick-add row has no quantity, cooking or state: only the figure the person typed.
    if (profile.isQuickAdd) return Strings.active.QUICK_ADD_NOTE
    if (!profile.available) return Strings.active.UNRECOGNISED_NOTE
    if (profile.cookingMethod == "raw") {
        val quantity = formatNumber(profile.quantity, LanguageSettings.effective)
        return "$quantity ${profile.unit} · ${Strings.active.cookingMethod(profile.cookingMethod)}"
    }
    return Strings.active.resultsRowSummary(
        quantity = profile.quantity,
        unit = profile.unit,
        method = Strings.active.cookingMethod(profile.cookingMethod),
        state = Strings.active.measuredState(profile.measuredState),
    )
}
