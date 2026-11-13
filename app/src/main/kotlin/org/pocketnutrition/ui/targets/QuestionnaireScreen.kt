// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.targets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pocketnutrition.core.CalculationBasis
import org.pocketnutrition.core.LactationPeriod
import org.pocketnutrition.core.ProfileVersion
import org.pocketnutrition.core.Situation
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.model.DailyTargetsController
import org.pocketnutrition.model.DayTargetsState
import org.pocketnutrition.model.TargetSettingsRules
import org.pocketnutrition.model.targets
import org.pocketnutrition.model.targetsForDay
import org.pocketnutrition.ui.PrimaryButton

// The optional questionnaire of spec 05 §4: five short steps, then a preview. It asks only what changes the
// numbers — never a gender — and every step offers to skip to entering one's own values.

/** EFSA's PAL grid for the four activity levels: one of the constants spec 05 §0 allows, labelled as ours. */
internal val ACTIVITY_PALS = listOf(1.4, 1.6, 1.8, 2.0)

/** EFSA only uses menopause status from 40. */
private const val MENOPAUSE_QUESTION_FROM_AGE = 40
private const val STEPS = 5
private const val PREVIEW_STEP = STEPS + 1
private const val MIN_BIRTH_YEAR = 1900
private val WEIGHT_RANGE_KG = 20.0..400.0
private val HEIGHT_RANGE_CM = 50.0..260.0

private fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

@Composable
fun QuestionnaireScreen(controller: DailyTargetsController, onDone: () -> Unit, onSkip: () -> Unit) {
    val s = TargetStrings.active
    val today = controller.today()
    val existing = controller.store.profiles.lastOrNull()

    var step by rememberSaveable { mutableIntStateOf(1) }
    var birthYear by rememberSaveable { mutableStateOf(existing?.birthYear?.toString().orEmpty()) }
    var height by rememberSaveable { mutableStateOf(controller.store.heightCm?.let { formatValue(it) }.orEmpty()) }
    var weight by rememberSaveable {
        mutableStateOf((controller.store.weights.lastOrNull()?.kg ?: existing?.weightKg)?.let { formatValue(it) }.orEmpty())
    }
    var basis by rememberSaveable { mutableStateOf(existing?.basis ?: CalculationBasis.AVERAGE) }
    var postmenopausal by rememberSaveable { mutableStateOf(existing?.postmenopausal ?: false) }
    var situation by rememberSaveable { mutableStateOf(existing?.situation ?: Situation.NONE) }
    var trimester by rememberSaveable { mutableIntStateOf(existing?.trimester ?: 1) }
    var lactation by rememberSaveable { mutableStateOf(existing?.lactation ?: LactationPeriod.FIRST_6_MONTHS) }
    var pal by rememberSaveable { mutableStateOf(existing?.pal ?: 1.6) }

    val year = birthYear.trim().toIntOrNull()?.takeIf { it in MIN_BIRTH_YEAR..today.year }
    val weightKg = parseDecimal(weight)?.takeIf { it in WEIGHT_RANGE_KG }
    val heightCm = parseDecimal(height)?.takeIf { it in HEIGHT_RANGE_CM }
    val heightOk = height.isBlank() || heightCm != null
    val age = year?.let { today.year - it }
    val asksMenopause = basis != CalculationBasis.MALE && (age ?: 0) >= MENOPAUSE_QUESTION_FROM_AGE
    // Pregnancy and breastfeeding are only offered with the female basis.
    val effectiveSituation = if (basis == CalculationBasis.FEMALE) situation else Situation.NONE

    fun version(): ProfileVersion? {
        if (year == null || weightKg == null) return null
        return ProfileVersion(
            effectiveFrom = today.toString(),
            birthYear = year,
            basis = basis,
            situation = effectiveSituation,
            trimester = if (effectiveSituation == Situation.PREGNANT) trimester else null,
            lactation = if (effectiveSituation == Situation.LACTATING) lactation else null,
            postmenopausal = if (asksMenopause) postmenopausal else null,
            pal = pal,
            weightKg = weightKg,
        )
    }

    val canContinue = when (step) {
        1 -> year != null
        2 -> weightKg != null && heightOk
        else -> true
    }

    TargetsPage(title = s.questionnaireTitle, onBack = onDone) {
        if (step <= STEPS) Text(s.stepOf(step, STEPS), color = PnColor.InkSoft, fontSize = 12.sp)
        when (step) {
            1 -> {
                Question(s.birthYearQuestion)
                NumberField(birthYear, s.birthYearLabel, KeyboardType.Number, isError = birthYear.isNotBlank() && year == null) { birthYear = it }
            }
            2 -> {
                Question(s.bodyQuestion)
                NumberField(height, s.heightLabel, KeyboardType.Decimal, isError = !heightOk) { height = it }
                Note(s.heightNote)
                NumberField(weight, s.weightLabel, KeyboardType.Decimal, isError = weight.isNotBlank() && weightKg == null) { weight = it }
            }
            3 -> {
                Question(s.basisQuestion)
                Note(s.basisExplain)
                ChoiceRow(s.basisFemale, selected = basis == CalculationBasis.FEMALE) { basis = CalculationBasis.FEMALE }
                ChoiceRow(s.basisMale, selected = basis == CalculationBasis.MALE) { basis = CalculationBasis.MALE }
                ChoiceRow(s.basisAverage, selected = basis == CalculationBasis.AVERAGE) { basis = CalculationBasis.AVERAGE }
                if (asksMenopause) {
                    Question(s.postmenopausalQuestion)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoicePill(s.yes, postmenopausal) { postmenopausal = true }
                        ChoicePill(s.no, !postmenopausal) { postmenopausal = false }
                    }
                }
            }
            4 -> {
                Question(s.situationQuestion)
                if (basis != CalculationBasis.FEMALE) {
                    ChoiceRow(s.situationNone, selected = true) {}
                } else {
                    ChoiceRow(s.situationNone, selected = situation == Situation.NONE) { situation = Situation.NONE }
                    ChoiceRow(s.situationPregnant, selected = situation == Situation.PREGNANT) { situation = Situation.PREGNANT }
                    if (situation == Situation.PREGNANT) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..3).forEach { n -> ChoicePill(s.trimester(n), trimester == n) { trimester = n } }
                        }
                    }
                    ChoiceRow(s.situationLactating, selected = situation == Situation.LACTATING) { situation = Situation.LACTATING }
                    if (situation == Situation.LACTATING) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoicePill(s.lactationFirst, lactation == LactationPeriod.FIRST_6_MONTHS) { lactation = LactationPeriod.FIRST_6_MONTHS }
                            ChoicePill(s.lactationAfter, lactation == LactationPeriod.AFTER_6_MONTHS) { lactation = LactationPeriod.AFTER_6_MONTHS }
                        }
                    }
                }
            }
            5 -> {
                Question(s.activityQuestion)
                ACTIVITY_PALS.forEach { level ->
                    ChoiceRow(s.activityName(level), s.activityExample(level), selected = pal == level) { pal = level }
                }
            }
            else -> Preview(controller, version())
        }

        if (step < PREVIEW_STEP) {
            PrimaryButton(text = s.next, enabled = canContinue, onClick = { step++ })
        } else {
            PrimaryButton(text = s.save, enabled = version() != null, onClick = {
                version()?.let { controller.store.saveProfile(it, heightCm) }
                onDone()
            })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (step > 1) LinkAction(s.previous) { step-- }
            LinkAction(s.skip, onClick = onSkip)
        }
    }
}

@Composable
private fun Question(text: String) {
    Text(text, color = PnColor.InkDefault, fontSize = 18.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun NumberField(value: String, label: String, type: KeyboardType, isError: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = if (isError) ({ Text(TargetStrings.active.invalidNumber) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The targets the answers give, before saving. Unsupported answers point to a professional; saving still works. */
@Composable
private fun Preview(controller: DailyTargetsController, version: ProfileVersion?) {
    val s = TargetStrings.active
    if (version == null) return
    val inputs = controller.inputs().let {
        it.copy(profiles = TargetSettingsRules.saveProfile(it.profiles, version))
    }
    val state = targetsForDay(inputs, version.effectiveFrom)
    Text(s.previewTitle, color = PnColor.InkDefault, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    when (state) {
        DayTargetsState.NeedsConnection -> Note(s.needsConnection)
        is DayTargetsState.Unsupported -> Note(s.unsupported(state.reason))
        else -> {
            Note(s.previewBody)
            TargetsCard(null) {
                state.targets.forEach { t ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(displayName(t.nutrientKey), color = PnColor.InkDefault, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${targetText(t)} · ${kindOf(t)}", color = PnColor.InkSoft, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
