// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import org.pocketnutrition.core.OverrideVersion
import org.pocketnutrition.core.ProfileVersion
import org.pocketnutrition.core.TargetOverride
import org.pocketnutrition.core.WeightEntry

/**
 * The editing rules of spec 05 §2, as pure functions over immutable lists so they are testable apart from
 * storage. Past days keep their targets because an older version is never edited: a change made today is a
 * new version dated today, replacing only a version already dated today.
 */
object TargetSettingsRules {

    /** Appends [version] (dated today), replacing the last version when it is already dated that day. */
    fun saveProfile(history: List<ProfileVersion>, version: ProfileVersion): List<ProfileVersion> =
        replaceOrAppend(history, version) { it.effectiveFrom }

    /** A full snapshot dated [today] with [key] set to [value], or removed when [value] is null. */
    fun setOverride(history: List<OverrideVersion>, today: String, key: String, value: TargetOverride?): List<OverrideVersion> {
        val current = history.lastOrNull()?.values ?: emptyMap()
        val next = if (value == null) current - key else current + (key to value)
        return replaceOrAppend(history, OverrideVersion(today, next)) { it.effectiveFrom }
    }

    /** "Reset all to EFSA": an empty snapshot from [today], earlier overridden days untouched. */
    fun resetAll(history: List<OverrideVersion>, today: String): List<OverrideVersion> =
        replaceOrAppend(history, OverrideVersion(today, emptyMap())) { it.effectiveFrom }

    /** At most one entry per date: a new one for the same date replaces it. Sorted by date. */
    fun logWeight(log: List<WeightEntry>, entry: WeightEntry): List<WeightEntry> =
        (log.filter { it.date != entry.date } + entry).sortedBy { it.date }

    fun deleteWeight(log: List<WeightEntry>, date: String): List<WeightEntry> = log.filter { it.date != date }

    private fun <T> replaceOrAppend(history: List<T>, item: T, date: (T) -> String): List<T> {
        val last = history.lastOrNull()
        return if (last != null && date(last) == date(item)) history.dropLast(1) + item else history + item
    }
}
