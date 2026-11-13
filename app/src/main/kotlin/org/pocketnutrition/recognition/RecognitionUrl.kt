// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import java.net.URI
import java.net.URISyntaxException

/** A usable server address plus the key to talk to it. */
data class RecognitionConfig(val baseUrl: String, val apiKey: String)

/**
 * https only, with a host. `http` is rejected on purpose: the API key travels in a header, and neither
 * the platform's cleartext policy nor this check is being relaxed for it. Credentials embedded in the
 * URL are rejected too — the key has its own field and its own storage.
 */
fun isValidRecognitionUrl(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
    val uri = try {
        URI(trimmed)
    } catch (e: URISyntaxException) {
        return false
    }
    return uri.scheme?.lowercase() == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null
}

/** Trims and drops trailing slashes so `"$base/captures"` never contains `//`. */
fun normalizeRecognitionUrl(raw: String): String = raw.trim().trimEnd('/')

/**
 * A key travels in an HTTP header, so only visible ASCII (0x21-0x7E) is allowed: anything else (a pasted
 * newline, a non-ASCII character) would make the platform reject the header.
 */
fun isValidApiKey(raw: String): Boolean {
    val key = raw.trim()
    return key.isNotEmpty() && key.length <= 512 && key.all { it.code in 0x21..0x7E }
}

/** scheme + host + port, case-insensitive: the thing a bearer token must never be replayed across. */
fun originOf(raw: String): String? {
    val uri = try { URI(raw.trim()) } catch (e: URISyntaxException) { return null }
    val host = uri.host?.lowercase() ?: return null
    val port = if (uri.port == -1) 443 else uri.port
    return "${uri.scheme?.lowercase()}://$host:$port"
}

/** The host to show the user at save time ("the key will be sent to ..."). */
fun hostOf(raw: String): String? = try { URI(raw.trim()).host } catch (e: URISyntaxException) { null }

/** `null` unless both halves are usable — the feature is "configured" only then. */
fun recognitionConfigOrNull(url: String?, apiKey: String?): RecognitionConfig? {
    if (url == null || !isValidRecognitionUrl(url)) return null
    val key = apiKey?.trim()
    if (key == null || !isValidApiKey(key)) return null
    return RecognitionConfig(normalizeRecognitionUrl(url), key)
}
