// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import java.util.concurrent.ConcurrentHashMap
import org.pocketnutrition.vault.VaultDropGrant

/**
 * Process-wide bookkeeping so a recording is uploaded at most once at a time, whichever screen,
 * lifecycle restart or button asked for it. It lives outside the composition on purpose: an upload runs
 * to completion even when the loop that started it is cancelled, and the next loop must see that.
 *
 * It also holds a ticket the server returned but the entries file could not store yet, so that the
 * persist is retried before anything is ever uploaded again.
 */
class InFlightUploads {
    private val busy = ConcurrentHashMap.newKeySet<String>()
    private val unsaved = ConcurrentHashMap<String, Ticket>()
    private val unsavedRecordIds = ConcurrentHashMap<String, String>()
    private val grants = ConcurrentHashMap<String, VaultDropGrant>()

    /** `true` if the caller now owns the upload of [localId]; `false` if one is already running. */
    fun tryAcquire(localId: String): Boolean {
        val acquired = busy.add(localId)
        // A new attempt starts from a clean slate: a ticket remembered for an earlier attempt is stale.
        if (acquired) forgetUnsaved(localId)
        return acquired
    }

    fun release(localId: String) {
        busy.remove(localId)
    }

    fun isBusy(localId: String): Boolean = localId in busy

    /** What a "Send now" / refresh press may do: only when nothing is on the wire for this entry. */
    fun canRestart(localId: String): Boolean = !isBusy(localId)

    fun rememberUnsaved(localId: String, ticket: Ticket, vaultRecordId: String? = null) {
        unsaved[localId] = ticket
        if (vaultRecordId != null) unsavedRecordIds[localId] = vaultRecordId else unsavedRecordIds.remove(localId)
    }

    /** The vault record the remembered ticket's result goes to. */
    fun unsavedRecordId(localId: String): String? = unsavedRecordIds[localId]

    /**
     * A drop minted for an entry whose upload has not gone through yet, reused by its retries: minting a
     * new one on every attempt would fill the vault's cap on pending drops while offline.
     */
    fun grant(localId: String): VaultDropGrant? = grants[localId]

    fun rememberGrant(localId: String, grant: VaultDropGrant) {
        grants[localId] = grant
    }

    /** The upload went through (the grant is now the ticket's), or the server refused its token. */
    fun forgetGrant(localId: String) {
        grants.remove(localId)
    }

    private fun forgetUnsaved(localId: String) {
        unsaved.remove(localId)
        unsavedRecordIds.remove(localId)
    }

    fun unsavedTicket(localId: String): Ticket? = unsaved[localId]

    /**
     * The remembered ticket to re-persist, but only while the stored entry still has no ticket. If it
     * has one (the ticket was saved, or the entry was reset and re-sent), the memory is stale: it is
     * dropped and `null` is returned.
     */
    fun usableUnsaved(localId: String, storedTicketId: String?): Ticket? {
        val remembered = unsaved[localId] ?: return null
        if (storedTicketId != null) {
            forgetUnsaved(localId)
            return null
        }
        return remembered
    }

    fun forget(localId: String) {
        forgetUnsaved(localId)
        grants.remove(localId)
    }
}

/** The one instance the app uses. */
object Uploads {
    val shared = InFlightUploads()
}
