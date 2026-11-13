// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.pocketnutrition.BuildConfig

/**
 * Settings → "Daily targets (preview)" (spec 05 §7). On by default in debug builds, off in release builds.
 * When it is off nothing of the feature is visible and nothing is fetched.
 */
object DailyTargetsFlag {
    private const val PREFS_NAME = "daily_targets_flag"
    private const val KEY_ENABLED = "enabled"

    private var prefs: SharedPreferences? = null

    var enabled: Boolean by mutableStateOf(BuildConfig.DEBUG)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val opened = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = opened
        enabled = opened.getBoolean(KEY_ENABLED, BuildConfig.DEBUG)
    }

    fun set(value: Boolean) {
        enabled = value
        prefs?.edit { putBoolean(KEY_ENABLED, value) }
    }
}
