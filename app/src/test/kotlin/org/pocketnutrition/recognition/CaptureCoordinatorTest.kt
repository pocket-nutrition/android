// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureCoordinatorTest {

    private fun entry(id: String, state: EntryState) =
        Entry(localId = id, createdAtEpochMs = 0, audioFile = "$id.m4a", state = state)

    /** Records each drive start; each run waits on its own gate until released. */
    private class FakeDrive {
        val started = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        suspend fun run(id: String, onOffline: (Boolean) -> Unit) {
            started += id
            val gate = CompletableDeferred<Unit>().also { gates[id] = it }
            try {
                onOffline(true)
                gate.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled += id
                throw e
            }
        }
    }

    private fun withCoordinator(
        entries: List<Entry>,
        canRestart: (String) -> Boolean = { true },
        block: suspend (CaptureCoordinator, FakeDrive) -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val drive = FakeDrive()
        val coordinator = CaptureCoordinator(scope, { entries }, drive::run, canRestart)
        try {
            block(coordinator, drive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `start drives only the entries that need the network, once each`() =
        withCoordinator(
            listOf(
                entry("a", EntryState.PENDING_UPLOAD),
                entry("b", EntryState.PROCESSING),
                entry("c", EntryState.READY),
                entry("d", EntryState.NEEDS_RETRY),
            ),
        ) { coordinator, drive ->
            coordinator.start()
            coordinator.start()
            yield()
            assertEquals(listOf("a", "b"), drive.started)
            assertTrue(coordinator.isOffline)
        }

    @Test
    fun `stop cancels every loop and nothing new starts until the next start`() =
        withCoordinator(listOf(entry("a", EntryState.PROCESSING))) { coordinator, drive ->
            coordinator.start()
            yield()
            coordinator.stop()
            yield()
            assertEquals(listOf("a"), drive.cancelled)
            coordinator.track("a")
            coordinator.sendNow("a")
            yield()
            assertEquals(listOf("a"), drive.started)
            assertFalse(coordinator.isDriving("a"))
        }

    @Test
    fun `send now restarts the loop, but not while an upload is on the wire`() {
        var busy = true
        withCoordinator(listOf(entry("a", EntryState.PENDING_UPLOAD)), canRestart = { !busy }) { coordinator, drive ->
            coordinator.start()
            yield()
            coordinator.sendNow("a")
            yield()
            assertEquals(listOf("a"), drive.started)
            busy = false
            coordinator.sendNow("a")
            yield()
            assertEquals(listOf("a", "a"), drive.started)
            assertEquals(listOf("a"), drive.cancelled)
        }
    }

    @Test
    fun `a finished loop frees its slot so a new entry or a restart can run`() =
        withCoordinator(emptyList()) { coordinator, drive ->
            coordinator.start()
            coordinator.track("n")
            yield()
            assertTrue(coordinator.isDriving("n"))
            drive.gates.getValue("n").complete(Unit)
            yield()
            yield()
            assertFalse(coordinator.isDriving("n"))
            coordinator.track("n")
            yield()
            assertEquals(listOf("n", "n"), drive.started)
        }
}
