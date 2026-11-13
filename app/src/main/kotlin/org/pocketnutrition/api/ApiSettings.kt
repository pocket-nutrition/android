// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.api

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.net.URI
import java.net.URISyntaxException

/**
 * Lets a contributor point the app at their own backend instead of the official API.
 *
 * Persisted in plain [SharedPreferences] rather than `EncryptedSharedPreferences`: the override is a
 * plain URL, not a secret. An absent or blank value means "use the official server" — that is what
 * lets [SettingsScreen]'s "back to official" action work by clearing the key rather than by having to
 * know [PocketNutritionApi.DEFAULT_BASE_URL]'s literal value.
 *
 * [init] must run once, from [org.pocketnutrition.MainActivity.onCreate], before anything reads
 * [baseUrl] — the same explicit-construction pattern `JournalStore` already uses for its file, rather
 * than an Application subclass or a DI framework this app has no other need for.
 */
object ApiSettings {

    private const val PREFS_NAME = "api_settings"
    private const val KEY_BASE_URL = "custom_base_url"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * The raw text the user saved, or `null` if no override is set — regardless of whether that text
     * still parses as a valid URL. [SettingsScreen] prefills its text field from this, not from
     * [baseUrl]: showing back exactly what was typed, even if it has since become invalid, is what
     * lets a typo be corrected instead of silently discarded.
     */
    val customRawValue: String?
        get() = prefs?.getString(KEY_BASE_URL, null)

    /**
     * What [PocketNutritionApi] actually sends every request to: the saved override when one is set
     * and still valid, otherwise [PocketNutritionApi.DEFAULT_BASE_URL]. Read fresh on every call
     * rather than cached — a change made from [SettingsScreen] must take effect on the very next
     * request, not after the app is relaunched.
     */
    val baseUrl: String
        get() = customRawValue?.takeIf { isValidServerUrl(it) } ?: PocketNutritionApi.DEFAULT_BASE_URL

    /**
     * Validates [raw] and, if it parses, saves it as the override. Returns `false` without touching
     * storage otherwise. A blank string is treated as "reset", since that is what an emptied text
     * field means.
     */
    fun setCustomBaseUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            reset()
            return true
        }
        if (!isValidServerUrl(trimmed)) return false
        prefs?.edit { putString(KEY_BASE_URL, trimmed) }
        return true
    }

    /** Restores the official server. */
    fun reset() {
        prefs?.edit { remove(KEY_BASE_URL) }
    }
}

/**
 * A URL only counts as a usable override if it has both a scheme and a host — a bare host like
 * `192.168.1.5` with no scheme would otherwise silently become a relative path with no server at all,
 * which is worse than rejecting it up front.
 *
 * A free function rather than a method on [ApiSettings], so it is unit-testable without a
 * [SharedPreferences] instance — the same split [ApiSettings.init] exists to keep clean.
 */
fun isValidServerUrl(raw: String): Boolean {
    val uri = try {
        URI(raw)
    } catch (e: URISyntaxException) {
        return false
    }
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && uri.host != null
}
