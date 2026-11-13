// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

// How far a day has got toward its targets, for the watch-list rings, and how today compares with the
// person's own recent days for nutrients that have no target. Port of the sandbox's `dayProgress.ts`,
// asserted by the `day-progress` and `self-comparison` suites. Unknown values are never counted as zero: a
// meal without a figure for a nutrient lowers the coverage instead.

enum class ProgressStatus(val id: String) {
    NO_TARGET("no-target"), UNDER("under"), OK("ok"), OVER("over")
}

data class NutrientProgress(
    val nutrientKey: String,
    val unit: TargetUnit,
    /** Sum of the known values, rounded like targets; null when no meal knows it. */
    val total: Double?,
    /** Meals that have a value for this nutrient, out of [totalMeals]. */
    val knownMeals: Int,
    val totalMeals: Int,
    /** Known total / reference x 100, rounded half-up; reference is min, else max. Null without a target. */
    val percent: Int?,
    val status: ProgressStatus,
    /** The known total exceeds EFSA's tolerable upper intake level. */
    val aboveUpperLimit: Boolean,
)

/**
 * Progress of the day's [meals] (each a map of known nutrient values) for each nutrient of [keys], in
 * that order. Status is computed on the unrounded known total.
 */
fun dayProgress(targets: List<ProgressTarget>, meals: List<Map<String, Double>>, keys: List<String>): List<NutrientProgress> {
    val byKey = targets.associateBy { it.nutrientKey }
    return keys.map { key ->
        val target = byKey[key]
        val unit = target?.unit ?: TargetUnit.ofKey(key)
        val known = meals.mapNotNull { it[key] }
        val sum = known.fold(0.0) { s, v -> s + v }
        val t = if (known.isNotEmpty()) sum else 0.0
        val min = target?.min
        val max = target?.max
        val reference = min ?: max

        val status = when {
            min == null && max == null -> ProgressStatus.NO_TARGET
            min != null && t < min -> ProgressStatus.UNDER
            max != null && t > max -> ProgressStatus.OVER
            else -> ProgressStatus.OK
        }
        val upper = target?.upperLimit
        NutrientProgress(
            nutrientKey = key,
            unit = unit,
            total = if (known.isNotEmpty()) roundForUnit(unit, sum) else null,
            knownMeals = known.size,
            totalMeals = meals.size,
            percent = if (reference == null || reference <= 0) null else roundHalfUp((t / reference) * 100),
            status = status,
            aboveUpperLimit = upper != null && t > upper,
        )
    }
}

data class DayTotal(val date: String, val total: Double?)

data class SelfComparison(
    /** Mean of the previous 7 calendar days that have a value, one decimal; null when none has. */
    val average: Double?,
    val daysWithData: Int,
    /** Today's total / average x 100, rounded half-up; null when either is missing or the average is 0. */
    val percentOfAverage: Int?,
)

private const val SELF_COMPARISON_DAYS = 7L

/**
 * Today against the person's own recent days, for a nutrient with no target. The window is the 7 calendar
 * days before [day], excluding [day]; days with no value are skipped, not counted as zero.
 */
fun selfComparison(history: List<DayTotal>, day: String, total: Double?): SelfComparison {
    val from = addDays(day, -SELF_COMPARISON_DAYS)
    val days = history.filter { it.date >= from && it.date < day && it.total != null }
    if (days.isEmpty()) return SelfComparison(average = null, daysWithData = 0, percentOfAverage = null)
    val mean = days.fold(0.0) { s, h -> s + (h.total ?: 0.0) } / days.size
    return SelfComparison(
        average = roundHalfUp(mean * 10) / 10.0,
        daysWithData = days.size,
        percentOfAverage = if (total == null || mean <= 0) null else roundHalfUp((total / mean) * 100),
    )
}
