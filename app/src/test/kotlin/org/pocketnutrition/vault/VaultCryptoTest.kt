// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import com.goterl.lazysodium.SodiumJava
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The JVM build of libsodium: the Android .so cannot load in a unit test. Same natives, same bytes. */
internal val testCrypto: VaultCrypto by lazy { VaultCrypto(SodiumJava()) }

internal fun hex(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/**
 * Known answers from `pocket-nutrition-vault/docs/test-vectors.json` (generated from libsodium by the
 * server's `scripts/gen_vectors.py`, copied verbatim into test resources), plus the cases of iOS
 * `VaultCryptoTests.swift`. Passing these is what makes this client speak the same protocol as the others.
 */
class VaultCryptoTest {

    private val crypto = testCrypto

    private val vectors: JsonObject by lazy {
        val text = checkNotNull(javaClass.getResourceAsStream("/vault/test-vectors.json")).bufferedReader().readText()
        Json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun expectKind(kind: VaultCryptoException.Kind, block: () -> Unit) {
        val thrown = assertThrows(VaultCryptoException::class.java) { block() }
        assertEquals(kind, thrown.kind)
    }

    // ── Cross-client vectors ─────────────────────────────────────────────────

    @Test
    fun `the passphrase derives the auth hash and wrap key libsodium produced`() {
        val v = vectors.getValue("kdf").jsonObject
        val kdf = VaultKdf(
            salt = hex(v.str("salt_hex")).toBase64(),
            opslimit = v.getValue("opslimit").jsonPrimitive.int,
            memlimit = v.getValue("memlimit").jsonPrimitive.long,
        )
        val credentials = crypto.credentials(v.str("passphrase_utf8"), kdf)
        assertArrayEquals(hex(v.str("auth_hash_hex")), credentials.authHash)
        assertEquals(v.str("auth_hash_b64"), credentials.authHash.toBase64())
        assertArrayEquals(hex(v.str("wrap_key_hex")), credentials.wrapKey)
    }

    @Test
    fun `a record encrypted by another client decrypts here`() {
        val v = vectors.getValue("record_aead").jsonObject
        val plaintext = crypto.decrypt(v.str("ciphertext_b64"), hex(v.str("key_hex")), v.str("aad_utf8").toByteArray())
        assertEquals(v.str("plaintext_utf8"), plaintext.toString(Charsets.UTF_8))
    }

    @Test
    fun `the vector record AAD is the one this client builds`() {
        val v = vectors.getValue("record_aead").jsonObject
        val aad = vaultRecordAad("acc-1", "rec-1", "capture", null, 3, VaultProtocol.USER_KEY_ID)
        assertEquals(v.str("aad_utf8"), aad.toString(Charsets.UTF_8))
    }

    @Test
    fun `the vector ciphertext starts with its nonce`() {
        val v = vectors.getValue("record_aead").jsonObject
        val raw = checkNotNull(decodeBase64OrNull(v.str("ciphertext_b64")))
        assertArrayEquals(hex(v.str("nonce_hex")), raw.copyOfRange(0, 24))
    }

    @Test
    fun `a sealed box made by another client opens here`() {
        val v = vectors.getValue("sealed_box").jsonObject
        val inbox = VaultInboxKeyPair(hex(v.str("inbox_public_key_hex")), hex(v.str("inbox_private_key_hex")))
        val plaintext = crypto.openSealed(v.str("sealed_b64"), inbox)
        assertEquals(v.str("plaintext_utf8"), plaintext.toString(Charsets.UTF_8))
    }

    @Test
    fun `the inbox public key is derived from the secret key`() {
        val pair = crypto.newInboxKeyPair()
        assertArrayEquals(pair.publicKey, crypto.inboxPublicKey(pair.secretKey))
        val v = vectors.getValue("sealed_box").jsonObject
        assertArrayEquals(hex(v.str("inbox_public_key_hex")), crypto.inboxPublicKey(hex(v.str("inbox_private_key_hex"))))
    }

    @Test
    fun `the fingerprint phrase matches an independent BLAKE2b computation`() {
        // Python: hashlib.blake2b(b"pnv1|fingerprint|acc-1|" + pk, digest_size=32), EFF list indexing.
        val pk = hex(vectors.getValue("sealed_box").jsonObject.str("inbox_public_key_hex"))
        assertArrayEquals(
            hex("5f77a98823f64424931ab6f2b47cc89a989314043492e23de3a6fc1521b9e683"),
            crypto.blake2b256(vaultFingerprintInput("acc-1", pk)),
        )
        assertEquals(listOf("blemish", "dubbed", "ankle", "compress", "earthling"), crypto.fingerprintPhrase("acc-1", pk))
    }

    // ── Passphrase handling ──────────────────────────────────────────────────

    @Test
    fun `an accented passphrase derives the same keys composed or decomposed`() {
        val kdf = crypto.newKdf()
        val composed = crypto.credentials("café crème brûlée", kdf)
        val decomposed = crypto.credentials("café crème brûlée", kdf)
        assertArrayEquals(composed.authHash, decomposed.authHash)
        assertArrayEquals(composed.wrapKey, decomposed.wrapKey)
    }

    @Test
    fun `different passphrases give different keys, and the two keys differ`() {
        val kdf = crypto.newKdf()
        val a = crypto.credentials("alpha", kdf)
        val b = crypto.credentials("bravo", kdf)
        assertFalse(a.authHash.contentEquals(b.authHash) || a.wrapKey.contentEquals(b.wrapKey))
        assertFalse(a.authHash.contentEquals(a.wrapKey))
    }

    @Test
    fun `an empty passphrase is refused`() {
        expectKind(VaultCryptoException.Kind.DERIVATION_FAILED) { crypto.credentials("", crypto.newKdf()) }
    }

    @Test
    fun `KDF settings below the floor are never used`() {
        val weak = VaultKdf(salt = ByteArray(16).toBase64(), opslimit = 1, memlimit = 1024)
        expectKind(VaultCryptoException.Kind.UNACCEPTABLE_KDF) { crypto.credentials("x", weak) }
        val shortSalt = VaultKdf(salt = ByteArray(8).toBase64(), opslimit = 3, memlimit = VaultProtocol.MIN_KDF_MEM_LIMIT)
        expectKind(VaultCryptoException.Kind.UNACCEPTABLE_KDF) { crypto.credentials("x", shortSalt) }
    }

    // ── Records ──────────────────────────────────────────────────────────────

    @Test
    fun `a record round-trips and each encryption uses a fresh nonce`() {
        val key = crypto.newKey()
        val aad = "pnv1|a|r|t||1|user".toByteArray()
        val plaintext = "{\"kcal\":420}".toByteArray()
        val first = crypto.encrypt(plaintext, key, aad)
        val second = crypto.encrypt(plaintext, key, aad)
        assertNotEquals(first, second)
        assertArrayEquals(plaintext, crypto.decrypt(first, key, aad))
    }

    @Test
    fun `an empty plaintext round-trips`() {
        val key = crypto.newKey()
        assertArrayEquals(ByteArray(0), crypto.decrypt(crypto.encrypt(ByteArray(0), key, ByteArray(0)), key, ByteArray(0)))
    }

    @Test
    fun `a wrong key, wrong AAD or tampered ciphertext fails to decrypt`() {
        val key = crypto.newKey()
        val aad = "pnv1|a|r|t||1|user".toByteArray()
        val sealed = crypto.encrypt("secret".toByteArray(), key, aad)
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) { crypto.decrypt(sealed, crypto.newKey(), aad) }
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) { crypto.decrypt(sealed, key, "pnv1|a|r|t||2|user".toByteArray()) }
        val raw = checkNotNull(decodeBase64OrNull(sealed))
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 1).toByte()
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) { crypto.decrypt(raw.toBase64(), key, aad) }
    }

    @Test
    fun `garbage input is rejected without crashing`() {
        val key = crypto.newKey()
        expectKind(VaultCryptoException.Kind.MALFORMED_INPUT) { crypto.decrypt("not base64!", key, ByteArray(0)) }
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) { crypto.decrypt("AAAA", key, ByteArray(0)) }
        expectKind(VaultCryptoException.Kind.MALFORMED_INPUT) { crypto.encrypt(ByteArray(0), ByteArray(5), ByteArray(0)) }
    }

    // ── Sealed boxes ─────────────────────────────────────────────────────────

    @Test
    fun `a sealed box opens only with the matching inbox key`() {
        val inbox = crypto.newInboxKeyPair()
        val sealed = crypto.seal("hello".toByteArray(), inbox.publicKey)
        assertArrayEquals("hello".toByteArray(), crypto.openSealed(sealed, inbox))
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) { crypto.openSealed(sealed, crypto.newInboxKeyPair()) }
    }

    // ── Accounts ─────────────────────────────────────────────────────────────

    @Test
    fun `a new account unlocks with its passphrase and with its recovery key, to the same keys`() {
        val account = crypto.createAccount("correct horse battery staple")
        val blob = checkNotNull(VaultKeyringBlob.decode(account.keyringBlob))
        val credentials = crypto.credentials("correct horse battery staple", blob.kdf)
        assertEquals(account.authHash, credentials.authHash.toBase64())
        assertEquals(account.keys, crypto.unlock(blob, credentials))
        assertEquals(account.keys, crypto.unlock(blob, account.recoveryKey))
    }

    @Test
    fun `the wrong passphrase cannot open the keyring`() {
        val account = crypto.createAccount("right passphrase")
        val blob = checkNotNull(VaultKeyringBlob.decode(account.keyringBlob))
        val wrong = crypto.credentials("wrong passphrase", blob.kdf)
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) { crypto.unlock(blob, wrong) }
    }

    @Test
    fun `a wrong or malformed recovery key is refused`() {
        val account = crypto.createAccount("right passphrase")
        val blob = checkNotNull(VaultKeyringBlob.decode(account.keyringBlob))
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) {
            crypto.unlock(blob, VaultRecoveryKeyFormat.encode(crypto.newKey()))
        }
        expectKind(VaultCryptoException.Kind.MALFORMED_INPUT) { crypto.unlock(blob, "nonsense") }
    }

    @Test
    fun `the passphrase copy of the user key cannot be opened as the recovery copy`() {
        val account = crypto.createAccount("right passphrase")
        val blob = checkNotNull(VaultKeyringBlob.decode(account.keyringBlob))
        val credentials = crypto.credentials("right passphrase", blob.kdf)
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) {
            crypto.decrypt(blob.passphraseWrappedUserKey, credentials.wrapKey, vaultKeyringAad(VaultKeyringPurpose.RECOVERY))
        }
    }

    @Test
    fun `the registered auth hash and recovery key are fresh per account`() {
        val a = crypto.createAccount("same passphrase")
        val b = crypto.createAccount("same passphrase")
        assertNotEquals(a.authHash, b.authHash)
        assertNotEquals(a.recoveryKey, b.recoveryKey)
    }

    @Test
    fun `a keyring whose inbox public key was swapped is refused`() {
        val account = crypto.createAccount("right passphrase")
        val honest = checkNotNull(VaultKeyringBlob.decode(account.keyringBlob))
        val forged = honest.copy(inboxPublicKey = crypto.newInboxKeyPair().publicKey.toBase64())
        val credentials = crypto.credentials("right passphrase", honest.kdf)
        expectKind(VaultCryptoException.Kind.INBOX_KEY_MISMATCH) { crypto.unlock(forged, credentials) }
        expectKind(VaultCryptoException.Kind.INBOX_KEY_MISMATCH) { crypto.unlock(forged, account.recoveryKey) }
    }

    @Test
    fun `an unlocked account always hands out the derived public key`() {
        val account = crypto.createAccount("right passphrase")
        val blob = checkNotNull(VaultKeyringBlob.decode(account.keyringBlob))
        val keys = crypto.unlock(blob, crypto.credentials("right passphrase", blob.kdf))
        assertArrayEquals(crypto.inboxPublicKey(keys.inbox.secretKey), keys.inbox.publicKey)
    }

    @Test
    fun `secrets never print`() {
        val account = crypto.createAccount("right passphrase")
        assertFalse(account.toString().contains(account.recoveryKey))
        assertFalse(account.keys.toString().contains(account.keys.userKey.toBase64()))
        assertNotNull(account.keys.inbox.toString())
    }
}
