// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** What the person sees about the account this phone is linked to. Never contains a key. */
data class VaultAccountSummary(val username: String, val serverUrl: String)

/** Every way an account step can fail, as a meaning the screen can word, never a status code. */
sealed interface VaultAccountError {
    data object InvalidServerUrl : VaultAccountError
    /** The address answers, but not like a Pocket Nutrition vault. */
    data object NotAVault : VaultAccountError
    data object Offline : VaultAccountError
    data object InvalidUsername : VaultAccountError
    data object PassphraseTooShort : VaultAccountError
    data object PassphraseMismatch : VaultAccountError
    data object UsernameTaken : VaultAccountError
    data object RegistrationClosed : VaultAccountError
    /** Wrong username or passphrase (the server does not say which). */
    data object InvalidCredentials : VaultAccountError
    data class TooManyAttempts(val retryAfterSeconds: Int?) : VaultAccountError
    /** The server asked for key-derivation settings below the floor, or ones that contradict its keyring. */
    data object UnsafeKdf : VaultAccountError
    data object KeyringUnreadable : VaultAccountError
    /** A wrong or already-used two-factor code, or text that is not a code at all. */
    data object InvalidTwoFactor : VaultAccountError
    data object ServerError : VaultAccountError
    /** The session could not be stored in the Keystore-backed store. */
    data object Storage : VaultAccountError
    data object Crypto : VaultAccountError
}

/**
 * A finished passphrase change: what the new emergency kit shows, once. Never printed. [isGenerated] false
 * means the person chose it: the kit does not repeat it, and asks for it whole.
 */
class VaultResetKit(
    val username: String,
    val serverUrl: String,
    val passphrase: String,
    val recoveryKey: String,
    val isGenerated: Boolean,
) {
    override fun toString(): String = "VaultResetKit(…)"
}

/**
 * Why a new passphrase (registration or change) is refused, or null when it is fine: the same rule
 * everywhere, so the screen can check before the device-lock prompt.
 */
fun vaultNewPassphraseError(passphrase: String, confirmation: String): VaultAccountError? = when {
    !isAcceptableVaultPassphrase(passphrase) -> VaultAccountError.PassphraseTooShort
    passphrase != confirmation -> VaultAccountError.PassphraseMismatch
    else -> null
}

private class AccountFailure(val error: VaultAccountError) : Exception("account failure")

/**
 * Drives the vault account screen: pick a server, create an account or log in (which links this phone),
 * unlink, erase. A port of iOS `VaultAccountModel`.
 *
 * What it never does: send the passphrase anywhere (only the login hash derived from it leaves the
 * phone), keep a key outside [VaultSessionStore], or trust the server's word on key-derivation settings.
 * Argon2id runs on [Dispatchers.Default]; state is Compose state, so the screen recomposes on change.
 */
class VaultAccountModel(
    private val deviceLabel: String,
    private val makeServer: (String) -> VaultServer,
    private val crypto: () -> VaultCrypto,
    private val sessions: VaultSessionStore,
    private val rememberServerUrl: (String) -> Unit = {},
) {
    sealed interface Phase {
        data object SignedOut : Phase
        data class SignedIn(val summary: VaultAccountSummary) : Phase
    }

    var phase: Phase by mutableStateOf(Phase.SignedOut)
        private set
    /** What the checked server said about itself; null until [checkServer] succeeds. */
    var serverInfo: VaultServerInfo? by mutableStateOf(null)
        private set
    /** The recovery key of a prepared, not yet registered account: shown once before registering. */
    var pendingRecoveryKey: String? by mutableStateOf(null)
        private set
    var isWorking: Boolean by mutableStateOf(false)
        private set
    var error: VaultAccountError? by mutableStateOf(null)
        private set
    /** The passphrase was right and the account has two-factor on: ask for a code, then log in again. */
    var needsTwoFactor: Boolean by mutableStateOf(false)
        private set

    /** A passphrase reset went through: show this kit, with the mandatory-save gate, until dismissed. */
    var resetKit: VaultResetKit? by mutableStateOf(null)
        private set
    /** The account has two-factor on: ask for a code, then call [resetPassphrase] again with it. */
    var resetNeedsTwoFactor: Boolean by mutableStateOf(false)
        private set

    private class PendingAccount(val username: String, val account: VaultNewAccount, val serverUrl: String)

    private var checkedServer: String? = null
    private var session: VaultStoredSession? = null
    private var pending: PendingAccount? = null
    /** Kept across a two-factor prompt so the slow derivation runs once per change. */
    private var preparedReset: VaultPassphraseReset? = null
    private var preparedIsGenerated = true

    init {
        reload()
    }

    /** The linked session, for the sync. */
    val linkedSession: VaultStoredSession? get() = session

    fun clearError() {
        error = null
    }

    /** Re-reads the stored session, for when another part of the app changed it. */
    fun reload() {
        session = sessions.load()
        phase = session?.let { Phase.SignedIn(VaultAccountSummary(it.username, it.serverUrl)) } ?: Phase.SignedOut
    }

    /** Back to the plain login form, dropping a pending two-factor step. */
    fun cancelTwoFactor() {
        needsTwoFactor = false
        error = null
    }

    // ── Server ───────────────────────────────────────────────────────────────

    suspend fun checkServer(rawUrl: String) = run {
        val url = validatedVaultServerUrl(rawUrl) ?: throw AccountFailure(VaultAccountError.InvalidServerUrl)
        serverInfo = null
        checkedServer = null
        serverInfo = makeServer(url).serverInfo()
        checkedServer = url
        rememberServerUrl(url)
    }

    // ── Log in ───────────────────────────────────────────────────────────────

    /** [twoFactorCode] is what the person typed in the code field, once [needsTwoFactor] asked for it. */
    suspend fun login(rawUsername: String, passphrase: String, twoFactorCode: String? = null) = run {
        val twoFactor = twoFactorCode?.let { VaultTwoFactorCode.parse(it) ?: throw AccountFailure(VaultAccountError.InvalidTwoFactor) }
        val username = normalizedVaultUsername(rawUsername) ?: throw AccountFailure(VaultAccountError.InvalidUsername)
        if (passphrase.isEmpty()) throw AccountFailure(VaultAccountError.InvalidCredentials)
        val url = checkedServer ?: throw AccountFailure(VaultAccountError.InvalidServerUrl)
        val server = makeServer(url)
        var issuedKey: String? = null
        try {
            val kdf = server.prelogin(username)
            if (!isAcceptableVaultKdf(kdf)) throw AccountFailure(VaultAccountError.UnsafeKdf)
            val crypto = crypto()
            val credentials = withContext(Dispatchers.Default) { crypto.credentials(passphrase, kdf) }
            val reply = server.login(VaultLoginRequest.of(username, credentials.authHash.toBase64(), deviceLabel, twoFactor))
            issuedKey = reply.apiKey
            needsTwoFactor = false
            val blob = reply.keyring.blob?.let(VaultKeyringBlob::decode) ?: throw AccountFailure(VaultAccountError.KeyringUnreadable)
            // The wrap key came from the KDF announced at prelogin; a keyring claiming another one is the
            // server contradicting itself.
            if (blob.kdf != kdf) throw AccountFailure(VaultAccountError.UnsafeKdf)
            val keys = withContext(Dispatchers.Default) { crypto.unlock(blob, credentials) }
            link(VaultStoredSession.of(url, username, reply.accountId, reply.apiKey, reply.keyId, kdf, keys))
        } catch (e: VaultException.Outcome) {
            if (e.outcome == VaultHttpOutcome.TwoFactorRequired) {
                needsTwoFactor = true // no key was issued: nothing to revoke
                return@run
            }
            revoke(server, issuedKey)
            throw e
        } catch (e: CancellationException) {
            revoke(server, issuedKey)
            throw e
        } catch (e: Exception) {
            // The server already issued this device a key: do not leave it active on a failed link.
            revoke(server, issuedKey)
            throw e
        }
    }

    // ── Create an account (keys first, registration after the recovery key is saved) ──

    /** Validates the form and builds the account's keys locally. Nothing is sent yet. */
    suspend fun prepareAccount(rawUsername: String, passphrase: String, confirmation: String) = run {
        val info = serverInfo
        val url = checkedServer
        if (info == null || url == null) throw AccountFailure(VaultAccountError.InvalidServerUrl)
        if (!info.isRegistrationOpen) throw AccountFailure(VaultAccountError.RegistrationClosed)
        val username = normalizedVaultUsername(rawUsername) ?: throw AccountFailure(VaultAccountError.InvalidUsername)
        vaultNewPassphraseError(passphrase, confirmation)?.let { throw AccountFailure(it) }
        val crypto = crypto()
        val account = withContext(Dispatchers.Default) { crypto.createAccount(passphrase) }
        pending = PendingAccount(username, account, url)
        pendingRecoveryKey = account.recoveryKey
    }

    suspend fun registerPreparedAccount() = run {
        val prepared = pending ?: return@run
        val server = makeServer(prepared.serverUrl)
        var issuedKey: String? = null
        try {
            val reply = server.register(
                VaultRegisterRequest(
                    username = prepared.username,
                    kdf = prepared.account.kdf,
                    authHash = prepared.account.authHash,
                    keyring = prepared.account.keyringBlob,
                    deviceLabel = deviceLabel,
                ),
            )
            issuedKey = reply.apiKey
            link(
                VaultStoredSession.of(
                    prepared.serverUrl, prepared.username, reply.accountId, reply.apiKey, reply.keyId,
                    prepared.account.kdf, prepared.account.keys,
                ),
            )
            discardPendingAccount()
        } catch (e: CancellationException) {
            revoke(server, issuedKey)
            throw e
        } catch (e: Exception) {
            revoke(server, issuedKey)
            val failure = mapVaultAccountError(e)
            // Taken or closed: the form must change. A network failure keeps the prepared account, so
            // "continue" can simply be tried again.
            if (failure == VaultAccountError.UsernameTaken || failure == VaultAccountError.RegistrationClosed) discardPendingAccount()
            throw e
        }
    }

    fun discardPendingAccount() {
        pending = null
        pendingRecoveryKey = null
    }

    // ── Unlink and erase ─────────────────────────────────────────────────────

    /**
     * Signs this phone out. Returns false when the server could not be told: this device's key then stays
     * valid there until revoked from another device. The phone is signed out either way.
     */
    suspend fun logout(): Boolean {
        var reached = false
        run {
            val current = session ?: return@run
            reached = try {
                makeServer(current.serverUrl).logout(current.apiKey)
                true
            } catch (e: VaultException.Outcome) {
                e.outcome == VaultHttpOutcome.Unauthorized // the key was already revoked
            } catch (e: VaultException) {
                false
            }
            signOutLocally()
        }
        return reached
    }

    /** Erases the account. The passphrase is re-derived with the KDF this phone was linked with. */
    suspend fun deleteAccount(passphrase: String) = run {
        val current = session ?: return@run
        if (passphrase.isEmpty()) throw AccountFailure(VaultAccountError.InvalidCredentials)
        val crypto = crypto()
        val credentials = withContext(Dispatchers.Default) { crypto.credentials(passphrase, current.kdf) }
        makeServer(current.serverUrl).deleteAccount(current.apiKey, credentials.authHash.toBase64())
        signOutLocally()
    }

    // ── Change the passphrase (this device is linked, so it holds the user key) ──

    /**
     * Replaces the passphrase WITHOUT the old one, through the reset endpoint: a new passphrase (generated
     * when [ownPassphrase] is null, else the person's, checked like at registration), a fresh KDF and a
     * new recovery key wrap the unchanged user key. The server then unlinks every other device. The
     * caller has already passed the device's own lock. On success [resetKit] holds what must be saved, and
     * the stored session carries the new KDF, so this phone stays linked and syncing.
     *
     * After [resetNeedsTwoFactor], call again with [twoFactorCode]: the prepared passphrase is reused and
     * [ownPassphrase] is ignored.
     */
    suspend fun changePassphrase(ownPassphrase: String? = null, confirmation: String? = null, twoFactorCode: String? = null) = run {
        val current = session ?: return@run
        val twoFactor = twoFactorCode?.let { VaultTwoFactorCode.parse(it) ?: throw AccountFailure(VaultAccountError.InvalidTwoFactor) }
        val crypto = crypto()
        val server = makeServer(current.serverUrl)
        val reset = preparedReset ?: prepareReset(crypto, ownPassphrase, confirmation)
        var retried = false
        while (true) {
            val stored = server.keyring(current.apiKey)
            val blob = stored.blob?.let(VaultKeyringBlob::decode) ?: throw AccountFailure(VaultAccountError.KeyringUnreadable)
            val keyring = withContext(Dispatchers.Default) { reset.keyring(crypto, blob, current.keys) }
            val request = VaultResetPassphraseRequest(
                newAuthHash = reset.authHash,
                kdf = reset.kdf,
                keyring = keyring,
                baseVersion = stored.version,
                totpCode = (twoFactor as? VaultTwoFactorCode.Totp)?.code,
                recoveryCode = (twoFactor as? VaultTwoFactorCode.Recovery)?.code,
            )
            try {
                server.resetPassphrase(current.apiKey, request)
                break
            } catch (e: VaultException.Outcome) {
                when {
                    e.outcome == VaultHttpOutcome.TwoFactorRequired -> {
                        resetNeedsTwoFactor = true
                        return@run
                    }
                    // Another device changed the keyring meanwhile: refetch it and try once more.
                    e.outcome == VaultHttpOutcome.KeyringConflict && !retried -> retried = true
                    else -> throw e
                }
            }
        }
        preparedReset = null
        resetNeedsTwoFactor = false
        // The kit first: the server already switched, so the new passphrase must reach the person even if
        // the session cannot be stored.
        resetKit = VaultResetKit(current.username, current.serverUrl, reset.passphrase, reset.recoveryKey, preparedIsGenerated)
        val updated = current.copy(kdf = reset.kdf)
        session = updated
        if (!sessions.save(updated)) throw AccountFailure(VaultAccountError.Storage)
    }

    private suspend fun prepareReset(crypto: VaultCrypto, ownPassphrase: String?, confirmation: String?): VaultPassphraseReset {
        if (ownPassphrase != null) vaultNewPassphraseError(ownPassphrase, confirmation.orEmpty())?.let { throw AccountFailure(it) }
        val passphrase = ownPassphrase ?: VaultPassphraseSuggestion.make()
        val reset = withContext(Dispatchers.Default) { crypto.preparePassphraseReset(passphrase) }
        preparedReset = reset
        preparedIsGenerated = ownPassphrase == null
        return reset
    }

    /** Drops a change that has not gone through (e.g. at the two-factor prompt). */
    fun cancelReset() {
        preparedReset = null
        resetNeedsTwoFactor = false
        error = null
    }

    /** The new kit was saved: back to the normal signed-in screen. */
    fun dismissResetKit() {
        resetKit = null
    }

    // ── Plumbing ─────────────────────────────────────────────────────────────

    /** One operation at a time; failures land in [error]. */
    private suspend fun run(body: suspend () -> Unit) {
        if (isWorking) return
        error = null
        isWorking = true
        try {
            body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = mapVaultAccountError(e)
        } finally {
            isWorking = false
        }
    }

    private suspend fun revoke(server: VaultServer, key: String?) {
        if (key == null) return
        withContext(NonCancellable) { runCatching { server.logout(key) } }
    }

    private fun link(stored: VaultStoredSession) {
        if (!sessions.save(stored)) throw AccountFailure(VaultAccountError.Storage)
        session = stored
        phase = Phase.SignedIn(VaultAccountSummary(stored.username, stored.serverUrl))
    }

    private fun signOutLocally() {
        sessions.clear()
        session = null
        phase = Phase.SignedOut
    }

    private companion object {
        fun mapVaultAccountError(e: Throwable): VaultAccountError = when (e) {
            is AccountFailure -> e.error
            is VaultException.Offline -> VaultAccountError.Offline
            is VaultException.Malformed -> VaultAccountError.NotAVault
            is VaultException.Outcome -> when (val o = e.outcome) {
                VaultHttpOutcome.InvalidCredentials, VaultHttpOutcome.Unauthorized -> VaultAccountError.InvalidCredentials
                VaultHttpOutcome.UsernameTaken -> VaultAccountError.UsernameTaken
                VaultHttpOutcome.RegistrationClosed -> VaultAccountError.RegistrationClosed
                is VaultHttpOutcome.TooManyAttempts -> VaultAccountError.TooManyAttempts(o.retryAfterSeconds)
                VaultHttpOutcome.TwoFactorRequired, VaultHttpOutcome.InvalidTwoFactor, VaultHttpOutcome.InvalidCode ->
                    VaultAccountError.InvalidTwoFactor
                else -> VaultAccountError.ServerError
            }
            is VaultCryptoException -> when (e.kind) {
                VaultCryptoException.Kind.UNACCEPTABLE_KDF -> VaultAccountError.UnsafeKdf
                VaultCryptoException.Kind.DECRYPTION_FAILED,
                VaultCryptoException.Kind.INBOX_KEY_MISMATCH,
                VaultCryptoException.Kind.MALFORMED_INPUT -> VaultAccountError.KeyringUnreadable
                VaultCryptoException.Kind.DERIVATION_FAILED -> VaultAccountError.Crypto
            }
            else -> VaultAccountError.ServerError
        }
    }
}
