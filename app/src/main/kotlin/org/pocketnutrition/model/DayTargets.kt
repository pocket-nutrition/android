// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import org.pocketnutrition.core.DayTarget
import org.pocketnutrition.core.DayTotal
import org.pocketnutrition.core.NUTRIENT_KEYS
import org.pocketnutrition.core.NutrientProgress
import org.pocketnutrition.core.OverrideVersion
import org.pocketnutrition.core.ProfileVersion
import org.pocketnutrition.core.ReferenceRow
import org.pocketnutrition.core.TargetProfile
import org.pocketnutrition.core.TargetsResult
import org.pocketnutrition.core.UnsupportedReason
import org.pocketnutrition.core.WeightEntry
import org.pocketnutrition.core.applyOverrides
import org.pocketnutrition.core.dailyTargets
import org.pocketnutrition.core.dayProgress
import org.pocketnutrition.core.overridesForDay
import org.pocketnutrition.core.profileForDay
import org.pocketnutrition.core.roundHalfUp

// Spec 05 §2, "Targets for a day D" and "Progress": the glue between the stores and the ported functions.
// Pure, so the definition-of-done behaviours (a weight changes today and not yesterday, coverage "1 of 2")
// are unit tests rather than only screenshots.

/** What the targets of one day are. */
sealed interface DayTargetsState {
    /** No profile and no override in force on the day: the journal shows totals only, exactly as before. */
    data object None : DayTargetsState

    /** A profile applies but no reference table was ever fetched (first launch offline). */
    data object NeedsConnection : DayTargetsState

    /** The answers are outside what EFSA's table covers; overrides still apply. */
    data class Unsupported(val reason: UnsupportedReason, val targets: List<DayTarget>) : DayTargetsState

    data class Ready(val targets: List<DayTarget>, val profile: TargetProfile?) : DayTargetsState
}

/** Every input the targets of a day depend on. */
data class TargetInputs(
    val table: List<ReferenceRow>?,
    val profiles: List<ProfileVersion>,
    val weights: List<WeightEntry>,
    val overrides: List<OverrideVersion>,
)

fun targetsForDay(inputs: TargetInputs, day: String): DayTargetsState {
    val profile = profileForDay(inputs.profiles, inputs.weights, day)
    if (profile == null) {
        // "Skip, I'll enter my own values": with no profile, every target is an override.
        if (overridesForDay(inputs.overrides, day).isEmpty()) return DayTargetsState.None
        return DayTargetsState.Ready(applyOverrides(emptyList(), inputs.overrides, day), profile = null)
    }
    val table = inputs.table ?: return DayTargetsState.NeedsConnection
    return when (val result = dailyTargets(table, profile)) {
        is TargetsResult.Unsupported -> DayTargetsState.Unsupported(result.reason, applyOverrides(emptyList(), inputs.overrides, day))
        is TargetsResult.Ok -> DayTargetsState.Ready(applyOverrides(result.targets, inputs.overrides, day), profile)
    }
}

/** The targets to draw rings and statuses against, whatever the state. */
val DayTargetsState.targets: List<DayTarget>
    get() = when (this) {
        is DayTargetsState.Ready -> targets
        is DayTargetsState.Unsupported -> targets
        DayTargetsState.None, DayTargetsState.NeedsConnection -> emptyList()
    }

private val V1_TOTAL_KEYS = listOf("energyKcal", "proteinG", "carbohydratesG", "fatG")

/**
 * A meal's known nutrients: its saved `nutrients` (record v2); for an older meal without them, only the
 * four macros of its totals — or energy alone when every item is a quick add. Absent keys are unknown.
 */
fun mealNutrients(meal: SavedMeal): Map<String, Double> {
    meal.nutrients?.let { return it }
    if (meal.items.isNotEmpty() && meal.items.all { it.isQuickAdd }) return mapOf("energyKcal" to meal.totals.energyKcal)
    val t = meal.totals
    return V1_TOTAL_KEYS.zip(listOf(t.energyKcal, t.proteinG, t.carbohydratesG, t.fatG)).toMap()
}

fun progressForDay(state: DayTargetsState, meals: List<SavedMeal>, keys: List<String>): List<NutrientProgress> =
    dayProgress(state.targets, meals.map(::mealNutrients), keys)

/** The unrounded known total of [key] over [meals]; null when no meal knows it. */
fun knownTotal(meals: List<SavedMeal>, key: String): Double? {
    val known = meals.mapNotNull { mealNutrients(it)[key] }
    return if (known.isEmpty()) null else known.fold(0.0) { s, v -> s + v }
}

/** Day totals of [key] for the self-comparison, one per day that has meals. */
fun dayTotals(mealsByDay: Map<String, List<SavedMeal>>, key: String): List<DayTotal> =
    mealsByDay.map { (day, meals) -> DayTotal(day, knownTotal(meals, key)) }

/** The meals that contributed most to [key] on a day, largest first, ties in logging order. */
fun topContributors(meals: List<SavedMeal>, key: String, limit: Int = 3): List<Pair<SavedMeal, Double>> =
    meals.mapNotNull { m -> mealNutrients(m)[key]?.let { m to it } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(limit)

/** Salt shown from sodium (spec 05 §6): display arithmetic only, the stored key stays `sodiumMg`. */
const val SALT_PER_SODIUM = 2.5

fun saltGramsFromSodiumMg(sodiumMg: Double): Double = sodiumMg * SALT_PER_SODIUM / 1000

/** The day-detail groups, covering every key of [NUTRIENT_KEYS] exactly once. */
enum class NutrientGroup(val keys: List<String>) {
    ENERGY_MACROS(listOf("energyKcal", "proteinG", "carbohydratesG", "sugarsG", "fiberG", "fatG", "waterG")),
    FATS(listOf("saturatedFatG", "monounsaturatedFatG", "polyunsaturatedFatG", "cholesterolMg")),
    MINERALS(listOf("sodiumMg", "potassiumMg", "calciumMg", "phosphorusMg", "magnesiumMg", "ironMg", "zincMg")),
    VITAMINS(listOf("vitaminAUg", "vitaminB6Mg", "vitaminB12Ug", "vitaminCMg", "vitaminDUg", "niacinMg")),
}

/** One nutrient over a week: the mean known daily total, and the mean of each day's percent of its own target. */
data class WeekAverage(val key: String, val averageTotal: Double?, val averagePercent: Int?, val daysWithData: Int)

/**
 * The 7-day averages against the targets of each day. A day with no meal at all is left out rather than
 * counted as zero intake: an unlogged day is unknown.
 */
fun weekAverages(days: List<Pair<DayTargetsState, List<SavedMeal>>>, keys: List<String>): List<WeekAverage> {
    val logged = days.filter { it.second.isNotEmpty() }
    return keys.map { key ->
        val progress = logged.map { (state, meals) -> progressForDay(state, meals, listOf(key)).single() }
        val totals = logged.mapNotNull { knownTotal(it.second, key) }
        // A day where no meal knows the nutrient is unknown, not 0 %: it stays out of the mean.
        val percents = progress.filter { it.knownMeals > 0 }.mapNotNull { it.percent }
        WeekAverage(
            key = key,
            averageTotal = if (totals.isEmpty()) null else totals.sum() / totals.size,
            averagePercent = if (percents.isEmpty()) null else roundHalfUp(percents.sum().toDouble() / percents.size),
            daysWithData = totals.size,
        )
    }
}
