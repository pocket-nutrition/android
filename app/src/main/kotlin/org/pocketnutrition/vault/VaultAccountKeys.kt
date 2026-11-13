// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import java.security.SecureRandom

/** The keys a device holds while linked. Never persisted as-is: see [VaultSessionStore]. */
class VaultUnlockedKeys(val userKey: ByteArray, val inbox: VaultInboxKeyPair) {
    override fun equals(other: Any?): Boolean =
        other is VaultUnlockedKeys && userKey.contentEquals(other.userKey) && inbox == other.inbox

    override fun hashCode(): Int = userKey.contentHashCode()

    override fun toString(): String = "VaultUnlockedKeys(…)"
}

/** Everything a brand-new account needs to register, plus what the person must be shown once. */
class VaultNewAccount(
    val kdf: VaultKdf,
    /** Base64, as the server expects it. */
    val authHash: String,
    /** The `keyring` string to send. */
    val keyringBlob: String,
    /** The recovery key in its display form. Shown exactly once; the server cannot recover it. */
    val recoveryKey: String,
    val keys: VaultUnlockedKeys,
) {
    override fun toString(): String = "VaultNewAccount(…)"
}

/**
 * Builds a new account's keys: a random user key and inbox keypair, wrapped by the passphrase and by a
 * fresh recovery key. Slow (Argon2id): call it off the main thread.
 */
fun VaultCrypto.createAccount(passphrase: String): VaultNewAccount {
    val kdf = newKdf()
    val credentials = credentials(passphrase, kdf)
    val userKey = newKey()
    val recoveryKey = newKey()
    val inbox = newInboxKeyPair()
    val blob = VaultKeyringBlob(
        inboxPublicKey = inbox.publicKey.toBase64(),
        inboxWrappedSecretKey = encrypt(inbox.secretKey, userKey, vaultKeyringAad(VaultKeyringPurpose.INBOX)),
        kdf = kdf,
        passphraseWrappedUserKey = encrypt(userKey, credentials.wrapKey, vaultKeyringAad(VaultKeyringPurpose.PASSPHRASE)),
        recoveryWrappedUserKey = encrypt(userKey, recoveryKey, vaultKeyringAad(VaultKeyringPurpose.RECOVERY)),
        version = VaultKeyringBlob.CURRENT_VERSION,
    )
    return VaultNewAccount(
        kdf = kdf,
        authHash = credentials.authHash.toBase64(),
        keyringBlob = blob.encoded(),
        recoveryKey = VaultRecoveryKeyFormat.encode(recoveryKey),
        keys = VaultUnlockedKeys(userKey, inbox),
    )
}

/**
 * A forgotten passphrase replaced from this linked device (`POST /v1/account/passphrase/reset`): a new
 * generated passphrase, a fresh KDF salt and a NEW recovery key, both wrapping the SAME user key. Holds
 * what the person must be shown once; never printed.
 */
class VaultPassphraseReset(
    val kdf: VaultKdf,
    /** Base64, as the server expects it. */
    val authHash: String,
    /** The new passphrase, shown once in the emergency kit. */
    val passphrase: String,
    /** The new recovery key in its display form, shown once in the emergency kit. */
    val recoveryKey: String,
    private val wrapKey: ByteArray,
    private val recoveryKeyBytes: ByteArray,
) {
    /**
     * The new `keyring` string: [current]'s inbox fields copied unchanged, the user key re-wrapped under the
     * new wrap key and the new recovery key. Cheap (no KDF), so a keyring conflict can rebuild it from the
     * refetched keyring. Refuses a [current] keyring whose inbox does not open with [keys]: this device must
     * not overwrite the account with a keyring for other keys.
     */
    fun keyring(crypto: VaultCrypto, current: VaultKeyringBlob, keys: VaultUnlockedKeys): String {
        val inbox = crypto.decrypt(current.inboxWrappedSecretKey, keys.userKey, vaultKeyringAad(VaultKeyringPurpose.INBOX))
        val stored = decodeBase64OrNull(current.inboxPublicKey)
        if (!inbox.contentEquals(keys.inbox.secretKey) || stored == null || !stored.contentEquals(keys.inbox.publicKey)) {
            throw VaultCryptoException(VaultCryptoException.Kind.INBOX_KEY_MISMATCH)
        }
        return VaultKeyringBlob(
            inboxPublicKey = current.inboxPublicKey,
            inboxWrappedSecretKey = current.inboxWrappedSecretKey,
            kdf = kdf,
            passphraseWrappedUserKey = crypto.encrypt(keys.userKey, wrapKey, vaultKeyringAad(VaultKeyringPurpose.PASSPHRASE)),
            recoveryWrappedUserKey = crypto.encrypt(keys.userKey, recoveryKeyBytes, vaultKeyringAad(VaultKeyringPurpose.RECOVERY)),
            version = VaultKeyringBlob.CURRENT_VERSION,
        ).encoded()
    }

    override fun toString(): String = "VaultPassphraseReset(…)"
}

/**
 * Derives everything a passphrase change needs from the new [passphrase] (generated, see
 * [VaultPassphraseSuggestion], or chosen). Slow (Argon2id): call it off the main thread.
 */
fun VaultCrypto.preparePassphraseReset(passphrase: String): VaultPassphraseReset {
    val kdf = newKdf()
    val credentials = credentials(passphrase, kdf)
    val recoveryKey = newKey()
    return VaultPassphraseReset(
        kdf = kdf,
        authHash = credentials.authHash.toBase64(),
        passphrase = passphrase,
        recoveryKey = VaultRecoveryKeyFormat.encode(recoveryKey),
        wrapKey = credentials.wrapKey,
        recoveryKeyBytes = recoveryKey,
    )
}

/** Opens a stored keyring with the passphrase-derived wrap key (the normal login path). */
fun VaultCrypto.unlock(blob: VaultKeyringBlob, credentials: VaultCredentials): VaultUnlockedKeys {
    val userKey = decrypt(blob.passphraseWrappedUserKey, credentials.wrapKey, vaultKeyringAad(VaultKeyringPurpose.PASSPHRASE))
    return openInbox(blob, userKey)
}

/** Opens a stored keyring with the printed recovery key (the forgot-passphrase path). */
fun VaultCrypto.unlock(blob: VaultKeyringBlob, recoveryKey: String): VaultUnlockedKeys {
    val key = VaultRecoveryKeyFormat.decode(recoveryKey)
        ?: throw VaultCryptoException(VaultCryptoException.Kind.MALFORMED_INPUT)
    val userKey = decrypt(blob.recoveryWrappedUserKey, key, vaultKeyringAad(VaultKeyringPurpose.RECOVERY))
    return openInbox(blob, userKey)
}

private fun VaultCrypto.openInbox(blob: VaultKeyringBlob, userKey: ByteArray): VaultUnlockedKeys {
    val secret = decrypt(blob.inboxWrappedSecretKey, userKey, vaultKeyringAad(VaultKeyringPurpose.INBOX))
    // The public key in the blob is not authenticated. A malicious server could swap it for its own, so
    // every drop would be sealed to the server. Derive it from the secret, and refuse a disagreeing blob.
    val derived = inboxPublicKey(secret)
    val stored = decodeBase64OrNull(blob.inboxPublicKey)
    if (stored == null || !stored.contentEquals(derived)) {
        throw VaultCryptoException(VaultCryptoException.Kind.INBOX_KEY_MISMATCH)
    }
    return VaultUnlockedKeys(userKey, VaultInboxKeyPair(derived, secret))
}

// ── Fingerprint phrase ───────────────────────────────────────────────────────

/** `pnv1|fingerprint|<account_id>|` followed by the raw 32-byte inbox public key. */
fun vaultFingerprintInput(accountId: String, inboxPublicKey: ByteArray): ByteArray =
    "${VaultProtocol.AAD_VERSION}|fingerprint|$accountId|".toByteArray(Charsets.UTF_8) + inboxPublicKey

/**
 * Five words naming the account's keys, the same on every device that opened them. Each word is one
 * big-endian 32-bit chunk of the BLAKE2b-256 digest, modulo the 7776-word EFF list. Null for a short digest.
 */
fun vaultFingerprintWords(digest: ByteArray, words: List<String> = EffLongWordlist.words): List<String>? {
    if (digest.size < FINGERPRINT_WORDS * 4) return null
    return (0 until FINGERPRINT_WORDS).map { index ->
        var chunk = 0L
        for (offset in 0 until 4) chunk = (chunk shl 8) or (digest[index * 4 + offset].toLong() and 0xFF)
        words[(chunk % words.size).toInt()]
    }
}

fun VaultCrypto.fingerprintPhrase(accountId: String, inboxPublicKey: ByteArray): List<String>? =
    vaultFingerprintWords(blake2b256(vaultFingerprintInput(accountId, inboxPublicKey)))

private const val FINGERPRINT_WORDS = 5

// ── Passphrase suggestion ────────────────────────────────────────────────────

/**
 * A random passphrase for a new account: five words from the EFF list (about 64 bits), joined by hyphens.
 * Always English whatever the app language, as on iOS. `nextInt(bound)` is uniform, so no modulo bias.
 */
object VaultPassphraseSuggestion {
    const val WORD_COUNT = 5

    fun make(random: SecureRandom = SecureRandom(), words: List<String> = EffLongWordlist.words): String =
        (0 until WORD_COUNT).joinToString("-") { words[random.nextInt(words.size)] }
}

/**
 * The EFF "long" wordlist (7776 words, one per five-dice roll), as a classpath resource so the APK and
 * the JVM unit tests read the same file. Source: https://www.eff.org/files/2016/07/18/eff_large_wordlist.txt
 * (words only, dice numbers dropped). Copyright Electronic Frontier Foundation, CC BY 3.0 US; credited
 * on the About screen.
 */
object EffLongWordlist {
    const val SIZE = 7776

    val words: List<String> by lazy {
        val stream = checkNotNull(EffLongWordlist::class.java.getResourceAsStream("eff_large_wordlist.txt")) {
            "EFF wordlist resource missing"
        }
        val list = stream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.map(String::trim).filter(String::isNotEmpty).toList() }
        check(list.size == SIZE) { "EFF wordlist has ${list.size} words" }
        list
    }
}
