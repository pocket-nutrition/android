// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The HTTP layer against a local JDK server (plain http on loopback: the https rule is the account
 * model's, tested there). Checks the wire format and how FastAPI's answers are read.
 */
class VaultClientTest {

    private class Seen(val method: String, val path: String, val query: String?, val auth: String?, val body: String)

    private val seen = mutableListOf<Seen>()
    private var reply: (HttpExchange) -> Unit = { respond(it, 200, "{}") }
    private val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            seen += Seen(
                exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.rawQuery,
                exchange.requestHeaders.getFirst("Authorization"), exchange.requestBody.readBytes().decodeToString(),
            )
            reply(exchange)
            exchange.close()
        }
        start()
    }
    private val client = VaultClient("http://127.0.0.1:${http.address.port}/prefix")

    @After
    fun stop() = http.stop(0)

    private fun respond(exchange: HttpExchange, status: Int, body: String, headers: Map<String, String> = emptyMap()) {
        headers.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
    }

    private inline fun <reified T : Throwable> expect(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("expected ${T::class.simpleName}")
        error("unreachable")
    }

    @Test
    fun `a record write is sent as the server expects it`() = runBlocking {
        reply = { respond(it, 200, "{\"id\":\"m\"}") }
        client.putRecord("pnv_k", "meal-1", VaultRecordWrite(type = "meal", revision = 2, keyId = "user", ciphertext = "QUJD"))
        val request = seen.single()
        assertEquals("PUT", request.method)
        assertEquals("/prefix/v1/records/meal-1", request.path)
        assertEquals("Bearer pnv_k", request.auth)
        assertEquals("{\"type\":\"meal\",\"revision\":2,\"key_id\":\"user\",\"ciphertext\":\"QUJD\"}", request.body)
    }

    @Test
    fun `sync and delete pass their numbers in the query`() = runBlocking {
        reply = { respond(it, 200, "{\"records\":[{\"id\":\"a\",\"type\":\"meal\",\"scope_id\":null,\"revision\":1,\"key_id\":\"user\",\"ciphertext\":\"\",\"deleted\":true,\"seq\":7,\"updated_at\":\"2026-11-06T10:00:00Z\",\"future\":1}],\"cursor\":7,\"has_more\":false}") }
        val page = client.sync("k", 5)
        assertEquals("since=5", seen.last().query)
        assertEquals(7L, page.cursor)
        assertTrue(page.records.single().deleted)
        reply = { respond(it, 200, "{}") }
        client.deleteRecord("k", "a", 3)
        assertEquals("DELETE", seen.last().method)
        assertEquals("revision=3", seen.last().query)
    }

    @Test
    fun `login sends no code fields unless asked, and reads the session`() = runBlocking {
        reply = { respond(it, 200, "{\"account_id\":\"acc\",\"api_key\":\"pnv_x\",\"key_id\":\"kid\",\"keyring\":{\"blob\":null,\"version\":1}}") }
        val session = client.login(VaultLoginRequest.of("alice", "aGFzaA==", "Pixel", null))
        assertEquals("{\"username\":\"alice\",\"auth_hash\":\"aGFzaA==\",\"device_label\":\"Pixel\"}", seen.last().body)
        assertEquals("pnv_x", session.apiKey)
        assertNull(session.keyring.blob)
    }

    @Test
    fun `FastAPI error details become outcomes`() = runBlocking {
        reply = { respond(it, 409, "{\"detail\":{\"error\":\"revision_conflict\",\"current_revision\":4}}") }
        assertEquals(VaultHttpOutcome.RevisionConflict, expect<VaultException.Outcome> { runBlocking { client.putRecord("k", "a", VaultRecordWrite(type = "t", revision = 1, keyId = "user", ciphertext = "QQ==")) } }.outcome)
        reply = { respond(it, 401, "{\"detail\":{\"error\":\"two_factor_required\"}}") }
        assertEquals(VaultHttpOutcome.TwoFactorRequired, expect<VaultException.Outcome> { runBlocking { client.login(VaultLoginRequest.of("a", "b", "c", null)) } }.outcome)
        reply = { respond(it, 401, "{\"detail\":\"Invalid or missing API key\"}") }
        assertEquals(VaultHttpOutcome.Unauthorized, expect<VaultException.Outcome> { runBlocking { client.sync("k", 0) } }.outcome)
        reply = { respond(it, 429, "{\"detail\":{\"error\":\"account_locked\"}}", mapOf("Retry-After" to "120")) }
        assertEquals(VaultHttpOutcome.TooManyAttempts(120), expect<VaultException.Outcome> { runBlocking { client.login(VaultLoginRequest.of("a", "b", "c", null)) } }.outcome)
    }

    @Test
    fun `a redirect is never followed`() = runBlocking {
        reply = { respond(it, 307, "", mapOf("Location" to "https://elsewhere.example/v1/login")) }
        assertEquals(VaultHttpOutcome.Other(307), expect<VaultException.Outcome> { runBlocking { client.serverInfo() } }.outcome)
        assertEquals(1, seen.size)
    }

    @Test
    fun `something that is not a vault answer is malformed`() = runBlocking {
        reply = { respond(it, 200, "<html>hello</html>") }
        expect<VaultException.Malformed> { runBlocking { client.serverInfo() } }
        reply = { respond(it, 200, "x".repeat(VaultClient.MAX_RESPONSE_BYTES + 1)) }
        expect<VaultException.Malformed> { runBlocking { client.serverInfo() } }
        Unit
    }

    @Test
    fun `an unreachable server is offline`() = runBlocking {
        http.stop(0)
        expect<VaultException.Offline> { runBlocking { client.serverInfo() } }
        Unit
    }
}
