// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.pocketnutrition.model.MealRecordPayload
import org.pocketnutrition.model.MealRecords
import org.pocketnutrition.model.RecordRead
import org.pocketnutrition.model.TargetsVaultRecord

/** Why a sync stopped before it finished. What was done up to that point is still returned. */
enum class VaultSyncFailure {
    /** No connection: try again later, nothing is lost. */
    OFFLINE,
    /** The account is full. Retrying will not help until something is freed. */
    QUOTA_EXCEEDED,
    /** The device key was revoked or the account erased: this phone is no longer linked. */
    UNAUTHORIZED,
    SERVER,
}

/**
 * How one kind of value travels as vault records, sealed under the user key. Meals and the daily-targets
 * records share the same sync rules through this (iOS `VaultSyncPayload`).
 */
interface VaultRecordCodec<P> {
    /** The record types this payload is read from; other records in the feed are skipped. */
    val recordTypes: Set<String>
    fun recordId(payload: P): String
    fun recordType(payload: P): String
    /** Seconds since 1970; the later one wins. */
    fun updatedAt(payload: P): Double
    fun encode(payload: P): String
    fun decode(plaintext: String, type: String, expectedId: String): RecordRead<P>
}

object MealRecordCodec : VaultRecordCodec<MealRecordPayload> {
    override val recordTypes = setOf(MealRecords.TYPE)
    override fun recordId(payload: MealRecordPayload) = payload.id
    override fun recordType(payload: MealRecordPayload) = MealRecords.TYPE
    override fun updatedAt(payload: MealRecordPayload) = payload.updatedAt
    override fun encode(payload: MealRecordPayload) = MealRecords.encode(payload)
    override fun decode(plaintext: String, type: String, expectedId: String) = MealRecords.decode(plaintext, expectedId)
}

object TargetsRecordCodec : VaultRecordCodec<TargetsVaultRecord> {
    override val recordTypes = TargetsVaultRecord.TYPES
    override fun recordId(payload: TargetsVaultRecord) = payload.recordId
    override fun recordType(payload: TargetsVaultRecord) = payload.recordType
    override fun updatedAt(payload: TargetsVaultRecord) = payload.updatedAt
    override fun encode(payload: TargetsVaultRecord) = payload.encoded()
    override fun decode(plaintext: String, type: String, expectedId: String) = TargetsVaultRecord.decode(plaintext, type, expectedId)
}

/**
 * What the phone remembers about one kind of records' sync with one vault account (iOS `JournalSyncState`).
 *
 * Bound to the account: a different account starts from nothing, so linking another vault uploads the whole
 * local data to it. A value with no known server revision has never been uploaded, and that alone makes it
 * go up, which is how a first sync transfers everything with no special mode.
 */
@Serializable
data class VaultSyncState(
    @SerialName("account_id") val accountId: String,
    /** `GET /v1/sync` position: everything up to here has been looked at. */
    val cursor: Long = 0,
    /** Record id to the last revision the server is known to hold, tombstones included. */
    val revisions: Map<String, Int> = emptyMap(),
    /** Changed here since last uploaded. */
    val dirty: Set<String> = emptySet(),
    /** Deleted here, and the server has not heard yet. */
    val deleted: Set<String> = emptySet(),
    /**
     * Records the server holds in a newer format than this app reads. Never overwritten nor deleted from
     * here, across runs (iOS only remembers this within one run; see the report).
     */
    val unsupported: Set<String> = emptySet(),
) {
    fun markChanged(id: String) = copy(deleted = deleted - id, dirty = dirty + id)

    /** A value that never reached the server needs no deletion there. */
    fun markDeleted(id: String) = copy(dirty = dirty - id, deleted = if (id in revisions) deleted + id else deleted)
}

/**
 * What one sync pass changed, as a delta the caller applies to its own, possibly newer, state: the person
 * keeps using the app while the network calls run.
 */
data class VaultSyncResult<P>(
    val cursor: Long,
    /** Revisions the server now holds, to merge into the state. */
    val revisions: Map<String, Int> = emptyMap(),
    /** Record id to the `updatedAt` that was uploaded. Clear its dirty flag only if unchanged since. */
    val pushed: Map<String, Double> = emptyMap(),
    /** Deleted here and now deleted on the server too (or abandoned, for an unsupported record). */
    val deletionsDelivered: Set<String> = emptySet(),
    /** Values to write locally: new from another device, or newer than the local one. */
    val upserts: List<P> = emptyList(),
    /** Deleted from another device, to remove locally. */
    val deletes: Set<String> = emptySet(),
    /** Every record known to be in a format newer than this app reads, after this pass. */
    val unsupported: Set<String> = emptySet(),
    val failure: VaultSyncFailure? = null,
) {
    /** The downloaded values that may still be written: not those edited here after the sync's snapshot. */
    fun applicableUpserts(localUpdatedAt: Map<String, Double>, codec: VaultRecordCodec<P>): List<P> =
        upserts.filter { (localUpdatedAt[codec.recordId(it)] ?: 0.0) <= codec.updatedAt(it) }
}

/**
 * This state with a sync result folded in. `this` is the CURRENT state, which may have moved on while the
 * calls ran: a value edited meanwhile (its `updatedAt` differs from what was uploaded) stays dirty.
 */
fun <P> VaultSyncState.merging(
    result: VaultSyncResult<P>,
    applied: List<P>,
    localUpdatedAt: Map<String, Double>,
    codec: VaultRecordCodec<P>,
): VaultSyncState {
    val stillDirty = dirty.toMutableSet()
    result.pushed.forEach { (id, uploadedAt) -> if (localUpdatedAt[id] == uploadedAt) stillDirty.remove(id) }
    applied.forEach { stillDirty.remove(codec.recordId(it)) }
    stillDirty.removeAll(result.deletes)
    return copy(
        cursor = maxOf(cursor, result.cursor),
        revisions = revisions + result.revisions,
        dirty = stillDirty,
        deleted = deleted - result.deletionsDelivered,
        unsupported = result.unsupported,
    )
}

/**
 * Two-way sync of local values with the vault, one record per value, sealed under the user key (iOS
 * `VaultRecordSync`). Conflicts are settled here because the server cannot read the records: the later
 * `updated_at` wins, a deletion wins over an untouched copy but not over a local edit, and a record this
 * version cannot read is left exactly as it is.
 */
class VaultRecordSync<P>(
    private val session: VaultStoredSession,
    private val server: VaultServer,
    private val codec: VaultRecordCodec<P>,
    private val crypto: VaultCrypto,
) {
    private inner class Run(local: List<P>, state: VaultSyncState) {
        val local: MutableMap<String, P> = local.associateByTo(LinkedHashMap()) { codec.recordId(it) }
        val dirty = state.dirty.toMutableSet()
        val deleted = state.deleted.toMutableSet()
        val revisions = state.revisions.toMutableMap()
        /** Unreadable this run (does not decrypt, malformed): not overwritten during it. */
        val unreadable = mutableSetOf<String>()
        val unsupported = state.unsupported.toMutableSet()
        var cursor = state.cursor
        val newRevisions = mutableMapOf<String, Int>()
        val pushed = mutableMapOf<String, Double>()
        val deletionsDelivered = mutableSetOf<String>()
        val upserts = LinkedHashMap<String, P>()
        val deletes = mutableSetOf<String>()

        fun result(failure: VaultSyncFailure?) = VaultSyncResult(
            cursor, newRevisions.toMap(), pushed.toMap(), deletionsDelivered.toSet(), upserts.values.toList(),
            deletes.toSet(), unsupported.toSet(), failure,
        )
    }

    /** [local] is the data as it is now. [state] must belong to this session's account. */
    suspend fun run(local: List<P>, state: VaultSyncState): VaultSyncResult<P> {
        val run = Run(local, state)
        return try {
            repeat(MAX_ROUNDS) {
                pull(run)
                if (push(run)) return run.result(null) // false: a write lost a race, pull again
            }
            run.result(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VaultException) {
            run.result(classify(e))
        }
    }

    private suspend fun pull(run: Run) {
        while (true) {
            val page = server.sync(session.apiKey, run.cursor)
            page.records.filter { it.type in codec.recordTypes }.forEach { absorb(it, run) }
            val before = run.cursor
            run.cursor = maxOf(run.cursor, page.cursor)
            if (!page.hasMore || run.cursor <= before) return
        }
    }

    private fun absorb(record: VaultRecord, run: Run) {
        val id = record.id
        run.revisions[id] = record.revision
        run.newRevisions[id] = record.revision

        if (record.deleted) {
            run.unsupported.remove(id)
            // A deletion elsewhere removes an untouched copy; a copy edited here since is kept and goes back up.
            if (id in run.dirty && id in run.local) return
            run.local.remove(id)
            if (run.deleted.remove(id)) run.deletionsDelivered.add(id)
            run.upserts.remove(id)
            run.deletes.add(id)
            return
        }

        val remote = when (val read = open(record)) {
            is RecordRead.Ok -> read.payload.also { run.unsupported.remove(id) }
            is RecordRead.UnsupportedVersion -> {
                run.unsupported.add(id)
                return
            }
            RecordRead.Malformed -> {
                run.unreadable.add(id)
                return
            }
        }
        // Deleted here, and the server has not heard yet: the deletion is about to be sent.
        if (id in run.deleted) return
        val mine = run.local[id]
        if (mine != null && codec.updatedAt(mine) >= codec.updatedAt(remote)) return

        run.local[id] = remote
        run.dirty.remove(id)
        run.deletes.remove(id)
        run.upserts.remove(id)
        run.upserts[id] = remote
    }

    /** The value inside a record, checked against exactly the fields the server returned. */
    private fun open(record: VaultRecord): RecordRead<P> {
        if (record.keyId != VaultProtocol.USER_KEY_ID) return RecordRead.Malformed
        val aad = vaultRecordAad(session.accountId, record.id, record.type, record.scopeId, record.revision, record.keyId)
        val plaintext = try {
            crypto.decrypt(record.ciphertext, session.userKey, aad)
        } catch (e: VaultCryptoException) {
            return RecordRead.Malformed
        }
        return codec.decode(plaintext.toString(Charsets.UTF_8), record.type, record.id)
    }

    /** Sends what is new here. Returns false when a write lost a race and the caller should pull and retry. */
    private suspend fun push(run: Run): Boolean {
        var retry = false

        for (id in run.local.keys.sorted()) {
            val value = run.local.getValue(id)
            if (id in run.unreadable || id in run.unsupported || !VaultProtocol.isValidRecordId(id)) continue
            if (id !in run.dirty && run.revisions[id] != null) continue
            val revision = (run.revisions[id] ?: 0) + 1
            try {
                server.putRecord(session.apiKey, id, write(value, revision))
                run.revisions[id] = revision
                run.newRevisions[id] = revision
                run.pushed[id] = codec.updatedAt(value)
                run.dirty.remove(id)
            } catch (e: VaultException.Outcome) {
                if (e.outcome != VaultHttpOutcome.RevisionConflict) throw e
                retry = true
            }
        }

        for (id in run.deleted.sorted()) {
            val known = run.revisions[id]
            // Never reached the server, or the server holds a version this app cannot read: nothing to send.
            if (known == null || id in run.unsupported) {
                run.deletionsDelivered.add(id)
                run.deleted.remove(id)
                continue
            }
            try {
                server.deleteRecord(session.apiKey, id, known + 1)
                run.revisions[id] = known + 1
                run.newRevisions[id] = known + 1
            } catch (e: VaultException.Outcome) {
                when (e.outcome) {
                    VaultHttpOutcome.RevisionConflict -> {
                        retry = true
                        continue
                    }
                    VaultHttpOutcome.Other(NOT_FOUND) -> Unit // already gone on the server
                    else -> throw e
                }
            }
            run.deletionsDelivered.add(id)
            run.deleted.remove(id)
        }
        return !retry
    }

    private fun write(value: P, revision: Int): VaultRecordWrite {
        val id = codec.recordId(value)
        val type = codec.recordType(value)
        val aad = vaultRecordAad(session.accountId, id, type, null, revision, VaultProtocol.USER_KEY_ID)
        val ciphertext = crypto.encrypt(codec.encode(value).toByteArray(Charsets.UTF_8), session.userKey, aad)
        return VaultRecordWrite(type = type, revision = revision, keyId = VaultProtocol.USER_KEY_ID, ciphertext = ciphertext)
    }

    private fun classify(e: VaultException): VaultSyncFailure = when (e) {
        VaultException.Offline -> VaultSyncFailure.OFFLINE
        is VaultException.Outcome -> when (e.outcome) {
            VaultHttpOutcome.Unauthorized -> VaultSyncFailure.UNAUTHORIZED
            VaultHttpOutcome.QuotaExceeded -> VaultSyncFailure.QUOTA_EXCEEDED
            else -> VaultSyncFailure.SERVER
        }
        VaultException.Malformed -> VaultSyncFailure.SERVER
    }

    private companion object {
        /** Pull-push rounds per run: a lost write race is retried after pulling, but not forever. */
        const val MAX_ROUNDS = 3
        const val NOT_FOUND = 404
    }
}
