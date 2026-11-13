// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import java.io.ByteArrayOutputStream
import java.util.UUID

// multipart/form-data written by hand, because the app's HTTP layer (java.net.HttpURLConnection) only
// knows JSON bodies and no HTTP library may be added. Kept as a pure function so it can be unit-tested.

class MultipartFile(
    val fieldName: String,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

private const val CRLF = "\r\n"

fun multipartContentType(boundary: String): String = "multipart/form-data; boundary=$boundary"

/** Unique per request; only characters that are safe in a header value and cannot occur in the text parts. */
fun newMultipartBoundary(): String = "PocketNutrition-" + UUID.randomUUID().toString().replace("-", "")

/** Text [fields] first, then the single [file] part, then the closing delimiter. */
fun buildMultipartBody(boundary: String, fields: List<Pair<String, String>>, file: MultipartFile): ByteArray {
    val out = ByteArrayOutputStream(file.bytes.size + 512)
    fun text(value: String) = out.write(value.toByteArray(Charsets.UTF_8))

    for ((name, value) in fields) {
        text("--$boundary$CRLF")
        text("Content-Disposition: form-data; name=\"${headerSafe(name)}\"$CRLF$CRLF")
        text(value)
        text(CRLF)
    }
    text("--$boundary$CRLF")
    text(
        "Content-Disposition: form-data; name=\"${headerSafe(file.fieldName)}\"; " +
            "filename=\"${headerSafe(file.fileName)}\"$CRLF",
    )
    text("Content-Type: ${file.contentType}$CRLF$CRLF")
    out.write(file.bytes)
    text("$CRLF--$boundary--$CRLF")
    return out.toByteArray()
}

/** Quotes and line breaks would end the header value early; the callers only pass constants, this is a guard. */
private fun headerSafe(value: String): String =
    value.replace("\"", "%22").replace("\r", "").replace("\n", "")
