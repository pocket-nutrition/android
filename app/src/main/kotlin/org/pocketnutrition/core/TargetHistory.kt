// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import java.time.LocalDate

// Which profile, weight and target overrides apply on a given day, so that editing the profile or logging a
// weight changes today's targets without rewriting the past. Port of the sandbox's `targetHistory.ts`,
// asserted by the `weight-for-day`, `profile-for-day` and `apply-target-overrides` suites. Dates are local
// calendar days written "YYYY-MM-DD"; they compare as strings.

/** One saved state of the questionnaire, in force from [effectiveFrom] until the next version. */
data class ProfileVersion(
    val effectiveFrom: String,
    val birthYear: Int,
    val basis: CalculationBasis,
    val situation: Situation,
    val trimester: Int? = null,
    val lactation: LactationPeriod? = null,
    val postmenopausal: Boolean? = null,
    val pal: Double,
    /** The weight typed in the questionnaire; the weight log takes over when it has entries. */
    val weightKg: Double,
    val phytateMgPerDay: Double? = null,
)

data class WeightEntry(val date: String, val kg: Double)

/** A user-set daily target for one nutrient: replaces the computed min/max, never the safety upper limit. */
data class TargetOverride(val min: Double?, val max: Double?)

/** The complete set of overrides in force from [effectiveFrom] (a snapshot, not a diff). */
data class OverrideVersion(val effectiveFrom: String, val values: Map<String, TargetOverride>)

data class DayTarget(
    override val nutrientKey: String,
    override val unit: TargetUnit,
    override val min: Double?,
    override val max: Double?,
    override val upperLimit: Double?,
    val sources: List<String>,
    val overridden: Boolean,
) : ProgressTarget

/** Our choice, labelled as such on the explanation screen. */
const val WEIGHT_WINDOW_DAYS = 7

internal fun addDays(day: String, delta: Long): String = LocalDate.parse(day).plusDays(delta).toString()

/** The latest item with effectiveFrom <= day; among equal dates the last one in the list wins. */
private fun <T> latestOnOrBefore(items: List<T>, day: String, effectiveFrom: (T) -> String): T? {
    var best: T? = null
    for (item in items) {
        val from = effectiveFrom(item)
        if (from > day) continue
        val current = best
        if (current == null || from >= effectiveFrom(current)) best = item
    }
    return best
}

/**
 * The weight to compute with on [day]: the mean of the entries of the 7 calendar days ending on [day]
 * (inclusive), rounded to one decimal. With no entry in that window, the most recent earlier entry; with
 * none at all, null. Entries after [day] are ignored, so a past day keeps the weight known then.
 */
fun weightForDay(entries: List<WeightEntry>, day: String): Double? {
    val windowStart = addDays(day, -(WEIGHT_WINDOW_DAYS - 1).toLong())
    val inWindow = entries.filter { it.date >= windowStart && it.date <= day }
    if (inWindow.isNotEmpty()) {
        val mean = inWindow.fold(0.0) { sum, e -> sum + e.kg } / inWindow.size
        return roundHalfUp(mean * 10) / 10.0
    }
    val earlier = entries.filter { it.date < windowStart }
    if (earlier.isEmpty()) return null
    return earlier.reduce { a, b -> if (b.date >= a.date) b else a }.kg
}

/**
 * The profile in force on [day]: the latest version on or before the day, with the age on that day
 * (calendar year minus birth year) and the weight from the log when it has one. Null before the first
 * version: a day logged before the questionnaire was filled gets no target.
 */
fun profileForDay(versions: List<ProfileVersion>, weights: List<WeightEntry>, day: String): TargetProfile? {
    val version = latestOnOrBefore(versions, day) { it.effectiveFrom } ?: return null
    return TargetProfile(
        ageYears = day.substring(0, 4).toInt() - version.birthYear,
        basis = version.basis,
        situation = version.situation,
        trimester = version.trimester,
        lactation = version.lactation,
        postmenopausal = version.postmenopausal,
        pal = version.pal,
        weightKg = weightForDay(weights, day) ?: version.weightKg,
        phytateMgPerDay = version.phytateMgPerDay,
    )
}

/** The override snapshot in force on [day], or none. */
fun overridesForDay(versions: List<OverrideVersion>, day: String): Map<String, TargetOverride> =
    latestOnOrBefore(versions, day) { it.effectiveFrom }?.values ?: emptyMap()

/**
 * The computed [targets] with the overrides in force on [day] applied. An override replaces min and max
 * and keeps the EFSA upper limit and sources; an override on a nutrient with no computed target adds one
 * with no upper limit and no source. Sorted by nutrientKey.
 */
fun applyOverrides(targets: List<NutrientTarget>, versions: List<OverrideVersion>, day: String): List<DayTarget> {
    val values = overridesForDay(versions, day)
    val out = targets.map { t ->
        val o = values[t.nutrientKey]
        DayTarget(
            nutrientKey = t.nutrientKey,
            unit = t.unit,
            min = if (o == null) t.min else o.min,
            max = if (o == null) t.max else o.max,
            upperLimit = t.upperLimit,
            sources = t.sources,
            overridden = o != null,
        )
    }.toMutableList()
    val known = targets.map { it.nutrientKey }.toSet()
    for ((key, o) in values) {
        if (key in known) continue
        out.add(DayTarget(key, TargetUnit.ofKey(key), o.min, o.max, upperLimit = null, sources = emptyList(), overridden = true))
    }
    return out.sortedWith(compareBy(NUTRIENT_KEY_ORDER) { it.nutrientKey })
}
