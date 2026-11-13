// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import kotlinx.serialization.Serializable

// The local model of one voice capture. The phone keeps the recording and the result; the server is
// only a relay whose TTL the client must not depend on. An entry is created BEFORE any network call,
// so nothing recorded is lost to a crash, an offline phone or an expired ticket.
//
// All transitions are pure functions on [Entry] so the whole state machine is unit-tested without a
// device. Side effects (deleting audio files, DELETE on the server) belong to the callers.

enum class EntryState {
    /** Audio saved locally, no ticket yet: offline, upload failed, or app killed mid-upload. */
    PENDING_UPLOAD,

    /** A ticket exists and the server status is not terminal. */
    PROCESSING,

    /** The server said done and the result is copied here; works offline and survives server expiry. */
    READY,

    /** 410 / 404 / failed, and the audio is still on the phone: can be re-sent as a new ticket. */
    NEEDS_RETRY,

    /** As [NEEDS_RETRY] but the audio is gone (or retrying cannot help): only deleting is offered. */
    UNRECOVERABLE,
}

/** Why an entry is in a non-happy state; the UI maps each to localised copy. */
enum class EntryIssue {
    OFFLINE, SERVER_ERROR, UNAUTHORIZED, TOO_LARGE, UNSUPPORTED, REJECTED, REDIRECTED, SECURE_CONNECTION,
    EXPIRED, GONE, FAILED, AUDIO_LOST,
}

enum class UploadFailure { OFFLINE, SERVER_ERROR, UNAUTHORIZED, TOO_LARGE, UNSUPPORTED, REJECTED, REDIRECTED, SECURE_CONNECTION }

/** Worth retrying on its own with back-off. The others wait for the user (new key, new URL, resend). */
val UploadFailure.retriesAutomatically: Boolean
    get() = this == UploadFailure.OFFLINE || this == UploadFailure.SERVER_ERROR || this == UploadFailure.SECURE_CONNECTION

/**
 * Raised by an upload step when another upload of the same entry was in flight and has finished (or the
 * entry is gone): the driver reloads the entry and decides again instead of uploading a second time.
 */
class UploadSupersededException : Exception("upload superseded")

/** Raised by an upload step when the entry's audio file is no longer on the phone. */
class AudioMissingException : Exception("audio file missing")

/**
 * Only 413/415 are about the recording itself and cannot succeed on a resend. Everything else —
 * connectivity, 5xx, 408/429, 401, 403/404/400, a redirect, a TLS failure — says something about the
 * server, the URL or the key, so the recording is kept and the entry stays pending.
 */
fun classifyUploadFailure(e: RecognitionException): UploadFailure = when (e) {
    RecognitionException.Offline, RecognitionException.TimedOut -> UploadFailure.OFFLINE
    RecognitionException.Unauthorized -> UploadFailure.UNAUTHORIZED
    RecognitionException.TooLarge -> UploadFailure.TOO_LARGE
    RecognitionException.UnsupportedMedia -> UploadFailure.UNSUPPORTED
    RecognitionException.Malformed -> UploadFailure.SERVER_ERROR
    RecognitionException.SecureConnectionFailed -> UploadFailure.SECURE_CONNECTION
    is RecognitionException.BadStatus -> when {
        e.code in 300..399 -> UploadFailure.REDIRECTED
        e.code >= 500 || e.code == 408 || e.code == 429 -> UploadFailure.SERVER_ERROR
        else -> UploadFailure.REJECTED
    }
}

@Serializable
data class Entry(
    val localId: String,
    val createdAtEpochMs: Long,
    /** File name inside the private audio folder; null once the audio has been released or lost. */
    val audioFile: String? = null,
    val ticketId: String? = null,
    val serverExpiresAtEpochMs: Long? = null,
    val state: EntryState,
    /** Last server status seen, for the "Transcription… / Analyse…" label. */
    val lastStatus: String? = null,
    val issue: EntryIssue? = null,
    /** A server-written message (failed tickets); shown as received. */
    val error: String? = null,
    val result: RecognitionResult? = null,
    val resultFetchedAtEpochMs: Long? = null,
    /** READY, but the best-effort server DELETE has not succeeded yet; retried on the next refresh. */
    val ticketDeletePending: Boolean = false,
    /** A meal photo, not a voice clip. Kept here because the file (whose extension says it) is released. */
    val isPhoto: Boolean = false,
) {
    /** Takes what the server now says. A ready entry never touches the network again, so it ignores this. */
    fun applyServer(ticket: Ticket, nowMs: Long): Entry {
        if (state == EntryState.READY) return this
        val withTicket = copy(ticketId = ticket.id, serverExpiresAtEpochMs = secondsToEpochMs(ticket.expiresAt))
        return when (ticket.status) {
            "done" -> withTicket.copy(
                state = EntryState.READY,
                lastStatus = ticket.status,
                result = ticket.result ?: RecognitionResult(),
                resultFetchedAtEpochMs = nowMs,
                ticketDeletePending = true,
                audioFile = null,
                issue = null,
                error = null,
            )
            "failed" -> withTicket.dead(EntryIssue.FAILED, ticket.error)
            else -> withTicket.copy(
                state = EntryState.PROCESSING,
                lastStatus = ticket.status,
                issue = null,
                error = null,
            )
        }
    }

    /** The upload returned a ticket. */
    fun uploaded(ticket: Ticket, nowMs: Long): Entry =
        copy(issue = null, error = null).applyServer(ticket, nowMs)

    fun ticketGone(): Entry = if (state == EntryState.READY) this else dead(EntryIssue.GONE, null)

    fun ticketExpired(): Entry = if (state == EntryState.READY) this else dead(EntryIssue.EXPIRED, null)

    fun uploadFailed(failure: UploadFailure): Entry = when (failure) {
        UploadFailure.OFFLINE -> copy(state = EntryState.PENDING_UPLOAD, issue = EntryIssue.OFFLINE)
        UploadFailure.SERVER_ERROR -> copy(state = EntryState.PENDING_UPLOAD, issue = EntryIssue.SERVER_ERROR)
        UploadFailure.UNAUTHORIZED -> copy(state = EntryState.PENDING_UPLOAD, issue = EntryIssue.UNAUTHORIZED)
        UploadFailure.REDIRECTED -> copy(state = EntryState.PENDING_UPLOAD, issue = EntryIssue.REDIRECTED)
        UploadFailure.SECURE_CONNECTION -> copy(state = EntryState.PENDING_UPLOAD, issue = EntryIssue.SECURE_CONNECTION)
        UploadFailure.REJECTED -> copy(state = EntryState.PENDING_UPLOAD, issue = EntryIssue.REJECTED)
        UploadFailure.TOO_LARGE -> unrecoverable(EntryIssue.TOO_LARGE)
        UploadFailure.UNSUPPORTED -> unrecoverable(EntryIssue.UNSUPPORTED)
    }

    /** A 401 while uploading or polling: flagged on the entry so the UI can offer Settings. */
    fun authRejected(): Entry =
        if (state == EntryState.PENDING_UPLOAD || state == EntryState.PROCESSING) copy(issue = EntryIssue.UNAUTHORIZED) else this

    /** "Renvoyer": back to pending so the same audio is uploaded as a NEW ticket on this same entry. */
    fun retryRequested(): Entry =
        if (state == EntryState.NEEDS_RETRY && audioFile != null) {
            copy(
                state = EntryState.PENDING_UPLOAD,
                ticketId = null,
                serverExpiresAtEpochMs = null,
                lastStatus = null,
                issue = null,
                error = null,
            )
        } else {
            this
        }

    /** The audio is not available any more. Nothing can be re-sent from a pending or retryable entry. */
    fun audioLost(): Entry = when (state) {
        EntryState.PENDING_UPLOAD, EntryState.NEEDS_RETRY ->
            copy(state = EntryState.UNRECOVERABLE, audioFile = null, issue = EntryIssue.AUDIO_LOST)
        else -> copy(audioFile = null)
    }

    private fun dead(issue: EntryIssue, message: String?): Entry = copy(
        state = if (audioFile != null) EntryState.NEEDS_RETRY else EntryState.UNRECOVERABLE,
        issue = issue,
        error = message,
    )

    /** Content-specific rejections (413/415). The audio is kept until retention so the user can still decide. */
    private fun unrecoverable(issue: EntryIssue): Entry =
        copy(state = EntryState.UNRECOVERABLE, issue = issue)

    companion object {
        fun pendingUpload(localId: String, createdAtEpochMs: Long, audioFile: String): Entry = Entry(
            localId = localId,
            createdAtEpochMs = createdAtEpochMs,
            audioFile = audioFile,
            state = EntryState.PENDING_UPLOAD,
            isPhoto = isPhotoFile(audioFile),
        )
    }
}

private fun secondsToEpochMs(seconds: Double): Long = Math.round(seconds * 1000)

/** Journal-menu badge, from local state only. */
data class VoiceBadge(val readyCount: Int, val needsAttention: Boolean)

fun voiceBadge(entries: List<Entry>): VoiceBadge = VoiceBadge(
    readyCount = entries.count { it.state == EntryState.READY },
    needsAttention = entries.any {
        it.state == EntryState.NEEDS_RETRY ||
            (it.state == EntryState.PENDING_UPLOAD && it.issue == EntryIssue.UNAUTHORIZED)
    },
)

/** Audio and un-imported results are kept at most this long. */
const val RETENTION_MS = 7L * 24 * 3_600_000

class Retention(val entries: List<Entry>, val audioToDelete: List<String>)

/**
 * Applied on load. Drops ready entries 7 days after their result was fetched; deletes audio that is
 * older than 7 days, missing from disk, or no longer wanted (degrading the entry accordingly); and
 * deletes audio files no entry refers to AND older than 7 days. [existingAudio] is the file names found in the audio folder,
 * or `null` when the folder could not be listed: "unknown" is never treated as "empty", so nothing is
 * degraded or swept.
 *
 * A recording that has never reached the server (`PENDING_UPLOAD`) is exempt from the 7-day age purge:
 * deleting it would destroy the only copy of something the user said. The privacy copy says the 7 days
 * run from sending.
 */
fun applyRetention(
    entries: List<Entry>,
    nowMs: Long,
    existingAudio: Set<String>?,
    /** Last-modified time per audio file, for the orphan rule. A file with no known time is never an orphan to delete. */
    audioModifiedMs: Map<String, Long> = emptyMap(),
): Retention {
    val toDelete = mutableSetOf<String>()
    val kept = mutableListOf<Entry>()
    for (entry in entries) {
        val audio = entry.audioFile
        if (entry.state == EntryState.READY &&
            nowMs - (entry.resultFetchedAtEpochMs ?: entry.createdAtEpochMs) > RETENTION_MS
        ) {
            if (audio != null && existingAudio != null && audio in existingAudio) toDelete += audio
            continue
        }
        val tooOld = entry.state != EntryState.PENDING_UPLOAD && nowMs - entry.createdAtEpochMs > RETENTION_MS
        val missing = existingAudio != null && audio !in existingAudio
        if (audio != null && (entry.state == EntryState.READY || tooOld || missing)) {
            if (existingAudio != null && audio in existingAudio) toDelete += audio
            kept += entry.audioLost()
        } else {
            kept += entry
        }
    }
    val referenced = kept.mapNotNull { it.audioFile }.toSet()
    if (existingAudio != null) {
        // An orphan is deleted only when older than the retention period: a clip whose entry failed to
        // save must stay recoverable.
        toDelete += (existingAudio - referenced).filter { name ->
            audioModifiedMs[name]?.let { nowMs - it > RETENTION_MS } == true
        }
    }
    return Retention(kept, toDelete.toList().sorted())
}
