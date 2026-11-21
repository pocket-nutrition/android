// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import java.net.URI
import java.net.URISyntaxException

/** The official recognition server, used until another one is saved. It needs no key. */
const val DEFAULT_RECOGNITION_URL = "https://recognition.pocketnutrition.org"

/**
 * A usable server address plus, only for servers that still use a shared key, that key. On the official
 * server there is none: a capture is authenticated by its vault drop token, and its ticket by the secret
 * the upload returned.
 */
data class RecognitionConfig(val baseUrl: String, val apiKey: String?) {
    /** Never prints the key. */
    override fun toString(): String = "RecognitionConfig(baseUrl=$baseUrl, apiKey=${if (apiKey == null) "none" else "…"})"
}

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

/**
 * The saved server, or the official one when none is saved (or the saved text no longer validates). The
 * key is attached only when it was saved together with a valid URL: [RecognitionSettings.save] never lets
 * a key outlive a change of host, so a key without its URL is never sent anywhere.
 */
fun recognitionConfig(url: String?, apiKey: String?): RecognitionConfig {
    val saved = url?.takeIf { isValidRecognitionUrl(it) }
    val key = apiKey?.trim()?.takeIf { saved != null && isValidApiKey(it) }
    return RecognitionConfig(normalizeRecognitionUrl(saved ?: DEFAULT_RECOGNITION_URL), key)
}
