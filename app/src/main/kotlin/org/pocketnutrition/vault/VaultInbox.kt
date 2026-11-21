// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.pocketnutrition.recognition.RecognitionResult
import org.pocketnutrition.recognition.sanitized

/**
 * What the recognition service needs to deposit one result in the vault: where, with which write-only
 * token, under which record, and the public key to seal it to. Mirrors iOS `VaultDropGrant`.
 */
data class VaultDropGrant(
    val vaultUrl: String,
    val token: String,
    val recordId: String,
    /** Base64 X25519 public key. */
    val inboxPublicKey: String,
) {
    /** Never prints the token. */
    override fun toString(): String = "VaultDropGrant(record=$recordId)"
}

/** What [VaultInbox.collect] found for a record. */
sealed interface VaultCollected {
    data class Result(val result: RecognitionResult) : VaultCollected

    /** Nothing under that id yet (or it was deleted): ask again later. */
    data object NotThere : VaultCollected

    /**
     * A record is there but is not a usable delivery of this capture: wrong id, not final, undecodable, or
     * it does not open with this phone's keys. Asking again will not change that.
     */
    data object Unreadable : VaultCollected
}

/** What the worker seals into the vault: a finished (or failed) capture. Mirrors iOS `VaultDropPayload`. */
@Serializable
data class VaultDropPayload(
    @SerialName("v") val version: Int,
    @SerialName("record_id") val recordId: String,
    @SerialName("ticket_id") val ticketId: String,
    val status: String,
    val result: RecognitionResult? = null,
    val error: String? = null,
)

class VaultDropPayloadException(reason: String) : Exception(reason)

/**
 * Decodes a decrypted drop and refuses one that was not meant for [expectedRecordId]. Sealed boxes carry no
 * AAD, so the embedded record id is the only binding between a payload and the record it arrived in.
 */
fun decodeVaultDropPayload(plaintext: ByteArray, expectedRecordId: String): VaultDropPayload {
    val payload = try {
        vaultJson.decodeFromString(VaultDropPayload.serializer(), plaintext.toString(Charsets.UTF_8))
    } catch (e: SerializationException) {
        throw VaultDropPayloadException("malformed")
    } catch (e: IllegalArgumentException) {
        throw VaultDropPayloadException("malformed")
    }
    if (payload.version != DROP_PAYLOAD_VERSION) throw VaultDropPayloadException("unsupported version")
    if (payload.recordId != expectedRecordId) throw VaultDropPayloadException("record mismatch")
    if (payload.status != "done" && payload.status != "failed") throw VaultDropPayloadException("not terminal")
    return payload
}

private const val DROP_PAYLOAD_VERSION = 1

/**
 * The phone's side of a capture delivered through the vault: mint the write-only drop that goes to the
 * recognition service, then collect what the worker sealed into it. Port of iOS `VaultInbox`.
 *
 * Holds the unlocked keys of one linked session, so build it from [VaultStoredSession] and drop it when
 * the work is done.
 */
class VaultInbox(
    private val session: VaultStoredSession,
    private val server: VaultServer,
    private val crypto: VaultCrypto,
) {

    /** A fresh record id and a drop token for it. Nothing is written yet. */
    suspend fun prepareDrop(): VaultDropGrant {
        val recordId = UUID.randomUUID().toString().lowercase()
        val token = server.createDrop(
            session.apiKey,
            VaultDropRequest(
                recordId = recordId,
                type = VaultProtocol.CAPTURE_RECORD_TYPE,
                keyId = VaultProtocol.INBOX_KEY_ID,
                ttlSeconds = DROP_TTL_SECONDS,
                maxWrites = DROP_MAX_WRITES,
            ),
        )
        return VaultDropGrant(session.serverUrl, token.token, recordId, session.inboxPublicKeyB64)
    }

    /**
     * Finds the record, opens it, and re-saves it under the user key so it becomes an ordinary record of
     * the account (and the drop's hold on it ends). The re-save is best effort: the result is returned even
     * if it fails, and a later [collect] finds the record still sealed and tries again.
     *
     * Throws [VaultException] when the vault cannot be read (offline, refused): the caller retries later.
     */
    suspend fun collect(recordId: String): VaultCollected {
        val record = find(recordId)?.takeIf { !it.deleted } ?: return VaultCollected.NotThere
        if (record.type != VaultProtocol.CAPTURE_RECORD_TYPE) return VaultCollected.Unreadable
        val plaintext = open(record) ?: return VaultCollected.Unreadable
        val payload = try {
            decodeVaultDropPayload(plaintext, recordId)
        } catch (e: VaultDropPayloadException) {
            return VaultCollected.Unreadable
        }
        val result = payload.result?.takeIf { payload.status == "done" } ?: return VaultCollected.Unreadable
        if (record.keyId == VaultProtocol.INBOX_KEY_ID) resave(record, plaintext)
        return VaultCollected.Result(result.sanitized())
    }

    private suspend fun find(recordId: String): VaultRecord? {
        var cursor = 0L
        while (true) {
            val page = server.sync(session.apiKey, cursor)
            page.records.firstOrNull { it.id == recordId }?.let { return it }
            if (!page.hasMore || page.cursor <= cursor) return null
            cursor = page.cursor
        }
    }

    /** The plaintext of a delivered record, or null if it does not open. */
    private fun open(record: VaultRecord): ByteArray? = try {
        when (record.keyId) {
            VaultProtocol.INBOX_KEY_ID -> crypto.openSealed(record.ciphertext, session.keys.inbox)
            VaultProtocol.USER_KEY_ID ->
                crypto.decrypt(record.ciphertext, session.userKey, aad(record, record.revision, record.keyId))
            else -> null
        }
    } catch (e: VaultCryptoException) {
        null
    }

    private suspend fun resave(record: VaultRecord, plaintext: ByteArray) {
        val revision = record.revision + 1
        try {
            val sealed = crypto.encrypt(plaintext, session.userKey, aad(record, revision, VaultProtocol.USER_KEY_ID))
            server.putRecord(
                session.apiKey,
                record.id,
                VaultRecordWrite(
                    type = record.type,
                    scopeId = record.scopeId,
                    revision = revision,
                    keyId = VaultProtocol.USER_KEY_ID,
                    ciphertext = sealed,
                ),
            )
        } catch (e: VaultException) {
            // Best effort: the record stays sealed to the inbox and the next collect tries again.
        } catch (e: VaultCryptoException) {
            // Same.
        }
    }

    private fun aad(record: VaultRecord, revision: Int, keyId: String): ByteArray =
        vaultRecordAad(session.accountId, record.id, record.type, record.scopeId, revision, keyId)

    companion object {
        /** The drop token outlives the 48 h ticket, so a late worker can still deposit. */
        const val DROP_TTL_SECONDS = 3 * 24 * 3600

        /** One retry if the first deposit's answer was lost; the token can do nothing else. */
        const val DROP_MAX_WRITES = 2
    }
}
