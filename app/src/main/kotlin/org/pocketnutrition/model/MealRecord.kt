// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.ExperimentalSerializationApi
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
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.nutrientTotals

// A journal meal as it is stored in the vault: the plaintext of one `meal` record, before encryption. The
// same JSON as iOS `MealRecordPayload` (version 2): its own type rather than [SavedMeal], so the wire format
// cannot change by accident when the UI model does. Timestamps are seconds since 1970, as a number. Readers
// ignore fields they do not know; a record whose `v` is newer than [MealRecords.READABLE_VERSIONS] is reported
// as unsupported and never overwritten.

/**
 * Seconds since 1970 as a plain JSON number (`1762430000.123`, never `1.762430000123E9`): both are valid
 * JSON, but the plain form is what the other clients write and what a person reading a record expects.
 */
internal object EpochSecondsDoubleSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("EpochSecondsDouble", PrimitiveKind.DOUBLE)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Double) {
        if (!value.isFinite()) throw SerializationException("timestamp is not finite")
        val json = encoder as? JsonEncoder ?: return encoder.encodeDouble(value)
        json.encodeJsonElement(JsonUnquotedLiteral(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()))
    }

    override fun deserialize(decoder: Decoder): Double {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeDouble()
        val value = element.jsonPrimitive.double
        if (!value.isFinite()) throw SerializationException("timestamp is not finite")
        return value
    }
}

@Serializable
data class MealRecordItem(
    val id: String,
    @SerialName("ingredient_id") val ingredientId: String? = null,
    val label: String,
    @SerialName("quantity_g") val quantityG: Double,
    @SerialName("cooking_method") val cookingMethod: String,
    @SerialName("measured_state") val measuredState: String,
    /** v2: set only on a quick-add item. */
    @SerialName("energy_kcal") val energyKcal: Double? = null,
    /** Written only as `false`; absent means available. */
    val available: Boolean? = null,
)

@Serializable
data class MealRecordTotals(
    @SerialName("energy_kcal") val energyKcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbohydrates_g") val carbohydratesG: Double,
    @SerialName("fat_g") val fatG: Double,
)

@Serializable
data class MealRecordPayload(
    @SerialName("v") val version: Int = MealRecords.VERSION,
    /** The meal's id, which is also the vault record id. Lowercase UUID. */
    val id: String,
    @SerialName("saved_at") @Serializable(with = EpochSecondsDoubleSerializer::class) val savedAt: Double,
    /** Last edit; the newer side wins when two devices edited the same meal. */
    @SerialName("updated_at") @Serializable(with = EpochSecondsDoubleSerializer::class) val updatedAt: Double,
    val items: List<MealRecordItem>,
    val totals: MealRecordTotals,
    /** v2: `breakfast`, `lunch`, `dinner` or `snack`. */
    val slot: String? = null,
    /** v2: every nutrient the engine returned, keyed by API field name. */
    val nutrients: Map<String, Double>? = null,
)

object MealRecords {
    /** Written by this client. */
    const val VERSION = 2
    /** Read by this client, as iOS `MealRecordPayload.readableVersions`. */
    val READABLE_VERSIONS: IntRange = 1..2
    const val TYPE = "meal"

    // Optional fields are omitted when absent, as Swift's encoder does.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(payload: MealRecordPayload): String = json.encodeToString(MealRecordPayload.serializer(), payload)

    /** Decodes one meal record, refusing one from a newer version or naming another record. */
    fun decode(text: String, expectedId: String): RecordRead<MealRecordPayload> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return RecordRead.Malformed
        val version = (root["v"] as? JsonPrimitive)?.intOrNull ?: return RecordRead.Malformed
        if (version > READABLE_VERSIONS.last) return RecordRead.UnsupportedVersion(version)
        if (version !in READABLE_VERSIONS) return RecordRead.Malformed
        val payload = runCatching { json.decodeFromString(MealRecordPayload.serializer(), text) }.getOrNull()
            ?: return RecordRead.Malformed
        if (payload.id != expectedId) return RecordRead.Malformed
        return RecordRead.Ok(payload)
    }
}

/** When the meal was last edited: the stored time, or its save time for a meal from before the field. */
val SavedMeal.effectiveUpdatedAtEpochMs: Long get() = updatedAtEpochMs ?: savedAtEpochMs

/** This meal as a vault record. */
fun SavedMeal.toRecord(): MealRecordPayload = MealRecordPayload(
    id = id,
    savedAt = savedAtEpochMs / 1000.0,
    updatedAt = effectiveUpdatedAtEpochMs / 1000.0,
    items = items.map {
        MealRecordItem(
            id = it.id, ingredientId = it.ingredientId, label = it.label, quantityG = it.quantityG,
            cookingMethod = it.cookingMethod, measuredState = it.measuredState, energyKcal = it.energyKcal,
            available = if (it.available) null else false,
        )
    },
    totals = MealRecordTotals(totals.energyKcal, totals.proteinG, totals.carbohydratesG, totals.fatG),
    slot = effectiveSlot().id,
    nutrients = nutrients,
)

/** Null when the record's ids are not UUIDs: not something a Pocket Nutrition app wrote. */
fun MealRecordPayload.toSavedMeal(): SavedMeal? {
    if (!isUuid(id) || items.any { !isUuid(it.id) }) return null
    if (!savedAt.isFinite() || !updatedAt.isFinite()) return null
    return SavedMeal(
        id = id.lowercase(),
        savedAtEpochMs = Math.round(savedAt * 1000),
        updatedAtEpochMs = Math.round(updatedAt * 1000),
        items = items.map {
            MealItem(
                id = it.id.lowercase(), ingredientId = it.ingredientId, label = it.label, quantityG = it.quantityG,
                cookingMethod = it.cookingMethod, measuredState = it.measuredState, energyKcal = it.energyKcal,
                available = it.available ?: true,
            )
        },
        totals = Macros(totals.energyKcal, totals.proteinG, totals.carbohydratesG, totals.fatG),
        slot = slot?.let { s -> MealSlot.entries.firstOrNull { it.id == s } },
        nutrients = nutrients?.let(::nutrientTotals),
    )
}

private fun isUuid(text: String): Boolean = runCatching { UUID.fromString(text) }.isSuccess && text.length == 36
