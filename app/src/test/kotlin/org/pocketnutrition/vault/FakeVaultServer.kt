// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import org.pocketnutrition.recognition.SecretStore

/**
 * An in-memory vault with the server's rules (pocket-nutrition-vault `app/routes.py`, `app/accounts.py`):
 * per-account `seq`, revision = current + 1 or 409, tombstones, 404 on deleting an unknown record, paged
 * sync, device keys, prelogin with stable fake settings, and two-factor at login.
 */
class FakeVaultServer(
    var registrationOpen: Boolean = true,
    private val pageSize: Int = 200,
) : VaultServer {

    class Account(val id: String, val username: String, var kdf: VaultKdf, var authHash: String, var keyring: String) {
        var totpCode: String? = null
        var keyringVersion = 1
        var seq = 0L
        val records = linkedMapOf<String, StoredRecord>()
    }

    data class StoredRecord(
        val id: String,
        val type: String,
        val revision: Int,
        val keyId: String,
        val ciphertext: String,
        val deleted: Boolean,
        val seq: Long,
    )

    val accounts = mutableMapOf<String, Account>()
    /** api key -> account id; revoked keys are removed. */
    val keys = mutableMapOf<String, String>()
    val logouts = mutableListOf<String>()
    var offline = false
    /** Lets a test override what prelogin answers (a downgrade attempt). */
    var preloginOverride: VaultKdf? = null
    /** Lets a test tamper with the keyring a login returns. */
    var keyringOverride: String? = null
    /** Called before a write is applied, to simulate another device writing first. */
    var beforeWrite: ((String) -> Unit)? = null
    var putCount = 0
    private var nextId = 0

    private fun outcome(o: VaultHttpOutcome): Nothing = throw VaultException.Outcome(o)

    private fun online() {
        if (offline) throw VaultException.Offline
    }

    private fun owner(apiKey: String): Account {
        online()
        val id = keys[apiKey] ?: outcome(VaultHttpOutcome.Unauthorized)
        return accounts.values.first { it.id == id }
    }

    private fun issueKey(account: Account): VaultSession {
        val key = "pnv_key_${nextId++}"
        keys[key] = account.id
        return VaultSession(account.id, key, "kid-$nextId", VaultKeyring(keyringOverride ?: account.keyring, account.keyringVersion))
    }

    override suspend fun serverInfo(): VaultServerInfo {
        online()
        return VaultServerInfo("Test vault", "1.0", if (registrationOpen) "open" else "closed", 1_000_000)
    }

    override suspend fun prelogin(username: String): VaultKdf {
        online()
        preloginOverride?.let { return it }
        return accounts[username]?.kdf ?: VaultKdf(salt = ByteArray(16) { username.hashCode().toByte() }.toBase64(), opslimit = 3, memlimit = 64L shl 20)
    }

    override suspend fun register(request: VaultRegisterRequest): VaultSession {
        online()
        if (!registrationOpen) outcome(classifyVaultHttp(403, "registration_closed"))
        if (request.username in accounts) outcome(classifyVaultHttp(409, "username_taken"))
        val account = Account("acc-${nextId++}", request.username, request.kdf, request.authHash, request.keyring)
        accounts[request.username] = account
        return issueKey(account)
    }

    override suspend fun login(request: VaultLoginRequest): VaultSession {
        online()
        val account = accounts[request.username]
        if (account == null || account.authHash != request.authHash) outcome(classifyVaultHttp(401, "invalid_credentials", isLogin = true))
        val code = account.totpCode
        if (code != null) {
            if (request.totpCode == null && request.recoveryCode == null) outcome(classifyVaultHttp(401, "two_factor_required", isLogin = true))
            if (request.totpCode != code) outcome(classifyVaultHttp(401, "invalid_two_factor", isLogin = true))
        }
        return issueKey(account)
    }

    override suspend fun logout(apiKey: String) {
        owner(apiKey)
        keys.remove(apiKey)
        logouts += apiKey
    }

    override suspend fun deleteAccount(apiKey: String, authHash: String) {
        val account = owner(apiKey)
        if (account.authHash != authHash) outcome(classifyVaultHttp(401, "invalid_credentials"))
        accounts.remove(account.username)
        keys.entries.removeAll { it.value == account.id }
    }

    /** How many upcoming resets find the keyring changed by another device first (a stale base_version). */
    var keyringChangesBeforeReset = 0
    val resetRequests = mutableListOf<VaultResetPassphraseRequest>()

    override suspend fun keyring(apiKey: String): VaultKeyring {
        val account = owner(apiKey)
        return VaultKeyring(account.keyring, account.keyringVersion)
    }

    override suspend fun resetPassphrase(apiKey: String, request: VaultResetPassphraseRequest): VaultPassphraseChanged {
        val account = owner(apiKey)
        resetRequests += request
        val code = account.totpCode
        if (code != null) {
            if (request.totpCode == null && request.recoveryCode == null) outcome(classifyVaultHttp(401, "two_factor_required"))
            if (request.totpCode != code) outcome(classifyVaultHttp(401, "invalid_two_factor"))
        }
        if (keyringChangesBeforeReset > 0) {
            keyringChangesBeforeReset -= 1
            account.keyringVersion += 1
        }
        if (request.baseVersion != account.keyringVersion) outcome(classifyVaultHttp(409, "keyring_conflict"))
        account.authHash = request.newAuthHash
        account.kdf = request.kdf
        account.keyring = request.keyring
        account.keyringVersion += 1
        val others = keys.filter { it.value == account.id && it.key != apiKey }.keys
        others.forEach(keys::remove)
        return VaultPassphraseChanged(account.keyringVersion, others.size)
    }

    override suspend fun sync(apiKey: String, since: Long): VaultSyncPage {
        val account = owner(apiKey)
        val rows = account.records.values.filter { it.seq > since }.sortedBy { it.seq }
        val page = rows.take(pageSize)
        return VaultSyncPage(
            records = page.map { VaultRecord(it.id, it.type, null, it.revision, it.keyId, it.ciphertext, it.deleted, it.seq) },
            cursor = page.lastOrNull()?.seq ?: since,
            hasMore = rows.size > pageSize,
        )
    }

    override suspend fun putRecord(apiKey: String, id: String, write: VaultRecordWrite) {
        val account = owner(apiKey)
        require(VaultProtocol.isValidRecordId(id)) { "422" }
        beforeWrite?.invoke(id)
        val current = account.records[id]?.revision ?: 0
        if (write.revision != current + 1) outcome(classifyVaultHttp(409, "revision_conflict"))
        account.seq += 1
        putCount += 1
        account.records[id] = StoredRecord(id, write.type, write.revision, write.keyId, write.ciphertext, false, account.seq)
    }

    override suspend fun deleteRecord(apiKey: String, id: String, revision: Int) {
        val account = owner(apiKey)
        beforeWrite?.invoke(id)
        val record = account.records[id] ?: outcome(VaultHttpOutcome.Other(404))
        if (revision != record.revision + 1) outcome(classifyVaultHttp(409, "revision_conflict"))
        account.seq += 1
        account.records[id] = record.copy(revision = revision, ciphertext = "", deleted = true, seq = account.seq)
    }

    /** Writes a record as another device of [accountId] would, encrypting [plaintext] under [userKey]. */
    fun writeAsOtherDevice(accountId: String, userKey: ByteArray, id: String, type: String, plaintext: String) {
        val account = accounts.values.first { it.id == accountId }
        val revision = (account.records[id]?.revision ?: 0) + 1
        val aad = vaultRecordAad(account.id, id, type, null, revision, VaultProtocol.USER_KEY_ID)
        val ciphertext = testCrypto.encrypt(plaintext.toByteArray(), userKey, aad)
        account.seq += 1
        account.records[id] = StoredRecord(id, type, revision, VaultProtocol.USER_KEY_ID, ciphertext, false, account.seq)
    }

    fun deleteAsOtherDevice(accountId: String, id: String) {
        val account = accounts.values.first { it.id == accountId }
        val record = account.records.getValue(id)
        account.seq += 1
        account.records[id] = record.copy(revision = record.revision + 1, ciphertext = "", deleted = true, seq = account.seq)
    }

    /** Opens a stored record with [userKey], checking its AAD the way a client must. */
    fun plaintextOf(accountId: String, userKey: ByteArray, id: String): String? {
        val account = accounts.values.first { it.id == accountId }
        val record = account.records[id]?.takeIf { !it.deleted } ?: return null
        val aad = vaultRecordAad(account.id, id, record.type, null, record.revision, record.keyId)
        return testCrypto.decrypt(record.ciphertext, userKey, aad).decodeToString()
    }
}

/** A [SecretStore] in memory, standing in for the Keystore-backed one. */
class MemorySecretStore(var failWrites: Boolean = false) : SecretStore {
    var value: String? = null
    override fun read(): String? = value
    override fun write(value: String): Boolean {
        if (failWrites) return false
        this.value = value
        return true
    }
    override fun clear() {
        value = null
    }
}
