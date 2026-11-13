// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

// The nutrients a person watches on the journal rings (spec 05 §5). Presets list nutrient keys only, never a
// value: a medical preset chooses *what* to watch and still uses EFSA's general-population targets.

enum class WatchListPreset(val id: String, val keys: List<String>, val isMedical: Boolean = false) {
    STANDARD("standard", listOf("energyKcal", "proteinG", "carbohydratesG", "fatG", "fiberG", "sugarsG")),
    SPORT("sport", listOf("energyKcal", "proteinG", "carbohydratesG", "fatG", "fiberG", "waterG")),
    KIDNEY("kidney", listOf("energyKcal", "proteinG", "potassiumMg", "phosphorusMg", "sodiumMg"), isMedical = true),
    HYPERTENSION("hypertension", listOf("energyKcal", "sodiumMg", "potassiumMg", "saturatedFatG", "fiberG"), isMedical = true),
    DIABETES("diabetes", listOf("energyKcal", "carbohydratesG", "sugarsG", "fiberG", "saturatedFatG"), isMedical = true),
    EVERYTHING("everything", NUTRIENT_KEYS),

    /** Keys picked one by one; has no list of its own. */
    CUSTOM("custom", emptyList());

    companion object {
        fun fromId(id: String): WatchListPreset? = entries.firstOrNull { it.id == id }

        /** The presets a person can pick from (custom is reached by editing). */
        val choosable: List<WatchListPreset> get() = entries.filter { it != CUSTOM }
    }
}

/** The watched nutrients, in display order. Not versioned: it is a display choice, not a target. */
data class WatchList(val preset: WatchListPreset, val keys: List<String>) {

    /** Editing the keys one by one makes the list custom. Unknown keys and repeats are dropped. */
    fun withKeys(newKeys: List<String>): WatchList = WatchList(WatchListPreset.CUSTOM, clean(newKeys))

    companion object {
        val DEFAULT = of(WatchListPreset.STANDARD)

        fun of(preset: WatchListPreset): WatchList = WatchList(preset, preset.keys)

        /** A stored list: unknown keys dropped, a known preset with no keys falls back to its own list. */
        fun restore(presetId: String?, keys: List<String>?): WatchList {
            val preset = presetId?.let { WatchListPreset.fromId(it) } ?: if (presetId == null) WatchListPreset.STANDARD else WatchListPreset.CUSTOM
            return WatchList(preset, clean(keys ?: preset.keys))
        }

        private fun clean(keys: List<String>): List<String> = keys.filter { it in NUTRIENT_KEYS }.distinct()
    }
}
