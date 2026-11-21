// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.recognition.RecognitionResult

/**
 * The phone's side of a capture delivered through the vault: mint the drop, then collect what the worker
 * sealed into it (pocket-nutrition-vault `docs/PROTOCOL.md`, "Drops"). Ports iOS `VaultInboxTests`.
 */
class VaultInboxTest {

    private val server = FakeVaultServer()
    private val keys = testCrypto.createAccount("correct horse battery staple").keys
    private val session: VaultStoredSession = runBlocking {
        val reply = server.register(VaultRegisterRequest("alice", testCrypto.newKdf(), "aGFzaA==", "{}", "test"))
        VaultStoredSession.of("https://v.example", "alice", reply.accountId, reply.apiKey, reply.keyId, testCrypto.newKdf(), keys)
    }
    private val inbox = VaultInbox(session, server, testCrypto)

    private fun payload(recordId: String, status: String = "done", v: Int = 1) =
        """{"v":$v,"record_id":"$recordId","ticket_id":"t1","status":"$status",""" +
            """"result":{"transcript":"riz","language":"fr","items":[{"raw_text":"riz","quantity":150,"unit":"g","candidates":[]}]},""" +
            """"error":null,"completed_at":1.0}"""

    private fun deposit(grant: VaultDropGrant, plaintext: String) =
        server.depositAsDropHolder(grant.token, keys.inbox.publicKey, plaintext)

    @Test
    fun `a drop is minted for a new capture record, sealed to this phone's inbox key`() = runBlocking {
        val grant = inbox.prepareDrop()
        val (_, request) = server.drops.getValue(grant.token)
        assertEquals(grant.recordId, request.recordId)
        assertEquals(VaultProtocol.CAPTURE_RECORD_TYPE, request.type)
        assertEquals(VaultProtocol.INBOX_KEY_ID, request.keyId)
        assertEquals(3 * 24 * 3600, request.ttlSeconds)
        assertEquals(2, request.maxWrites)
        assertEquals("https://v.example", grant.vaultUrl)
        assertEquals(keys.inbox.publicKey.toBase64(), grant.inboxPublicKey)
        assertTrue(VaultProtocol.isValidRecordId(grant.recordId))
    }

    @Test
    fun `nothing there yet, then the sealed result, which is re-saved under the user key`() = runBlocking {
        val grant = inbox.prepareDrop()
        assertEquals(VaultCollected.NotThere, inbox.collect(grant.recordId))

        deposit(grant, payload(grant.recordId))
        val got = inbox.collect(grant.recordId)
        assertTrue(got is VaultCollected.Result)
        assertEquals("riz", (got as VaultCollected.Result).result.transcript)
        assertEquals(150.0, got.result.items.single().quantity)

        // Re-saved: an ordinary record of the account now, readable with the user key and its AAD.
        val stored = server.accounts.getValue("alice").records.getValue(grant.recordId)
        assertEquals(VaultProtocol.USER_KEY_ID, stored.keyId)
        assertEquals(2, stored.revision)
        assertTrue(checkNotNull(server.plaintextOf(session.accountId, keys.userKey, grant.recordId)).contains("\"riz\""))
        // And collecting it again opens that copy.
        assertEquals(got, inbox.collect(grant.recordId))
    }

    @Test
    fun `a payload naming another record is refused`() = runBlocking {
        val grant = inbox.prepareDrop()
        deposit(grant, payload("someone-else"))
        assertEquals(VaultCollected.Unreadable, inbox.collect(grant.recordId))
    }

    @Test
    fun `a failed capture, an unknown version or garbage is not a result`() = runBlocking {
        listOf(payload("x", status = "failed"), payload("x", v = 2), "not json").forEach { body ->
            val grant = inbox.prepareDrop()
            deposit(grant, body.replace("\"x\"", "\"${grant.recordId}\""))
            assertEquals(body, VaultCollected.Unreadable, inbox.collect(grant.recordId))
        }
    }

    @Test
    fun `a drop sealed to another key does not open`() = runBlocking {
        val grant = inbox.prepareDrop()
        server.depositAsDropHolder(grant.token, testCrypto.newInboxKeyPair().publicKey, payload(grant.recordId))
        assertEquals(VaultCollected.Unreadable, inbox.collect(grant.recordId))
    }

    @Test
    fun `decoding checks the version, the record and that the status is final`() {
        val ok = decodeVaultDropPayload(payload("r1").toByteArray(), "r1")
        assertEquals(RecognitionResult("riz", "fr", checkNotNull(ok.result).items), ok.result)
        listOf(payload("r2"), payload("r1", v = 2), payload("r1", status = "extracting"), "{}").forEach {
            assertThrows(it, VaultDropPayloadException::class.java) { decodeVaultDropPayload(it.toByteArray(), "r1") }
        }
    }

    @Test
    fun `an offline vault is an error the caller retries, not a missing record`() {
        server.offline = true
        assertThrows(VaultException.Offline::class.java) { runBlocking { inbox.collect("r1") } }
    }
}
