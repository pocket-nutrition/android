// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import android.Manifest
import android.app.Activity
import android.content.Context
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

// The microphone twin of CameraPermission.kt. The decision table is the same pure function — it was
// written for the camera but is permission-agnostic — so it is reused rather than copied, and so is the
// two-state `CameraPermissionDialog` marker (Explain / Settings). Only the flag, the permission and the
// copy differ.

private const val PREFS_NAME = "microphone_permission"
private const val KEY_HAS_REQUESTED = "has_requested"

fun markMicrophonePermissionRequested(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_HAS_REQUESTED, true) }
}

fun currentMicrophoneAccess(activity: Activity): CameraAccess {
    val granted = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
    return decideCameraAccess(
        granted = granted,
        hasRequestedBefore = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAS_REQUESTED, false),
        shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO,
        ),
    )
}

@Composable
fun MicrophonePrePermissionDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.active.MIC_EXPLAIN_TITLE, color = PnColor.InkDefault) },
        text = { Text(Strings.active.MIC_EXPLAIN_BODY, color = PnColor.InkSoft) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(Strings.active.MIC_EXPLAIN_CONTINUE, color = PnColor.BrandLeafDeep) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.active.MIC_EXPLAIN_CANCEL, color = PnColor.InkSoft) }
        },
        containerColor = PnColor.SurfaceCard,
    )
}

@Composable
fun MicrophoneSettingsDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.active.MIC_SETTINGS_TITLE, color = PnColor.InkDefault) },
        text = { Text(Strings.active.MIC_SETTINGS_BODY, color = PnColor.InkSoft) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(Strings.active.MIC_SETTINGS_OPEN, color = PnColor.BrandLeafDeep) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.active.MIC_SETTINGS_CANCEL, color = PnColor.InkSoft) }
        },
        containerColor = PnColor.SurfaceCard,
    )
}
