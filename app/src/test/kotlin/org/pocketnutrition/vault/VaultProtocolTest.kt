// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cryptography-free rules, mirroring iOS `VaultProtocolTests`, `VaultKeyringTests` and `VaultSecurityTests`. */
class VaultProtocolTest {

    // ── Passphrase normalisation ─────────────────────────────────────────────

    @Test
    fun `composed and decomposed accents derive from the same bytes`() {
        val composed = "café crème"
        val decomposed = "café crème"
        assertFalse(composed.toByteArray().contentEquals(decomposed.toByteArray()))
        assertArrayEquals(normalizedVaultPassphrase(composed), normalizedVaultPassphrase(decomposed))
    }

    @Test
    fun `compatibility forms fold and whitespace is kept`() {
        assertArrayEquals(normalizedVaultPassphrase("fish"), normalizedVaultPassphrase("ﬁsh"))
        assertFalse(normalizedVaultPassphrase(" a ").contentEquals(normalizedVaultPassphrase("a")))
        assertArrayEquals("abc".toByteArray(), normalizedVaultPassphrase("abc"))
    }

    @Test
    fun `passphrase length counts characters as a person does`() {
        assertFalse(isAcceptableVaultPassphrase("elevenchars"))
        assertTrue(isAcceptableVaultPassphrase("twelve chars"))
        // Twelve accented letters typed decomposed are still twelve characters, not twenty-four code units.
        assertTrue(isAcceptableVaultPassphrase("é".repeat(12)))
        assertFalse(isAcceptableVaultPassphrase("é".repeat(11)))
    }

    // ── Username, server ─────────────────────────────────────────────────────

    @Test
    fun `usernames are trimmed, lowercased and checked`() {
        assertEquals("alice.b-c_1", normalizedVaultUsername("  Alice.B-c_1 "))
        assertNull(normalizedVaultUsername("ab"))
        assertNull(normalizedVaultUsername("_alice"))
        assertNull(normalizedVaultUsername("alice smith"))
        assertNull(normalizedVaultUsername("élodie"))
        assertNull(normalizedVaultUsername("a".repeat(65)))
        assertEquals("a".repeat(64), normalizedVaultUsername("a".repeat(64)))
    }

    @Test
    fun `server URLs must be plain https`() {
        assertEquals("https://vault.example.org", validatedVaultServerUrl(" https://vault.example.org/ "))
        assertEquals("https://example.org/vault", validatedVaultServerUrl("https://example.org/vault"))
        assertNull(validatedVaultServerUrl("http://vault.example.org"))
        assertNull(validatedVaultServerUrl("https://user:pw@vault.example.org"))
        assertNull(validatedVaultServerUrl("https://vault.example.org?x=1"))
        assertNull(validatedVaultServerUrl("https://vault.example.org#top"))
        assertNull(validatedVaultServerUrl("vault.example.org"))
        assertNull(validatedVaultServerUrl(""))
    }

    @Test
    fun `endpoint URLs keep a self-hosted prefix`() {
        assertEquals("https://example.org/vault/v1/accounts/prelogin", vaultUrl("https://example.org/vault", listOf("accounts", "prelogin")))
        assertEquals("https://v.org/v1/sync?since=12", vaultUrl("https://v.org/", listOf("sync"), listOf("since" to "12")))
        assertEquals("https://v.org/v1/records/weight-2026-11-06", vaultUrl("https://v.org", listOf("records", "weight-2026-11-06")))
    }

    @Test
    fun `KDF settings are checked against the server floor and ceiling`() {
        val salt = ByteArray(16).toBase64()
        assertTrue(isAcceptableVaultKdf(VaultKdf(salt = salt, opslimit = 3, memlimit = 64L shl 20)))
        assertTrue(isAcceptableVaultKdf(VaultKdf(salt = salt, opslimit = 10, memlimit = 1L shl 30)))
        assertFalse(isAcceptableVaultKdf(VaultKdf(salt = salt, opslimit = 2, memlimit = 64L shl 20)))
        assertFalse(isAcceptableVaultKdf(VaultKdf(salt = salt, opslimit = 11, memlimit = 64L shl 20)))
        assertFalse(isAcceptableVaultKdf(VaultKdf(salt = salt, opslimit = 3, memlimit = (64L shl 20) - 1)))
        assertFalse(isAcceptableVaultKdf(VaultKdf(salt = salt, opslimit = 3, memlimit = (1L shl 30) + 1)))
        assertFalse(isAcceptableVaultKdf(VaultKdf(alg = "argon2i", salt = salt, opslimit = 3, memlimit = 64L shl 20)))
        assertFalse(isAcceptableVaultKdf(VaultKdf(salt = ByteArray(15).toBase64(), opslimit = 3, memlimit = 64L shl 20)))
        assertFalse(isAcceptableVaultKdf(VaultKdf(salt = "%%%", opslimit = 3, memlimit = 64L shl 20)))
    }

    @Test
    fun `record ids follow the server pattern`() {
        assertTrue(VaultProtocol.isValidRecordId("weight-2026-11-06"))
        assertTrue(VaultProtocol.isValidRecordId("0b5f2b7e-1f3a-4f7b-9c1e-3a2b1c0d9e8f"))
        assertFalse(VaultProtocol.isValidRecordId(""))
        assertFalse(VaultProtocol.isValidRecordId("a.b"))
        assertFalse(VaultProtocol.isValidRecordId("x".repeat(65)))
    }

    // ── AAD ──────────────────────────────────────────────────────────────────

    @Test
    fun `AADs follow the documented layouts`() {
        assertEquals("pnv1|acc|meal-1|meal||2|user", vaultRecordAad("acc", "meal-1", "meal", null, 2, "user").decodeToString())
        assertEquals("pnv1|acc|r|t|s1|1|scope:s1:0", vaultRecordAad("acc", "r", "t", "s1", 1, "scope:s1:0").decodeToString())
        assertEquals("pnv1|keyring|passphrase", vaultKeyringAad(VaultKeyringPurpose.PASSPHRASE).decodeToString())
        assertEquals("pnv1|keyring|recovery", vaultKeyringAad(VaultKeyringPurpose.RECOVERY).decodeToString())
        assertEquals("pnv1|keyring|inbox", vaultKeyringAad(VaultKeyringPurpose.INBOX).decodeToString())
    }

    // ── HTTP outcomes ────────────────────────────────────────────────────────

    @Test
    fun `answers are classified by meaning`() {
        assertEquals(VaultHttpOutcome.Success, classifyVaultHttp(204))
        assertEquals(VaultHttpOutcome.Unauthorized, classifyVaultHttp(401))
        assertEquals(VaultHttpOutcome.InvalidCredentials, classifyVaultHttp(401, isLogin = true))
        assertEquals(VaultHttpOutcome.InvalidCredentials, classifyVaultHttp(401, "invalid_credentials"))
        assertEquals(VaultHttpOutcome.TwoFactorRequired, classifyVaultHttp(401, "two_factor_required", isLogin = true))
        assertEquals(VaultHttpOutcome.InvalidTwoFactor, classifyVaultHttp(401, "invalid_two_factor", isLogin = true))
        assertEquals(VaultHttpOutcome.UsernameTaken, classifyVaultHttp(409, "username_taken"))
        assertEquals(VaultHttpOutcome.RevisionConflict, classifyVaultHttp(409, "revision_conflict"))
        assertEquals(VaultHttpOutcome.KeyringConflict, classifyVaultHttp(409, "keyring_conflict"))
        assertEquals(VaultHttpOutcome.TwoFactorStateChanged, classifyVaultHttp(409, "no_pending_setup"))
        assertEquals(VaultHttpOutcome.RegistrationClosed, classifyVaultHttp(403, "registration_closed"))
        assertEquals(VaultHttpOutcome.QuotaExceeded, classifyVaultHttp(413, "quota_exceeded"))
        assertEquals(VaultHttpOutcome.Other(413), classifyVaultHttp(413))
        assertEquals(VaultHttpOutcome.TooManyAttempts(900), classifyVaultHttp(429, "account_locked", "900"))
        assertEquals(VaultHttpOutcome.TooManyAttempts(null), classifyVaultHttp(429))
        assertEquals(VaultHttpOutcome.ServerError, classifyVaultHttp(503, "busy"))
        assertEquals(VaultHttpOutcome.Other(404), classifyVaultHttp(404))
    }

    // ── Two-factor codes ─────────────────────────────────────────────────────

    @Test
    fun `two-factor input is a TOTP code or a recovery code`() {
        assertEquals(VaultTwoFactorCode.Totp("123456"), VaultTwoFactorCode.parse(" 123 456 "))
        assertEquals(VaultTwoFactorCode.Recovery("abcd2345efgh"), VaultTwoFactorCode.parse("ABCD-2345-efgh"))
        assertNull(VaultTwoFactorCode.parse("12345"))
        assertNull(VaultTwoFactorCode.parse("abcd-1234-efgh")) // '1' is not Base32
        assertNull(VaultTwoFactorCode.parse(""))
        val request = VaultLoginRequest.of("u", "h", "d", VaultTwoFactorCode.Totp("123456"))
        assertEquals("123456", request.totpCode)
        assertNull(request.recoveryCode)
        // Absent fields are omitted from the JSON, as the server's optional fields expect.
        assertFalse(vaultJson.encodeToString(VaultLoginRequest.serializer(), VaultLoginRequest.of("u", "h", "d", null)).contains("code"))
    }

    // ── Recovery key ─────────────────────────────────────────────────────────

    private val key = ByteArray(32) { ((it * 7) and 255).toByte() }

    @Test
    fun `a recovery key round-trips through its display form`() {
        val text = VaultRecoveryKeyFormat.encode(key)
        assertEquals(52 + 12, text.length)
        assertTrue(text.split("-").all { it.length == 4 })
        assertArrayEquals(key, VaultRecoveryKeyFormat.decode(text))
    }

    @Test
    fun `every byte value survives`() {
        for (byte in listOf(0, 1, 127, 128, 255)) {
            val value = ByteArray(32) { byte.toByte() }
            assertArrayEquals(value, VaultRecoveryKeyFormat.decode(VaultRecoveryKeyFormat.encode(value)))
        }
    }

    @Test
    fun `the encoding is RFC 4648 Base32`() {
        // All zeros: 52 'A'. All ones: 51 '7' then 'Q' (the 1 remaining key bit followed by 4 zero padding bits).
        assertEquals("A".repeat(52).chunked(4).joinToString("-"), VaultRecoveryKeyFormat.encode(ByteArray(32)))
        assertEquals(("7".repeat(51) + "Q").chunked(4).joinToString("-"), VaultRecoveryKeyFormat.encode(ByteArray(32) { -1 }))
    }

    @Test
    fun `decoding ignores case, dashes and spaces`() {
        val text = VaultRecoveryKeyFormat.encode(key)
        assertArrayEquals(key, VaultRecoveryKeyFormat.decode(text.lowercase()))
        assertArrayEquals(key, VaultRecoveryKeyFormat.decode(text.replace("-", " ")))
        assertArrayEquals(key, VaultRecoveryKeyFormat.decode(text.replace("-", "")))
        assertArrayEquals(key, VaultRecoveryKeyFormat.decode("  $text\n"))
    }

    @Test
    fun `wrong length, wrong alphabet and nonzero padding are refused`() {
        val text = VaultRecoveryKeyFormat.encode(key)
        assertNull(VaultRecoveryKeyFormat.decode(""))
        assertNull(VaultRecoveryKeyFormat.decode(text.dropLast(5)))
        assertNull(VaultRecoveryKeyFormat.decode(text + "AAAA"))
        assertNull(VaultRecoveryKeyFormat.decode("1" + text.drop(1)))
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val chars = text.replace("-", "").toCharArray()
        chars[51] = alphabet[alphabet.indexOf(chars[51]) or 1]
        assertNull(VaultRecoveryKeyFormat.decode(String(chars)))
    }

    // ── Keyring blob ─────────────────────────────────────────────────────────

    private val blob = VaultKeyringBlob(
        inboxPublicKey = "cHVi",
        inboxWrappedSecretKey = "c2VjcmV0",
        kdf = VaultKdf(salt = "AAAAAAAAAAAAAAAAAAAAAA==", opslimit = 3, memlimit = 67_108_864),
        passphraseWrappedUserKey = "cGFzcw==",
        recoveryWrappedUserKey = "cmVjb3Zlcnk=",
        version = 1,
    )

    @Test
    fun `the blob is compact JSON with sorted keys, byte for byte what iOS writes`() {
        // Swift JSONEncoder with [.sortedKeys, .withoutEscapingSlashes] writes exactly this.
        assertEquals(
            "{\"inbox_public_key\":\"cHVi\",\"inbox_wrapped_secret_key\":\"c2VjcmV0\"," +
                "\"kdf\":{\"alg\":\"argon2id\",\"memlimit\":67108864,\"opslimit\":3,\"salt\":\"AAAAAAAAAAAAAAAAAAAAAA==\"}," +
                "\"passphrase_wrapped_user_key\":\"cGFzcw==\",\"recovery_wrapped_user_key\":\"cmVjb3Zlcnk=\",\"v\":1}",
            blob.encoded(),
        )
        assertEquals(blob, VaultKeyringBlob.decode(blob.encoded()))
        // Base64 slashes are not escaped.
        assertTrue(blob.copy(inboxPublicKey = "a/b=").encoded().contains("\"a/b=\""))
    }

    @Test
    fun `a future version or garbage does not decode, unknown fields are ignored`() {
        assertNull(VaultKeyringBlob.decode(blob.encoded().replace("\"v\":1", "\"v\":2")))
        assertNull(VaultKeyringBlob.decode(blob.encoded().replace(",\"v\":1", "")))
        assertNull(VaultKeyringBlob.decode("not json"))
        assertNull(VaultKeyringBlob.decode("{}"))
        assertEquals(blob, VaultKeyringBlob.decode(blob.encoded().replace("{\"inbox", "{\"later\":true,\"inbox")))
    }

    // ── Fingerprint and suggestion ───────────────────────────────────────────

    @Test
    fun `the EFF list is the published 7776 words`() {
        val words = EffLongWordlist.words
        assertEquals(7776, words.size)
        assertEquals(7776, words.toSet().size)
        assertEquals("abacus", words.first())
        assertEquals("zoom", words.last())
    }

    @Test
    fun `fingerprint words come from the EFF list, one per 32-bit chunk`() {
        val words = EffLongWordlist.words
        assertEquals(List(5) { words[0] }, vaultFingerprintWords(ByteArray(32)))
        // 0x00001E60 = 7776 wraps to the first word; 0x00001E61 to the second.
        val digest = byteArrayOf(0, 0, 0x1E, 0x60, 0, 0, 0x1E, 0x61) + ByteArray(24)
        val mixed = checkNotNull(vaultFingerprintWords(digest))
        assertEquals(words[0], mixed[0])
        assertEquals(words[1], mixed[1])
        // High bit set: unsigned, not negative.
        assertEquals(words[(0xFFFFFFFFL % 7776).toInt()], vaultFingerprintWords(ByteArray(32) { -1 })?.first())
        assertNull(vaultFingerprintWords(ByteArray(19)))
    }

    @Test
    fun `a suggested passphrase is five EFF words and long enough`() {
        val random = SecureRandom()
        val a = VaultPassphraseSuggestion.make(random)
        val parts = a.split("-")
        assertTrue(parts.size >= 5) // a few EFF words contain a hyphen ("yo-yo")
        assertTrue(isAcceptableVaultPassphrase(a))
        assertNotEquals(a, VaultPassphraseSuggestion.make(random))
    }
}
