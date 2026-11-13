// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resetting a forgotten passphrase from a linked device (`POST /v1/account/passphrase/reset`). */
class VaultPassphraseResetTest {

    private val crypto = testCrypto
    private val oldPassphrase = "correct horse battery staple"

    // ── Crypto ───────────────────────────────────────────────────────────────

    @Test
    fun `the reset keyring wraps the same user key under the new passphrase and the new recovery key`() {
        val account = crypto.createAccount(oldPassphrase)
        val current = checkNotNull(VaultKeyringBlob.decode(account.keyringBlob))
        val newPassphrase = VaultPassphraseSuggestion.make()
        val reset = crypto.preparePassphraseReset(newPassphrase)
        val blob = checkNotNull(VaultKeyringBlob.decode(reset.keyring(crypto, current, account.keys)))

        assertEquals(newPassphrase, reset.passphrase)
        assertEquals(reset.kdf, blob.kdf)
        assertNotEquals("fresh salt", current.kdf.salt, blob.kdf.salt)
        assertEquals(crypto.credentials(newPassphrase, reset.kdf).authHash.toBase64(), reset.authHash)
        assertEquals(account.keys, crypto.unlock(blob, crypto.credentials(newPassphrase, blob.kdf)))
        assertEquals(account.keys, crypto.unlock(blob, reset.recoveryKey))
        // Inbox fields copied as they were.
        assertEquals(current.inboxPublicKey, blob.inboxPublicKey)
        assertEquals(current.inboxWrappedSecretKey, blob.inboxWrappedSecretKey)
        // The old passphrase and the old recovery key no longer open it.
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) { crypto.unlock(blob, crypto.credentials(oldPassphrase, blob.kdf)) }
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) { crypto.unlock(blob, account.recoveryKey) }
        assertFalse(reset.toString().contains(newPassphrase))
    }

    @Test
    fun `the reset keyring has the registration shape, sorted and compact`() {
        val account = crypto.createAccount(oldPassphrase)
        val current = checkNotNull(VaultKeyringBlob.decode(account.keyringBlob))
        val text = crypto.preparePassphraseReset(VaultPassphraseSuggestion.make()).keyring(crypto, current, account.keys)
        val keys = Json.parseToJsonElement(text).jsonObject.keys.toList()
        val registered = Json.parseToJsonElement(account.keyringBlob).jsonObject.keys.toList()
        assertEquals(registered, keys)
        assertEquals(keys.sorted(), keys)
        assertFalse(text.contains(' ') || text.contains('\n'))
        assertEquals(
            Json.parseToJsonElement(account.keyringBlob).jsonObject.getValue("kdf").jsonObject.keys.toList(),
            Json.parseToJsonElement(text).jsonObject.getValue("kdf").jsonObject.keys.toList(),
        )
    }

    @Test
    fun `a keyring for other keys is never overwritten`() {
        val mine = crypto.createAccount(oldPassphrase)
        val theirs = checkNotNull(VaultKeyringBlob.decode(crypto.createAccount(oldPassphrase).keyringBlob))
        val reset = crypto.preparePassphraseReset(VaultPassphraseSuggestion.make())
        expectKind(VaultCryptoException.Kind.DECRYPTION_FAILED) { reset.keyring(crypto, theirs, mine.keys) }
        // Same user key, but a swapped inbox public key.
        val honest = checkNotNull(VaultKeyringBlob.decode(mine.keyringBlob))
        val swapped = honest.copy(inboxPublicKey = crypto.newInboxKeyPair().publicKey.toBase64())
        expectKind(VaultCryptoException.Kind.INBOX_KEY_MISMATCH) { reset.keyring(crypto, swapped, mine.keys) }
    }

    // ── Account model against the fake server ────────────────────────────────

    private val server = FakeVaultServer()
    private val firstStore = VaultSessionStore(MemorySecretStore())

    private fun model(store: VaultSessionStore) = VaultAccountModel("Pixel test", { server }, { crypto }, store)

    private fun linkedPair(): Pair<VaultAccountModel, VaultAccountModel> = runBlocking {
        val first = model(firstStore)
        first.checkServer("https://vault.example.org")
        first.prepareAccount("alice", oldPassphrase, oldPassphrase)
        first.registerPreparedAccount()
        val second = model(VaultSessionStore(MemorySecretStore()))
        second.checkServer("https://vault.example.org")
        second.login("alice", oldPassphrase)
        assertTrue(second.phase is VaultAccountModel.Phase.SignedIn)
        first to second
    }

    @Test
    fun `a reset keeps this phone linked, unlinks the others and shows a new kit`() = runBlocking {
        val (first, second) = linkedPair()
        val before = checkNotNull(firstStore.load())
        first.changePassphrase()
        assertNull(first.error)
        val kit = checkNotNull(first.resetKit)
        assertTrue(kit.isGenerated)
        assertEquals(VaultPassphraseSuggestion.WORD_COUNT, VaultKitCheck.words(kit.passphrase).size)
        val account = server.accounts.getValue("alice")
        assertEquals(2, account.keyringVersion)
        assertEquals(1, server.resetRequests.single().baseVersion)

        // The stored session carries the new KDF; the device key and the keys are unchanged.
        val after = checkNotNull(firstStore.load())
        assertEquals(account.kdf, after.kdf)
        assertNotEquals(before.kdf, after.kdf)
        assertEquals(before.apiKey, after.apiKey)
        assertEquals(before.keys, after.keys)
        assertTrue(first.phase is VaultAccountModel.Phase.SignedIn)
        server.sync(after.apiKey, 0) // still authorised

        // The other device is unlinked.
        val secondKey = checkNotNull(second.linkedSession).apiKey
        assertThrows(VaultException.Outcome::class.java) { runBlocking { server.sync(secondKey, 0) } }

        // The new passphrase logs in and opens the same keys; the old one does not.
        val third = model(VaultSessionStore(MemorySecretStore()))
        third.checkServer("https://vault.example.org")
        third.login("alice", oldPassphrase)
        assertEquals(VaultAccountError.InvalidCredentials, third.error)
        third.login("alice", kit.passphrase)
        assertNull(third.error)
        assertEquals(before.keys, checkNotNull(third.linkedSession).keys)
        assertEquals(before.keys, crypto.unlock(checkNotNull(VaultKeyringBlob.decode(account.keyring)), kit.recoveryKey))

        first.dismissResetKit()
        assertNull(first.resetKit)
    }

    @Test
    fun `a keyring conflict refetches and retries once`() = runBlocking {
        val (first, _) = linkedPair()
        val account = server.accounts.getValue("alice")
        server.keyringChangesBeforeReset = 1
        first.changePassphrase()
        assertNull(first.error)
        assertNotNull(first.resetKit)
        assertEquals(listOf(1, 2), server.resetRequests.map { it.baseVersion })
        assertEquals(3, account.keyringVersion)
    }

    @Test
    fun `a second conflict gives up without a kit and keeps the old session`() = runBlocking {
        val (first, _) = linkedPair()
        val before = checkNotNull(firstStore.load())
        server.keyringChangesBeforeReset = 2
        first.changePassphrase()
        assertEquals(VaultAccountError.ServerError, first.error)
        assertNull(first.resetKit)
        assertEquals(2, server.resetRequests.size)
        assertEquals(before.kdf, checkNotNull(firstStore.load()).kdf)
    }

    @Test
    fun `with two-factor on, a code is asked for and the same reset goes through with it`() = runBlocking {
        val (first, _) = linkedPair()
        server.accounts.getValue("alice").totpCode = "123456"
        first.changePassphrase()
        assertTrue(first.resetNeedsTwoFactor)
        assertNull(first.resetKit)
        assertNull(first.error)

        first.changePassphrase(twoFactorCode = "654321")
        assertEquals(VaultAccountError.InvalidTwoFactor, first.error)
        assertNull(first.resetKit)

        first.changePassphrase(twoFactorCode = "123 456")
        assertNull(first.error)
        assertFalse(first.resetNeedsTwoFactor)
        assertNotNull(first.resetKit)
        // The slow derivation ran once: every attempt sent the same new credentials.
        assertEquals(1, server.resetRequests.map { it.newAuthHash }.distinct().size)
        assertEquals("123456", server.resetRequests.last().totpCode)
    }

    @Test
    fun `cancelling at the two-factor prompt forgets the prepared reset`() = runBlocking {
        val (first, _) = linkedPair()
        server.accounts.getValue("alice").totpCode = "123456"
        first.changePassphrase()
        first.cancelReset()
        assertFalse(first.resetNeedsTwoFactor)
        first.changePassphrase(twoFactorCode = "123456")
        assertEquals(2, server.resetRequests.map { it.newAuthHash }.distinct().size)
    }

    @Test
    fun `the kit is shown even when the new session cannot be stored`() = runBlocking {
        val secrets = MemorySecretStore()
        val store = VaultSessionStore(secrets)
        val first = model(store)
        first.checkServer("https://vault.example.org")
        first.prepareAccount("alice", oldPassphrase, oldPassphrase)
        first.registerPreparedAccount()
        secrets.failWrites = true
        first.changePassphrase()
        assertEquals(VaultAccountError.Storage, first.error)
        assertNotNull("the server already switched: the new passphrase must still reach the person", first.resetKit)
    }

    @Test
    fun `a chosen passphrase follows the registration rule and becomes the new one`() = runBlocking {
        val (first, _) = linkedPair()
        first.changePassphrase("too short", "too short")
        assertEquals(VaultAccountError.PassphraseTooShort, first.error)
        first.changePassphrase("a perfectly long phrase", "a perfectly long phrase!")
        assertEquals(VaultAccountError.PassphraseMismatch, first.error)
        assertTrue("nothing sent for a refused passphrase", server.resetRequests.isEmpty())

        val chosen = "a perfectly long phrase"
        first.changePassphrase(chosen, chosen)
        assertNull(first.error)
        val kit = checkNotNull(first.resetKit)
        assertFalse(kit.isGenerated)
        assertEquals(chosen, kit.passphrase)
        val account = server.accounts.getValue("alice")
        assertEquals(crypto.credentials(chosen, account.kdf).authHash.toBase64(), account.authHash)
        val blob = checkNotNull(VaultKeyringBlob.decode(account.keyring))
        assertEquals(checkNotNull(firstStore.load()).keys, crypto.unlock(blob, kit.recoveryKey))
        assertEquals(account.kdf, checkNotNull(firstStore.load()).kdf)
    }

    @Test
    fun `a chosen passphrase survives the two-factor prompt`() = runBlocking {
        val (first, _) = linkedPair()
        server.accounts.getValue("alice").totpCode = "123456"
        val chosen = "a perfectly long phrase"
        first.changePassphrase(chosen, chosen)
        assertTrue(first.resetNeedsTwoFactor)
        first.changePassphrase(twoFactorCode = "123456")
        val kit = checkNotNull(first.resetKit)
        assertEquals(chosen, kit.passphrase)
        assertFalse(kit.isGenerated)
    }

    @Test
    fun `registration and change share one passphrase rule`() {
        assertEquals(VaultAccountError.PassphraseTooShort, vaultNewPassphraseError("short", "short"))
        assertEquals(VaultAccountError.PassphraseMismatch, vaultNewPassphraseError("long enough phrase", "long enough phrase."))
        assertNull(vaultNewPassphraseError("long enough phrase", "long enough phrase"))
    }

    private fun expectKind(kind: VaultCryptoException.Kind, block: () -> Unit) {
        val e = assertThrows(VaultCryptoException::class.java) { block() }
        assertEquals(kind, e.kind)
    }
}
