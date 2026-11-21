// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.pocketnutrition.vault.VaultClient
import org.pocketnutrition.vault.VaultCollected
import org.pocketnutrition.vault.VaultCrypto
import org.pocketnutrition.vault.VaultDropGrant
import org.pocketnutrition.vault.VaultException
import org.pocketnutrition.vault.VaultInbox
import org.pocketnutrition.vault.VaultSettings

// The real-network side of captures: what CaptureCoordinator drives, and deleting one. Moved as-is from
// the former voice screen; the rules in the comments are the ones that keep a ticket from being lost or
// a recording from being sent twice.

/** Hard ceiling on one upload; the connection is closed when it passes. */
private const val UPLOAD_DEADLINE_MS = 90_000L

/**
 * The vault access of the session this phone is linked with, or null when it is not linked. Results only
 * travel through the vault, so without it nothing is sent. Reads the Keystore: call off the main thread.
 */
fun liveVaultInbox(): VaultInbox? {
    val session = VaultSettings.sessions.load() ?: return null
    return VaultInbox(session, VaultClient(session.serverUrl), VaultCrypto())
}

/** A write-only drop for one upload. Vault failures map to upload failures the driver already handles. */
private suspend fun mintGrant(inbox: VaultInbox): VaultDropGrant = try {
    inbox.prepareDrop()
} catch (e: VaultException) {
    throw if (e is VaultException.Offline) RecognitionException.Offline else RecognitionException.BadStatus(503)
}

/** The stored secret of [ticketId], if [localId] still holds that ticket. */
private fun EntryStore.secretOf(localId: String, ticketId: String): String? =
    find(localId)?.takeIf { it.ticketId == ticketId }?.ticketSecret

/**
 * Runs [driveEntry] for one stored entry with the real network, writing every step back to [store].
 * Nothing is uploaded while no vault account is linked: the entry is flagged and waits.
 */
suspend fun driveStoredCapture(store: EntryStore, localId: String, onOffline: (Boolean) -> Unit) {
    // Off the main thread: reading the config may decrypt the key through the Keystore.
    val config = withContext(Dispatchers.IO) { RecognitionSettings.config }
    val start = store.find(localId) ?: return
    val uploads = Uploads.shared
    driveEntry(
        start = start,
        upload = upload@{ entry ->
            val id = entry.localId
            // A ticket the server returned but the entries file could not store: persist it again
            // before anything is ever uploaded a second time.
            val storedNow = store.find(id)
            uploads.usableUnsaved(id, storedNow?.ticketId)?.let { remembered ->
                val stored = storedNow
                if (stored == null) {
                    uploads.forget(id)
                    withContext(NonCancellable) {
                        runCatching { RecognitionApi.delete(config, remembered.id, remembered.secret) }
                    }
                    throw UploadSupersededException()
                }
                val recordId = uploads.unsavedRecordId(id)
                if (store.upsert(stored.uploaded(remembered, System.currentTimeMillis(), recordId))) uploads.forget(id)
                return@upload remembered
            }
            // One upload in flight per entry, process-wide: wait for the running one, then let the
            // driver reload the entry and follow its ticket instead of uploading again.
            if (!uploads.tryAcquire(id)) {
                while (uploads.isBusy(id)) delay(300)
                throw UploadSupersededException()
            }
            try {
                val fresh = store.find(id) ?: throw UploadSupersededException()
                if (fresh.ticketId != null) throw UploadSupersededException()
                val file = store.audioFor(fresh)?.takeIf { it.exists() } ?: throw AudioMissingException()
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                // The worker seals the result into the vault with this write-only drop. Without a linked
                // vault nothing is sent: the result would have nowhere to go but the recognition server.
                val grant = uploads.grant(id) ?: run {
                    val inbox = withContext(Dispatchers.IO) { liveVaultInbox() } ?: throw VaultRequiredException()
                    mintGrant(inbox).also { uploads.rememberGrant(id, it) }
                }
                // NonCancellable, and the ticket is recorded INSIDE it: leaving the app after the
                // server accepted the upload must not lose the ticket or cause a second upload. The
                // deadline closes the connection if the server never answers.
                withContext(NonCancellable) {
                    val ticket = try {
                        withTimeout(UPLOAD_DEADLINE_MS) { RecognitionApi.upload(config, bytes, grant, mediaFor(file.name)) }
                    } catch (e: TimeoutCancellationException) {
                        throw RecognitionException.TimedOut
                    } catch (e: RecognitionException.DropRefused) {
                        // Used or no longer valid: the next attempt mints a new one.
                        uploads.forgetGrant(id)
                        throw e
                    }
                    uploads.forgetGrant(id)
                    val stored = store.find(id)
                    if (stored == null) {
                        // Deleted while uploading: the ticket would be orphaned on the server.
                        runCatching { RecognitionApi.delete(config, ticket.id, ticket.secret) }
                        throw UploadSupersededException()
                    }
                    if (!store.upsert(stored.uploaded(ticket, System.currentTimeMillis(), grant.recordId))) {
                        uploads.rememberUnsaved(id, ticket, grant.recordId)
                    }
                    ticket
                }
            } finally {
                uploads.release(id)
            }
        },
        fetch = { ticketId, wait, known ->
            RecognitionApi.fetch(config, ticketId, wait, known, store.secretOf(localId, ticketId))
        },
        // NonCancellable: the entry stops needing the network the moment it is ready, which ends this
        // loop; the server-side DELETE must still go out.
        afterReady = { ticketId ->
            val secret = store.secretOf(localId, ticketId)
            withContext(NonCancellable) { RecognitionApi.delete(config, ticketId, secret) }
        },
        // The worker sealed the result into the vault: open it there. A phone unlinked meanwhile cannot.
        collect = { entry ->
            withContext(Dispatchers.IO) {
                val recordId = entry.vaultRecordId
                val inbox = liveVaultInbox()
                if (recordId == null || inbox == null) VaultCollected.Unreadable else inbox.collect(recordId)
            }
        },
        onUpdate = { store.upsert(it) },
        onOffline = onOffline,
        onUnauthorized = {},
        reload = { store.find(it.localId) },
    )
}

/**
 * Removes a capture and its file, then (best effort, once the removal is persisted) its server ticket.
 * A ready entry's ticket was deleted when the result arrived, unless that DELETE is still pending.
 */
fun deleteCapture(store: EntryStore, coordinator: CaptureCoordinator, localId: String, scope: CoroutineScope) {
    coordinator.forget(localId)
    // Re-read: a ticket obtained a moment ago must be deleted too.
    val fresh = store.find(localId) ?: return
    val ticketId = fresh.ticketId
    val secret = fresh.ticketSecret
    Uploads.shared.forget(localId)
    val removed = store.remove(localId)
    if (removed && ticketId != null && (fresh.state != EntryState.READY || fresh.ticketDeletePending)) {
        scope.launch {
            val config = withContext(Dispatchers.IO) { RecognitionSettings.config }
            withContext(NonCancellable) { runCatching { RecognitionApi.delete(config, ticketId, secret) } }
        }
    }
}

/** "Send again" for a failed or expired capture: back to pending, then sent. */
fun resendCapture(store: EntryStore, coordinator: CaptureCoordinator, entry: Entry) {
    Uploads.shared.forget(entry.localId)
    if (store.upsert(entry.retryRequested())) coordinator.sendNow(entry.localId)
}
