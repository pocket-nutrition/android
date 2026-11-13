// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryStateTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 100 * day

    private val result = RecognitionResult("deux oeufs", "fr", listOf(RecognizedItem("deux oeufs", 2.0, "piece", null, emptyList())))

    private fun pending(id: String = "e1", created: Long = now) = Entry.pendingUpload(id, created, "$id.m4a")
    private fun ticket(status: String, id: String = "t1", res: RecognitionResult? = null, error: String? = null) =
        Ticket(id, status, 1.0, 1.0, 172_800.0 + 1.0, res, error)

    // ---- transitions ----

    @Test
    fun `a new recording is a pending upload that holds its audio and no ticket`() {
        val e = pending()
        assertEquals(EntryState.PENDING_UPLOAD, e.state)
        assertEquals("e1.m4a", e.audioFile)
        assertNull(e.ticketId)
    }

    @Test
    fun `upload success stores the ticket and moves to processing`() {
        val e = pending().uploaded(ticket("queued"), now)
        assertEquals(EntryState.PROCESSING, e.state)
        assertEquals("t1", e.ticketId)
        assertEquals("queued", e.lastStatus)
        assertEquals(172_801_000L, e.serverExpiresAtEpochMs)
        assertNotNull("audio stays until the result arrives", e.audioFile)
    }

    @Test
    fun `server progress updates the status and stays processing`() {
        val e = pending().uploaded(ticket("queued"), now).applyServer(ticket("transcribing"), now)
        assertEquals(EntryState.PROCESSING, e.state)
        assertEquals("transcribing", e.lastStatus)
        // an unknown status is just in progress
        assertEquals(EntryState.PROCESSING, e.applyServer(ticket("summarising"), now).state)
    }

    @Test
    fun `done copies the result, becomes ready and releases the audio`() {
        val e = pending().uploaded(ticket("queued"), now).applyServer(ticket("done", res = result), now + 5)
        assertEquals(EntryState.READY, e.state)
        assertEquals(result, e.result)
        assertEquals(now + 5, e.resultFetchedAtEpochMs)
        assertNull(e.audioFile)
    }

    @Test
    fun `a ready entry ignores any later server answer`() {
        val ready = pending().uploaded(ticket("queued"), now).applyServer(ticket("done", res = result), now)
        assertEquals(ready, ready.applyServer(ticket("failed", error = "x"), now + 1))
        assertEquals(ready, ready.ticketGone())
        assertEquals(ready, ready.ticketExpired())
    }

    @Test
    fun `failed, 404 and 410 with the audio still present need a retry`() {
        val processing = pending().uploaded(ticket("queued"), now)
        val failed = processing.applyServer(ticket("failed", error = "bad audio"), now)
        assertEquals(EntryState.NEEDS_RETRY, failed.state)
        assertEquals(EntryIssue.FAILED, failed.issue)
        assertEquals("bad audio", failed.error)
        assertNotNull(failed.audioFile)
        assertEquals(EntryIssue.GONE, processing.ticketGone().issue)
        assertEquals(EntryState.NEEDS_RETRY, processing.ticketGone().state)
        assertEquals(EntryIssue.EXPIRED, processing.ticketExpired().issue)
        assertEquals(EntryState.NEEDS_RETRY, processing.ticketExpired().state)
    }

    @Test
    fun `the same failures without audio are unrecoverable`() {
        val processing = pending().uploaded(ticket("queued"), now).audioLost()
        assertEquals(EntryState.PROCESSING, processing.state)
        assertEquals(EntryState.UNRECOVERABLE, processing.ticketGone().state)
        assertEquals(EntryState.UNRECOVERABLE, processing.ticketExpired().state)
        assertEquals(EntryState.UNRECOVERABLE, processing.applyServer(ticket("failed"), now).state)
    }

    @Test
    fun `retry creates a new ticket on the same entry`() {
        val needs = pending().uploaded(ticket("queued", id = "old"), now).ticketExpired()
        val again = needs.retryRequested()
        assertEquals(EntryState.PENDING_UPLOAD, again.state)
        assertNull(again.ticketId)
        assertEquals("e1", again.localId)
        assertEquals("e1.m4a", again.audioFile)
        val resent = again.uploaded(ticket("queued", id = "new"), now + 1)
        assertEquals(EntryState.PROCESSING, resent.state)
        assertEquals("new", resent.ticketId)
        assertEquals("e1", resent.localId)
    }

    @Test
    fun `retry only applies to needsRetry entries that still have audio`() {
        val p = pending()
        assertEquals(p, p.retryRequested())
        val dead = pending().uploaded(ticket("queued"), now).audioLost().ticketExpired()
        assertEquals(dead, dead.retryRequested())
    }

    @Test
    fun `losing the audio of a pending or retryable entry makes it unrecoverable`() {
        val lost = pending().audioLost()
        assertEquals(EntryState.UNRECOVERABLE, lost.state)
        assertEquals(EntryIssue.AUDIO_LOST, lost.issue)
        assertNull(lost.audioFile)
        val needs = pending().uploaded(ticket("queued"), now).ticketGone().audioLost()
        assertEquals(EntryState.UNRECOVERABLE, needs.state)
        val ready = pending().uploaded(ticket("queued"), now).applyServer(ticket("done", res = result), now)
        assertEquals(ready, ready.audioLost())
    }

    // ---- upload failure classification ----

    @Test
    fun `upload failures classify as offline, auth, size, media, server or rejected`() {
        assertEquals(UploadFailure.OFFLINE, classifyUploadFailure(RecognitionException.Offline))
        assertEquals(UploadFailure.OFFLINE, classifyUploadFailure(RecognitionException.TimedOut))
        assertEquals(UploadFailure.UNAUTHORIZED, classifyUploadFailure(RecognitionException.Unauthorized))
        assertEquals(UploadFailure.TOO_LARGE, classifyUploadFailure(RecognitionException.TooLarge))
        assertEquals(UploadFailure.UNSUPPORTED, classifyUploadFailure(RecognitionException.UnsupportedMedia))
        assertEquals(UploadFailure.SERVER_ERROR, classifyUploadFailure(RecognitionException.BadStatus(503)))
        assertEquals(UploadFailure.SERVER_ERROR, classifyUploadFailure(RecognitionException.BadStatus(429)))
        assertEquals(UploadFailure.SERVER_ERROR, classifyUploadFailure(RecognitionException.Malformed))
        assertEquals(UploadFailure.REJECTED, classifyUploadFailure(RecognitionException.BadStatus(400)))
        assertEquals(UploadFailure.REJECTED, classifyUploadFailure(RecognitionException.BadStatus(404)))
        assertEquals(UploadFailure.REJECTED, classifyUploadFailure(RecognitionException.BadStatus(403)))
        assertEquals(UploadFailure.REDIRECTED, classifyUploadFailure(RecognitionException.BadStatus(301)))
        assertEquals(UploadFailure.SECURE_CONNECTION, classifyUploadFailure(RecognitionException.SecureConnectionFailed))
    }

    @Test
    fun `every failure that is about the server, URL or key keeps the entry pending with its audio`() {
        listOf(
            UploadFailure.OFFLINE to EntryIssue.OFFLINE,
            UploadFailure.SERVER_ERROR to EntryIssue.SERVER_ERROR,
            UploadFailure.UNAUTHORIZED to EntryIssue.UNAUTHORIZED,
            UploadFailure.REJECTED to EntryIssue.REJECTED,
            UploadFailure.REDIRECTED to EntryIssue.REDIRECTED,
            UploadFailure.SECURE_CONNECTION to EntryIssue.SECURE_CONNECTION,
        ).forEach { (failure, issue) ->
            val e = pending().uploadFailed(failure)
            assertEquals(EntryState.PENDING_UPLOAD, e.state)
            assertEquals(issue, e.issue)
            assertNotNull("$failure must not delete the only recording", e.audioFile)
        }
    }

    @Test
    fun `only 413 and 415 are unrecoverable and even then the audio is kept until retention`() {
        listOf(
            UploadFailure.TOO_LARGE to EntryIssue.TOO_LARGE,
            UploadFailure.UNSUPPORTED to EntryIssue.UNSUPPORTED,
        ).forEach { (failure, issue) ->
            val e = pending().uploadFailed(failure)
            assertEquals(EntryState.UNRECOVERABLE, e.state)
            assertEquals(issue, e.issue)
            assertNotNull(e.audioFile)
        }
    }

    @Test
    fun `only connectivity, 5xx and TLS retry on their own`() {
        assertTrue(UploadFailure.OFFLINE.retriesAutomatically)
        assertTrue(UploadFailure.SERVER_ERROR.retriesAutomatically)
        assertTrue(UploadFailure.SECURE_CONNECTION.retriesAutomatically)
        listOf(UploadFailure.UNAUTHORIZED, UploadFailure.REJECTED, UploadFailure.REDIRECTED, UploadFailure.TOO_LARGE, UploadFailure.UNSUPPORTED)
            .forEach { assertFalse("$it", it.retriesAutomatically) }
    }

    @Test
    fun `a 401 while processing flags the entry without changing its state`() {
        val processing = pending().uploaded(ticket("queued"), now)
        val flagged = processing.authRejected()
        assertEquals(EntryState.PROCESSING, flagged.state)
        assertEquals(EntryIssue.UNAUTHORIZED, flagged.issue)
        val ready = processing.applyServer(ticket("done", res = result), now)
        assertEquals(ready, ready.authRejected())
    }

    // ---- retention ----

    @Test
    fun `audio older than 7 days is deleted once the ticket exists, and the entry degrades`() {
        val old = pending("old", created = now - 7 * day - 1).uploaded(ticket("queued"), now).ticketExpired()
        val fresh = pending("fresh", created = now - 7 * day).uploaded(ticket("queued"), now).ticketExpired()
        val r = applyRetention(listOf(old, fresh), now, existingAudio = setOf("old.m4a", "fresh.m4a"))
        assertEquals(listOf("old.m4a"), r.audioToDelete)
        assertEquals(EntryState.UNRECOVERABLE, r.entries.first { it.localId == "old" }.state)
        assertEquals(EntryState.NEEDS_RETRY, r.entries.first { it.localId == "fresh" }.state)
    }

    @Test
    fun `a recording that never reached the server is never purged by age`() {
        val ancient = pending("never", created = now - 30 * day)
        val r = applyRetention(listOf(ancient), now, setOf("never.m4a"))
        assertEquals(listOf(ancient), r.entries)
        assertTrue(r.audioToDelete.isEmpty())
    }

    @Test
    fun `ready entries are purged 7 days after the result was fetched`() {
        val base = pending("r").uploaded(ticket("queued"), now).applyServer(ticket("done", res = result), now - 7 * day)
        val stale = base.copy(localId = "stale", resultFetchedAtEpochMs = now - 7 * day - 1)
        val kept = base.copy(localId = "kept", resultFetchedAtEpochMs = now - 7 * day)
        val r = applyRetention(listOf(stale, kept), now, emptySet())
        assertEquals(listOf("kept"), r.entries.map { it.localId })
    }

    @Test
    fun `orphan audio is deleted only when older than 7 days, and a missing audio file degrades its entry`() {
        val e = pending("a")
        val r = applyRetention(
            listOf(e), now, existingAudio = setOf("old.m4a", "recent.m4a", "unknown.m4a"),
            audioModifiedMs = mapOf("old.m4a" to now - 7 * day - 1, "recent.m4a" to now - 6 * day),
        )
        assertEquals(listOf("old.m4a"), r.audioToDelete)
        assertEquals(EntryState.UNRECOVERABLE, r.entries.single().state)
    }

    @Test
    fun `an audio folder that could not be listed is unknown, not empty`() {
        val e = pending("a")
        val r = applyRetention(listOf(e), now, existingAudio = null)
        assertEquals(listOf(e), r.entries)
        assertTrue(r.audioToDelete.isEmpty())
    }

    @Test
    fun `retention keeps referenced audio and never touches processing entries without audio problems`() {
        val processing = pending("p").uploaded(ticket("queued"), now)
        val r = applyRetention(listOf(processing), now, setOf("p.m4a"))
        assertEquals(listOf(processing), r.entries)
        assertTrue(r.audioToDelete.isEmpty())
    }

    // ---- badge ----

    @Test
    fun `badge counts ready entries and flags those needing attention`() {
        val ready = pending("1").uploaded(ticket("queued"), now).applyServer(ticket("done", res = result), now)
        val needs = pending("2").uploaded(ticket("queued"), now).ticketExpired()
        val auth = pending("3").uploadFailed(UploadFailure.UNAUTHORIZED)
        val offline = pending("4").uploadFailed(UploadFailure.OFFLINE)
        assertEquals(VoiceBadge(1, false), voiceBadge(listOf(ready, offline)))
        assertEquals(VoiceBadge(1, true), voiceBadge(listOf(ready, needs)))
        assertEquals(VoiceBadge(0, true), voiceBadge(listOf(auth)))
        assertEquals(VoiceBadge(0, false), voiceBadge(emptyList()))
    }

    // ---- persistence: a ready entry reads with no network ----

    @Test
    fun `entries round trip through the file and a ready entry needs nothing else`() {
        val dir = Files.createTempDirectory("entries").toFile()
        val file = File(dir, "recognition_entries.json")
        val ready = pending("r").uploaded(ticket("queued"), now).applyServer(ticket("done", res = result), now)
        val processing = pending("p").uploaded(ticket("transcribing", id = "t2"), now)
        saveEntriesFile(file, listOf(ready, processing))
        val loaded = loadEntriesFile(file).entries
        assertEquals(listOf(ready, processing), loaded)
        assertEquals(result, loaded.first().result)
        assertFalse(File(dir, "${file.name}.tmp").exists())
        assertTrue(file.readText().contains("\"version\""))
    }

    @Test
    fun `a missing or corrupt entries file loads as empty`() {
        val file = File(Files.createTempDirectory("entries").toFile(), "e.json")
        assertTrue("absent is intact and empty", loadEntriesFile(file).intact)
        assertEquals(emptyList<Entry>(), loadEntriesFile(file).entries)
        file.writeText("{nope")
        assertFalse("unreadable is not intact", loadEntriesFile(file).intact)
        assertEquals(emptyList<Entry>(), loadEntriesFile(file).entries)
    }

    @Test
    fun `one undecodable entry does not hide the others and marks the load as not intact`() {
        val file = File(Files.createTempDirectory("entries").toFile(), "e.json")
        val good = pending("good")
        saveEntriesFile(file, listOf(good, pending("bad")))
        val text = file.readText()
        val at = text.lastIndexOf("\"PENDING_UPLOAD\"")
        file.writeText(text.substring(0, at) + "\"FROM_THE_FUTURE\"" + text.substring(at + "\"PENDING_UPLOAD\"".length))
        val loaded = loadEntriesFile(file)
        assertEquals(listOf(good), loaded.entries)
        assertFalse(loaded.intact)
    }

    @Test
    fun `saving reports failure instead of throwing when the disk refuses`() {
        val dir = Files.createTempDirectory("entries").toFile()
        val blocked = File(dir, "missing-subdir/e.json")
        assertFalse(saveEntriesFile(blocked, listOf(pending())))
    }

    // ---- driver ----

    private class Script(
        val uploads: MutableList<() -> Ticket>,
        val fetches: MutableList<() -> TicketFetch> = mutableListOf(),
    ) {
        val fetchCalls = mutableListOf<Pair<Int?, String?>>()
        val pauses = mutableListOf<Long>()
        val updates = mutableListOf<Entry>()
        val offline = mutableListOf<Boolean>()
        var unauthorized = 0
        var persist = true
        var reload: (Entry) -> Entry? = { it }
        val readyTickets = mutableListOf<String>()
    }

    private fun drive(start: Entry, s: Script) = runBlocking {
        driveEntry(
            start = start,
            upload = { s.uploads.removeAt(0)() },
            fetch = { _, wait, known -> s.fetchCalls += wait to known; s.fetches.removeAt(0)() },
            afterReady = { s.readyTickets += it },
            onUpdate = { s.updates += it; s.persist },
            reload = { s.reload(it) },
            onOffline = { s.offline += it },
            onUnauthorized = { s.unauthorized++ },
            nowMs = { now },
            pause = { s.pauses += it },
        )
    }

    @Test
    fun `driver retries an offline upload with back-off then follows the ticket to ready`() {
        val s = Script(
            uploads = mutableListOf({ throw RecognitionException.Offline }, { throw RecognitionException.TimedOut }, { ticket("queued") }),
            fetches = mutableListOf({ TicketFetch.Found(ticket("transcribing")) }, { TicketFetch.Found(ticket("done", res = result)) }),
        )
        drive(pending(), s)
        assertEquals(listOf(3_000L, 6_000L), s.pauses.take(2))
        assertEquals(EntryState.READY, s.updates.last().state)
        // long polls carry the last known status
        assertEquals(listOf(20 to "queued", 20 to "transcribing"), s.fetchCalls)
        assertEquals(listOf("t1"), s.readyTickets)
        assertEquals(true, s.offline.first())
        assertEquals(false, s.offline.last())
    }

    @Test
    fun `driver stops on 401 and keeps the entry pending with the auth issue`() {
        val s = Script(uploads = mutableListOf({ throw RecognitionException.Unauthorized }))
        drive(pending(), s)
        assertEquals(1, s.unauthorized)
        assertEquals(EntryState.PENDING_UPLOAD, s.updates.last().state)
        assertEquals(EntryIssue.UNAUTHORIZED, s.updates.last().issue)
        assertTrue(s.pauses.isEmpty())
    }

    @Test
    fun `driver ends in unrecoverable on 413 without retrying`() {
        val s = Script(uploads = mutableListOf({ throw RecognitionException.TooLarge }))
        drive(pending(), s)
        assertEquals(EntryState.UNRECOVERABLE, s.updates.last().state)
        assertTrue(s.uploads.isEmpty())
    }

    @Test
    fun `driver reads a restored processing entry once plainly then long polls, expired becomes needsRetry`() {
        val start = pending().uploaded(ticket("queued"), now)
        val s = Script(
            uploads = mutableListOf(),
            fetches = mutableListOf({ TicketFetch.Found(ticket("extracting")) }, { TicketFetch.Expired }),
        )
        drive(start, s)
        assertEquals(listOf<Pair<Int?, String?>>(null to null, 20 to "extracting"), s.fetchCalls)
        assertEquals(EntryState.NEEDS_RETRY, s.updates.last().state)
        assertEquals(EntryIssue.EXPIRED, s.updates.last().issue)
    }

    @Test
    fun `driver keeps a processing entry through network errors`() {
        val start = pending().uploaded(ticket("queued"), now)
        val s = Script(
            uploads = mutableListOf(),
            fetches = mutableListOf({ throw RecognitionException.Offline }, { TicketFetch.Found(ticket("done", res = result)) }),
        )
        drive(start, s)
        assertEquals(listOf(3_000L), s.pauses)
        assertEquals(EntryState.READY, s.updates.last().state)
    }

    @Test
    fun `retry of a needsRetry entry uploads the same audio as a new ticket`() {
        val needs = pending().uploaded(ticket("queued", id = "old"), now).ticketExpired().retryRequested()
        val s = Script(
            uploads = mutableListOf({ ticket("queued", id = "new") }),
            fetches = mutableListOf({ TicketFetch.Found(ticket("done", id = "new", res = result)) }),
        )
        drive(needs, s)
        assertEquals(listOf("new"), s.readyTickets)
        assertEquals("e1", s.updates.last().localId)
    }

    @Test
    fun `a missing audio file turns a pending entry unrecoverable`() {
        val s = Script(uploads = mutableListOf({ throw AudioMissingException() }))
        drive(pending(), s)
        assertEquals(EntryState.UNRECOVERABLE, s.updates.last().state)
        assertEquals(EntryIssue.AUDIO_LOST, s.updates.last().issue)
    }

    @Test
    fun `a one-off refresh applies the server answer and swallows network errors`() {
        val processing = pending().uploaded(ticket("queued"), now)
        val expired = runBlocking {
            refreshOnce(processing, { _, _, _ -> TicketFetch.Expired })
        }
        assertEquals(EntryState.NEEDS_RETRY, expired!!.state)
        val failed = runBlocking {
            refreshOnce(processing, { _, _, _ -> throw RecognitionException.Offline })
        }
        assertNull(failed)
        // It never deletes the server ticket itself: the caller persists first, then deletes.
        val ready = runBlocking {
            refreshOnce(processing, { _, _, _ -> TicketFetch.Found(ticket("done", res = result)) })
        }
        assertEquals(EntryState.READY, ready!!.state)
        // entries that are not processing are never read
        assertNull(runBlocking { refreshOnce(pending(), { _, _, _ -> TicketFetch.Expired }) })
    }

    @Test
    fun `driver stops without retrying on a redirect or a 404 and keeps the audio`() {
        listOf(RecognitionException.BadStatus(301), RecognitionException.BadStatus(404)).forEach { failure ->
            val s = Script(uploads = mutableListOf({ throw failure }))
            drive(pending(), s)
            assertEquals(EntryState.PENDING_UPLOAD, s.updates.last().state)
            assertNotNull(s.updates.last().audioFile)
            assertTrue(s.pauses.isEmpty())
            assertEquals(0, s.unauthorized)
        }
    }

    @Test
    fun `a TLS failure backs off but does not raise the offline banner`() {
        val s = Script(
            uploads = mutableListOf({ throw RecognitionException.SecureConnectionFailed }, { ticket("queued") }),
            fetches = mutableListOf({ TicketFetch.Found(ticket("done", res = result)) }),
        )
        drive(pending(), s)
        assertEquals(listOf(3_000L), s.pauses)
        assertFalse(s.offline.contains(true))
        assertEquals(EntryState.READY, s.updates.last().state)
    }

    @Test
    fun `a 401 while polling flags the entry and stops`() {
        val start = pending().uploaded(ticket("queued"), now)
        val s = Script(uploads = mutableListOf(), fetches = mutableListOf({ throw RecognitionException.Unauthorized }))
        drive(start, s)
        assertEquals(1, s.unauthorized)
        assertEquals(EntryIssue.UNAUTHORIZED, s.updates.last().issue)
        assertEquals(EntryState.PROCESSING, s.updates.last().state)
    }

    @Test
    fun `an entry that already has a ticket is followed, never uploaded again`() {
        val odd = pending().copy(ticketId = "t1")
        val s = Script(
            uploads = mutableListOf({ throw AssertionError("must not upload twice") }),
            fetches = mutableListOf({ TicketFetch.Found(ticket("done", res = result)) }),
        )
        drive(odd, s)
        assertEquals(EntryState.READY, s.updates.last().state)
        assertEquals(1, s.uploads.size)
    }

    // ---- second review pass ----

    @Test
    fun `done sets the pending delete flag and the driver clears it after a successful DELETE`() {
        val s = Script(
            uploads = mutableListOf({ ticket("done", res = result) }),
        )
        drive(pending(), s)
        assertTrue(s.updates.any { it.state == EntryState.READY && it.ticketDeletePending })
        assertEquals(listOf("t1"), s.readyTickets)
        assertFalse(s.updates.last().ticketDeletePending)
    }

    @Test
    fun `no server DELETE is sent when the entry could not be persisted`() {
        val s = Script(uploads = mutableListOf({ ticket("done", res = result) }))
        s.persist = false
        drive(pending(), s)
        assertTrue(s.readyTickets.isEmpty())
    }

    @Test
    fun `a failed DELETE leaves the flag for a later settle`() = runBlocking {
        val ready = pending().uploaded(ticket("queued"), now).applyServer(ticket("done", res = result), now)
        assertTrue(ready.ticketDeletePending)
        assertNull(settleReadyTicket(ready) { throw RecognitionException.Offline })
        val settled = settleReadyTicket(ready) { }
        assertFalse(settled!!.ticketDeletePending)
        assertNull(settleReadyTicket(settled) { throw AssertionError("nothing to delete") })
        assertNull(settleReadyTicket(pending()) { throw AssertionError("not ready") })
    }

    @Test
    fun `a 403 while polling stops the loop with a visible issue`() {
        val start = pending().uploaded(ticket("queued"), now)
        val s = Script(uploads = mutableListOf(), fetches = mutableListOf({ throw RecognitionException.BadStatus(403) }))
        drive(start, s)
        assertEquals(EntryIssue.REJECTED, s.updates.last().issue)
        assertTrue(s.pauses.isEmpty())
    }

    @Test
    fun `repeated malformed answers while polling end in a visible error after a few tries`() {
        val start = pending().uploaded(ticket("queued"), now)
        val s = Script(uploads = mutableListOf(), fetches = MutableList(10) { { throw RecognitionException.Malformed } })
        drive(start, s)
        assertEquals(EntryIssue.SERVER_ERROR, s.updates.last().issue)
        assertEquals(5, s.fetchCalls.size)
        assertFalse(s.offline.contains(true))
    }

    @Test
    fun `a superseded upload is followed through the reloaded entry, never repeated`() {
        val s = Script(
            uploads = mutableListOf({ throw UploadSupersededException() }),
            fetches = mutableListOf({ TicketFetch.Found(ticket("done", res = result)) }),
        )
        val uploaded = pending().uploaded(ticket("queued"), now)
        var calls = 0
        s.reload = { cur -> if (calls++ > 0 && cur.state == EntryState.PENDING_UPLOAD) uploaded else cur }
        drive(pending(), s)
        assertEquals(EntryState.READY, s.updates.last().state)
        assertTrue(s.uploads.isEmpty())
    }

    @Test
    fun `a deleted entry stops the driver`() {
        val s = Script(uploads = mutableListOf({ throw AssertionError("must not upload") }))
        s.reload = { null }
        drive(pending(), s)
        assertTrue(s.updates.isEmpty())
    }

    // ---- store: partial loads are written back verbatim ----

    @Test
    fun `undecodable entries survive a later save`() {
        val file = File(Files.createTempDirectory("entries").toFile(), "e.json")
        saveEntriesFile(file, listOf(pending("good"), pending("bad")))
        val text = file.readText()
        val at = text.lastIndexOf("\"PENDING_UPLOAD\"")
        file.writeText(text.substring(0, at) + "\"FROM_THE_FUTURE\"" + text.substring(at + "\"PENDING_UPLOAD\"".length))

        val loaded = loadEntriesFile(file)
        assertFalse(loaded.intact)
        assertEquals(1, loaded.preserved.size)
        assertTrue(saveEntriesFile(file, loaded.entries + pending("new"), loaded.preserved))
        val after = file.readText()
        assertTrue(after.contains("FROM_THE_FUTURE"))
        assertTrue(after.contains("\"new\""))
        assertEquals(2, loadEntriesFile(file).entries.size)
    }

    @Test
    fun `the store refuses to overwrite an unreadable file and writes once it is readable`() {
        val dir = Files.createTempDirectory("entries").toFile()
        val file = File(dir, "recognition_entries.json")
        file.writeText("{garbage")
        val store = EntryStore(file, File(dir, "audio"), now)
        assertFalse(store.upsert(pending("x")))
        assertEquals("{garbage", file.readText())
        // The file heals (a transient read failure): the next write merges and succeeds.
        saveEntriesFile(file, listOf(pending("disk")))
        assertTrue(store.upsert(pending("y")))
        assertEquals(setOf("disk", "x", "y"), loadEntriesFile(file).entries.map { it.localId }.toSet())
    }

    @Test
    fun `store init does not sweep a recent orphan recording`() {
        val dir = Files.createTempDirectory("entries").toFile()
        val audio = File(dir, "audio").apply { mkdirs() }
        File(audio, "lost.m4a").writeBytes(ByteArray(10))
        EntryStore(File(dir, "recognition_entries.json"), audio)
        assertTrue(File(audio, "lost.m4a").exists())
    }

    // ---- in-flight guard (regression: a released upload must be restartable) ----

    @Test
    fun `an upload slot can be taken once, blocks restarts, and is restartable after release`() {
        val g = InFlightUploads()
        assertTrue(g.canRestart("a"))
        assertTrue(g.tryAcquire("a"))
        assertFalse(g.tryAcquire("a"))
        assertFalse(g.canRestart("a"))
        assertTrue("other entries are unaffected", g.canRestart("b"))
        g.release("a")
        assertTrue("after an attempt ends, Send now must work again", g.canRestart("a"))
        assertTrue(g.tryAcquire("a"))
    }

    @Test
    fun `an unsaved ticket is remembered until forgotten`() {
        val g = InFlightUploads()
        val t = ticket("queued")
        g.rememberUnsaved("a", t)
        assertEquals(t, g.unsavedTicket("a"))
        g.forget("a")
        assertNull(g.unsavedTicket("a"))
    }

    // ---- third pass ----

    @Test
    fun `a remembered ticket is honoured only while the entry still has no ticket`() {
        val g = InFlightUploads()
        val t = ticket("queued", id = "old")
        g.rememberUnsaved("a", t)
        assertEquals(t, g.usableUnsaved("a", storedTicketId = null))
        // The entry has a ticket now (persisted, or reset and re-sent): the stale memory is dropped.
        assertNull(g.usableUnsaved("a", storedTicketId = "new"))
        assertNull("and it stays dropped", g.unsavedTicket("a"))
        // After a resend the entry has no ticket again, but nothing stale remains to be re-applied.
        assertNull(g.usableUnsaved("a", storedTicketId = null))
    }

    @Test
    fun `starting a fresh upload attempt forgets any earlier remembered ticket`() {
        val g = InFlightUploads()
        g.rememberUnsaved("a", ticket("queued", id = "stale"))
        assertTrue(g.tryAcquire("a"))
        assertNull(g.unsavedTicket("a"))
    }

    @Test
    fun `removing an entry during the unreadable window does not resurrect it when the file heals`() {
        val dir = Files.createTempDirectory("entries").toFile()
        val file = File(dir, "recognition_entries.json")
        file.writeText("{garbage")
        val store = EntryStore(file, File(dir, "audio"), now)
        assertFalse(store.upsert(pending("x")))
        saveEntriesFile(file, listOf(pending("x"), pending("other")))
        assertTrue(store.remove("x"))
        assertEquals(listOf("other"), loadEntriesFile(file).entries.map { it.localId })
        assertNull(store.find("x"))
    }

    @Test
    fun `a preserved undecodable entry does not disable retention for the others`() {
        val dir = Files.createTempDirectory("entries").toFile()
        val file = File(dir, "recognition_entries.json")
        val audio = File(dir, "audio").apply { mkdirs() }
        val stale = pending("stale").uploaded(ticket("queued"), now).applyServer(ticket("done", res = result), now - 8 * day)
        saveEntriesFile(file, listOf(stale, pending("bad")))
        val text = file.readText()
        val at = text.lastIndexOf("\"PENDING_UPLOAD\"")
        file.writeText(text.substring(0, at) + "\"FROM_THE_FUTURE\"" + text.substring(at + "\"PENDING_UPLOAD\"".length))
        // An old unreferenced file, and the audio of the undecodable entry: neither may be swept.
        File(audio, "orphan.m4a").apply { writeBytes(ByteArray(3)); setLastModified(now - 30 * day) }
        File(audio, "bad.m4a").apply { writeBytes(ByteArray(3)); setLastModified(now - 30 * day) }

        val store = EntryStore(file, audio, now)

        assertNull("the stale ready entry is purged", store.find("stale"))
        val after = file.readText()
        assertTrue("the undecodable entry is written back verbatim", after.contains("FROM_THE_FUTURE"))
        assertTrue(File(audio, "orphan.m4a").exists())
        assertTrue(File(audio, "bad.m4a").exists())
    }
}
