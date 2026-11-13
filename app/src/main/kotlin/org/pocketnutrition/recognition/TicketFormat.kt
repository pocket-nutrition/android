// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

/** Hard stop for a recording. */
const val MAX_RECORDING_MS = 60_000L

/** `m:ss` for the recording timer. */
fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * A coarse duration for ticket rows: `<1 min`, `5 min`, `3 h`, `2 d`. The units are valid in both
 * French and English, so the surrounding sentence is the only thing that needs translating.
 */
fun compactDuration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "<1 min"
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "${minutes / 60} h"
        else -> "${minutes / (24 * 60)} d"
    }
}

/** Anything shorter is a silent or accidental clip and is discarded instead of being uploaded. */
const val MIN_RECORDING_MS = 1_000L

fun isUsableRecording(durationMs: Long): Boolean = durationMs >= MIN_RECORDING_MS
