// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * AAC in an MPEG-4 container (`.m4a`, `audio/mp4`), mono 16 kHz — speech, so about 6 KB per second.
 * The platform enforces the 60 s ceiling through `setMaxDuration`; the screen's timer is a second guard.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null

    /** `false` when recording could not start; the output file is removed in that case. */
    fun start(output: File, onMaxDuration: () -> Unit): Boolean {
        cancel()
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(SAMPLE_RATE_HZ)
            created.setAudioEncodingBitRate(BIT_RATE)
            created.setMaxDuration(MAX_RECORDING_MS.toInt())
            created.setOutputFile(output.absolutePath)
            created.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxDuration()
            }
            created.prepare()
            created.start()
            recorder = created
            target = output
            true
        } catch (e: Exception) {
            created.release()
            output.delete()
            false
        }
    }

    /** Finishes the file. `false` when the platform produced nothing usable (e.g. stopped too quickly). */
    fun stop(): Boolean {
        val active = recorder ?: return false
        val file = target
        recorder = null
        target = null
        val ok = try {
            active.stop()
            true
        } catch (e: RuntimeException) {
            false
        } finally {
            active.release()
        }
        if (!ok) file?.delete()
        return ok && file != null && file.length() > 0
    }

    /** Abandons a recording in progress and deletes its file. Safe to call when idle. */
    fun cancel() {
        val active = recorder ?: return
        val file = target
        recorder = null
        target = null
        try {
            active.stop()
        } catch (e: RuntimeException) {
            // Nothing recorded yet; the file is deleted below either way.
        } finally {
            active.release()
        }
        file?.delete()
    }

    val isRecording: Boolean get() = recorder != null

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val BIT_RATE = 48_000
    }
}
