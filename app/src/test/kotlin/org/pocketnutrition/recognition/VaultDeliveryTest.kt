// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.vault.VaultCollected
import org.pocketnutrition.vault.VaultDropGrant
import org.pocketnutrition.vault.VaultException

/**
 * Voice and photo results always travel through the vault (website#78): the upload carries a drop, the
 * ticket says `delivered` with no result, and the driver collects the result from the vault. Ports the
 * cases of iOS `RecognitionVaultDeliveryTests`.
 */
class VaultDeliveryTest {

    private val now = 1_000_000L
    private val lenient = Json { ignoreUnknownKeys = true }
    private val result = RecognitionResult("riz", "fr", listOf(RecognizedItem("riz", 150.0, "g", null, emptyList())))
    private val grant = VaultDropGrant("https://vault.example", "pnv_drop_1", "rec-1", "cHVibGlj")

    private fun pending() = Entry.pendingUpload("e1", now, "e1.m4a")
    private fun ticket(status: String, delivery: String? = null, res: RecognitionResult? = null, secret: String? = null) =
        Ticket("t1", status, 1.0, 1.0, 172_801.0, res, null, delivery, secret)

    private class Script(
        val uploads: MutableList<() -> Ticket>,
        val fetches: MutableList<() -> TicketFetch> = mutableListOf(),
        val collects: MutableList<() -> VaultCollected> = mutableListOf(),
    ) {
        val pauses = mutableListOf<Long>()
        val updates = mutableListOf<Entry>()
        val readyTickets = mutableListOf<String>()
        val collected = mutableListOf<String?>()
        val offline = mutableListOf<Boolean>()
    }

    private fun drive(start: Entry, s: Script) = runBlocking {
        driveEntry(
            start = start,
            upload = { s.uploads.removeAt(0)() },
            fetch = { _, _, _ -> s.fetches.removeAt(0)() },
            afterReady = { s.readyTickets += it },
            collect = { s.collected += it.vaultRecordId; s.collects.removeAt(0)() },
            onUpdate = { s.updates += it; true },
            onOffline = { s.offline += it },
            onUnauthorized = {},
            nowMs = { now },
            pause = { s.pauses += it },
        )
    }

    /** A pending entry whose upload went through with the drop for `rec-1`. */
    private fun processing() = pending().uploaded(ticket("queued", secret = "s3cret"), now, grant.recordId)

    // ---- wire ----

    @Test
    fun `every upload carries the four vault fields`() {
        assertEquals(
            listOf(
                "mode" to "async",
                "vault_url" to "https://vault.example",
                "vault_token" to "pnv_drop_1",
                "vault_record_id" to "rec-1",
                "inbox_public_key" to "cHVibGlj",
            ),
            captureFields(grant),
        )
    }

    @Test
    fun `a ticket decodes its secret and delivery, and only a resultless delivered done counts as delivered`() {
        val created = decodeTicket(
            """{"id":"t1","status":"queued","created_at":1,"updated_at":1,"expires_at":2,"delivery":"none","secret":"abc_DEF-1"}""",
        )
        assertEquals("abc_DEF-1", created.secret)
        val delivered = decodeTicket("""{"id":"t1","status":"done","created_at":1,"updated_at":1,"expires_at":2,"delivery":"delivered"}""")
        assertTrue(isDeliveredToVault(delivered))
        assertNull(delivered.secret)
        assertFalse(isDeliveredToVault(delivered.copy(status = "extracting")))
        assertFalse(isDeliveredToVault(delivered.copy(delivery = "none")))
        // A secret that could not go back in a header is dropped, not stored.
        assertNull(decodeTicket("""{"id":"t1","status":"queued","created_at":1,"updated_at":1,"expires_at":2,"secret":"a b"}""").secret)
    }

    @Test
    fun `the ticket secret is preferred over the shared key, and nothing is sent without either`() {
        assertEquals("s", recognitionCredential("s", "k"))
        assertEquals("k", recognitionCredential(null, "k"))
        assertEquals("k", recognitionCredential("", "k"))
        assertNull(recognitionCredential(null, null))
    }

    @Test
    fun `a refused drop token is retried on its own`() {
        assertEquals(UploadFailure.SERVER_ERROR, classifyUploadFailure(RecognitionException.DropRefused))
        assertTrue(UploadFailure.SERVER_ERROR.retriesAutomatically)
    }

    // ---- entry ----

    @Test
    fun `an upload records the ticket secret and the vault record, a resend forgets both`() {
        val entry = processing()
        assertEquals("s3cret", entry.ticketSecret)
        assertEquals("rec-1", entry.vaultRecordId)
        // Later reads carry no secret: the stored one stays.
        val polled = entry.applyServer(ticket("extracting"), now)
        assertEquals("s3cret", polled.ticketSecret)
        val failed = polled.applyServer(ticket("failed"), now)
        val resent = failed.retryRequested()
        assertEquals(EntryState.PENDING_UPLOAD, resent.state)
        assertNull(resent.ticketSecret)
        assertNull(resent.vaultRecordId)
    }

    @Test
    fun `done with no result is a failure that keeps the recording, not an empty meal`() {
        val ended = processing().applyServer(ticket("done", delivery = "delivered"), now)
        assertEquals(EntryState.NEEDS_RETRY, ended.state)
        assertEquals(EntryIssue.FAILED, ended.issue)
        assertEquals("e1.m4a", ended.audioFile)
    }

    @Test
    fun `entries saved before the vault fields existed still decode`() {
        val old = Json.encodeToJsonElement(processing()).jsonObject
            .filterKeys { it != "ticketSecret" && it != "vaultRecordId" }
        val decoded = lenient.decodeFromJsonElement(Entry.serializer(), JsonObject(old))
        assertNull(decoded.ticketSecret)
        assertNull(decoded.vaultRecordId)
        assertEquals(EntryState.PROCESSING, decoded.state)
        // And an entry written by a newer version with an unknown field is still read.
        val newer = JsonObject(old + ("someFutureField" to JsonPrimitive(1)))
        assertEquals("t1", lenient.decodeFromJsonElement(Entry.serializer(), newer).ticketId)
    }

    @Test
    fun `a capture waiting for a vault shows in the journal badge`() {
        assertTrue(voiceBadge(listOf(pending().vaultRequired())).needsAttention)
    }

    // ---- driver ----

    @Test
    fun `without a vault nothing is sent, the entry is flagged and the loop stops`() {
        val s = Script(uploads = mutableListOf({ throw VaultRequiredException() }))
        drive(pending(), s)
        assertEquals(EntryState.PENDING_UPLOAD, s.updates.last().state)
        assertEquals(EntryIssue.VAULT_REQUIRED, s.updates.last().issue)
        assertTrue(s.pauses.isEmpty())
    }

    @Test
    fun `a delivered result is collected from the vault before the entry turns ready`() {
        val s = Script(
            uploads = mutableListOf({ ticket("queued", secret = "s3cret") }),
            fetches = mutableListOf({ TicketFetch.Found(ticket("done", delivery = "delivered")) }),
            collects = mutableListOf({ VaultCollected.Result(result) }),
        )
        // The glue records the grant's record id with the ticket; here the entry already carries it.
        drive(pending().copy(vaultRecordId = "rec-1"), s)
        val last = s.updates.last()
        assertEquals(EntryState.READY, last.state)
        assertEquals(result, last.result)
        assertEquals(listOf<String?>("rec-1"), s.collected)
        // The server ticket goes only after the result is stored here.
        assertEquals(listOf("t1"), s.readyTickets)
        assertFalse(last.ticketDeletePending)
    }

    @Test
    fun `a record not in the vault yet is asked for again after a pause`() {
        val s = Script(
            uploads = mutableListOf(),
            fetches = mutableListOf(
                { TicketFetch.Found(ticket("done", delivery = "delivered")) },
                { TicketFetch.Found(ticket("done", delivery = "delivered")) },
                { TicketFetch.Found(ticket("done", delivery = "delivered")) },
            ),
            collects = mutableListOf({ VaultCollected.NotThere }, { throw VaultException.Offline }, { VaultCollected.Result(result) }),
        )
        drive(processing(), s)
        assertEquals(EntryState.READY, s.updates.last().state)
        assertEquals(listOf(3_000L, 6_000L), s.pauses)
        assertTrue(s.offline.contains(true))
    }

    @Test
    fun `a record that never lands ends as a failure after a bounded number of tries`() {
        val s = Script(
            uploads = mutableListOf(),
            fetches = MutableList(MAX_VAULT_MISSES) { { TicketFetch.Found(ticket("done", delivery = "delivered")) } },
            collects = MutableList(MAX_VAULT_MISSES) { { VaultCollected.NotThere } },
        )
        drive(processing(), s)
        assertEquals(EntryState.NEEDS_RETRY, s.updates.last().state)
        assertEquals(EntryIssue.FAILED, s.updates.last().issue)
        assertTrue(s.readyTickets.isEmpty())
    }

    @Test
    fun `an unreadable delivery ends as a failure the person can resend`() {
        val s = Script(
            uploads = mutableListOf(),
            fetches = mutableListOf({ TicketFetch.Found(ticket("done", delivery = "delivered")) }),
            collects = mutableListOf({ VaultCollected.Unreadable }),
        )
        drive(processing(), s)
        assertEquals(EntryState.NEEDS_RETRY, s.updates.last().state)
        assertTrue(s.readyTickets.isEmpty())
    }

    @Test
    fun `the journal refresh leaves a delivered capture to the driver`() = runBlocking {
        val refreshed = refreshOnce(processing(), fetch = { _, _, _ -> TicketFetch.Found(ticket("done", delivery = "delivered")) })
        assertNull(refreshed)
    }
}
