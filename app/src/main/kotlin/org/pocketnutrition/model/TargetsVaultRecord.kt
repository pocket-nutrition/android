// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

/**
 * One daily-targets record as the vault holds it (spec 05 §3): `profile`, `targets` or `weight-YYYY-MM-DD`,
 * with the record type of the same name. Mirrors iOS `TargetsVaultRecord`.
 */
sealed interface TargetsVaultRecord {
    val recordId: String
    val recordType: String
    /** `updated_at`, seconds since 1970. */
    val updatedAt: Double
    fun encoded(): String

    data class Profile(val payload: ProfileRecordPayload) : TargetsVaultRecord {
        override val recordId get() = TargetRecords.PROFILE_ID
        override val recordType get() = TargetRecords.TYPE_PROFILE
        override val updatedAt get() = payload.updatedAt.toDouble()
        override fun encoded() = TargetRecords.encode(payload)
    }

    data class Targets(val payload: TargetsRecordPayload) : TargetsVaultRecord {
        override val recordId get() = TargetRecords.TARGETS_ID
        override val recordType get() = TargetRecords.TYPE_TARGETS
        override val updatedAt get() = payload.updatedAt.toDouble()
        override fun encoded() = TargetRecords.encode(payload)
    }

    data class Weight(val payload: WeightRecordPayload) : TargetsVaultRecord {
        override val recordId get() = TargetRecords.weightId(payload.date)
        override val recordType get() = TargetRecords.TYPE_WEIGHT
        override val updatedAt get() = payload.updatedAt.toDouble()
        override fun encoded() = TargetRecords.encode(payload)
    }

    companion object {
        val TYPES: Set<String> = setOf(TargetRecords.TYPE_PROFILE, TargetRecords.TYPE_TARGETS, TargetRecords.TYPE_WEIGHT)

        /**
         * Decodes one record by its type, refusing a payload that does not belong to the record it arrived in
         * (a `profile` payload under the id `targets`, a weight whose date is not in its id).
         */
        fun decode(text: String, type: String, recordId: String): RecordRead<TargetsVaultRecord> {
            val read: RecordRead<TargetsVaultRecord> = when (type) {
                TargetRecords.TYPE_PROFILE ->
                    if (recordId != TargetRecords.PROFILE_ID) RecordRead.Malformed else TargetRecords.decodeProfile(text).map(::Profile)
                TargetRecords.TYPE_TARGETS ->
                    if (recordId != TargetRecords.TARGETS_ID) RecordRead.Malformed else TargetRecords.decodeTargets(text).map(::Targets)
                TargetRecords.TYPE_WEIGHT -> TargetRecords.decodeWeight(text, recordId).map(::Weight)
                else -> RecordRead.Malformed
            }
            return read
        }
    }
}

/** The same read with its payload transformed; the version and malformed cases pass through. */
fun <T, R> RecordRead<T>.map(transform: (T) -> R): RecordRead<R> = when (this) {
    is RecordRead.Ok -> RecordRead.Ok(transform(payload))
    is RecordRead.UnsupportedVersion -> RecordRead.UnsupportedVersion(version)
    RecordRead.Malformed -> RecordRead.Malformed
}
