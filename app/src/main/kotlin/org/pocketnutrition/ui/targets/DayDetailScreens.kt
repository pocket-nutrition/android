// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.targets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import org.pocketnutrition.core.DayTarget
import org.pocketnutrition.core.NutrientProgress
import org.pocketnutrition.core.ProgressStatus
import org.pocketnutrition.core.TargetUnit
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.model.DailyTargetsController
import org.pocketnutrition.model.DayTargetsState
import org.pocketnutrition.model.NutrientGroup
import org.pocketnutrition.model.knownTotal
import org.pocketnutrition.model.progressForDay
import org.pocketnutrition.model.saltGramsFromSodiumMg
import org.pocketnutrition.model.targets
import org.pocketnutrition.model.topContributors
import org.pocketnutrition.model.weekAverages

// The day against its targets, the week averages and one nutrient's trend (spec 05 §6).

private const val WEEK_DAYS = 7L
private val WEEKLY_KEYS = setOf("vitaminDUg", "vitaminB12Ug")

private fun groupTitle(group: NutrientGroup): String {
    val s = TargetStrings.active
    return when (group) {
        NutrientGroup.ENERGY_MACROS -> s.groupEnergyMacros
        NutrientGroup.FATS -> s.groupFats
        NutrientGroup.MINERALS -> s.groupMinerals
        NutrientGroup.VITAMINS -> s.groupVitamins
    }
}

@Composable
fun DayDetailScreen(
    controller: DailyTargetsController,
    day: LocalDate,
    onBack: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenTrends: (String) -> Unit,
    onOpenExplanation: () -> Unit,
) {
    val s = TargetStrings.active
    val state = controller.stateFor(day)
    val meals = controller.mealsOn(day)
    val targets = state.targets.associateBy { it.nutrientKey }
    TargetsPage(title = formatDay(day).replaceFirstChar { it.uppercase() }, onBack = onBack) {
        Text(s.dayDetailTitle, color = PnColor.InkSoft, fontSize = 13.sp)
        when (state) {
            DayTargetsState.NeedsConnection -> Note(s.needsConnection)
            is DayTargetsState.Unsupported -> Note(s.unsupported(state.reason))
            else -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LinkAction(s.seeWeek, onClick = onOpenWeek)
            LinkAction(s.explanationTitle, onClick = onOpenExplanation)
        }
        NutrientGroup.entries.forEach { group ->
            val progress = progressForDay(state, meals, group.keys)
            TargetsCard(groupTitle(group)) {
                progress.forEachIndexed { index, p ->
                    if (index > 0) HorizontalDivider(color = PnColor.InkLine)
                    NutrientDetailRow(p, targets[p.nutrientKey], meals, onOpenWeek, onOpenTrends)
                }
            }
        }
    }
}

@Composable
private fun NutrientDetailRow(
    progress: NutrientProgress,
    target: DayTarget?,
    meals: List<org.pocketnutrition.model.SavedMeal>,
    onOpenWeek: () -> Unit,
    onOpenTrends: (String) -> Unit,
) {
    val s = TargetStrings.active
    val key = progress.nutrientKey
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onOpenTrends(key) }.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (target != null && progress.status != ProgressStatus.NO_TARGET) TargetRing(progress, target, size = 34.dp, stroke = 4.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName(key), color = PnColor.InkDefault, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                val total = progress.total?.let { formatAmount(key, progress.unit, it) } ?: "–"
                val against = target?.let { " / ${targetText(it)} (${kindOf(it)})" }.orEmpty()
                Text(total + against, color = PnColor.InkSoft, fontSize = 12.sp)
                // Sodium itself, in mg, under the salt figure.
                if (key == SODIUM_KEY && progress.total != null) {
                    Text("${s.nutrientName(key)} ${formatAmount(key, progress.unit, progress.total, asSalt = false)}", color = PnColor.InkSoft, fontSize = 11.sp)
                }
            }
            Text(s.status(progress.status), color = PnColor.InkSoft, fontSize = 11.sp)
        }
        if (progress.totalMeals > 0 && progress.knownMeals < progress.totalMeals) Note(s.coverage(progress.knownMeals, progress.totalMeals))
        val upper = target?.upperLimit
        if (progress.aboveUpperLimit && upper != null) Note(s.aboveUpperLimit(formatAmount(key, progress.unit, upper, asSalt = false)))
        val top = topContributors(meals, key)
        if (top.isNotEmpty()) {
            Note(s.mostFrom + " " + top.joinToString(" · ") { (meal, value) -> "${meal.items.joinToString(", ") { it.label }} ${formatAmount(key, progress.unit, value)}" })
        }
        if (key in WEEKLY_KEYS) LinkAction(s.weeklyNutrientNote + " " + s.seeWeek + " ›", onClick = onOpenWeek)
    }
}

@Composable
fun WeekScreen(controller: DailyTargetsController, endDay: LocalDate, onBack: () -> Unit, onOpenTrends: (String) -> Unit) {
    val s = TargetStrings.active
    val days = (WEEK_DAYS - 1 downTo 0).map { endDay.minusDays(it) }
    val perDay = days.map { controller.stateFor(it) to controller.mealsOn(it) }
    TargetsPage(title = s.weekTitle, onBack = onBack) {
        NutrientGroup.entries.forEach { group ->
            val averages = weekAverages(perDay, group.keys)
            TargetsCard(groupTitle(group)) {
                averages.forEach { avg ->
                    val unit = TargetUnit.ofKey(avg.key)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenTrends(avg.key) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName(avg.key), color = PnColor.InkDefault, fontSize = 14.sp)
                            Note(
                                (avg.averageTotal?.let { formatAmount(avg.key, unit, it) } ?: "–") + " · " + s.weekDays(avg.daysWithData),
                            )
                        }
                        Text(avg.averagePercent?.let { "$it %" } ?: "", color = PnColor.BrandLeafDeep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private val TREND_RANGES = listOf(7, 30, 90)

@Composable
fun TrendsScreen(controller: DailyTargetsController, key: String, endDay: LocalDate, onBack: () -> Unit) {
    val s = TargetStrings.active
    var range by rememberSaveable { mutableIntStateOf(TREND_RANGES.first()) }
    val unit = TargetUnit.ofKey(key)
    val byDay = controller.mealsByDay()
    val days = (range - 1L downTo 0L).map { endDay.minusDays(it) }
    val scale: (Double) -> Double = if (key == SODIUM_KEY) ::saltGramsFromSodiumMg else { v -> v }
    val totals = days.map { knownTotal(byDay[it.toString()].orEmpty(), key)?.let(scale) }
    val targetLine = days.map { day -> controller.stateFor(day).targets.firstOrNull { it.nutrientKey == key }?.let { it.min ?: it.max }?.let(scale) }
    TargetsPage(title = "${s.trendsTitle} · ${displayName(key)}", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TREND_RANGES.forEach { n -> ChoicePill(s.lastDays(n), selected = range == n) { range = n } }
        }
        TargetsCard(null) {
            if (totals.all { it == null }) {
                Note(s.noDataYet)
            } else {
                LineChart(values = totals, line = targetLine, modifier = Modifier.fillMaxWidth().height(180.dp).semantics {
                    contentDescription = "${displayName(key)}, ${s.lastDays(range)}"
                })
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Note("● ${displayName(key)} (${if (key == SODIUM_KEY) "g" else unit.symbol})")
                    Note("┄ ${s.targetLine}")
                }
            }
        }
    }
}

/**
 * Daily points joined by a line, gaps where a day has no value, and a dashed step line for the target
 * (it steps where the profile changed). Sodium is drawn as salt, like every other place it is shown.
 */
@Composable
internal fun LineChart(values: List<Double?>, line: List<Double?>, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = PnColor.BrandLeaf) {
    val all = (values + line).filterNotNull()
    val top = (all.maxOrNull() ?: 1.0).coerceAtLeast(1e-9) * 1.1
    Canvas(modifier = modifier) {
        val n = values.size
        val stepX = if (n > 1) size.width / (n - 1) else 0f
        fun x(i: Int) = if (n > 1) i * stepX else size.width / 2
        fun y(v: Double) = (size.height - (v / top * size.height)).toFloat()
        drawLine(PnColor.InkLine, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        // Target: horizontal segments per day, stepping where the value changes.
        for (i in line.indices) {
            val v = line[i] ?: continue
            val start = if (i == 0) 0f else x(i) - stepX / 2
            val end = if (i == line.lastIndex) size.width else x(i) + stepX / 2
            drawLine(PnColor.InkSoft, Offset(start, y(v)), Offset(end, y(v)), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        var previous: Offset? = null
        values.forEachIndexed { i, v ->
            if (v == null) {
                previous = null
                return@forEachIndexed
            }
            val point = Offset(x(i), y(v))
            previous?.let { drawLine(valueColor, it, point, strokeWidth = 4f) }
            drawCircle(valueColor, radius = 6f, center = point)
            previous = point
        }
    }
}
