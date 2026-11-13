// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pocketnutrition.core.CameraAccess
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.recognition.AudioRecorder
import org.pocketnutrition.recognition.CaptureCoordinator
import org.pocketnutrition.recognition.Entry
import org.pocketnutrition.recognition.EntryStore
import org.pocketnutrition.recognition.MAX_RECORDING_MS
import org.pocketnutrition.recognition.PHOTO_EXTENSION
import org.pocketnutrition.recognition.PhotoEncoder
import org.pocketnutrition.recognition.RecognitionSettings
import org.pocketnutrition.recognition.formatElapsed
import org.pocketnutrition.recognition.isUsableRecording
import org.pocketnutrition.recognition.isValidRecognitionUrl

private class CaptureSetup(val configured: Boolean, val urlValid: Boolean, val hasKey: Boolean)

/**
 * The microphone and camera beside the meal builder's search field: dictate the meal, or photograph it.
 *
 * Every recording or photo becomes a stored `PENDING_UPLOAD` entry BEFORE any network call, then the
 * [coordinator] sends it; [onCaptured] tells the builder so it can add the result when it is in. What
 * outlives the screen shows in the journal as a meal in progress. Moved from the former voice screen.
 *
 * While recording, the two icons become the elapsed time (tap to stop) and a cancel button.
 */
@Composable
fun CaptureButtons(
    store: EntryStore,
    coordinator: CaptureCoordinator,
    onCaptured: (localId: String) -> Unit,
    onError: (String?) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val strings = Strings.active

    val recorder = remember { AudioRecorder(context.applicationContext) }
    var recordingId by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var permissionDialog by remember { mutableStateOf<CameraPermissionDialog?>(null) }
    var photoMenu by remember { mutableStateOf(false) }

    // Off the main thread: reading the config may decrypt the key through the Keystore.
    val setup by produceState<CaptureSetup?>(null) {
        value = withContext(Dispatchers.IO) {
            CaptureSetup(
                configured = RecognitionSettings.config != null,
                urlValid = isValidRecognitionUrl(RecognitionSettings.rawUrl ?: ""),
                hasKey = RecognitionSettings.hasApiKey,
            )
        }
    }

    /** Captures need the recognition server set up in Settings; say what is missing otherwise. */
    fun ready(): Boolean {
        val current = setup ?: return false
        if (!current.configured) {
            onError(strings.voiceMissing(!current.urlValid, !current.hasKey))
            return false
        }
        onError(null)
        return true
    }

    fun persist(id: String, fileName: String): Boolean {
        if (!store.upsert(Entry.pendingUpload(id, System.currentTimeMillis(), fileName))) return false
        coordinator.track(id)
        onCaptured(id)
        return true
    }

    fun stopRecording() {
        val id = recordingId ?: return
        recordingId = null
        val file = File(store.audioDir, "$id.m4a")
        val duration = SystemClock.elapsedRealtime() - startedAt
        // A silent or accidental clip is discarded, not uploaded.
        if (recorder.stop() && isUsableRecording(duration) && persist(id, file.name)) return
        recorder.cancel()
        file.delete()
        onError(strings.VOICE_ERR_TOO_SHORT)
    }

    fun cancelRecording() {
        recorder.cancel()
        recordingId?.let { File(store.audioDir, "$it.m4a").delete() }
        recordingId = null
    }

    fun startRecording() {
        val id = UUID.randomUUID().toString()
        onError(null)
        if (recorder.start(File(store.audioDir, "$id.m4a"), onMaxDuration = ::stopRecording)) {
            recordingId = id
            startedAt = SystemClock.elapsedRealtime()
            elapsedMs = 0
        } else {
            onError(strings.VOICE_ERR_RECORDER)
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }

    // Meal photo: encoded (metadata dropped, size bounded) into the private capture folder as `<id>.jpg`.
    // The `.jpg` extension is what makes it upload as a photo.
    fun submitPhoto(source: Uri, scratch: File? = null) {
        onError(null)
        scope.launch {
            val id = UUID.randomUUID().toString()
            val file = File(store.audioDir, "$id.$PHOTO_EXTENSION")
            val saved = withContext(Dispatchers.IO) {
                val jpeg = PhotoEncoder.jpeg(context.contentResolver, source)
                scratch?.delete()
                jpeg != null && runCatching { file.writeBytes(jpeg) }.isSuccess
            }
            if (!saved || !persist(id, file.name)) {
                file.delete()
                onError(strings.PHOTO_ERR_SAVE)
            }
        }
    }

    val libraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) submitPhoto(uri)
    }

    // The camera app writes into a scratch file in the cache, shared through the FileProvider only.
    val cameraScratch = remember { File(File(context.cacheDir, "meal-photo"), "capture.jpg") }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) submitPhoto(Uri.fromFile(cameraScratch), scratch = cameraScratch) else cameraScratch.delete()
    }

    fun takePhoto() {
        cameraScratch.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", cameraScratch)
        runCatching { cameraLauncher.launch(uri) }.onFailure { onError(strings.PHOTO_ERR_CAMERA) }
    }

    // The app declares CAMERA (barcode scanning), so the system camera may only be opened once it is granted.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        activity?.let(::markCameraPermissionRequested)
        if (granted) takePhoto() else onError(strings.PHOTO_ERR_CAMERA_DENIED)
    }

    // A backgrounded app records silence on current Android: save what was said so far.
    val owner = remember(activity) { activity as? LifecycleOwner }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recordingId != null) stopRecording()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    // Leaving the screen discards a recording in progress: it has no entry yet.
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    LaunchedEffect(recordingId) {
        if (recordingId == null) return@LaunchedEffect
        while (true) {
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (elapsedMs >= MAX_RECORDING_MS) {
                stopRecording()
                break
            }
            delay(200)
        }
    }
    BackHandler(enabled = recordingId != null) { cancelRecording() }

    if (recordingId != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::stopRecording) {
                Text("■ ${formatElapsed(elapsedMs)}", color = PnColor.StatusBad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = ::cancelRecording) { Text("✕", color = PnColor.InkSoft, fontSize = 16.sp) }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val act = activity ?: return@IconButton
                if (!ready()) return@IconButton
                when (currentMicrophoneAccess(act)) {
                    CameraAccess.GRANTED -> startRecording()
                    CameraAccess.NOT_DETERMINED, CameraAccess.DENIED_RETRIABLE ->
                        permissionDialog = CameraPermissionDialog.Explain
                    CameraAccess.PERMANENTLY_DENIED -> permissionDialog = CameraPermissionDialog.Settings
                }
            }) {
                Text("🎙️", fontSize = 18.sp, modifier = Modifier.semantics { contentDescription = strings.DICTATION_START })
            }
            Box {
                IconButton(onClick = { if (ready()) photoMenu = true }) {
                    Text("📷", fontSize = 18.sp, modifier = Modifier.semantics { contentDescription = strings.PHOTO_START })
                }
                DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                    DropdownMenuItem(text = { Text(strings.PHOTO_TAKE) }, onClick = {
                        photoMenu = false
                        val act = activity ?: return@DropdownMenuItem
                        when (currentCameraAccess(act)) {
                            CameraAccess.GRANTED -> takePhoto()
                            CameraAccess.PERMANENTLY_DENIED -> onError(strings.PHOTO_ERR_CAMERA_DENIED)
                            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    })
                    DropdownMenuItem(text = { Text(strings.PHOTO_CHOOSE) }, onClick = {
                        photoMenu = false
                        libraryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
            }
        }
    }

    when (permissionDialog) {
        CameraPermissionDialog.Explain -> MicrophonePrePermissionDialog(
            onContinue = {
                activity?.let(::markMicrophonePermissionRequested)
                permissionDialog = null
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { permissionDialog = null },
        )
        CameraPermissionDialog.Settings -> MicrophoneSettingsDialog(
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
                permissionDialog = null
            },
            onDismiss = { permissionDialog = null },
        )
        null -> Unit
    }
}
