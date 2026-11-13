// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Pause after a long poll that came back unchanged, so a server that ignores `wait` is not hammered. */
private const val UNCHANGED_PAUSE_MS = 1_000L

/** Consecutive unparseable answers while polling before the entry shows a visible error. */
private const val MAX_MALFORMED = 5

/**
 * Drives one entry until it needs nothing more from the network: uploads while it is
 * `PENDING_UPLOAD`, then reads the ticket (a plain read first when resuming, then long polls whose
 * `known_status` is the last status seen) while it is `PROCESSING`.
 *
 * - Connectivity errors and 5xx keep the entry, report [onOffline] (connectivity only) and retry after
 *   [backoffDelayMs].
 * - 401 keeps the entry flagged with the auth issue and stops; so does a 403 while polling. Retrying
 *   with the same key or URL cannot succeed ([onUnauthorized] lets the UI offer Settings for a 401).
 * - Repeated unparseable answers while polling stop the loop with a visible error after a few tries.
 * - 413/415 end in `UNRECOVERABLE` (audio kept); redirects and other 4xx stay pending and stop the loop
 *   until the user acts; a TLS failure backs off like a network error but is not reported as offline.
 * - 404 / 410 / failed become `NEEDS_RETRY` (audio present) or `UNRECOVERABLE`.
 * - On `READY`, once the entry has been persisted ([onUpdate] returned `true`), [afterReady] is called
 *   for the best-effort server DELETE and the pending-delete flag is cleared on success. If the DELETE
 *   fails the flag stays, and [settleReadyTicket] retries later. A failed persist never triggers a DELETE.
 * - [reload] gives the freshest stored version of the entry at the top of every iteration (`null` = the
 *   entry was deleted, stop), so an upload that finished elsewhere is followed, never repeated.
 * - Cancellation is not an error and propagates untouched.
 *
 * Every parameter that touches the outside world is injected, so this loop is testable without a
 * network or a clock.
 */
suspend fun driveEntry(
    start: Entry,
    upload: suspend (Entry) -> Ticket,
    fetch: suspend (ticketId: String, waitSeconds: Int?, knownStatus: String?) -> TicketFetch,
    afterReady: suspend (ticketId: String) -> Unit,
    onUpdate: (Entry) -> Boolean,
    onOffline: (Boolean) -> Unit,
    onUnauthorized: () -> Unit,
    reload: (Entry) -> Entry? = { it },
    nowMs: () -> Long = { System.currentTimeMillis() },
    pause: suspend (Long) -> Unit = { delay(it) },
) {
    var current = start
    var failures = 0
    var malformed = 0
    var firstRead = true

    /** Applies [next] and reports whether it was persisted. */
    fun update(next: Entry): Boolean {
        current = next
        return onUpdate(next)
    }

    suspend fun finishIfReady(persisted: Boolean) {
        val ticketId = current.ticketId
        if (!persisted || current.state != EntryState.READY || !current.ticketDeletePending || ticketId == null) return
        try {
            afterReady(ticketId)
        } catch (e: RecognitionException) {
            return // Best effort: the flag stays, and the next refresh tries again.
        }
        update(current.copy(ticketDeletePending = false))
    }

    while (true) {
        current = reload(current) ?: return
        if (current.state != EntryState.PENDING_UPLOAD && current.state != EntryState.PROCESSING) return

        if (current.state == EntryState.PENDING_UPLOAD && current.ticketId != null) {
            // A ticket already exists (an upload that was accepted but not recorded as such): never
            // upload the same recording again, follow the ticket.
            update(current.copy(state = EntryState.PROCESSING, issue = null))
            continue
        }
        if (current.state == EntryState.PENDING_UPLOAD) {
            val ticket = try {
                upload(current)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: UploadSupersededException) {
                continue
            } catch (e: AudioMissingException) {
                update(current.audioLost())
                continue
            } catch (e: RecognitionException) {
                val failure = classifyUploadFailure(e)
                update(current.uploadFailed(failure))
                if (failure == UploadFailure.UNAUTHORIZED) onUnauthorized()
                if (failure.retriesAutomatically) {
                    // Only a real connectivity failure raises the offline banner.
                    onOffline(failure == UploadFailure.OFFLINE)
                    pause(backoffDelayMs(failures))
                    failures++
                    continue
                }
                // Waits for the user: 401 (new key), redirect/403/404/400 (check the URL), or
                // 413/415 (unrecoverable). Retrying in a loop could not succeed.
                return
            }
            failures = 0
            onOffline(false)
            firstRead = false
            finishIfReady(update(current.uploaded(ticket, nowMs())))
            continue
        }

        val ticketId = current.ticketId
        if (ticketId == null) {
            update(current.ticketGone())
            continue
        }
        val outcome = try {
            if (firstRead) fetch(ticketId, null, null)
            else fetch(ticketId, RecognitionApi.LONG_POLL_SECONDS, current.lastStatus)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: RecognitionException.Unauthorized) {
            update(current.authRejected())
            onUnauthorized()
            return
        } catch (e: RecognitionException) {
            if (e is RecognitionException.BadStatus && e.code == 403) {
                update(current.copy(issue = EntryIssue.REJECTED))
                return
            }
            if (e is RecognitionException.Malformed && ++malformed >= MAX_MALFORMED) {
                update(current.copy(issue = EntryIssue.SERVER_ERROR))
                return
            }
            onOffline(e is RecognitionException.Offline || e is RecognitionException.TimedOut)
            pause(backoffDelayMs(failures))
            failures++
            continue
        }
        failures = 0
        malformed = 0
        onOffline(false)
        when (outcome) {
            is TicketFetch.Found -> {
                val before = current.lastStatus
                finishIfReady(update(current.applyServer(outcome.ticket, nowMs())))
                if (!firstRead && current.state == EntryState.PROCESSING && current.lastStatus == before) {
                    pause(UNCHANGED_PAUSE_MS)
                }
            }
            TicketFetch.Gone -> update(current.ticketGone())
            TicketFetch.Expired -> update(current.ticketExpired())
        }
        firstRead = false
    }
}

/**
 * One plain read of a processing entry, for the Journal screen's once-per-appearance refresh. Returns
 * the updated entry, or `null` when the read failed (the entry is simply left as it was).
 *
 * It does NOT delete the server ticket: the caller persists the result first, then calls
 * [settleReadyTicket], so a cancellation in between can never lose a result that was already fetched.
 */
suspend fun refreshOnce(
    entry: Entry,
    fetch: suspend (ticketId: String, waitSeconds: Int?, knownStatus: String?) -> TicketFetch,
    nowMs: () -> Long = { System.currentTimeMillis() },
): Entry? {
    val ticketId = entry.ticketId
    if (entry.state != EntryState.PROCESSING || ticketId == null) return null
    val outcome = try {
        fetch(ticketId, null, null)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (e: RecognitionException) {
        return null
    }
    return when (outcome) {
        is TicketFetch.Found -> entry.applyServer(outcome.ticket, nowMs())
        TicketFetch.Gone -> entry.ticketGone()
        TicketFetch.Expired -> entry.ticketExpired()
    }
}

/**
 * For a READY entry whose server ticket was never deleted (the loop was cancelled right after the
 * result arrived, or the DELETE failed): tries the DELETE and returns the entry with the flag cleared,
 * or `null` when there is nothing to do or the DELETE failed (it stays flagged for next time).
 */
suspend fun settleReadyTicket(entry: Entry, delete: suspend (ticketId: String) -> Unit): Entry? {
    val ticketId = entry.ticketId
    if (entry.state != EntryState.READY || !entry.ticketDeletePending || ticketId == null) return null
    try {
        delete(ticketId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (e: RecognitionException) {
        return null
    }
    return entry.copy(ticketDeletePending = false)
}
