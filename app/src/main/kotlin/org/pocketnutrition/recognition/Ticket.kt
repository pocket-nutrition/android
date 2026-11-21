// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// Wire types of the voice-recognition server. This endpoint family is NOT part of the pinned contract
// (contracts 2.0.0), so these types are hand-written from the shared client spec rather than generated
// or vendored. Times arrive as Unix epoch SECONDS in floating point.
//
// Decoding is deliberately lenient: unknown keys are ignored, and `status` stays a plain String so a
// status this client has never heard of decodes fine and is treated as "in progress" instead of
// crashing the screen.

/** One ranked catalogue match for a spoken item. */
@Serializable
data class Candidate(
    @SerialName("ingredient_id") val ingredientId: String,
    val name: String = "",
    val score: Double = 0.0,
)

/** One food the speaker mentioned. [quantity] and [unit] are as spoken, never converted. */
@Serializable
data class RecognizedItem(
    @SerialName("raw_text") val rawText: String,
    val quantity: Double? = null,
    val unit: String? = null,
    @SerialName("cooking_method") val cookingMethod: String? = null,
    /** Best first. */
    val candidates: List<Candidate> = emptyList(),
)

@Serializable
data class RecognitionResult(
    val transcript: String = "",
    val language: String? = null,
    val items: List<RecognizedItem> = emptyList(),
)

/** The server's ticket for one capture. */
@Serializable
data class Ticket(
    val id: String,
    val status: String,
    @SerialName("created_at") val createdAt: Double,
    @SerialName("updated_at") val updatedAt: Double,
    @SerialName("expires_at") val expiresAt: Double,
    val result: RecognitionResult? = null,
    /** A server-written message when [status] is `failed`; shown as received. */
    val error: String? = null,
    /** `delivered` when the worker sealed the result into the user's vault: the server never holds it. */
    val delivery: String? = null,
    /**
     * Only in the answer to `POST /captures`: authenticates every later read or delete of this one ticket,
     * so a phone without a shared key can follow it.
     */
    val secret: String? = null,
) {
    /** Never prints the secret. */
    override fun toString(): String = "Ticket(id=$id, status=$status, delivery=$delivery)"
}

internal val recognitionJson = Json { ignoreUnknownKeys = true }

private val TICKET_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

// Server data is untrusted and ends up on screen and on disk, so it is capped after decoding.
internal const val MAX_TRANSCRIPT_CHARS = 8 * 1024
internal const val MAX_ITEMS = 50
internal const val MAX_CANDIDATES = 10
internal const val MAX_ERROR_CHARS = 200
internal const val MAX_FIELD_CHARS = 200

private fun String.capped(max: Int) = if (length <= max) this else take(max)

/** Truncates and limits everything a server controls. Pure; applied by [decodeTicket]. */
fun Ticket.sanitized(): Ticket = copy(
    error = error?.capped(MAX_ERROR_CHARS),
    delivery = delivery?.capped(32),
    secret = secret?.takeIf { it.length <= MAX_SECRET_CHARS && isValidTicketSecret(it) },
    result = result?.sanitized(),
)

/** The same limits for a result that arrives by another road (a vault drop). */
fun RecognitionResult.sanitized(): RecognitionResult = copy(
    transcript = transcript.capped(MAX_TRANSCRIPT_CHARS),
    language = language?.capped(16),
    items = items.take(MAX_ITEMS).map { item ->
        item.copy(
            rawText = item.rawText.capped(MAX_FIELD_CHARS),
            unit = item.unit?.capped(32),
            cookingMethod = item.cookingMethod?.capped(32),
            candidates = item.candidates.take(MAX_CANDIDATES).map { c ->
                c.copy(ingredientId = c.ingredientId.capped(128), name = c.name.capped(MAX_FIELD_CHARS))
            },
        )
    },
)

internal const val MAX_SECRET_CHARS = 256

/** A secret goes back in an HTTP header: visible ASCII only, or it is dropped. */
private fun isValidTicketSecret(value: String): Boolean = value.isNotEmpty() && value.all { it.code in 0x21..0x7E }

/** `done`, and the result went to the vault instead of staying on the ticket. */
fun isDeliveredToVault(ticket: Ticket): Boolean =
    ticket.status == "done" && ticket.result == null && ticket.delivery == "delivered"

/** What goes in `Authorization: Bearer` for a ticket call: the ticket's own secret, else the shared key. */
fun recognitionCredential(ticketSecret: String?, apiKey: String?): String? =
    listOf(ticketSecret, apiKey).firstOrNull { !it.isNullOrEmpty() }

/** Throws [SerializationException] on a malformed body or an id outside `[A-Za-z0-9_-]{1,64}`. */
fun decodeTicket(raw: String): Ticket {
    val ticket = recognitionJson.decodeFromString<Ticket>(raw)
    if (!TICKET_ID.matches(ticket.id)) throw SerializationException("invalid ticket id")
    return ticket.sanitized()
}

fun decodeTicketOrNull(raw: String): Ticket? = try {
    decodeTicket(raw)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

/**
 * Server terminal states: `done` and `failed`. Everything else — including a status this client does
 * not know — is in progress, so a server that adds a stage never makes a ticket look finished.
 */
fun isTerminalStatus(status: String): Boolean = status == "done" || status == "failed"
