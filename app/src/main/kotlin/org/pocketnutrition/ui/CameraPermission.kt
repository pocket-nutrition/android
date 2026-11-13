// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.pocketnutrition.core.CameraAccess
import org.pocketnutrition.core.decideCameraAccess
import org.pocketnutrition.core.design.PnColor

private const val PREFS_NAME = "camera_permission"
private const val KEY_HAS_REQUESTED = "has_requested"

/**
 * Walks the `ContextWrapper` chain to the underlying `Activity`. Needed because
 * `ActivityCompat.shouldShowRequestPermissionRationale` requires one, and `LocalContext.current` inside
 * `setContent {}` is normally the `Activity` itself here — but the wrapper walk is the safe general form
 * rather than an unchecked cast.
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Whether a permission dialog has ever been shown before, persisted because
 * `shouldShowRequestPermissionRationale()` alone cannot distinguish "never asked" from "permanently
 * denied" — see the doc comment on [decideCameraAccess]. This is the app's first use of
 * `SharedPreferences`; the journal itself persists to a raw file (`model/Journal.kt`), and a single
 * boolean flag does not justify that heavier machinery.
 */
fun hasRequestedCameraPermissionBefore(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_HAS_REQUESTED, false)

fun markCameraPermissionRequested(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_HAS_REQUESTED, true) }
}

/** The three real Android inputs [decideCameraAccess] needs, read fresh on every call. */
fun currentCameraAccess(activity: Activity): CameraAccess {
    val granted = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
    return decideCameraAccess(
        granted = granted,
        hasRequestedBefore = hasRequestedCameraPermissionBefore(activity),
        shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA),
    )
}

/** Sealed only to keep the `when` in [MealBuilderScreen] exhaustive; carries no data of its own. */
sealed interface CameraPermissionDialog {
    data object Explain : CameraPermissionDialog
    data object Settings : CameraPermissionDialog
}

/**
 * Shown before the real OS permission dialog, on every tap while access is `NOT_DETERMINED` or
 * `DENIED_RETRIABLE` — never once granted, and never again once permanently denied (that state shows
 * [CameraSettingsDialog] instead). Built from the app's own design tokens rather than plain
 * `AlertDialog` defaults, matching the rest of the product.
 */
@Composable
fun CameraPrePermissionDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.active.SCAN_EXPLAIN_TITLE, color = PnColor.InkDefault) },
        text = { Text(Strings.active.SCAN_EXPLAIN_BODY, color = PnColor.InkSoft) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(Strings.active.SCAN_EXPLAIN_CONTINUE, color = PnColor.BrandLeafDeep) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.active.SCAN_EXPLAIN_CANCEL, color = PnColor.InkSoft) }
        },
        containerColor = PnColor.SurfaceCard,
    )
}

/** Shown once access is `PERMANENTLY_DENIED` — the OS will not show its own dialog again either. */
@Composable
fun CameraSettingsDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.active.SCAN_SETTINGS_TITLE, color = PnColor.InkDefault) },
        text = { Text(Strings.active.SCAN_SETTINGS_BODY, color = PnColor.InkSoft) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(Strings.active.SCAN_SETTINGS_OPEN, color = PnColor.BrandLeafDeep) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.active.SCAN_SETTINGS_CANCEL, color = PnColor.InkSoft) }
        },
        containerColor = PnColor.SurfaceCard,
    )
}
