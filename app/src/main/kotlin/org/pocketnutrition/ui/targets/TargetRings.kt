// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.targets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import org.pocketnutrition.core.NutrientProgress
import org.pocketnutrition.core.ProgressStatus
import org.pocketnutrition.core.ProgressTarget
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.selfComparison
import org.pocketnutrition.model.DailyTargetsController
import org.pocketnutrition.model.DailyTargetsFlag
import org.pocketnutrition.model.DayTargetsState
import org.pocketnutrition.model.dayTotals
import org.pocketnutrition.model.knownTotal
import org.pocketnutrition.model.progressForDay
import org.pocketnutrition.model.targets

// The compact rings at the top of each journal day (spec 05 §6). All kinds fill the same way; past 100 % a
// ring keeps filling in a muted tone — never red, no warning icon. A range is full anywhere between its
// bounds. A ring whose meals do not all know the nutrient is drawn lighter.

private val RingFill = PnColor.BrandLeaf
private val RingFillPartial = PnColor.BrandLeaf.copy(alpha = 0.45f)
/** The second lap: deliberately quiet. Not StatusBad, not StatusWarn. */
private val RingSecondLap = PnColor.InkSoft.copy(alpha = 0.38f)
private val RingTrack = PnColor.InkLine

private const val FULL_PERCENT = 100

/** How much of the ring to fill, 0..200 (a second lap at most), given the target's shape. */
internal fun ringPercent(progress: NutrientProgress, target: ProgressTarget?): Int {
    val percent = progress.percent ?: return 0
    val isRange = target?.min != null && target.max != null && target.min != target.max
    // A range is full anywhere between its bounds: only going past the upper bound starts a second lap.
    if (isRange && progress.status == ProgressStatus.OK) return FULL_PERCENT
    return percent.coerceIn(0, 2 * FULL_PERCENT)
}

@Composable
internal fun TargetRing(progress: NutrientProgress, target: ProgressTarget?, size: Dp = 46.dp, stroke: Dp = 5.dp) {
    val fill = ringPercent(progress, target)
    val partial = progress.knownMeals < progress.totalMeals
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = Size(this.size.width - width, this.size.height - width)
            val topLeft = Offset(inset, inset)
            drawArc(RingTrack, 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
            val first = fill.coerceAtMost(FULL_PERCENT)
            if (first > 0) {
                drawArc(if (partial) RingFillPartial else RingFill, -90f, 360f * first / FULL_PERCENT, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            }
            if (fill > FULL_PERCENT) {
                drawArc(RingSecondLap, -90f, 360f * (fill - FULL_PERCENT) / FULL_PERCENT, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            }
        }
        Text(
            text = progress.percent?.let { "$it" } ?: "–",
            color = PnColor.InkDefault,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A ring with its label below, as the header and the day detail show it. */
@Composable
private fun LabelledRing(progress: NutrientProgress, target: ProgressTarget?) {
    val s = TargetStrings.active
    val total = progress.total?.let { formatAmount(progress.nutrientKey, progress.unit, it) } ?: "–"
    val description = buildString {
        append(displayName(progress.nutrientKey)).append(", ").append(total)
        target?.let { append(" / ").append(targetText(it)) }
        append(", ").append(s.status(progress.status))
        if (progress.knownMeals < progress.totalMeals) append(", ").append(s.coverage(progress.knownMeals, progress.totalMeals))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(58.dp).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        TargetRing(progress, target)
        Text(
            displayName(progress.nutrientKey),
            color = PnColor.InkSoft,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (isLimit(target)) {
            Text(s.limitMarker, color = PnColor.InkSoft, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Everything the feature adds under a journal day header: the rings, the self-comparison lines, and on
 * today the "Set my targets" card or the "Log weight" link. Nothing at all when the flag is off, and
 * nothing on a day before the first profile (totals only, exactly as before).
 */
@Composable
fun DayTargetsHeader(
    controller: DailyTargetsController,
    day: LocalDate,
    onOpenDetail: (LocalDate) -> Unit,
    onSetTargets: () -> Unit,
    onLogWeight: () -> Unit,
) {
    if (!DailyTargetsFlag.enabled) return
    val s = TargetStrings.active
    val isToday = day == controller.today()
    when (val state = controller.stateFor(day)) {
        DayTargetsState.None -> if (isToday && controller.needsSetup()) SetTargetsCard(onSetTargets)
        DayTargetsState.NeedsConnection -> if (isToday) Note(s.needsConnection)
        is DayTargetsState.Unsupported -> Column {
            if (isToday) Note(s.unsupported(state.reason))
            if (state.targets.isNotEmpty()) RingsRow(controller, day, state, onOpenDetail)
        }
        is DayTargetsState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RingsRow(controller, day, state, onOpenDetail)
            if (isToday && state.profile != null) LinkAction(s.logWeight, onClick = onLogWeight)
        }
    }
}

@Composable
private fun RingsRow(controller: DailyTargetsController, day: LocalDate, state: DayTargetsState, onOpenDetail: (LocalDate) -> Unit) {
    val keys = controller.store.watchList.keys
    val meals = controller.mealsOn(day)
    val progress = progressForDay(state, meals, keys)
    val targets = state.targets.associateBy { it.nutrientKey }
    val (ringed, untargeted) = progress.partition { it.status != ProgressStatus.NO_TARGET }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))
            .border(1.dp, PnColor.InkLine, RoundedCornerShape(PnRadius.Default))
            .clickable { onOpenDetail(day) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (ringed.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ringed.forEach { LabelledRing(it, targets[it.nutrientKey]) }
            }
        }
        if (untargeted.isNotEmpty()) {
            val byDay = controller.mealsByDay()
            untargeted.forEach { p -> SelfComparisonLine(p, byDay, day) }
        }
    }
}

/** "Salt 6.2 g · usual 5.1 g": today against the person's own previous 7 days, no percentage, no judgement. */
@Composable
private fun SelfComparisonLine(progress: NutrientProgress, byDay: Map<String, List<org.pocketnutrition.model.SavedMeal>>, day: LocalDate) {
    val s = TargetStrings.active
    val key = progress.nutrientKey
    val today = knownTotal(byDay[day.toString()].orEmpty(), key)
    val comparison = selfComparison(dayTotals(byDay, key), day.toString(), today)
    val text = buildString {
        append(displayName(key)).append(' ')
        append(progress.total?.let { formatAmount(key, progress.unit, it) } ?: "–")
        comparison.average?.let { append(" · ").append(s.usual(formatAmount(key, progress.unit, it))) }
    }
    Text(text, color = PnColor.InkSoft, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun SetTargetsCard(onSetTargets: () -> Unit) {
    val s = TargetStrings.active
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PnColor.StatusOkSoft, RoundedCornerShape(PnRadius.Default))
            .clickable(onClick = onSetTargets)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(s.setMyTargets + " ›", color = PnColor.BrandLeafDeep, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(s.setMyTargetsBody, color = PnColor.InkSoft, fontSize = 12.sp)
    }
}
