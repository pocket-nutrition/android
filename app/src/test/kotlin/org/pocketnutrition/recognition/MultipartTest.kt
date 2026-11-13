// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultipartTest {

    private val crlf = "\r\n"

    @Test
    fun `body has the field part then the file part then the closing boundary`() {
        val audio = byteArrayOf(0x00, 0x01, 0x7F, -1, -128, 0x0D, 0x0A)
        val body = buildMultipartBody(
            boundary = "BOUND",
            fields = listOf("mode" to "async"),
            file = MultipartFile("audio", "meal.m4a", "audio/mp4", audio),
        )

        val head = "--BOUND$crlf" +
            "Content-Disposition: form-data; name=\"mode\"$crlf$crlf" +
            "async$crlf" +
            "--BOUND$crlf" +
            "Content-Disposition: form-data; name=\"audio\"; filename=\"meal.m4a\"$crlf" +
            "Content-Type: audio/mp4$crlf$crlf"
        val tail = "$crlf--BOUND--$crlf"

        val expected = head.toByteArray(Charsets.UTF_8) + audio + tail.toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, body)
    }

    @Test
    fun `binary payload bytes are preserved untouched`() {
        val audio = ByteArray(256) { it.toByte() }
        val body = buildMultipartBody("B", emptyList(), MultipartFile("audio", "meal.m4a", "audio/mp4", audio))
        val start = indexOf(body, audio)
        assertTrue("payload must appear verbatim", start >= 0)
    }

    @Test
    fun `content type header value matches the part`() {
        assertEquals("multipart/form-data; boundary=XYZ", multipartContentType("XYZ"))
    }

    @Test
    fun `generated boundaries are unique and header safe`() {
        val a = newMultipartBoundary()
        val b = newMultipartBoundary()
        assertTrue(a != b)
        assertTrue(a.matches(Regex("[A-Za-z0-9-]{20,70}")))
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
