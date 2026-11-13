// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** The server sent more than the client is willing to buffer. */
class ResponseTooLargeException : Exception("response too large")

/** Reads [stream] in chunks and gives up as soon as more than [maxBytes] have arrived. */
fun readCapped(stream: InputStream, maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw ResponseTooLargeException()
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
