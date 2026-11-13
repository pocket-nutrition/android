// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import com.goterl.lazysodium.Sodium
import com.goterl.lazysodium.SodiumAndroid
import com.sun.jna.NativeLong

/** Why a crypto step failed. Messages are fixed words, never data: these can end up in a crash report. */
class VaultCryptoException(val kind: Kind) : Exception(kind.name) {
    enum class Kind {
        /** The KDF settings are below the floor, or malformed. Never derive keys with them. */
        UNACCEPTABLE_KDF,
        DERIVATION_FAILED,
        /** Wrong key, a tampered ciphertext, or AAD that does not match: indistinguishable on purpose. */
        DECRYPTION_FAILED,
        MALFORMED_INPUT,
        /** The keyring's inbox public key is not the one its secret key produces: a swapped key. */
        INBOX_KEY_MISMATCH,
    }
}

/** The two values a passphrase yields. [authHash] goes to the server; [wrapKey] never leaves the device. */
class VaultCredentials(val authHash: ByteArray, val wrapKey: ByteArray)

/** An X25519 keypair for drops. */
class VaultInboxKeyPair(val publicKey: ByteArray, val secretKey: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is VaultInboxKeyPair && publicKey.contentEquals(other.publicKey) && secretKey.contentEquals(other.secretKey)

    override fun hashCode(): Int = publicKey.contentHashCode()

    /** Never prints key material. */
    override fun toString(): String = "VaultInboxKeyPair(…)"
}

/** The one native binding of the app. Registering JNA natives is not free, so it happens once. */
object VaultSodium {
    val android: Sodium by lazy { SodiumAndroid() }
}

/**
 * libsodium primitives exactly as `PROTOCOL.md` specifies them, mirroring iOS `VaultCrypto.swift`:
 *
 *     master_key = Argon2id(NFKC(passphrase), kdf.salt, opslimit, memlimit)      32 bytes
 *     auth_hash  = crypto_kdf(master_key, id 1, ctx "pnvauth1")
 *     wrap_key   = crypto_kdf(master_key, id 2, ctx "pnvwrap1")
 *     records    = XChaCha20-Poly1305-IETF, base64(nonce[24] || ciphertext), AAD bound per record
 *     drops      = crypto_box_seal to the inbox public key
 *
 * Only the raw `Sodium` natives are used, injected so unit tests can run the JVM build of the same
 * library. Argon2id is slow and memory-hungry by design: call [credentials] off the main thread.
 */
class VaultCrypto(private val sodium: Sodium = VaultSodium.android) {

    init {
        // Idempotent; returns 1 when already initialised. Below 0 the library cannot be used at all.
        check(sodium.sodium_init() >= 0) { "libsodium failed to initialise" }
    }

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { sodium.randombytes_buf(it, count) }

    fun newKey(): ByteArray = randomBytes(VaultProtocol.KEY_BYTES)

    /** Fresh KDF settings for a new account: a random salt and the app's default cost. */
    fun newKdf(): VaultKdf = VaultKdf(
        salt = randomBytes(VaultProtocol.KDF_SALT_BYTES).toBase64(),
        opslimit = VaultProtocol.DEFAULT_KDF_OPS_LIMIT,
        memlimit = VaultProtocol.DEFAULT_KDF_MEM_LIMIT,
    )

    // ── Passphrase ───────────────────────────────────────────────────────────

    /** Derives the login hash and the wrap key. Refuses KDF settings below the floor. */
    fun credentials(passphrase: String, kdf: VaultKdf): VaultCredentials {
        if (!isAcceptableVaultKdf(kdf)) fail(VaultCryptoException.Kind.UNACCEPTABLE_KDF)
        val salt = decodeBase64OrNull(kdf.salt) ?: fail(VaultCryptoException.Kind.UNACCEPTABLE_KDF)
        val password = normalizedVaultPassphrase(passphrase)
        // An empty passphrase would derive a valid-looking key from nothing.
        if (password.isEmpty()) fail(VaultCryptoException.Kind.DERIVATION_FAILED)
        val master = ByteArray(VaultProtocol.KEY_BYTES)
        try {
            val status = sodium.crypto_pwhash(
                master, master.size.toLong(), password, password.size.toLong(), salt,
                kdf.opslimit.toLong(), NativeLong(kdf.memlimit), ARGON2ID13,
            )
            if (status != 0) fail(VaultCryptoException.Kind.DERIVATION_FAILED)
            return VaultCredentials(
                authHash = derive(master, 1, AUTH_CONTEXT, VaultProtocol.AUTH_HASH_BYTES),
                wrapKey = derive(master, 2, WRAP_CONTEXT, VaultProtocol.KEY_BYTES),
            )
        } finally {
            master.fill(0)
            password.fill(0)
        }
    }

    private fun derive(master: ByteArray, id: Long, context: String, length: Int): ByteArray {
        val out = ByteArray(length)
        val ctx = context.toByteArray(Charsets.US_ASCII)
        if (sodium.crypto_kdf_derive_from_key(out, length, id, ctx, master) != 0) {
            fail(VaultCryptoException.Kind.DERIVATION_FAILED)
        }
        return out
    }

    // ── Symmetric encryption ─────────────────────────────────────────────────

    /** `base64(nonce || ciphertext)`, with a fresh random nonce every time. */
    fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray): String {
        if (key.size != VaultProtocol.KEY_BYTES) fail(VaultCryptoException.Kind.MALFORMED_INPUT)
        val nonce = randomBytes(NONCE_BYTES)
        val sealed = ByteArray(plaintext.size + TAG_BYTES)
        val sealedLength = LongArray(1)
        val status = sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
            sealed, sealedLength, plaintext, plaintext.size.toLong(), aad, aad.size.toLong(), null, nonce, key,
        )
        if (status != 0) fail(VaultCryptoException.Kind.MALFORMED_INPUT)
        return (nonce + sealed.copyOf(sealedLength[0].toInt())).toBase64()
    }

    fun decrypt(ciphertext: String, key: ByteArray, aad: ByteArray): ByteArray {
        if (key.size != VaultProtocol.KEY_BYTES) fail(VaultCryptoException.Kind.MALFORMED_INPUT)
        val raw = decodeBase64OrNull(ciphertext) ?: fail(VaultCryptoException.Kind.MALFORMED_INPUT)
        if (raw.size < NONCE_BYTES + TAG_BYTES) fail(VaultCryptoException.Kind.DECRYPTION_FAILED)
        val nonce = raw.copyOfRange(0, NONCE_BYTES)
        val body = raw.copyOfRange(NONCE_BYTES, raw.size)
        val plaintext = ByteArray(body.size - TAG_BYTES)
        val plaintextLength = LongArray(1)
        val status = sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(
            plaintext, plaintextLength, null, body, body.size.toLong(), aad, aad.size.toLong(), nonce, key,
        )
        if (status != 0) fail(VaultCryptoException.Kind.DECRYPTION_FAILED)
        return plaintext.copyOf(plaintextLength[0].toInt())
    }

    // ── Sealed boxes (drops) ─────────────────────────────────────────────────

    fun newInboxKeyPair(): VaultInboxKeyPair {
        val publicKey = ByteArray(VaultProtocol.KEY_BYTES)
        val secretKey = ByteArray(VaultProtocol.KEY_BYTES)
        if (sodium.crypto_box_keypair(publicKey, secretKey) != 0) fail(VaultCryptoException.Kind.DERIVATION_FAILED)
        return VaultInboxKeyPair(publicKey, secretKey)
    }

    fun seal(plaintext: ByteArray, publicKey: ByteArray): String {
        if (publicKey.size != VaultProtocol.KEY_BYTES) fail(VaultCryptoException.Kind.MALFORMED_INPUT)
        val sealed = ByteArray(plaintext.size + SEAL_BYTES)
        if (sodium.crypto_box_seal(sealed, plaintext, plaintext.size.toLong(), publicKey) != 0) {
            fail(VaultCryptoException.Kind.MALFORMED_INPUT)
        }
        return sealed.toBase64()
    }

    fun openSealed(sealed: String, inbox: VaultInboxKeyPair): ByteArray {
        val raw = decodeBase64OrNull(sealed) ?: fail(VaultCryptoException.Kind.MALFORMED_INPUT)
        if (raw.size < SEAL_BYTES) fail(VaultCryptoException.Kind.DECRYPTION_FAILED)
        val plaintext = ByteArray(raw.size - SEAL_BYTES)
        if (sodium.crypto_box_seal_open(plaintext, raw, raw.size.toLong(), inbox.publicKey, inbox.secretKey) != 0) {
            fail(VaultCryptoException.Kind.DECRYPTION_FAILED)
        }
        return plaintext
    }

    /** The public key an X25519 secret key belongs to. */
    fun inboxPublicKey(secretKey: ByteArray): ByteArray {
        if (secretKey.size != VaultProtocol.KEY_BYTES) fail(VaultCryptoException.Kind.MALFORMED_INPUT)
        val publicKey = ByteArray(VaultProtocol.KEY_BYTES)
        if (sodium.crypto_scalarmult_base(publicKey, secretKey) != 0) fail(VaultCryptoException.Kind.DERIVATION_FAILED)
        return publicKey
    }

    /** Unkeyed BLAKE2b-256 (`crypto_generichash`). */
    fun blake2b256(input: ByteArray): ByteArray {
        val out = ByteArray(32)
        if (sodium.crypto_generichash(out, out.size, input, input.size.toLong(), null, 0) != 0) {
            fail(VaultCryptoException.Kind.DERIVATION_FAILED)
        }
        return out
    }

    private companion object {
        const val AUTH_CONTEXT = "pnvauth1"
        const val WRAP_CONTEXT = "pnvwrap1"
        /** `crypto_pwhash_ALG_ARGON2ID13`. */
        const val ARGON2ID13 = 2
        const val NONCE_BYTES = 24
        const val TAG_BYTES = 16
        /** Ephemeral public key (32) + MAC (16). */
        const val SEAL_BYTES = 48
    }
}

private fun fail(kind: VaultCryptoException.Kind): Nothing = throw VaultCryptoException(kind)
