// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * Turns what the scanner recognises into the 13-digit key `GET /ingredients/barcode/{ean13}` expects, or
 * null for a format this app is not scoped to look up.
 *
 * UPC-A is EAN-13 with a leading zero omitted — the same catalogue entry is addressed by "0" plus the
 * 12 UPC-A digits, so a UPC-A scan is zero-padded rather than looked up on its own. EAN-8 is NOT
 * expanded: it is a checksum-compressed encoding, not a truncated EAN-13, so there is no safe way to
 * recover a 13-digit key from it. A scanned EAN-8 product finds nothing under this MVP — a real gap,
 * recorded rather than papered over with a guess (see AGENTS.md: "never invent food data").
 */
fun normalizeToEan13(rawValue: String): String? {
    val digits = rawValue.filter(Char::isDigit)
    return when (digits.length) {
        13 -> digits
        12 -> "0$digits"
        else -> null
    }
}
