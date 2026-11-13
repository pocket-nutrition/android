// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/** Whether an entry still needs the network: being sent, or its ticket being followed. */
val Entry.needsNetwork: Boolean get() = state == EntryState.PENDING_UPLOAD || state == EntryState.PROCESSING

/**
 * Keeps every capture (dictation or meal photo) moving while the app is in the foreground, whichever
 * screen is showing: one [drive] loop per entry that still needs the network.
 *
 * It replaces the per-entry `LaunchedEffect`s the voice screen used to hold, so captures progress from
 * the journal and the meal builder alike. The safety rules stay where they were: [drive] is
 * [driveStoredCapture], whose upload records the ticket inside a non-cancellable block and goes through
 * [Uploads] (one upload per entry, process-wide). So cancelling a loop here ([stop], or [sendNow]
 * restarting one) never loses a ticket nor sends a recording twice.
 *
 * Main-thread only, like the composition that calls it.
 */
class CaptureCoordinator(
    private val scope: CoroutineScope,
    private val entries: () -> List<Entry>,
    private val drive: suspend (localId: String, onOffline: (Boolean) -> Unit) -> Unit,
    private val canRestart: (localId: String) -> Boolean = { Uploads.shared.canRestart(it) },
) {
    /** The last network attempt failed for lack of connectivity. */
    var isOffline by mutableStateOf(false)
        private set

    private val jobs = mutableMapOf<String, Job>()
    private var active = false

    /** The app came to the foreground: drive every entry that needs it (a plain read first, when resuming). */
    fun start() {
        active = true
        entries().filter { it.needsNetwork }.forEach { launchIfIdle(it.localId) }
    }

    /** The app left the foreground: loops end cleanly and resume on the next [start]. */
    fun stop() {
        active = false
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    /** A new entry was just persisted. */
    fun track(localId: String) {
        if (active) launchIfIdle(localId)
    }

    /** "Send now" / "Send again": restart this entry's loop, unless an upload of it is on the wire. */
    fun sendNow(localId: String) {
        if (!active || !canRestart(localId)) return
        jobs.remove(localId)?.cancel()
        launch(localId)
    }

    /** Stops driving an entry that is being deleted. */
    fun forget(localId: String) {
        jobs.remove(localId)?.cancel()
    }

    fun isDriving(localId: String): Boolean = jobs[localId]?.isActive == true

    private fun launchIfIdle(localId: String) {
        if (isDriving(localId)) return
        launch(localId)
    }

    private fun launch(localId: String) {
        jobs[localId] = scope.launch {
            try {
                drive(localId) { isOffline = it }
            } finally {
                // Only this loop's own slot: a restart may already have replaced it.
                val self = currentCoroutineContext()[Job]
                if (jobs[localId] === self) jobs.remove(localId)
            }
        }
    }
}
