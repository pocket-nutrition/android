// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.api

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import java.util.Locale

/**
 * The app's active display language: French or English, either following the device's own
 * language or pinned by an explicit choice in the "Langue"/"Language" section of `AboutScreen`.
 *
 * WHY THIS IS BACKED BY `mutableStateOf`, UNLIKE [ApiSettings]
 * [ApiSettings] is read imperatively, at request time, and nothing needs to know when it changes.
 * A language setting is read inside nearly every screen's composition, so a plain `SharedPreferences`
 * read on every access would leave already-composed screens showing stale text after a switch —
 * Compose has no way to know a bare preferences read changed. Backing the live value with
 * `mutableStateOf` fixes that for free: Compose's snapshot system tracks the read the moment any
 * composable calls [effective], and recomposes that composable automatically the next time
 * [setOverride] writes a new value — no `CompositionLocal`, no `ViewModel`, needed for a value this
 * small and this global.
 *
 * [init] must run once, from `MainActivity.onCreate`, before anything reads [effective] — the same
 * explicit-construction pattern already established by `ApiSettings.init`.
 */
object LanguageSettings {

    private const val PREFS_NAME = "language_settings"
    private const val KEY_OVERRIDE = "language_override"

    private var prefs: SharedPreferences? = null

    /** `null` means "follow the device" — [effective] applies the fallback rule below. */
    private val overrideState = mutableStateOf<String?>(null)

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        overrideState.value = p.getString(KEY_OVERRIDE, null)
    }

    /** An explicit `"fr"`/`"en"` override, or `null` when following the device. */
    val override: String? get() = overrideState.value

    /**
     * What the app actually displays right now: the override when one is set, otherwise French only
     * when the device's own language is French — English for every other device language, which is
     * the "English unless the device is French" rule this object exists to implement.
     */
    val effective: String
        get() = overrideState.value ?: if (Locale.getDefault().language == "fr") "fr" else "en"

    /** Sets an explicit override, or clears it back to "follow the device" when [lang] is `null`. */
    fun setOverride(lang: String?) {
        overrideState.value = lang
        prefs?.edit {
            if (lang != null) putString(KEY_OVERRIDE, lang) else remove(KEY_OVERRIDE)
        }
    }
}
