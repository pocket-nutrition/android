// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.pocketnutrition.recognition.ResponseTooLargeException
import org.pocketnutrition.recognition.readCapped

/** Why a vault call failed. Messages are fixed words: no key, body or username is ever put in one. */
sealed class VaultException(message: String) : Exception(message) {
    /** No connectivity, a timeout, or any transport failure: safe to retry. */
    object Offline : VaultException("offline")

    /** The server answered something that is not a vault answer. */
    object Malformed : VaultException("malformed response")

    /** A meaningful non-success answer. */
    class Outcome(val outcome: VaultHttpOutcome) : VaultException("vault outcome")
}

/** The calls onboarding and sync need. An interface so the account model and the sync run against fakes. */
interface VaultServer {
    suspend fun serverInfo(): VaultServerInfo
    /** The account's KDF settings. Unknown usernames get stable fake ones, so this never fails for them. */
    suspend fun prelogin(username: String): VaultKdf
    suspend fun register(request: VaultRegisterRequest): VaultSession
    suspend fun login(request: VaultLoginRequest): VaultSession
    /** Unlinks this device: the server revokes the key used for the call. */
    suspend fun logout(apiKey: String)
    /** Erases the account and everything stored for it. Needs the login hash as proof of the passphrase. */
    suspend fun deleteAccount(apiKey: String, authHash: String)
    /** The account's stored keyring and its version. */
    suspend fun keyring(apiKey: String): VaultKeyring
    /**
     * Replaces a forgotten passphrase from this linked device. 409 `keyring_conflict` when
     * `base_version` is stale; `two_factor_required` / `invalid_two_factor` as at login.
     */
    suspend fun resetPassphrase(apiKey: String, request: VaultResetPassphraseRequest): VaultPassphraseChanged
    /** Records changed after [since], in order. A page may be partial: see `hasMore`. */
    suspend fun sync(apiKey: String, since: Long): VaultSyncPage
    /** Writes one record the owner encrypted. `revision` is the current revision + 1. */
    suspend fun putRecord(apiKey: String, id: String, write: VaultRecordWrite)
    /** Deletes one record (a tombstone). [revision] is the current revision + 1. */
    suspend fun deleteRecord(apiKey: String, id: String, revision: Int)
}

/**
 * Client for one vault server, on `HttpURLConnection` like the rest of the app (no HTTP library). The
 * device key is passed per call, so the client holds nothing secret. Redirects are refused (one would
 * carry the login hash or the device key elsewhere), caches are off, and answers are capped in size.
 */
class VaultClient(private val baseUrl: String) : VaultServer {

    override suspend fun serverInfo(): VaultServerInfo =
        decode(VaultServerInfo.serializer(), call(listOf("server-info"), "GET"))

    override suspend fun prelogin(username: String): VaultKdf =
        decode(VaultKdf.serializer(), call(listOf("accounts", "prelogin"), "POST", body = stringBody("username" to username)))

    override suspend fun register(request: VaultRegisterRequest): VaultSession = decode(
        VaultSession.serializer(),
        call(listOf("accounts"), "POST", body = vaultJson.encodeToString(VaultRegisterRequest.serializer(), request)),
    )

    override suspend fun login(request: VaultLoginRequest): VaultSession = decode(
        VaultSession.serializer(),
        call(listOf("login"), "POST", body = vaultJson.encodeToString(VaultLoginRequest.serializer(), request), isLogin = true),
    )

    override suspend fun logout(apiKey: String) {
        call(listOf("logout"), "POST", key = apiKey)
    }

    override suspend fun deleteAccount(apiKey: String, authHash: String) {
        call(listOf("account"), "DELETE", body = stringBody("auth_hash" to authHash), key = apiKey)
    }

    override suspend fun keyring(apiKey: String): VaultKeyring =
        decode(VaultKeyring.serializer(), call(listOf("keyring"), "GET", key = apiKey))

    override suspend fun resetPassphrase(apiKey: String, request: VaultResetPassphraseRequest): VaultPassphraseChanged = decode(
        VaultPassphraseChanged.serializer(),
        call(
            listOf("account", "passphrase", "reset"), "POST",
            body = vaultJson.encodeToString(VaultResetPassphraseRequest.serializer(), request), key = apiKey,
        ),
    )

    override suspend fun sync(apiKey: String, since: Long): VaultSyncPage =
        decode(VaultSyncPage.serializer(), call(listOf("sync"), "GET", key = apiKey, query = listOf("since" to since.toString())))

    override suspend fun putRecord(apiKey: String, id: String, write: VaultRecordWrite) {
        call(listOf("records", id), "PUT", body = vaultJson.encodeToString(VaultRecordWrite.serializer(), write), key = apiKey)
    }

    override suspend fun deleteRecord(apiKey: String, id: String, revision: Int) {
        call(listOf("records", id), "DELETE", key = apiKey, query = listOf("revision" to revision.toString()))
    }

    // ── Plumbing ─────────────────────────────────────────────────────────────

    private fun stringBody(vararg fields: Pair<String, String>): String =
        vaultJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), mapOf(*fields))

    private fun <T> decode(serializer: KSerializer<T>, body: String): T =
        try {
            vaultJson.decodeFromString(serializer, body)
        } catch (e: IllegalArgumentException) {
            throw VaultException.Malformed
        }

    /** One request. Returns the body of a 2xx answer; anything else is a [VaultException]. */
    private suspend fun call(
        segments: List<String>,
        method: String,
        body: String? = null,
        key: String? = null,
        isLogin: Boolean = false,
        query: List<Pair<String, String>> = emptyList(),
    ): String = runInterruptible(Dispatchers.IO) {
        val connection = try {
            URL(vaultUrl(baseUrl, segments, query)).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw VaultException.Offline
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("Accept", "application/json")
            if (key != null) connection.setRequestProperty("Authorization", "Bearer $key")
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                // Not streaming mode: bodies are small, and a streamed request cannot have its 401 body
                // read back on every HttpURLConnection implementation (it would lose `two_factor_required`).
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            if (connection.contentLengthLong > MAX_RESPONSE_BYTES) throw VaultException.Malformed
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { readCapped(it, MAX_RESPONSE_BYTES).toString(Charsets.UTF_8) }.orEmpty()
            val outcome = classifyVaultHttp(status, errorCode(text), connection.getHeaderField("Retry-After"), isLogin)
            if (outcome != VaultHttpOutcome.Success) throw VaultException.Outcome(outcome)
            text
        } catch (e: ResponseTooLargeException) {
            throw VaultException.Malformed
        } catch (e: IOException) {
            throw VaultException.Offline
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        /** `detail.error` of a FastAPI error body, when there is one. */
        internal fun errorCode(body: String): String? {
            val root = runCatching { vaultJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            val detail = root["detail"] as? JsonObject ?: return null
            return (detail["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        }
    }
}
