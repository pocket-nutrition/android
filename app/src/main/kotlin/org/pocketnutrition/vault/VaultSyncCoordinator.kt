// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pocketnutrition.model.JournalStore
import org.pocketnutrition.model.MealRecordPayload
import org.pocketnutrition.model.RecordChange
import org.pocketnutrition.model.TargetsStore
import org.pocketnutrition.model.TargetsVaultRecord
import org.pocketnutrition.model.effectiveUpdatedAtEpochMs
import org.pocketnutrition.model.toRecord
import org.pocketnutrition.model.toSavedMeal

/**
 * Where the sync bookkeeping lives: one small file per kind of record. Not a secret (record ids and
 * revision numbers), excluded from backup with the data it describes.
 */
class VaultSyncStateFile(private val file: File) {
    fun load(): VaultSyncState? =
        runCatching { vaultJson.decodeFromString(VaultSyncState.serializer(), file.readText()) }.getOrNull()

    fun save(state: VaultSyncState) {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(vaultJson.encodeToString(VaultSyncState.serializer(), state))
            temporary.renameTo(file)
        }
    }

    fun clear() {
        file.delete()
    }
}

/** What a coordinator shows on the vault screen. */
enum class VaultSyncStatus { OFF, IDLE, SYNCING, OFFLINE, FULL, FAILED }

/** The local side of one kind of records: what is there now, and how to write what the sync brought. */
interface VaultSyncLocal<P> {
    fun snapshot(): List<P>
    /** Record id to last local edit, in seconds. */
    fun localUpdatedAt(): Map<String, Double>
    fun apply(upserts: List<P>, deletes: Set<String>)
}

/**
 * Keeps one kind of local records in step with the linked vault account (iOS `JournalSyncCoordinator` and
 * `TargetsSyncCoordinator`): uploads what is new or edited here, brings down what changed elsewhere, and
 * settles conflicts (see [VaultRecordSync]). Without a linked account it does nothing and the data stays on
 * the phone. A failed sync is tried again at the next change or the next time the app comes to the front.
 *
 * Runs on [scope] (the activity's main-thread scope), so local writes happen on the main thread; the network
 * and cipher work run on background dispatchers.
 */
class VaultSyncCoordinator<P>(
    private val codec: VaultRecordCodec<P>,
    private val local: VaultSyncLocal<P>,
    private val stateFile: VaultSyncStateFile,
    private val loadSession: () -> VaultStoredSession?,
    private val makeServer: (String) -> VaultServer,
    private val crypto: () -> VaultCrypto,
    private val scope: CoroutineScope,
    /** Called when the server says this device is no longer linked. */
    private val onUnauthorized: () -> Unit = {},
) {
    var status: VaultSyncStatus by mutableStateOf(VaultSyncStatus.OFF)
        private set
    /** Records the vault holds in a format this version cannot read, so they are left untouched. */
    var unsupportedCount: Int by mutableIntStateOf(0)
        private set

    private var state: VaultSyncState? = stateFile.load()
    private var running: Job? = null
    private var runAgain = false
    private var pending: Job? = null

    init {
        unsupportedCount = state?.unsupported?.size ?: 0
    }

    /** Records a change the person made, then syncs after a short pause (one edit saves several times). */
    fun noteChange(change: RecordChange) {
        val session = loadSession() ?: return
        val current = boundState(session)
        store(
            when (change) {
                is RecordChange.Saved -> current.markChanged(change.id)
                is RecordChange.Deleted -> current.markDeleted(change.id)
            },
        )
        syncSoon()
    }

    fun syncSoon(delayMs: Long = DEBOUNCE_MS) {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    /** This phone is no longer linked: forget the bookkeeping, keep every local value. */
    fun unlinked() {
        pending?.cancel()
        state = null
        stateFile.clear()
        status = VaultSyncStatus.OFF
        unsupportedCount = 0
    }

    fun syncNow() {
        if (running?.isActive == true) {
            runAgain = true
            return
        }
        running = scope.launch {
            do {
                runAgain = false
                runOnce()
            } while (runAgain)
        }
    }

    private suspend fun runOnce() {
        val session = withContext(Dispatchers.IO) { loadSession() }
        if (session == null) {
            if (state != null) unlinked()
            status = VaultSyncStatus.OFF
            return
        }
        status = VaultSyncStatus.SYNCING
        val snapshot = boundState(session)
        if (state != snapshot) store(snapshot)
        val values = local.snapshot()
        val result = withContext(Dispatchers.Default) {
            VaultRecordSync(session, makeServer(session.serverUrl), codec, crypto()).run(values, snapshot)
        }
        // Back on the scope's thread: the person may have edited meanwhile, so merge into the CURRENT state.
        val current = state ?: return
        val edited = local.localUpdatedAt()
        val applicable = result.applicableUpserts(edited, codec)
        local.apply(applicable, result.deletes)
        store(current.merging(result, applicable, edited, codec))
        unsupportedCount = result.unsupported.size
        status = when (result.failure) {
            null -> VaultSyncStatus.IDLE
            VaultSyncFailure.OFFLINE -> VaultSyncStatus.OFFLINE
            VaultSyncFailure.QUOTA_EXCEEDED -> VaultSyncStatus.FULL
            VaultSyncFailure.UNAUTHORIZED -> VaultSyncStatus.FAILED.also { onUnauthorized() }
            VaultSyncFailure.SERVER -> VaultSyncStatus.FAILED
        }
    }

    /** The state for this account: the saved one, or an empty one for a new or different account. */
    private fun boundState(session: VaultStoredSession): VaultSyncState =
        state?.takeIf { it.accountId == session.accountId } ?: VaultSyncState(session.accountId)

    private fun store(next: VaultSyncState) {
        state = next
        stateFile.save(next)
    }

    private companion object {
        const val DEBOUNCE_MS = 2_000L
    }
}

/** The journal as the meal sync sees it. */
class JournalSyncLocal(private val journal: JournalStore) : VaultSyncLocal<MealRecordPayload> {
    override fun snapshot() = journal.meals.map { it.toRecord() }
    override fun localUpdatedAt() = journal.meals.associate { it.id to it.effectiveUpdatedAtEpochMs / 1000.0 }
    override fun apply(upserts: List<MealRecordPayload>, deletes: Set<String>) =
        journal.apply(upserts.mapNotNull { it.toSavedMeal() }, deletes)
}

/** The daily-targets settings as their sync sees them. */
class TargetsSyncLocal(private val store: TargetsStore) : VaultSyncLocal<TargetsVaultRecord> {
    override fun snapshot() = store.records
    override fun localUpdatedAt() = store.localUpdatedAt
    override fun apply(upserts: List<TargetsVaultRecord>, deletes: Set<String>) = store.apply(upserts, deletes)
}
