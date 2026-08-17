// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.formatFrenchNumber
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.model.JournalStore
import org.pocketnutrition.model.SavedMeal

/**
 * Home screen: the meals kept on this device, newest day first.
 *
 * No counterpart in the sandbox — the prototype persists nothing. It exists because a food diary that
 * forgets is not a diary, and it is why the app needs its own privacy copy rather than the sandbox's
 * "aucune donnée personnelle n'est conservée".
 */
@Composable
fun JournalScreen(
    store: JournalStore,
    onAddMeal: () -> Unit,
    onEditMeal: (SavedMeal) -> Unit,
    onShowAbout: () -> Unit,
) {
    // Closed by default: a single-entry menu one tap away from the empty state's own headline is
    // still one tap cheaper for the entry it actually holds, but it is the shape a second entry
    // (settings, export, whatever comes next) can grow into without a redesign.
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(PnColor.SurfacePaper)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Fr.JOURNAL_TITLE,
                color = PnColor.InkDefault,
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Text("⚙", color = PnColor.InkSoft, fontSize = 20.sp)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Informations de licence") },
                        onClick = {
                            menuExpanded = false
                            onShowAbout()
                        },
                    )
                }
            }
        }

        if (store.meals.isEmpty()) {
            EmptyJournal(onAddMeal = onAddMeal, modifier = Modifier.weight(1f))
        } else {
            MealList(store = store, onEditMeal = onEditMeal, modifier = Modifier.weight(1f))
            PrimaryButton(text = Fr.JOURNAL_ADD, onClick = onAddMeal)
        }
    }
}

@Composable
private fun EmptyJournal(onAddMeal: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🥗", fontSize = 48.sp)
        Text(
            text = Fr.JOURNAL_EMPTY,
            color = PnColor.InkSoft,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = Fr.JOURNAL_ADD, onClick = onAddMeal)
        // Shown on the empty state rather than buried in a settings screen: the one moment the user is
        // deciding whether to trust the app with a diary is before they start one.
        Text(
            text = Fr.JOURNAL_PRIVACY,
            color = PnColor.InkSoft,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MealList(store: JournalStore, onEditMeal: (SavedMeal) -> Unit, modifier: Modifier = Modifier) {
    // Grouped by calendar day for the section headers. Done here rather than in the store because it
    // is a presentation concern and depends on the device's current time zone.
    val grouped = store.meals.groupBy { dayFormatter.format(Date(it.savedAtEpochMs)) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouped.forEach { (day, meals) ->
            item(key = "header-$day") {
                Text(
                    text = day,
                    color = PnColor.InkSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(meals, key = { it.id }) { meal ->
                MealCard(meal = meal, onClick = { onEditMeal(meal) }, onDelete = { store.delete(meal) })
            }
        }
    }
}

@Composable
private fun MealCard(meal: SavedMeal, onClick: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = meal.items.joinToString(" · ") { it.label },
                color = PnColor.InkDefault,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDelete) {
                Text(Fr.JOURNAL_DELETE, color = PnColor.InkSoft, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // roundHalfUp rather than toInt(): the corpus rule, so the phone and the web never
            // disagree about which side of a .5 a number lands on.
            Text(
                text = "${roundHalfUp(meal.totals.energyKcal)} kcal",
                color = PnColor.BrandLeafDeep,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Macro(Fr.NUTRIENT_PROTEIN_ABBR, meal.totals.proteinG)
            Macro(Fr.NUTRIENT_CARBS_ABBR, meal.totals.carbohydratesG)
            Macro(Fr.NUTRIENT_FAT_ABBR, meal.totals.fatG)
        }
    }
}

@Composable
private fun Macro(abbreviation: String, grams: Double) {
    Text(
        text = "$abbreviation ${formatFrenchNumber(Math.round(grams * 10) / 10.0)} g",
        color = PnColor.InkSoft,
        fontSize = 12.sp,
    )
}

/**
 * A date formatter *is* the right tool here, unlike for numbers. The corpus pins number rendering
 * because a separator changes the value a user reads; a date header has no such contract, and the
 * platform's French formatting is what a French user expects.
 */
private val dayFormatter = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)

/** The one filled button in the app, from the design tokens rather than Material defaults. */
@Composable
/**
 * The filled primary action, including its disabled appearance.
 *
 * The disabled pairing was a real legibility bug: an [PnColor.InkOnInverse] label (#FAF7F0, near-white)
 * on an [PnColor.InkLine] fill, which is a 14%-opacity dark over paper and composites to #DBDAD3. That
 * is **1.31:1** — barely distinguishable from the background, and it sat on the screen's main call to
 * action, whose label is "Analyser le repas (0 aliment)". The count is the very thing telling you why
 * the button cannot be pressed, so it is the last text that should be unreadable.
 *
 * Disabled is now a muted fill with dark text — cream + inkSoft, **6.12:1**. It still reads as inert,
 * because the fill drops from deep green to pale cream and the label stops being white, but it stays
 * readable. iOS carries the identical pairing in PrimaryButtonStyle.
 *
 * The design deck defines no disabled state for `.btn-primary`, so this is a local decision that should
 * go back into pocket-nutrition-design before a third client needs it.
 */
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(PnRadius.Default)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (enabled) PnColor.BrandLeafDeep else PnColor.SurfaceCream, shape)
            // A border on the disabled state only. Enabled, the deep-green fill is 6.85:1 against paper
            // and the shape is unmistakable. Disabled, the cream fill measures 1.12:1 against the paper
            // behind it — so while the label is a comfortable 6.12:1, the button had no perceivable edge
            // and read as a stray line of text rather than a control you cannot press yet. iOS carries
            // the same treatment in PrimaryButtonStyle.
            .then(if (enabled) Modifier else Modifier.border(1.dp, PnColor.InkLine, shape))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, enabled = enabled) {
            Text(
                text = text,
                color = if (enabled) PnColor.InkOnInverse else PnColor.InkSoft,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
