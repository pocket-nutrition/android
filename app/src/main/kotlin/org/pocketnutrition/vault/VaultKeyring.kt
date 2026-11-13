// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The stored `keyring` blob (the server keeps it verbatim and never parses it). Everything secret in it
 * is wrapped; [inboxPublicKey] is NOT trusted (see [VaultCrypto.unlock]).
 *
 * Properties are declared in alphabetical order of their JSON names: the protocol wants sorted keys and
 * compact JSON, so every device saves the same bytes, and kotlinx.serialization writes in declaration
 * order. `VaultKeyringTest` pins the exact output.
 */
@Serializable
data class VaultKeyringBlob(
    /** Base64 X25519 public key. */
    @SerialName("inbox_public_key") val inboxPublicKey: String,
    /** The inbox secret key, wrapped by the user key. */
    @SerialName("inbox_wrapped_secret_key") val inboxWrappedSecretKey: String,
    val kdf: VaultKdf,
    /** Base64 `nonce || AEAD`: the user key wrapped by the passphrase-derived wrap key. */
    @SerialName("passphrase_wrapped_user_key") val passphraseWrappedUserKey: String,
    /** The same user key, wrapped by the recovery key. */
    @SerialName("recovery_wrapped_user_key") val recoveryWrappedUserKey: String,
    // No default: a blob without `v` is not one this build understands.
    @SerialName("v") val version: Int,
) {
    /** The compact, key-sorted JSON the server stores. */
    fun encoded(): String = vaultJson.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_VERSION = 1

        /** Null for anything that is not a complete keyring of a version this build understands. */
        fun decode(blob: String): VaultKeyringBlob? =
            runCatching { vaultJson.decodeFromString(serializer(), blob) }.getOrNull()?.takeIf { it.version == CURRENT_VERSION }
    }
}

/**
 * How the 32-byte recovery key is shown and typed: RFC 4648 Base32 in groups of four, `ABCD-EFGH-…`
 * (52 characters, 13 groups). Decoding ignores case, dashes and whitespace, and refuses nonzero padding.
 */
object VaultRecoveryKeyFormat {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val ENCODED_LENGTH = 52

    fun encode(key: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in key) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.chunked(4).joinToString("-")
    }

    /** The key bytes, or null if the text is not a valid 32-byte recovery key. */
    fun decode(text: String): ByteArray? {
        val cleaned = text.uppercase(java.util.Locale.ROOT).filter { it != '-' && !it.isWhitespace() }
        if (cleaned.length != ENCODED_LENGTH) return null
        val bytes = ArrayList<Byte>(VaultProtocol.KEY_BYTES)
        var buffer = 0
        var bits = 0
        for (character in cleaned) {
            val value = ALPHABET.indexOf(character)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bytes.add(((buffer shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        // 52 characters carry 260 bits: 256 of key plus 4 bits of padding, which must be zero.
        if (bytes.size != VaultProtocol.KEY_BYTES || buffer != 0) return null
        return bytes.toByteArray()
    }
}
