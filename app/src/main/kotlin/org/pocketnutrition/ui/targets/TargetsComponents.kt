// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.targets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.core.ProgressTarget
import org.pocketnutrition.core.TargetUnit
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.formatNumber
import org.pocketnutrition.core.roundForUnit
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.model.saltGramsFromSodiumMg

// Formatting and small building blocks shared by the daily-targets screens. Every figure shown is rounded
// with the platform's half-up rule before it is formatted.

internal const val SODIUM_KEY = "sodiumMg"

internal fun formatValue(value: Double): String = formatNumber(value, LanguageSettings.effective)

/** One decimal, half-up. */
internal fun oneDecimal(value: Double): Double = roundHalfUp(value * 10) / 10.0

/** An amount of [key] with its unit; sodium shows as salt in grams when [asSalt]. */
internal fun formatAmount(key: String, unit: TargetUnit, value: Double, asSalt: Boolean = true): String =
    if (asSalt && key == SODIUM_KEY) {
        "${formatValue(oneDecimal(saltGramsFromSodiumMg(value)))} g"
    } else {
        "${formatValue(roundForUnit(unit, value))} ${unit.symbol}"
    }

internal fun displayName(key: String, asSalt: Boolean = true): String =
    if (asSalt && key == SODIUM_KEY) TargetStrings.active.salt else TargetStrings.active.nutrientName(key)

/** "goal", "range", "limit" or "maintenance", from the shape of the target. */
internal fun kindOf(target: ProgressTarget): String {
    val s = TargetStrings.active
    val min = target.min
    val max = target.max
    return when {
        min != null && max != null && min == max -> s.kindEnergy
        min != null && max != null -> s.kindRange
        min != null -> s.kindGoal
        else -> s.kindLimit
    }
}

internal fun isLimit(target: ProgressTarget?): Boolean = target != null && target.min == null && target.max != null

/** "≥ 62 g", "≤ 50 g", "291–388 g", "2 588 kcal". */
internal fun targetText(target: ProgressTarget, asSalt: Boolean = true): String {
    val key = target.nutrientKey
    val min = target.min
    val max = target.max
    return when {
        min != null && max != null && min == max -> formatAmount(key, target.unit, min, asSalt)
        min != null && max != null -> {
            val low = formatAmount(key, target.unit, min, asSalt).substringBefore(' ')
            "$low–${formatAmount(key, target.unit, max, asSalt)}"
        }
        min != null -> "≥ ${formatAmount(key, target.unit, min, asSalt)}"
        max != null -> "≤ ${formatAmount(key, target.unit, max, asSalt)}"
        else -> ""
    }
}

internal fun formatDay(day: LocalDate): String {
    val locale = if (LanguageSettings.effective == "en") Locale.US else Locale.FRANCE
    return SimpleDateFormat("EEEE d MMMM", locale).format(Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant()))
}

/** A full-screen page with the app's header style and a scrolling body. */
@Composable
internal fun TargetsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(PnColor.SurfacePaper),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = PnColor.InkDefault,
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onBack) {
                Text(TargetStrings.active.close, color = PnColor.BrandLeafDeep, fontSize = 14.sp)
            }
        }
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** A card with a title, the app's Section look. */
@Composable
internal fun TargetsCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(PnRadius.Default)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PnColor.SurfaceCard, shape)
            .border(1.dp, PnColor.InkLine, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) {
            Text(title, color = PnColor.BrandLeafDeep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        }
        content()
    }
}

/** A selectable pill, the language switcher's look. */
@Composable
internal fun ChoicePill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PnRadius.Default)
    Text(
        text = label,
        color = if (selected) PnColor.InkOnInverse else PnColor.InkDefault,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .background(if (selected) PnColor.BrandLeafDeep else PnColor.SurfaceCard, shape)
            .border(1.dp, PnColor.InkLine, shape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** A full-width selectable option with an optional second line (questionnaire answers). */
@Composable
internal fun ChoiceRow(label: String, detail: String? = null, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PnRadius.Default)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) PnColor.StatusOkSoft else PnColor.SurfaceCard, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) PnColor.BrandLeaf else PnColor.InkLine, shape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, color = PnColor.InkDefault, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        if (detail != null) Text(detail, color = PnColor.InkSoft, fontSize = 12.sp)
    }
}

/** A text action in the brand colour. */
@Composable
internal fun LinkAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = PnColor.BrandLeafDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Note(text: String) {
    Text(text, color = PnColor.InkSoft, fontSize = 12.sp, lineHeight = 17.sp)
}
