// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.pocketnutrition.core.CalculationBasis
import org.pocketnutrition.core.LactationPeriod
import org.pocketnutrition.core.OverrideVersion
import org.pocketnutrition.core.ProfileVersion
import org.pocketnutrition.core.Situation
import org.pocketnutrition.core.TargetOverride
import org.pocketnutrition.core.WatchList
import org.pocketnutrition.core.WeightEntry
import kotlin.math.floor

// The three daily-targets record payloads of spec 05 §3 — `profile`, `targets` and `weight-YYYY-MM-DD` —
// in the snake_case JSON every client and the `me` viewer share. Health data: these go to local storage and,
// encrypted under the user key, to the vault; never to the API.
//
// Readers ignore unknown fields. A record whose `version` is newer than [TargetRecords.READABLE_VERSIONS] is
// reported as unsupported and never overwritten; a record that does not validate is rejected whole, because
// a profile history with a hole would give the wrong targets for that period.

/** `updated_at`, in seconds since 1970. Written whole; a fractional value from another client is floored. */
internal object EpochSecondsSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("EpochSeconds", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    override fun deserialize(decoder: Decoder): Long {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeLong()
        val value = element.jsonPrimitive.double
        if (!value.isFinite()) throw SerializationException("updated_at is not finite")
        return floor(value).toLong()
    }
}

@Serializable
data class ProfileVersionPayload(
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("birth_year") val birthYear: Int,
    val basis: String,
    val situation: String,
    val trimester: Int? = null,
    val lactation: String? = null,
    val postmenopausal: Boolean? = null,
    val pal: Double,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("phytate_mg_per_day") val phytateMgPerDay: Double? = null,
) {
    /** The core value, or null when a field is outside its allowed set. */
    fun toCoreOrNull(): ProfileVersion? {
        val basis = CalculationBasis.fromId(basis) ?: return null
        val situation = Situation.fromId(situation) ?: return null
        val lactation = lactation?.let { LactationPeriod.fromId(it) ?: return null }
        if (!TargetRecords.isDate(effectiveFrom)) return null
        if (trimester != null && trimester !in 1..3) return null
        if (!(pal > 0) || !(weightKg > 0)) return null
        if (phytateMgPerDay != null && !(phytateMgPerDay > 0)) return null
        return ProfileVersion(effectiveFrom, birthYear, basis, situation, trimester, lactation, postmenopausal, pal, weightKg, phytateMgPerDay)
    }

    fun toCore(): ProfileVersion = requireNotNull(toCoreOrNull()) { "invalid profile version $this" }

    companion object {
        fun of(v: ProfileVersion) = ProfileVersionPayload(
            effectiveFrom = v.effectiveFrom, birthYear = v.birthYear, basis = v.basis.id, situation = v.situation.id,
            trimester = v.trimester, lactation = v.lactation?.id, postmenopausal = v.postmenopausal,
            pal = v.pal, weightKg = v.weightKg, phytateMgPerDay = v.phytateMgPerDay,
        )
    }
}

@Serializable
data class ProfileRecordPayload(
    val version: Int = TargetRecords.CURRENT_VERSION,
    @SerialName("updated_at") @Serializable(with = EpochSecondsSerializer::class) val updatedAt: Long,
    val versions: List<ProfileVersionPayload>,
)

@Serializable
data class BoundsPayload(val min: Double? = null, val max: Double? = null)

@Serializable
data class OverrideVersionPayload(
    @SerialName("effective_from") val effectiveFrom: String,
    val values: Map<String, BoundsPayload>,
) {
    fun toCore() = OverrideVersion(effectiveFrom, values.mapValues { (_, b) -> TargetOverride(b.min, b.max) })

    companion object {
        fun of(v: OverrideVersion) = OverrideVersionPayload(v.effectiveFrom, v.values.mapValues { (_, o) -> BoundsPayload(o.min, o.max) })
    }
}

@Serializable
data class WatchListPayload(val preset: String, val keys: List<String>) {
    fun toCore(): WatchList = WatchList.restore(preset, keys)

    companion object {
        fun of(w: WatchList) = WatchListPayload(w.preset.id, w.keys)
    }
}

@Serializable
data class TargetsRecordPayload(
    val version: Int = TargetRecords.CURRENT_VERSION,
    @SerialName("updated_at") @Serializable(with = EpochSecondsSerializer::class) val updatedAt: Long,
    val overrides: List<OverrideVersionPayload> = emptyList(),
    @SerialName("watch_list") val watchList: WatchListPayload? = null,
)

@Serializable
data class WeightRecordPayload(
    val version: Int = TargetRecords.CURRENT_VERSION,
    @SerialName("updated_at") @Serializable(with = EpochSecondsSerializer::class) val updatedAt: Long,
    val date: String,
    val kg: Double,
) {
    fun toCore() = WeightEntry(date, kg)
}

/** What reading one record gives. */
sealed interface RecordRead<out T> {
    data class Ok<T>(val payload: T) : RecordRead<T>

    /** Written by a newer app: shown as such and never overwritten. */
    data class UnsupportedVersion<T>(val version: Int) : RecordRead<T>

    data object Malformed : RecordRead<Nothing>
}

enum class SyncDecision { TAKE_REMOTE, KEEP_LOCAL, UNSUPPORTED_VERSION }

object TargetRecords {
    const val CURRENT_VERSION = 1

    /** The payload versions this client reads, as `MealRecordPayload.readableVersions` on iOS. */
    val READABLE_VERSIONS: Set<Int> = setOf(1)

    const val PROFILE_ID = "profile"
    const val TARGETS_ID = "targets"
    const val TYPE_PROFILE = "profile"
    const val TYPE_TARGETS = "targets"
    const val TYPE_WEIGHT = "weight"

    private val ID_RE = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun isValidId(id: String): Boolean = ID_RE.matches(id)

    fun isDate(value: String): Boolean = DATE_RE.matches(value)

    fun weightId(date: String): String = "weight-$date"

    fun encode(payload: ProfileRecordPayload): String = json.encodeToString(ProfileRecordPayload.serializer(), payload)
    fun encode(payload: TargetsRecordPayload): String = json.encodeToString(TargetsRecordPayload.serializer(), payload)
    fun encode(payload: WeightRecordPayload): String = json.encodeToString(WeightRecordPayload.serializer(), payload)

    /** The version gate every type shares: null when the version is readable, else the read to report. */
    private fun <T> gate(text: String): RecordRead<T>? {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return RecordRead.Malformed
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: return RecordRead.Malformed
        if (version > READABLE_VERSIONS.max()) return RecordRead.UnsupportedVersion(version)
        if (version !in READABLE_VERSIONS) return RecordRead.Malformed
        return null
    }

    private inline fun <T> decodeOrMalformed(block: () -> T?): RecordRead<T> =
        runCatching(block).getOrNull()?.let { RecordRead.Ok(it) } ?: RecordRead.Malformed

    fun decodeProfile(text: String): RecordRead<ProfileRecordPayload> = gate(text) ?: decodeOrMalformed {
        json.decodeFromString(ProfileRecordPayload.serializer(), text).takeIf { p -> p.versions.all { it.toCoreOrNull() != null } }
    }

    fun decodeTargets(text: String): RecordRead<TargetsRecordPayload> = gate(text) ?: decodeOrMalformed {
        json.decodeFromString(TargetsRecordPayload.serializer(), text).takeIf { p -> p.overrides.all { isDate(it.effectiveFrom) } }
    }

    fun decodeWeight(text: String, recordId: String): RecordRead<WeightRecordPayload> = gate(text) ?: decodeOrMalformed {
        json.decodeFromString(WeightRecordPayload.serializer(), text)
            .takeIf { isDate(it.date) && recordId == weightId(it.date) && it.kg > 0 }
    }

    /**
     * Last writer wins on `updated_at`: the remote copy replaces the local one only when strictly newer.
     * A remote record of a newer version is never overwritten; a malformed one never replaces a good one.
     */
    fun <T> decide(localUpdatedAt: Long?, remote: RecordRead<T>, updatedAt: (T) -> Long): SyncDecision = when (remote) {
        is RecordRead.UnsupportedVersion -> SyncDecision.UNSUPPORTED_VERSION
        RecordRead.Malformed -> SyncDecision.KEEP_LOCAL
        is RecordRead.Ok -> if (localUpdatedAt == null || updatedAt(remote.payload) > localUpdatedAt) SyncDecision.TAKE_REMOTE else SyncDecision.KEEP_LOCAL
    }
}
