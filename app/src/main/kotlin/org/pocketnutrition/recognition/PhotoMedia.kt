// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import kotlin.math.max
import kotlin.math.roundToInt

// Meal photos ride the voice outbox: same entry, same ticket, same `audio` multipart field. Pure helpers
// here so they are unit-tested; the bitmap work is in PhotoEncoder.

/** Extension of the local file a meal photo is stored in, beside the voice clips. */
const val PHOTO_EXTENSION = "jpg"

/** Longest side, in pixels, of the photo sent for analysis: enough to tell foods apart, ~200-400 KB. */
const val PHOTO_MAX_SIDE = 1024

class CaptureMedia(val fileName: String, val contentType: String)

val VOICE_MEDIA = CaptureMedia("meal.m4a", "audio/mp4")
val PHOTO_MEDIA = CaptureMedia("meal.jpg", "image/jpeg")

fun isPhotoFile(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() == PHOTO_EXTENSION

/** The part to send for a stored capture file: a photo by its extension, a voice clip otherwise. */
fun mediaFor(fileName: String): CaptureMedia = if (isPhotoFile(fileName)) PHOTO_MEDIA else VOICE_MEDIA

/** Scaled so the longest side is at most [maxSide]; never upscales. Null for a degenerate size. */
fun photoTargetSize(width: Int, height: Int, maxSide: Int = PHOTO_MAX_SIDE): Pair<Int, Int>? {
    if (width <= 0 || height <= 0 || maxSide <= 0) return null
    val scale = minOf(1.0, maxSide.toDouble() / max(width, height))
    return (width * scale).roundToInt() to (height * scale).roundToInt()
}

/** Largest power-of-two decode subsampling that keeps the longest side at or above [maxSide]. */
fun photoSampleSize(width: Int, height: Int, maxSide: Int = PHOTO_MAX_SIDE): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
    return sample
}
