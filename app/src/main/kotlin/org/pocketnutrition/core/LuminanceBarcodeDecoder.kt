// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/**
 * Reads a retail barcode out of one camera frame's luminance (Y) plane, using ZXing.
 *
 * Restricted to EAN-13 and UPC-A, like the scanner before it: the backend is addressed by a 13-digit
 * key and [normalizeToEan13] cannot safely turn any other symbology into one. ZXing reports UPC-A as
 * 12 digits, which [normalizeToEan13] zero-pads.
 *
 * A phone held upright delivers frames rotated 90° from the barcode's natural orientation. ZXing's own
 * rotate-and-retry does not apply to [PlanarYUVLuminanceSource] (it reports rotation as unsupported), so
 * a frame that yields nothing is rotated a quarter turn here and tried once more. `TRY_HARDER` still
 * pays for itself on the horizontal pass: it scans more rows per frame.
 *
 * Not thread-safe: [MultiFormatReader] keeps state between calls. One instance per analyzer, which
 * CameraX calls from a single executor.
 */
class LuminanceBarcodeDecoder {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13, BarcodeFormat.UPC_A),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    /**
     * @param luminance the Y plane, [rowStride] bytes per row — which may exceed [width] when the
     *   camera pads rows, so the stride is the data width and [width] only crops it back.
     * @return the decoded digits, or null when the frame holds no EAN-13/UPC-A code.
     */
    fun decode(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        require(rowStride >= width) { "rowStride $rowStride is smaller than width $width" }
        require(height <= 0 || luminance.size >= rowStride * (height - 1) + width) {
            "luminance buffer is too small for ${width}x$height"
        }
        return read(luminance, rowStride, width, height)
            ?: rotateQuarterTurn(luminance, rowStride, width, height).let { read(it, height, height, width) }
    }

    private fun read(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            null // the normal case for almost every frame
        } catch (_: ReaderException) {
            null // checksum or format errors: a bad read of a real code, try the next frame
        } finally {
            reader.reset()
        }
    }

    /** Clockwise quarter turn into a tightly packed `height`-wide, `width`-tall plane. */
    private fun rotateQuarterTurn(luminance: ByteArray, rowStride: Int, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(width * height)
        for (y in 0 until height) {
            val row = y * rowStride
            for (x in 0 until width) rotated[x * height + (height - 1 - y)] = luminance[row + x]
        }
        return rotated
    }
}
