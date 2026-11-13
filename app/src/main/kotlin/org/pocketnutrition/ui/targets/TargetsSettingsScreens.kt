// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.targets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.pocketnutrition.core.CalculationBasis
import org.pocketnutrition.core.DayTarget
import org.pocketnutrition.core.NUTRIENT_KEYS
import org.pocketnutrition.core.NutrientTarget
import org.pocketnutrition.core.Situation
import org.pocketnutrition.core.TargetOverride
import org.pocketnutrition.core.TargetProfile
import org.pocketnutrition.core.TargetUnit
import org.pocketnutrition.core.WatchList
import org.pocketnutrition.core.WatchListPreset
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.weightForDay
import org.pocketnutrition.model.DailyTargetsController
import org.pocketnutrition.model.DayTargetsState
import org.pocketnutrition.model.targets
import org.pocketnutrition.ui.PrimaryButton

// Settings → Daily targets: the hub, the Targets screen (presets, watch list, overrides), the explanation
// screen and the weight log (spec 05 §5–§6).

private const val ADULT_AGE = 18
private const val WEIGHT_CHART_DAYS = 30L

@Composable
fun TargetsHubScreen(
    controller: DailyTargetsController,
    onBack: () -> Unit,
    onQuestionnaire: () -> Unit,
    onTargets: () -> Unit,
    onExplanation: () -> Unit,
    onWeight: () -> Unit,
) {
    val s = TargetStrings.active
    TargetsPage(title = s.featureTitle, onBack = onBack) {
        Note(s.notMedicalAdvice)
        HubRow(if (controller.store.profiles.isEmpty()) s.answerQuestions else s.editAnswers, onQuestionnaire)
        HubRow(s.targetsTitle, onTargets)
        HubRow(s.explanationTitle, onExplanation)
        HubRow(s.weightTitle, onWeight)
        if (controller.reference.current == null) Note(s.needsConnection)
    }
}

@Composable
private fun HubRow(label: String, onClick: () -> Unit) {
    TargetsCard(null) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = PnColor.InkDefault, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("›", color = PnColor.InkSoft, fontSize = 18.sp)
        }
    }
}

// ── Targets: presets, watch list, overrides ─────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TargetsEditorScreen(controller: DailyTargetsController, onBack: () -> Unit) {
    val s = TargetStrings.active
    val today = controller.today()
    val store = controller.store
    val watch = store.watchList
    val state = controller.stateFor(today)
    val current = state.targets.associateBy { it.nutrientKey }
    val computed = controller.computedFor(today).associateBy { it.nutrientKey }
    var editing by remember { mutableStateOf<String?>(null) }

    TargetsPage(title = s.targetsTitle, onBack = onBack) {
        TargetsCard(s.presetsTitle) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchListPreset.choosable.forEach { preset ->
                    ChoicePill(s.presetName(preset.id), selected = watch.preset == preset) { store.setWatchList(WatchList.of(preset)) }
                }
                if (watch.preset == WatchListPreset.CUSTOM) ChoicePill(s.presetName("custom"), selected = true) {}
            }
            if (watch.preset.isMedical) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(PnColor.BrandCarrotSoft, RoundedCornerShape(PnRadius.Small)).padding(12.dp),
                ) {
                    Text(s.medicalBanner, color = PnColor.InkDefault, fontSize = 13.sp)
                    LinkAction(s.medicalBannerAction) { editing = watch.keys.firstOrNull() }
                }
            }
        }

        TargetsCard(s.watchListTitle) {
            Note(s.watchListHint)
            val ordered = watch.keys + NUTRIENT_KEYS.filter { it !in watch.keys }
            ordered.forEachIndexed { index, key ->
                if (index > 0) HorizontalDivider(color = PnColor.InkLine)
                WatchRow(
                    key = key,
                    shownIndex = watch.keys.indexOf(key),
                    shownCount = watch.keys.size,
                    current = current[key],
                    computed = computed[key],
                    onToggle = { shown -> store.setWatchList(watch.withKeys(if (shown) watch.keys + key else watch.keys - key)) },
                    onMove = { delta ->
                        val i = watch.keys.indexOf(key)
                        val j = i + delta
                        if (i >= 0 && j in watch.keys.indices) {
                            val keys = watch.keys.toMutableList()
                            keys[i] = keys[j].also { keys[j] = keys[i] }
                            store.setWatchList(watch.withKeys(keys))
                        }
                    },
                    onEdit = { editing = key },
                    onReset = { store.setOverride(today.toString(), key, null) },
                )
            }
        }
        if (state.targets.any { it.overridden }) {
            LinkAction(s.resetAll) { store.resetAllOverrides(today.toString()) }
        }
    }

    editing?.let { key ->
        OverrideDialog(
            key = key,
            initial = current[key],
            onSave = { value ->
                store.setOverride(today.toString(), key, value)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun WatchRow(
    key: String,
    shownIndex: Int,
    shownCount: Int,
    current: DayTarget?,
    computed: NutrientTarget?,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
) {
    val s = TargetStrings.active
    val shown = shownIndex >= 0
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName(key), color = if (shown) PnColor.InkDefault else PnColor.InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                val line = when {
                    current != null && current.overridden -> "${targetText(current)} · ${s.yourValue}" +
                        (computed?.let { " (${s.efsaValue} ${targetText(it)})" } ?: "")
                    current != null -> "${targetText(current)} · ${kindOf(current)}"
                    else -> s.noEfsaTarget
                }
                Note(line)
            }
            if (shown) {
                IconButton(onClick = { onMove(-1) }, enabled = shownIndex > 0, modifier = Modifier.semantics { contentDescription = s.moveUp }) {
                    Text("↑", color = PnColor.InkSoft)
                }
                IconButton(onClick = { onMove(1) }, enabled = shownIndex < shownCount - 1, modifier = Modifier.semantics { contentDescription = s.moveDown }) {
                    Text("↓", color = PnColor.InkSoft)
                }
            }
            ChoicePill(if (shown) s.shown else s.hidden, selected = shown) { onToggle(!shown) }
        }
        Row {
            LinkAction(s.edit, onClick = onEdit)
            if (current?.overridden == true) LinkAction(s.resetToEfsa, onClick = onReset)
        }
    }
}

@Composable
private fun OverrideDialog(key: String, initial: DayTarget?, onSave: (TargetOverride) -> Unit, onDismiss: () -> Unit) {
    val s = TargetStrings.active
    val unit = TargetUnit.ofKey(key)
    var min by rememberSaveable(key) { mutableStateOf(initial?.min?.let { formatValue(it) }.orEmpty()) }
    var max by rememberSaveable(key) { mutableStateOf(initial?.max?.let { formatValue(it) }.orEmpty()) }
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val minValue = parse(min)
    val maxValue = parse(max)
    val valid = (min.isBlank() || minValue != null) && (max.isBlank() || maxValue != null) &&
        (minValue != null || maxValue != null) && (minValue == null || maxValue == null || minValue <= maxValue)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${s.editTitle} · ${s.nutrientName(key)} (${unit.symbol})", color = PnColor.InkDefault) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(min, { min = it }, label = { Text(s.minLabel) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(max, { max = it }, label = { Text(s.maxLabel) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(TargetOverride(minValue, maxValue)) }) { Text(s.save, color = PnColor.BrandLeafDeep) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel, color = PnColor.InkSoft) } },
        containerColor = PnColor.SurfaceCard,
    )
}

// ── Explanation ─────────────────────────────────────────────────────────────

private fun basisLabel(basis: CalculationBasis): String {
    val s = TargetStrings.active
    return when (basis) {
        CalculationBasis.FEMALE -> s.basisFemale
        CalculationBasis.MALE -> s.basisMale
        CalculationBasis.AVERAGE -> s.basisAverage
    }
}

private fun situationLabel(profile: TargetProfile): String? {
    val s = TargetStrings.active
    return when (profile.situation) {
        Situation.NONE -> null
        Situation.PREGNANT -> "${s.situationPregnant}, ${s.trimester(profile.trimester ?: 1)}"
        Situation.LACTATING -> s.situationLactating
    }
}

/**
 * The EFSA value kinds behind [target], for the explanation: AR for energy, RI for a range, PRI (else AI)
 * for a goal, and UL when an upper level is set. PRI versus AI is read from the rows of the person's age
 * band that the target cites, the same preference the calculation applies.
 */
private fun efsaKinds(target: DayTarget, table: List<org.pocketnutrition.core.ReferenceRow>, profile: TargetProfile?): List<String> {
    val min = target.min
    val max = target.max
    val main = when {
        min != null && max != null && min == max -> "AR"
        min != null && max != null -> "RI"
        min != null -> {
            val age = profile?.ageYears
            val cited = table.filter { row ->
                row.nutrientKey == target.nutrientKey && row.sourceLink in target.sources && row.valueMin != null &&
                    (age == null || (row.ageUnit == "y" && (row.ageFrom ?: 0.0) <= age && age <= (row.ageTo ?: 0.0)))
            }
            if (cited.any { it.valueKind == "pri" }) "PRI" else "AI"
        }
        else -> null
    }
    return listOfNotNull(main, if (target.upperLimit != null) "UL" else null)
}

@Composable
fun ExplanationScreen(controller: DailyTargetsController, day: LocalDate, onBack: () -> Unit) {
    val s = TargetStrings.active
    val state = controller.stateFor(day)
    val profile = (state as? DayTargetsState.Ready)?.profile
    val table = controller.table.orEmpty()
    val uri = LocalUriHandler.current
    TargetsPage(title = s.explanationTitle, onBack = onBack) {
        Note(s.notMedicalAdvice)
        if (profile != null) {
            Note(
                s.inputsUsed(
                    age = profile.ageYears,
                    population = if (profile.ageYears >= ADULT_AGE) s.populationAdults else s.populationChildren,
                    pal = formatValue(profile.pal),
                    weight = formatValue(profile.weightKg),
                    basis = basisLabel(profile.basis),
                    situation = situationLabel(profile),
                ),
            )
        } else {
            Note(s.noProfile)
        }
        controller.store.profileUpdatedAtEpochSeconds?.let { seconds ->
            Note(s.profileUpdated(formatDay(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).toLocalDate())))
        }
        when (state) {
            DayTargetsState.NeedsConnection -> Note(s.needsConnection)
            is DayTargetsState.Unsupported -> Note(s.unsupported(state.reason))
            else -> Unit
        }
        state.targets.forEach { t ->
            TargetsCard(displayName(t.nutrientKey)) {
                Text("${targetText(t)} · ${kindOf(t)}", color = PnColor.InkDefault, fontSize = 14.sp)
                val upper = t.upperLimit
                if (upper != null) Note("UL ${formatAmount(t.nutrientKey, t.unit, upper, asSalt = false)}")
                if (t.overridden) {
                    Text(s.overridden, color = PnColor.BrandLeafDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    LinkAction(s.resetToEfsa) { controller.store.setOverride(controller.today().toString(), t.nutrientKey, null) }
                } else {
                    val kinds = efsaKinds(t, table, profile)
                    if (kinds.isNotEmpty()) Note("EFSA ${kinds.joinToString(" · ")}")
                }
                if (t.sources.isNotEmpty()) {
                    Note(s.sourcesLabel)
                    t.sources.forEach { link ->
                        val isUrl = link.startsWith("https://") || link.startsWith("http://")
                        Text(
                            link,
                            color = if (isUrl) PnColor.BrandLeafDeep else PnColor.InkSoft,
                            fontSize = 12.sp,
                            textDecoration = if (isUrl) TextDecoration.Underline else null,
                            modifier = if (isUrl) Modifier.clickable { runCatching { uri.openUri(link) } } else Modifier,
                        )
                    }
                }
            }
        }
        TargetsCard(s.ourChoicesTitle) {
            s.ourChoices.forEach { Note("• $it") }
        }
        controller.reference.current?.response?.let { Note("${it.source} — ${it.sourceUrl}") }
    }
}

// ── Weight ──────────────────────────────────────────────────────────────────

@Composable
fun WeightScreen(controller: DailyTargetsController, onBack: () -> Unit) {
    val s = TargetStrings.active
    val today = controller.today()
    val entries = controller.store.weights
    var text by rememberSaveable { mutableStateOf(entries.firstOrNull { it.date == today.toString() }?.kg?.let { formatValue(it) }.orEmpty()) }
    val kg = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..400.0 }
    TargetsPage(title = s.weightTitle, onBack = onBack) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(s.weightEntryLabel) },
            singleLine = true,
            isError = text.isNotBlank() && kg == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(text = s.save, enabled = kg != null, onClick = { kg?.let { controller.store.logWeight(today.toString(), it) } })

        if (entries.isEmpty()) {
            Note(s.noWeights)
        } else {
            val first = LocalDate.parse(entries.first().date).coerceAtLeast(today.minusDays(WEIGHT_CHART_DAYS - 1))
            val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
            val byDate = entries.associateBy { it.date }
            TargetsCard(null) {
                LineChart(
                    values = days.map { byDate[it.toString()]?.kg },
                    line = days.map { weightForDay(entries, it.toString()) },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Note("● ${s.weightTitle}")
                    Note("┄ ${s.weightMean}")
                }
            }
            TargetsCard(null) {
                entries.reversed().forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDay(LocalDate.parse(e.date)), color = PnColor.InkDefault, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(s.kg(formatValue(oneDecimal(e.kg))), color = PnColor.InkDefault, fontSize = 14.sp)
                        TextButton(onClick = { controller.store.deleteWeight(e.date) }) { Text(s.delete, color = PnColor.InkSoft, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}
