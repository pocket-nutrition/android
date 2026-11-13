// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LuminanceBarcodeDecoderTest {

    private val decoder = LuminanceBarcodeDecoder()

    /** Renders [contents] as a luminance plane: 0 = black bar, 255 = white, [padding] extra bytes per row. */
    private fun render(contents: String, format: BarcodeFormat, width: Int, height: Int, padding: Int = 0): Triple<ByteArray, Int, Pair<Int, Int>> {
        val matrix = MultiFormatWriter().encode(contents, format, width, height)
        val stride = width + padding
        val plane = ByteArray(stride * height) { 0x80.toByte() }
        for (y in 0 until height) for (x in 0 until width) {
            plane[y * stride + x] = if (matrix[x, y]) 0 else 0xFF.toByte()
        }
        return Triple(plane, stride, width to height)
    }

    @Test
    fun `decodes a real EAN-13`() {
        val (plane, stride, size) = render("3017620422003", BarcodeFormat.EAN_13, 400, 200)
        assertEquals("3017620422003", decoder.decode(plane, stride, size.first, size.second))
    }

    @Test
    fun `decodes when the camera pads each row beyond the image width`() {
        val (plane, stride, size) = render("3017620422003", BarcodeFormat.EAN_13, 400, 200, padding = 48)
        assertEquals("3017620422003", decoder.decode(plane, stride, size.first, size.second))
    }

    @Test
    fun `decodes a barcode rotated a quarter turn, as an upright phone delivers it`() {
        val (plane, _, size) = render("3017620422003", BarcodeFormat.EAN_13, 400, 200)
        val (w, h) = size
        val rotated = ByteArray(h * w)
        for (y in 0 until h) for (x in 0 until w) rotated[x * h + (h - 1 - y)] = plane[y * w + x]
        assertEquals("3017620422003", decoder.decode(rotated, h, h, w))
    }

    @Test
    fun `a UPC-A comes back as 12 digits that normalizeToEan13 pads to a 13-digit key`() {
        val (plane, stride, size) = render("036000291452", BarcodeFormat.UPC_A, 400, 200)
        val raw = decoder.decode(plane, stride, size.first, size.second)
        assertEquals("036000291452", raw)
        assertEquals("0036000291452", normalizeToEan13(raw!!))
    }

    @Test
    fun `ignores symbologies the backend cannot address`() {
        val (plane, stride, size) = render("ABC-123", BarcodeFormat.CODE_128, 400, 200)
        assertNull(decoder.decode(plane, stride, size.first, size.second))
    }

    @Test
    fun `a blank frame is not an error`() {
        assertNull(decoder.decode(ByteArray(300 * 200) { 0xFF.toByte() }, 300, 300, 200))
    }

    @Test
    fun `rejects a buffer smaller than the declared frame`() {
        assertThrows(IllegalArgumentException::class.java) { decoder.decode(ByteArray(10), 100, 100, 100) }
    }
}
