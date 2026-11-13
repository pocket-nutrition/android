// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.model.MealItem
import org.pocketnutrition.model.SavedMeal
import org.pocketnutrition.model.TimelineDay
import org.pocketnutrition.model.TimelineItem

// The journal's day and slot headers and today's empty main meals. Mirrors iOS `JournalView`.

/** The day's energy: the sum of its saved meals (captures are not meals yet). */
fun dayKcal(day: TimelineDay<SavedMeal>): Double =
    day.items.sumOf { if (it is TimelineItem.Meal) it.meal.totals.energyKcal else 0.0 }

@Composable
internal fun DayHeader(label: String, kcal: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = PnColor.InkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (kcal > 0) {
            Text("${roundHalfUp(kcal)} kcal", color = PnColor.InkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SlotHeader(slot: MealSlot) {
    Text(
        Strings.active.mealSlotName(slot).uppercase(),
        color = PnColor.InkSoft,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).semantics { heading() },
    )
}

/** Today's main meal with nothing logged: one tap to compose it, and one tap to repeat yesterday's. */
@Composable
internal fun EmptySlotRow(slot: MealSlot, yesterday: SavedMeal?, onAdd: () -> Unit, onRepeat: (SavedMeal) -> Unit) {
    val strings = Strings.active
    Row(
        modifier = Modifier.fillMaxWidth().dashedOutline().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "+ " + strings.mealSlotName(slot),
            color = PnColor.BrandLeafDeep,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onAdd).padding(vertical = 4.dp),
        )
        if (yesterday != null) {
            Text(
                strings.journalSameAsYesterday(yesterday.items.joinToString(" · ") { it.journalLabel() }) + " ↺",
                color = PnColor.InkSoft,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onRepeat(yesterday) }
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = strings.journalRepeatYesterdayA11y(strings.mealSlotName(slot)) },
            )
        }
    }
}

/** An item's label in the journal, flagged when the server could not compute it (not in the totals). */
internal fun MealItem.journalLabel(): String =
    if (available) label else Strings.active.unrecognisedLabel(label)
