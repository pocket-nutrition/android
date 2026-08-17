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
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.formatFrenchNumber
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.model.Macros
import org.pocketnutrition.model.MealItem

/**
 * What the API returned for a composed meal, and the chance to keep it.
 *
 * The total is summed here rather than requested: `POST /nutrition` answers per item, and summing on
 * the client keeps the screen honest when one row fails to resolve — the visible rows always add up to
 * the total shown.
 */
@Composable
fun ResultsScreen(
    profiles: List<NutritionalProfile>,
    items: List<MealItem>,
    onSave: (Macros) -> Unit,
    onEdit: () -> Unit,
) {
    val totals = remember(profiles) {
        profiles.map(Macros::of).fold(Macros.Zero) { accumulator, macros -> accumulator + macros }
    }
    var hasSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(PnColor.SurfacePaper)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TotalCard(totals)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(profiles) { _, profile -> ProfileCard(profile) }
        }

        PrimaryButton(
            text = Fr.RESULTS_SAVE,
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
            Text(Fr.RESULTS_EDIT, color = PnColor.BrandLeafDeep, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TotalCard(totals: Macros) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = Fr.RESULTS_TOTAL,
            color = PnColor.InkSoft,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // roundHalfUp, not toInt() and not Math.round: the corpus rule that keeps this number
            // identical to what the web client shows for the same meal.
            Text(
                text = "${roundHalfUp(totals.energyKcal)}",
                color = PnColor.BrandLeafDeep,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("kcal", color = PnColor.InkSoft, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            MacroColumn(Fr.NUTRIENT_PROTEIN, totals.proteinG, Modifier.weight(1f))
            MacroColumn(Fr.NUTRIENT_CARBS, totals.carbohydratesG, Modifier.weight(1f))
            MacroColumn(Fr.NUTRIENT_FAT, totals.fatG, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroColumn(label: String, grams: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${formatFrenchNumber(Math.round(grams * 10) / 10.0)} g",
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
            Text(
                text = "${roundHalfUp(profile.energyKcal)} kcal",
                color = PnColor.BrandLeafDeep,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
    if (profile.cookingMethod == "raw") {
        return "${formatFrenchNumber(profile.quantity)} ${profile.unit} · ${Fr.cookingMethod(profile.cookingMethod)}"
    }
    return Fr.resultsRowSummary(
        quantity = profile.quantity,
        unit = profile.unit,
        method = Fr.cookingMethod(profile.cookingMethod),
        state = Fr.measuredState(profile.measuredState),
    )
}
