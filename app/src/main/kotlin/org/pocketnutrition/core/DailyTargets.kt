// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.Serializable

// Daily nutrient targets from the EFSA reference table served by GET /reference-intakes.
// Port of the sandbox's `src/lib/dailyTargets.ts`, asserted by the `daily-targets` vector suite.
//
// The table comes from the API; this file only selects the rows that apply to one person and does the
// arithmetic (increments, per-kg protein, % of energy). It embeds no reference value: every number in
// the output traces back to a row of the input table. The arithmetic is kept in the reference's exact
// operation order, because a reordered product or quotient can land on the other side of a rounding tie.

/** One row of GET /reference-intakes, reduced to the fields the selection reads. Unknown fields are ignored. */
@Serializable
data class ReferenceRow(
    val nutrient: String,
    val nutrientKey: String? = null,
    val population: String,
    val populationDescription: String,
    val ageFrom: Double? = null,
    val ageTo: Double? = null,
    val ageUnit: String? = null,
    val pal: Double? = null,
    val isMale: Boolean,
    val isFemale: Boolean,
    val valueKind: String,
    val valueMin: Double? = null,
    val valueMax: Double? = null,
    val isIncrement: Boolean,
    val unit: String? = null,
    val sourceLink: String? = null,
)

/** Which reference values to compute with. Never a gender question: see the questionnaire. */
enum class CalculationBasis(val id: String) {
    FEMALE("female"), MALE("male"), AVERAGE("average");

    companion object {
        fun fromId(id: String): CalculationBasis? = entries.firstOrNull { it.id == id }
    }
}

enum class Situation(val id: String) {
    NONE("none"), PREGNANT("pregnant"), LACTATING("lactating");

    companion object {
        fun fromId(id: String): Situation? = entries.firstOrNull { it.id == id }
    }
}

enum class LactationPeriod(val id: String) {
    FIRST_6_MONTHS("first-6-months"), AFTER_6_MONTHS("after-6-months");

    companion object {
        fun fromId(id: String): LactationPeriod? = entries.firstOrNull { it.id == id }
    }
}

data class TargetProfile(
    /** Age in whole years on the day the targets are for. */
    val ageYears: Int,
    /** Pregnancy and lactation always use the female values, whatever the basis. */
    val basis: CalculationBasis,
    val situation: Situation,
    /** 1, 2 or 3; required when pregnant. */
    val trimester: Int? = null,
    /** Required when lactating. */
    val lactation: LactationPeriod? = null,
    /** Selects EFSA's postmenopausal values where it publishes them (iron, from 40). */
    val postmenopausal: Boolean? = null,
    /** Physical activity level; EFSA publishes energy for 1.4, 1.6, 1.8 and 2.0. */
    val pal: Double,
    val weightKg: Double,
    /** Dietary phytate (LPI) for zinc; [DEFAULT_PHYTATE_MG_PER_DAY] when absent. */
    val phytateMgPerDay: Double? = null,
)

enum class TargetUnit(val symbol: String) {
    KCAL("kcal"), G("g"), MG("mg"), UG("µg");

    companion object {
        fun fromSymbol(symbol: String): TargetUnit? = entries.firstOrNull { it.symbol == symbol }

        /** From the key suffix: Kcal, Ug, Mg, else g. */
        fun ofKey(key: String): TargetUnit = strictOfKey(key) ?: G

        internal fun strictOfKey(key: String): TargetUnit? = when {
            key.endsWith("Kcal") -> KCAL
            key.endsWith("Ug") -> UG
            key.endsWith("Mg") -> MG
            key.endsWith("G") -> G
            else -> null
        }
    }
}

/** The fields progress reads; a [NutrientTarget] or a [DayTarget] fits. */
interface ProgressTarget {
    val nutrientKey: String
    val unit: TargetUnit
    val min: Double?
    val max: Double?
    val upperLimit: Double?
}

data class NutrientTarget(
    override val nutrientKey: String,
    override val unit: TargetUnit,
    /** Reach at least this (PRI, else AI); equal to max for energy, a point target. */
    override val min: Double?,
    /** Upper end of a recommended range (RI) or of a point target (energy). Not a safety limit. */
    override val max: Double?,
    /** EFSA tolerable upper intake level: a safety ceiling, shown apart from the target. */
    override val upperLimit: Double?,
    /** Links of the EFSA opinions the values come from, sorted and de-duplicated. */
    val sources: List<String>,
) : ProgressTarget

enum class UnsupportedReason(val id: String) {
    INFANT("infant"), PREGNANCY_UNDER_18("pregnancy-under-18"), LACTATION_UNDER_18("lactation-under-18")
}

sealed interface TargetsResult {
    data class Ok(val targets: List<NutrientTarget>) : TargetsResult
    data class Unsupported(val reason: UnsupportedReason) : TargetsResult
}

/** Our choice, labelled as such on the explanation screen: EFSA publishes 300, 600, 900 and 1200. */
const val DEFAULT_PHYTATE_MG_PER_DAY = 600.0

private const val ADULT_AGE = 18
private const val ENERGY_KEY = "energyKcal"

/** kcal per gram, for reference intakes given in % of energy (E%). Unit arithmetic, not food data. */
private val ENERGY_DENSITY = mapOf("carbohydratesG" to 4.0, "proteinG" to 4.0, "fatG" to 9.0, "saturatedFatG" to 9.0)

private val TRIMESTER_LABEL = mapOf(1 to "1st trimester", 2 to "2nd trimester", 3 to "3rd trimester")

private enum class TargetField { ENERGY, GOAL, RANGE, UPPER_LIMIT }

private fun fieldOf(valueKind: String): TargetField? = when (valueKind) {
    "ar_kcal" -> TargetField.ENERGY
    "pri", "ai" -> TargetField.GOAL
    "ri" -> TargetField.RANGE
    "ul" -> TargetField.UPPER_LIMIT
    else -> null
}

private val MASS_IN_MG = mapOf("g" to 1000.0, "mg" to 1.0, "µg" to 0.001)
private val PER_DAY_RE = Regex("^(g|mg|µg)/day$")
private val LPI_RE = Regex("LPI\\s*(\\d+)\\s*mg/day")

/**
 * Converts one EFSA amount to the journal unit of [key]; null for a unit that cannot be converted, so
 * the row is skipped rather than compared wrongly.
 */
private fun toJournalUnit(amount: Double, unit: String, key: String, weightKg: Double, energyKcal: Double?): Double? {
    val target = TargetUnit.strictOfKey(key) ?: return null
    // U+03BC (Greek mu) and U+00B5 (micro sign) are the same unit; the reference replaces the first only.
    val u = unit.trim().replaceFirst('μ', 'µ')
    if (u == "kcal/day") return if (target == TargetUnit.KCAL) amount else null
    if (u == "g/kg bw per day") return if (target == TargetUnit.G) amount * weightKg else null
    if (u == "E%") {
        val density = ENERGY_DENSITY[key]
        if (density == null || energyKcal == null || target != TargetUnit.G) return null
        return (energyKcal * amount) / (100 * density)
    }
    val match = PER_DAY_RE.find(u) ?: return null
    if (target == TargetUnit.KCAL) return null
    return (amount * MASS_IN_MG.getValue(match.groupValues[1])) / MASS_IN_MG.getValue(target.symbol)
}

private fun inAgeBand(row: ReferenceRow, age: Int): Boolean {
    if (row.ageUnit != "y" || row.ageFrom == null || row.ageTo == null) return false
    return row.ageFrom <= age && age <= row.ageTo
}

private fun matchesSex(row: ReferenceRow, female: Boolean): Boolean = if (female) row.isFemale else row.isMale

/**
 * Narrows the rows of one nutrient and one kind to the variant that applies: the activity level for
 * energy, the phytate level for zinc, menopause status for iron. A postmenopausal narrowing that would
 * leave nothing is not applied (EFSA has no postmenopausal value under 40).
 */
private fun narrowVariants(rows: List<ReferenceRow>, profile: TargetProfile): List<ReferenceRow> {
    var out = rows.filter { it.pal == null || it.pal == profile.pal }

    val phytate = profile.phytateMgPerDay ?: DEFAULT_PHYTATE_MG_PER_DAY
    if (out.any { LPI_RE.containsMatchIn(it.populationDescription) }) {
        out = out.filter { row ->
            val m = LPI_RE.find(row.populationDescription)
            m == null || m.groupValues[1].toDouble() == phytate
        }
    }

    val post = out.filter { it.populationDescription.contains("Postmenopausal") }
    out = if (profile.postmenopausal == true && post.isNotEmpty()) post else out.filterNot { it.populationDescription.contains("Postmenopausal") }
    return out
}

/** Matches a pregnancy or lactation row to the trimester or post-partum period, when the row names one. */
private fun matchesPeriod(row: ReferenceRow, profile: TargetProfile): Boolean {
    val d = row.populationDescription
    if (d.contains("trimester")) {
        val label = profile.trimester?.let { TRIMESTER_LABEL[it] } ?: return false
        return d.contains(label)
    }
    if (d.contains("post partum")) {
        val period = profile.lactation ?: return false
        return if (period == LactationPeriod.FIRST_6_MONTHS) d.contains("0–6") || d.contains("0-6") else d.contains(">6")
    }
    return true
}

private class RawTarget(
    var energy: Double? = null,
    var goal: Double? = null,
    var rangeMin: Double? = null,
    var rangeMax: Double? = null,
    var upperLimit: Double? = null,
    val sources: MutableSet<String> = linkedSetOf(),
)

private val KIND_ORDER = listOf("ar_kcal", "pri", "ai", "ri", "ul")

/** The first row of each target field for one nutrient key, in table order, after narrowing. PRI before AI. */
private fun pickByField(rows: List<ReferenceRow>, profile: TargetProfile): Map<TargetField, ReferenceRow> {
    val picked = linkedMapOf<TargetField, ReferenceRow>()
    val byKind = rows.groupBy { it.valueKind }
    for (kind in KIND_ORDER) {
        val field = fieldOf(kind) ?: continue
        if (field in picked) continue
        val candidates = narrowVariants(byKind[kind].orEmpty(), profile).filter { it.valueMin != null }
        if (candidates.isNotEmpty()) picked[field] = candidates.first()
    }
    return picked
}

private data class Amount(val min: Double, val max: Double)

private fun convert(row: ReferenceRow, key: String, profile: TargetProfile, energyKcal: Double?): Amount? {
    val unit = row.unit ?: return null
    val valueMin = row.valueMin ?: return null
    val min = toJournalUnit(valueMin, unit, key, profile.weightKg, energyKcal)
    val max = toJournalUnit(row.valueMax ?: valueMin, unit, key, profile.weightKg, energyKcal)
    return if (min == null || max == null) null else Amount(min, max)
}

/**
 * Orders nutrient keys the way JavaScript's `localeCompare` does for these ASCII identifiers: letters
 * compared case-insensitively (digits before letters), lower case first on an otherwise equal key.
 */
internal val NUTRIENT_KEY_ORDER: Comparator<String> = compareBy<String> { it.lowercase() }.thenByDescending { it }

/** Targets for one sex: base population, then the pregnancy or lactation rows on top. */
private fun targetsForSex(table: List<ReferenceRow>, profile: TargetProfile, female: Boolean): Map<String, RawTarget> {
    val age = profile.ageYears
    val basePopulation = if (age >= ADULT_AGE) "Adults" else "Children"
    val situationPopulation = when (profile.situation) {
        Situation.PREGNANT -> "Pregnant women"
        Situation.LACTATING -> "Lactating women"
        Situation.NONE -> null
    }

    fun rowsByKey(population: String, extra: (ReferenceRow) -> Boolean): Map<String, List<ReferenceRow>> {
        val out = linkedMapOf<String, MutableList<ReferenceRow>>()
        for (row in table) {
            val key = row.nutrientKey ?: continue
            if (row.population != population) continue
            if (!inAgeBand(row, age) || !matchesSex(row, female) || !extra(row)) continue
            out.getOrPut(key) { mutableListOf() }.add(row)
        }
        return out
    }

    val base = rowsByKey(basePopulation) { true }
    val situation = situationPopulation?.let { rowsByKey(it) { row -> matchesPeriod(row, profile) } } ?: emptyMap()

    // Energy first: % of energy intakes are converted with it.
    val ordered = (base.keys + situation.keys).distinct().sortedWith { a, b ->
        when {
            a == ENERGY_KEY -> -1
            b == ENERGY_KEY -> 1
            else -> NUTRIENT_KEY_ORDER.compare(a, b)
        }
    }

    val result = linkedMapOf<String, RawTarget>()
    var energyKcal: Double? = null

    for (key in ordered) {
        val basePicked = pickByField(base[key].orEmpty(), profile)
        val situationPicked = pickByField(situation[key].orEmpty(), profile)
        val raw = RawTarget()

        for (field in TargetField.entries) {
            val baseRow = basePicked[field]
            val extraRow = situationPicked[field]
            var amount: Amount? = baseRow?.let { convert(it, key, profile, energyKcal) }
            var links: List<String?> = if (amount == null) emptyList() else listOf(baseRow?.sourceLink)

            if (extraRow != null) {
                val extra = convert(extraRow, key, profile, energyKcal)
                if (extra != null) {
                    val current = amount
                    if (!extraRow.isIncrement) {
                        amount = extra
                        links = listOf(extraRow.sourceLink)
                    } else if (current != null) {
                        amount = Amount(current.min + extra.min, current.max + extra.max)
                        links = links + extraRow.sourceLink
                    }
                }
            }
            val final = amount ?: continue
            links.forEach { link -> if (!link.isNullOrEmpty()) raw.sources.add(link) }

            when (field) {
                TargetField.ENERGY -> raw.energy = final.min
                TargetField.GOAL -> raw.goal = final.min
                TargetField.RANGE -> { raw.rangeMin = final.min; raw.rangeMax = final.max }
                TargetField.UPPER_LIMIT -> raw.upperLimit = final.min
            }
        }

        if (key == ENERGY_KEY) energyKcal = raw.energy
        if (raw.energy != null || raw.goal != null || raw.rangeMin != null || raw.upperLimit != null) result[key] = raw
    }
    return result
}

/** kcal and g to whole numbers, mg and µg to one decimal, half-up. */
internal fun roundForUnit(unit: TargetUnit, value: Double): Double =
    if (unit == TargetUnit.KCAL || unit == TargetUnit.G) roundHalfUp(value).toDouble() else roundHalfUp(value * 10) / 10.0

private fun toTarget(key: String, raw: RawTarget): NutrientTarget? {
    val unit = TargetUnit.strictOfKey(key) ?: return null
    var min: Double? = null
    var max: Double? = null
    when {
        raw.energy != null -> { min = raw.energy; max = raw.energy }
        raw.rangeMin != null -> { min = raw.rangeMin; max = raw.rangeMax }
        raw.goal != null -> min = raw.goal
    }
    return NutrientTarget(
        nutrientKey = key,
        unit = unit,
        min = min?.let { roundForUnit(unit, it) },
        max = max?.let { roundForUnit(unit, it) },
        upperLimit = raw.upperLimit?.let { roundForUnit(unit, it) },
        sources = raw.sources.sorted(),
    )
}

private fun mean(a: Double?, b: Double?): Double? = when {
    a == null -> b
    b == null -> a
    else -> (a + b) / 2
}

private fun averageRaw(f: RawTarget?, m: RawTarget?): RawTarget {
    val a = f ?: RawTarget()
    val b = m ?: RawTarget()
    return RawTarget(
        energy = mean(a.energy, b.energy),
        goal = mean(a.goal, b.goal),
        rangeMin = mean(a.rangeMin, b.rangeMin),
        rangeMax = mean(a.rangeMax, b.rangeMax),
        upperLimit = mean(a.upperLimit, b.upperLimit),
        sources = (a.sources + b.sources).toMutableSet(),
    )
}

/**
 * The daily targets that apply to [profile], from the EFSA [table].
 *
 * - Infants (under 1 year) and pregnancy or lactation under 18 are not supported.
 * - Pregnancy and lactation always compute with the female values, whatever the basis.
 * - The "average" basis averages the female and male results field by field, after each is complete.
 */
fun dailyTargets(table: List<ReferenceRow>, profile: TargetProfile): TargetsResult {
    if (profile.ageYears < 1) return TargetsResult.Unsupported(UnsupportedReason.INFANT)
    if (profile.ageYears < ADULT_AGE && profile.situation == Situation.PREGNANT) {
        return TargetsResult.Unsupported(UnsupportedReason.PREGNANCY_UNDER_18)
    }
    if (profile.ageYears < ADULT_AGE && profile.situation == Situation.LACTATING) {
        return TargetsResult.Unsupported(UnsupportedReason.LACTATION_UNDER_18)
    }

    val basis = if (profile.situation == Situation.NONE) profile.basis else CalculationBasis.FEMALE
    val raws: Map<String, RawTarget> = when (basis) {
        CalculationBasis.AVERAGE -> {
            val f = targetsForSex(table, profile, female = true)
            val m = targetsForSex(table, profile, female = false)
            (f.keys + m.keys).associateWith { averageRaw(f[it], m[it]) }
        }
        CalculationBasis.FEMALE -> targetsForSex(table, profile, female = true)
        CalculationBasis.MALE -> targetsForSex(table, profile, female = false)
    }

    val targets = raws.mapNotNull { (key, raw) -> toTarget(key, raw) }.sortedWith(compareBy(NUTRIENT_KEY_ORDER) { it.nutrientKey })
    return TargetsResult.Ok(targets)
}
