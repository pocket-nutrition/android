// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.pocketnutrition.core.OverrideVersion
import org.pocketnutrition.core.ProfileVersion
import org.pocketnutrition.core.TargetOverride
import org.pocketnutrition.core.WatchList
import org.pocketnutrition.core.WeightEntry

/**
 * The on-disk shape: the three vault payloads exactly as they would be encrypted, plus the height, which is
 * asked for later use and is not part of any record (EFSA's table does not use it).
 */
@Serializable
private data class TargetsFile(
    val version: Int = 1,
    val profile: ProfileRecordPayload? = null,
    val targets: TargetsRecordPayload? = null,
    val weights: List<WeightRecordPayload> = emptyList(),
    @SerialName("height_cm") val heightCm: Double? = null,
)

/**
 * The daily-targets settings of this device: profile history, override history, watch list and weight log
 * (spec 05 §2). Health data: stored here, excluded from backup like the journal, never sent to the API; with
 * a linked vault account each record is also synced there, encrypted (see `TargetsSyncCoordinator`).
 *
 * Each change goes through [TargetSettingsRules] and bumps the `updated_at` of the record it touches, so the
 * payloads stay ready for last-writer-wins sync.
 */
class TargetsStore(
    private val file: File,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private var state by mutableStateOf(load())

    /** Told about every change the person makes (by record id), never about what [apply] writes. */
    var onChange: ((RecordChange) -> Unit)? = null

    val profiles: List<ProfileVersion> get() = state.profile?.versions?.mapNotNull { it.toCoreOrNull() }.orEmpty()
    val overrides: List<OverrideVersion> get() = state.targets?.overrides?.map { it.toCore() }.orEmpty()
    val watchList: WatchList get() = state.targets?.watchList?.toCore() ?: WatchList.DEFAULT
    val weights: List<WeightEntry> get() = state.weights.map { it.toCore() }.sortedBy { it.date }
    val heightCm: Double? get() = state.heightCm

    /** When the questionnaire was last saved, as the explanation screen shows it. */
    val profileUpdatedAtEpochSeconds: Long? get() = state.profile?.updatedAt

    /** The payloads, for the vault. */
    val profileRecord: ProfileRecordPayload? get() = state.profile
    val targetsRecord: TargetsRecordPayload? get() = state.targets
    val weightRecords: List<WeightRecordPayload> get() = state.weights

    fun saveProfile(version: ProfileVersion, heightCm: Double?) {
        val versions = TargetSettingsRules.saveProfile(profiles, version).map(ProfileVersionPayload::of)
        update(state.copy(profile = ProfileRecordPayload(updatedAt = nowEpochSeconds(), versions = versions), heightCm = heightCm ?: state.heightCm))
        onChange?.invoke(RecordChange.Saved(TargetRecords.PROFILE_ID))
    }

    fun setOverride(today: String, key: String, value: TargetOverride?) =
        updateTargets(TargetSettingsRules.setOverride(overrides, today, key, value), watchList)

    fun resetAllOverrides(today: String) = updateTargets(TargetSettingsRules.resetAll(overrides, today), watchList)

    fun setWatchList(list: WatchList) = updateTargets(overrides, list)

    fun logWeight(date: String, kg: Double) {
        val others = state.weights.filter { it.date != date }
        update(state.copy(weights = (others + WeightRecordPayload(updatedAt = nowEpochSeconds(), date = date, kg = kg)).sortedBy { it.date }))
        onChange?.invoke(RecordChange.Saved(TargetRecords.weightId(date)))
    }

    /** Deleting a weight entry deletes its vault record too. */
    fun deleteWeight(date: String) {
        update(state.copy(weights = state.weights.filter { it.date != date }))
        onChange?.invoke(RecordChange.Deleted(TargetRecords.weightId(date)))
    }

    // ── Vault records ────────────────────────────────────────────────────────

    /** Every record this device holds, as the sync sends them. */
    val records: List<TargetsVaultRecord>
        get() = buildList {
            state.profile?.takeIf { it.versions.isNotEmpty() }?.let { add(TargetsVaultRecord.Profile(it)) }
            state.targets?.let { add(TargetsVaultRecord.Targets(it)) }
            state.weights.forEach { add(TargetsVaultRecord.Weight(it)) }
        }

    /** Record id to last local edit, for settling a sync against edits made while it ran. */
    val localUpdatedAt: Map<String, Double> get() = records.associate { it.recordId to it.updatedAt }

    /** Writes what the vault sync brought in: one write, and no [onChange]. */
    fun apply(upserts: List<TargetsVaultRecord>, deletes: Set<String>) {
        if (upserts.isEmpty() && deletes.isEmpty()) return
        var next = state.copy(weights = state.weights.filter { TargetRecords.weightId(it.date) !in deletes })
        if (TargetRecords.PROFILE_ID in deletes) next = next.copy(profile = null)
        if (TargetRecords.TARGETS_ID in deletes) next = next.copy(targets = null)
        for (record in upserts) {
            next = when (record) {
                is TargetsVaultRecord.Profile -> next.copy(profile = record.payload)
                is TargetsVaultRecord.Targets -> next.copy(targets = record.payload)
                is TargetsVaultRecord.Weight -> next.copy(
                    weights = (next.weights.filter { it.date != record.payload.date } + record.payload).sortedBy { it.date },
                )
            }
        }
        update(next)
    }

    private fun updateTargets(history: List<OverrideVersion>, list: WatchList) {
        val payload = TargetsRecordPayload(
            updatedAt = nowEpochSeconds(),
            overrides = history.map(OverrideVersionPayload::of),
            watchList = WatchListPayload.of(list),
        )
        update(state.copy(targets = payload))
        onChange?.invoke(RecordChange.Saved(TargetRecords.TARGETS_ID))
    }

    private fun update(next: TargetsFile) {
        state = next
        persist(next)
    }

    private fun load(): TargetsFile {
        if (!file.exists()) return TargetsFile()
        val decoded = runCatching { TargetRecords.json.decodeFromString(TargetsFile.serializer(), file.readText()) }.getOrNull()
        if (decoded == null) {
            // Set aside rather than overwritten by the next save, like the journal: it is the user's only copy.
            val salvage = File(file.parentFile, "${file.nameWithoutExtension}.unreadable.json")
            salvage.delete()
            file.renameTo(salvage)
            return TargetsFile()
        }
        return decoded
    }

    private fun persist(next: TargetsFile) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(TargetRecords.json.encodeToString(TargetsFile.serializer(), next))
        temporary.renameTo(file)
    }
}
