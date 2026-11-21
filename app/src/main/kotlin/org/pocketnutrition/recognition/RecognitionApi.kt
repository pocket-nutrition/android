// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.ProtocolException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.pocketnutrition.vault.VaultDropGrant

// Client for the voice-recognition server. This endpoint family is NOT part of contracts 2.0.0, so it is
// hand-written against the shared client spec. Same transport choice as PocketNutritionApi
// (java.net.HttpURLConnection, no HTTP library), with two differences that justify a separate file:
// requests are cancellable, and a long poll needs a read timeout longer than the server's hold time.

/** Failures a caller can act on differently; the UI maps each to localised copy. */
sealed class RecognitionException(message: String) : Exception(message) {
    object Unauthorized : RecognitionException("unauthorized")
    object TooLarge : RecognitionException("too large")
    object UnsupportedMedia : RecognitionException("unsupported media")
    object Offline : RecognitionException("offline")
    object TimedOut : RecognitionException("timed out")
    object Malformed : RecognitionException("malformed response")
    object SecureConnectionFailed : RecognitionException("secure connection failed")

    /**
     * The server refused the vault drop token: already used (409), or no longer valid (401 on a server
     * where no shared key is in use). Not the person's mistake: a new drop is minted and the upload retried.
     */
    object DropRefused : RecognitionException("drop refused")
    class BadStatus(val code: Int) : RecognitionException("status $code")
}

/** What a read of a ticket found. 404 and 410 are outcomes, not errors. */
sealed interface TicketFetch {
    data class Found(val ticket: Ticket) : TicketFetch
    data object Gone : TicketFetch
    data object Expired : TicketFetch
}

object RecognitionApi {

    /** Server-side hold time of one long poll, seconds (the contract allows up to 25). */
    const val LONG_POLL_SECONDS = 20

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val PLAIN_READ_TIMEOUT_MS = 15_000
    private const val UPLOAD_READ_TIMEOUT_MS = 60_000
    /** The read timeout must exceed the server's hold time. */
    private const val LONG_POLL_SLACK_MS = 10_000
    const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024

    /** The server keeps this field name for voice clips and meal photos alike. */
    private const val AUDIO_FIELD = "audio"

    /** Server data is untrusted: a body past this size is refused rather than buffered. */
    const val MAX_RESPONSE_BYTES = 1024 * 1024

    private class Response(val status: Int, val body: String)

    /**
     * `POST /captures`, always async. Returns the new ticket (HTTP 202) with its secret. [media] says whether
     * the bytes are a voice clip or a meal photo (see [mediaFor]). The [grant] tells the worker where to
     * seal the result: it always goes to the vault, never back to this server.
     */
    suspend fun upload(
        config: RecognitionConfig,
        audio: ByteArray,
        grant: VaultDropGrant,
        media: CaptureMedia = VOICE_MEDIA,
    ): Ticket {
        if (audio.size > MAX_UPLOAD_BYTES) throw RecognitionException.TooLarge
        val boundary = newMultipartBoundary()
        val body = buildMultipartBody(
            boundary = boundary,
            fields = captureFields(grant),
            file = MultipartFile(AUDIO_FIELD, media.fileName, media.contentType, audio),
        )
        val response = call(
            url = "${config.baseUrl}/captures",
            method = "POST",
            credential = config.apiKey,
            readTimeoutMs = UPLOAD_READ_TIMEOUT_MS,
            contentType = multipartContentType(boundary),
            body = body,
        )
        return when {
            response.status in 200..299 -> decode(response.body)
            response.status == 409 -> throw RecognitionException.DropRefused
            // Without a shared key the only credential was the drop token: the vault no longer vouches for it.
            response.status == 401 && config.apiKey == null -> throw RecognitionException.DropRefused
            response.status == 401 -> throw RecognitionException.Unauthorized
            response.status == 413 -> throw RecognitionException.TooLarge
            response.status == 415 -> throw RecognitionException.UnsupportedMedia
            else -> throw RecognitionException.BadStatus(response.status)
        }
    }

    /**
     * `GET /captures/{id}`. A plain read when [waitSeconds] is null; otherwise a long poll that returns
     * when the status differs from [knownStatus], the ticket is terminal, or the wait elapses.
     */
    suspend fun fetch(
        config: RecognitionConfig,
        id: String,
        waitSeconds: Int? = null,
        knownStatus: String? = null,
        ticketSecret: String? = null,
    ): TicketFetch {
        val query = buildString {
            if (waitSeconds != null) append("?wait=$waitSeconds")
            if (waitSeconds != null && knownStatus != null) append("&known_status=${encode(knownStatus)}")
        }
        val readTimeout =
            if (waitSeconds != null) waitSeconds * 1000 + LONG_POLL_SLACK_MS else PLAIN_READ_TIMEOUT_MS
        val response = call(
            url = "${config.baseUrl}/captures/${encode(id)}$query",
            method = "GET",
            credential = recognitionCredential(ticketSecret, config.apiKey),
            readTimeoutMs = readTimeout,
        )
        return when {
            response.status in 200..299 -> TicketFetch.Found(decode(response.body))
            response.status == 404 -> TicketFetch.Gone
            response.status == 410 -> TicketFetch.Expired
            response.status == 401 -> throw RecognitionException.Unauthorized
            else -> throw RecognitionException.BadStatus(response.status)
        }
    }

    /** `DELETE /captures/{id}`. 204, 404 and 410 all mean "nothing left to delete". */
    suspend fun delete(config: RecognitionConfig, id: String, ticketSecret: String? = null) {
        val response = call(
            url = "${config.baseUrl}/captures/${encode(id)}",
            method = "DELETE",
            credential = recognitionCredential(ticketSecret, config.apiKey),
            readTimeoutMs = PLAIN_READ_TIMEOUT_MS,
        )
        when {
            response.status in 200..299 || response.status == 404 || response.status == 410 -> Unit
            response.status == 401 -> throw RecognitionException.Unauthorized
            else -> throw RecognitionException.BadStatus(response.status)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun decode(body: String): Ticket =
        decodeTicketOrNull(body) ?: throw RecognitionException.Malformed

    /**
     * One blocking HTTP exchange on its own thread, wired so coroutine cancellation closes the socket.
     * `withContext(Dispatchers.IO)` would not do: a blocked read ignores cancellation until it returns,
     * which for a long poll means up to 30 s of a dead request keeping its thread.
     *
     * Redirects are not followed: the key is sent in a header, and it must not be replayed to a
     * location the user never configured.
     */
    private suspend fun call(
        url: String,
        method: String,
        credential: String?,
        readTimeoutMs: Int,
        contentType: String? = null,
        body: ByteArray? = null,
    ): Response = suspendCancellableCoroutine { continuation ->
        val connection = AtomicReference<HttpURLConnection?>(null)
        // disconnect() does network work and can throw; never on the thread that cancelled us.
        continuation.invokeOnCancellation {
            thread(isDaemon = true, name = "recognition-cancel") { runCatching { connection.get()?.disconnect() } }
        }
        thread(isDaemon = true, name = "recognition-http") {
            try {
                val opened = URL(url).openConnection() as HttpURLConnection
                connection.set(opened)
                if (!continuation.isActive) {
                    opened.disconnect()
                    return@thread
                }
                opened.requestMethod = method
                opened.connectTimeout = CONNECT_TIMEOUT_MS
                opened.readTimeout = readTimeoutMs
                opened.instanceFollowRedirects = false
                opened.useCaches = false
                opened.setRequestProperty("Cache-Control", "no-store")
                if (credential != null) opened.setRequestProperty("Authorization", "Bearer $credential")
                opened.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    opened.doOutput = true
                    opened.setFixedLengthStreamingMode(body.size)
                    opened.setRequestProperty("Content-Type", requireNotNull(contentType))
                    opened.outputStream.use { it.write(body) }
                }
                val status = opened.responseCode
                if (opened.contentLengthLong > MAX_RESPONSE_BYTES) throw ResponseTooLargeException()
                val stream = if (status in 200..299) opened.inputStream else opened.errorStream
                val text = stream?.use { readCapped(it, MAX_RESPONSE_BYTES).toString(Charsets.UTF_8) } ?: ""
                continuation.resume(Response(status, text))
            } catch (e: UnknownHostException) {
                continuation.resumeWithException(RecognitionException.Offline)
            } catch (e: SocketTimeoutException) {
                continuation.resumeWithException(RecognitionException.TimedOut)
            } catch (e: SSLException) {
                continuation.resumeWithException(RecognitionException.SecureConnectionFailed)
            } catch (e: ProtocolException) {
                continuation.resumeWithException(RecognitionException.SecureConnectionFailed)
            } catch (e: IOException) {
                continuation.resumeWithException(RecognitionException.Offline)
            } catch (e: ResponseTooLargeException) {
                continuation.resumeWithException(RecognitionException.Malformed)
            } catch (e: Throwable) {
                // Anything else (a header the platform rejects, OOM, a bug) must still resume the
                // continuation: an uncaught throw here would kill the process and never answer the caller.
                continuation.resumeWithException(RecognitionException.Malformed)
            } finally {
                connection.get()?.disconnect()
            }
        }
    }
}

/** The form fields of one upload: async mode plus the four that route the result into the vault. */
internal fun captureFields(grant: VaultDropGrant): List<Pair<String, String>> = listOf(
    "mode" to "async",
    "vault_url" to grant.vaultUrl,
    "vault_token" to grant.token,
    "vault_record_id" to grant.recordId,
    "inbox_public_key" to grant.inboxPublicKey,
)
