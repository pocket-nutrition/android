// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Onboarding against a fake server, as iOS `VaultAccountModelTests`. */
class VaultAccountModelTest {

    private val server = FakeVaultServer()
    private val secrets = MemorySecretStore()
    private val sessions = VaultSessionStore(secrets)
    private val passphrase = "correct horse battery staple"

    private fun model(store: VaultSessionStore = sessions) = VaultAccountModel(
        deviceLabel = "Pixel test",
        makeServer = { server },
        crypto = { testCrypto },
        sessions = store,
    )

    private fun VaultAccountModel.createAccount(username: String = "alice") = runBlocking {
        checkServer("https://vault.example.org")
        prepareAccount(username, passphrase, passphrase)
        registerPreparedAccount()
    }

    @Test
    fun `creating an account shows the recovery key first, then links this phone`() = runBlocking {
        val model = model()
        model.checkServer("https://vault.example.org")
        assertEquals("Test vault", model.serverInfo?.name)
        model.prepareAccount("Alice", passphrase, passphrase)
        assertNotNull(model.pendingRecoveryKey)
        assertTrue("nothing is sent before the recovery key is saved", server.accounts.isEmpty())
        assertEquals(VaultAccountModel.Phase.SignedOut, model.phase)

        model.registerPreparedAccount()
        assertNull(model.error)
        assertNull(model.pendingRecoveryKey)
        assertEquals(VaultAccountModel.Phase.SignedIn(VaultAccountSummary("alice", "https://vault.example.org")), model.phase)
        val stored = checkNotNull(sessions.load())
        assertEquals(server.accounts.getValue("alice").id, stored.accountId)
        // The registered keyring opens with the passphrase to the keys this phone kept.
        val blob = checkNotNull(VaultKeyringBlob.decode(server.accounts.getValue("alice").keyring))
        assertEquals(stored.keys, testCrypto.unlock(blob, testCrypto.credentials(passphrase, blob.kdf)))
        // The session never prints a key.
        assertFalse(stored.toString().contains(stored.apiKey))
    }

    @Test
    fun `the form is checked before any work`() = runBlocking {
        val model = model()
        model.prepareAccount("alice", passphrase, passphrase)
        assertEquals(VaultAccountError.InvalidServerUrl, model.error)
        model.checkServer("http://vault.example.org")
        assertEquals(VaultAccountError.InvalidServerUrl, model.error)
        model.checkServer("https://vault.example.org")
        model.prepareAccount("a", passphrase, passphrase)
        assertEquals(VaultAccountError.InvalidUsername, model.error)
        model.prepareAccount("alice", "short", "short")
        assertEquals(VaultAccountError.PassphraseTooShort, model.error)
        model.prepareAccount("alice", passphrase, "$passphrase!")
        assertEquals(VaultAccountError.PassphraseMismatch, model.error)
        server.registrationOpen = false
        model.checkServer("https://vault.example.org")
        model.prepareAccount("alice", passphrase, passphrase)
        assertEquals(VaultAccountError.RegistrationClosed, model.error)
    }

    @Test
    fun `a taken username sends the person back to the form`() = runBlocking {
        model().createAccount("alice")
        val other = model(VaultSessionStore(MemorySecretStore()))
        other.checkServer("https://vault.example.org")
        other.prepareAccount("alice", passphrase, passphrase)
        other.registerPreparedAccount()
        assertEquals(VaultAccountError.UsernameTaken, other.error)
        assertNull(other.pendingRecoveryKey)
    }

    @Test
    fun `a network failure keeps the prepared account for another try`() = runBlocking {
        val model = model()
        model.checkServer("https://vault.example.org")
        model.prepareAccount("alice", passphrase, passphrase)
        server.offline = true
        model.registerPreparedAccount()
        assertEquals(VaultAccountError.Offline, model.error)
        assertNotNull(model.pendingRecoveryKey)
        server.offline = false
        model.registerPreparedAccount()
        assertTrue(model.phase is VaultAccountModel.Phase.SignedIn)
    }

    @Test
    fun `logging in on a second phone links it to the same keys`() = runBlocking {
        model().createAccount()
        val firstKeys = checkNotNull(sessions.load()).keys
        val secondStore = VaultSessionStore(MemorySecretStore())
        val second = model(secondStore)
        second.checkServer("https://vault.example.org")
        second.login(" ALICE ", passphrase)
        assertNull(second.error)
        assertTrue(second.phase is VaultAccountModel.Phase.SignedIn)
        assertEquals(firstKeys, checkNotNull(secondStore.load()).keys)
    }

    @Test
    fun `a wrong passphrase is refused and nothing is stored`() = runBlocking {
        model().createAccount()
        val store = VaultSessionStore(MemorySecretStore())
        val second = model(store)
        second.checkServer("https://vault.example.org")
        second.login("alice", "wrong passphrase!")
        assertEquals(VaultAccountError.InvalidCredentials, second.error)
        assertNull(store.load())
    }

    @Test
    fun `two-factor asks for a code, then accepts the right one`() = runBlocking {
        model().createAccount()
        server.accounts.getValue("alice").totpCode = "123456"
        val store = VaultSessionStore(MemorySecretStore())
        val second = model(store)
        second.checkServer("https://vault.example.org")
        second.login("alice", passphrase)
        assertTrue(second.needsTwoFactor)
        assertNull(second.error)
        second.login("alice", passphrase, "654321")
        assertEquals(VaultAccountError.InvalidTwoFactor, second.error)
        second.login("alice", passphrase, "not a code")
        assertEquals(VaultAccountError.InvalidTwoFactor, second.error)
        second.login("alice", passphrase, "123 456")
        assertNull(second.error)
        assertFalse(second.needsTwoFactor)
        assertNotNull(store.load())
    }

    @Test
    fun `a downgraded KDF at prelogin is refused before deriving`() = runBlocking {
        model().createAccount()
        server.preloginOverride = VaultKdf(salt = ByteArray(16).toBase64(), opslimit = 1, memlimit = 8L shl 20)
        val second = model(VaultSessionStore(MemorySecretStore()))
        second.checkServer("https://vault.example.org")
        second.login("alice", passphrase)
        assertEquals(VaultAccountError.UnsafeKdf, second.error)
    }

    @Test
    fun `a keyring that contradicts prelogin is refused and the issued key revoked`() = runBlocking {
        model().createAccount()
        val honest = checkNotNull(VaultKeyringBlob.decode(server.accounts.getValue("alice").keyring))
        server.keyringOverride = honest.copy(kdf = honest.kdf.copy(opslimit = 4)).encoded()
        val store = VaultSessionStore(MemorySecretStore())
        val second = model(store)
        second.checkServer("https://vault.example.org")
        second.login("alice", passphrase)
        assertEquals(VaultAccountError.UnsafeKdf, second.error)
        assertNull(store.load())
        assertEquals(1, server.logouts.size)
    }

    @Test
    fun `a keyring with a swapped inbox key is unreadable`() = runBlocking {
        model().createAccount()
        val honest = checkNotNull(VaultKeyringBlob.decode(server.accounts.getValue("alice").keyring))
        server.keyringOverride = honest.copy(inboxPublicKey = testCrypto.newInboxKeyPair().publicKey.toBase64()).encoded()
        val second = model(VaultSessionStore(MemorySecretStore()))
        second.checkServer("https://vault.example.org")
        second.login("alice", passphrase)
        assertEquals(VaultAccountError.KeyringUnreadable, second.error)
        assertEquals(1, server.logouts.size)
    }

    @Test
    fun `a session that cannot be stored is not linked, and its key is revoked`() = runBlocking {
        val model = model(VaultSessionStore(MemorySecretStore(failWrites = true)))
        model.createAccount()
        assertEquals(VaultAccountError.Storage, model.error)
        assertEquals(VaultAccountModel.Phase.SignedOut, model.phase)
        assertEquals(1, server.logouts.size)
    }

    @Test
    fun `logging out revokes this device's key and forgets the session`() = runBlocking {
        val model = model()
        model.createAccount()
        val key = checkNotNull(sessions.load()).apiKey
        assertTrue(model.logout())
        assertEquals(VaultAccountModel.Phase.SignedOut, model.phase)
        assertNull(sessions.load())
        assertFalse(key in server.keys)
    }

    @Test
    fun `logging out offline still signs out, and says the server was not told`() = runBlocking {
        val model = model()
        model.createAccount()
        server.offline = true
        assertFalse(model.logout())
        assertEquals(VaultAccountModel.Phase.SignedOut, model.phase)
        assertNull(sessions.load())
    }

    @Test
    fun `deleting the account needs the passphrase`() = runBlocking {
        val model = model()
        model.createAccount()
        model.deleteAccount("wrong passphrase!")
        assertEquals(VaultAccountError.InvalidCredentials, model.error)
        assertTrue(model.phase is VaultAccountModel.Phase.SignedIn)
        model.deleteAccount(passphrase)
        assertNull(model.error)
        assertEquals(VaultAccountModel.Phase.SignedOut, model.phase)
        assertTrue(server.accounts.isEmpty())
    }

    @Test
    fun `a stored session comes back on the next launch`() {
        model().createAccount()
        assertTrue(model().phase is VaultAccountModel.Phase.SignedIn)
        secrets.value = "{not json"
        assertEquals(VaultAccountModel.Phase.SignedOut, model().phase)
    }
}
