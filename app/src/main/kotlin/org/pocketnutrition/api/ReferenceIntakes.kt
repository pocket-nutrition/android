// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.pocketnutrition.core.ReferenceRow

/** `GET /reference-intakes`: the full EFSA table, the same for every caller. */
@Serializable
data class ReferenceIntakesResponse(val source: String, val sourceUrl: String, val values: List<ReferenceRow>)

/**
 * The client for the reference table. A separate object from [PocketNutritionApi] because the request is
 * deliberately different: it takes **no parameter at all**, so the profile (age, weight, pregnancy) can
 * never leak into it — the app picks its rows locally.
 */
object ReferenceIntakesClient {
    private const val TIMEOUT_MS = 15_000
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): ReferenceIntakesResponse = json.decodeFromString(ReferenceIntakesResponse.serializer(), text)

    suspend fun fetch(): ReferenceIntakesResponse = withContext(Dispatchers.IO) {
        val connection = (URL("${ApiSettings.baseUrl}/reference-intakes").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            // 503 means the server has not loaded the table yet: a failure, so the cache is kept.
            if (status !in 200..299) throw ApiException.BadStatus(status)
            decode(connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } catch (e: UnknownHostException) {
            throw ApiException.Offline
        } catch (e: SocketTimeoutException) {
            throw ApiException.TimedOut
        } catch (e: SerializationException) {
            throw ApiException.Malformed
        } catch (e: IllegalArgumentException) {
            throw ApiException.Malformed
        } catch (e: IOException) {
            throw ApiException.Offline
        } finally {
            connection.disconnect()
        }
    }
}

@Serializable
data class CachedReferenceIntakes(
    @SerialName("fetched_at_ms") val fetchedAtEpochMs: Long,
    val response: ReferenceIntakesResponse,
)

/**
 * The last successful `GET /reference-intakes`, on disk with its fetch time (spec 05 §1). Refetched when
 * older than 24 h; on failure the cache stays in use. There is **never a bundled copy**: with no cache at all
 * (first launch offline) [current] is null and the targets UI says it needs a connection.
 */
class ReferenceIntakesCache(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fetch: suspend () -> ReferenceIntakesResponse = ReferenceIntakesClient::fetch,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Null until [ensureLoaded] has read the disk copy (off the main thread: the table is ~1 MB). */
    var current: CachedReferenceIntakes? by mutableStateOf(null)
        private set

    private var loaded = false

    /** The last refresh failed (only meaningful while [current] is null or stale). */
    var lastRefreshFailed: Boolean by mutableStateOf(false)
        private set

    private fun load(): CachedReferenceIntakes? =
        if (!file.exists()) null else runCatching { json.decodeFromString(CachedReferenceIntakes.serializer(), file.readText()) }.getOrNull()

    /** Reads the disk copy once. */
    suspend fun ensureLoaded() {
        if (loaded) return
        val fromDisk = withContext(Dispatchers.IO) { load() }
        if (!loaded) {
            loaded = true
            if (current == null) current = fromDisk
        }
    }

    fun isStale(): Boolean = current?.let { clock() - it.fetchedAtEpochMs >= MAX_AGE_MS } ?: true

    /** Fetches when there is no cache or it is 24 h old. Never throws for a network or server failure. */
    suspend fun refreshIfStale() {
        ensureLoaded()
        if (!isStale()) return
        try {
            val response = fetch()
            val cached = CachedReferenceIntakes(clock(), response)
            withContext(Dispatchers.IO) { persist(cached) }
            current = cached
            lastRefreshFailed = false
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: ApiException) {
            lastRefreshFailed = true
        }
    }

    private fun persist(cached: CachedReferenceIntakes) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(CachedReferenceIntakes.serializer(), cached))
        temporary.renameTo(file)
    }

    companion object {
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
