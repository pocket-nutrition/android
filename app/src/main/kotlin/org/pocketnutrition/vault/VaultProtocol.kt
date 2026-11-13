// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import java.net.URI
import java.text.Normalizer
import java.util.Base64

// The vault protocol, as far as it can be expressed without cryptography. The rules mirror
// `pocket-nutrition-vault/docs/PROTOCOL.md` and the iOS client (`VaultProtocol.swift`); the cipher work
// lives in [VaultCrypto].

object VaultProtocol {
    /** First field of every record's authenticated data. Bumped if the AAD layout ever changes. */
    const val AAD_VERSION = "pnv1"
    /** `key_id` of records encrypted under the user key. */
    const val USER_KEY_ID = "user"
    /** `key_id` of records sealed to the phone's inbox public key by a drop. */
    const val INBOX_KEY_ID = "inbox"

    // The server refuses anything below these, and so does the client: a hostile server must not be able
    // to talk the phone into deriving its keys with a weak KDF. The floor IS the app's own default.
    const val MIN_KDF_OPS_LIMIT = 3
    const val MAX_KDF_OPS_LIMIT = 10
    const val MIN_KDF_MEM_LIMIT = 64L * 1024 * 1024
    const val MAX_KDF_MEM_LIMIT = 1024L * 1024 * 1024
    const val KDF_SALT_BYTES = 16
    const val DEFAULT_KDF_OPS_LIMIT = 3
    const val DEFAULT_KDF_MEM_LIMIT = 64L * 1024 * 1024

    /** The server never sees the passphrase, so the app enforces its length. */
    const val MIN_PASSPHRASE_LENGTH = 12

    const val AUTH_HASH_BYTES = 32
    const val KEY_BYTES = 32

    const val DEFAULT_SERVER_URL = "https://vault.pocketnutrition.org"

    private val RECORD_ID_RE = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** Record ids the server accepts. */
    fun isValidRecordId(id: String): Boolean = RECORD_ID_RE.matches(id)
}

/** `pnv1|<account_id>|<record_id>|<type>|<scope_id or "">|<revision>|<key_id>` as UTF-8. */
fun vaultRecordAad(
    accountId: String,
    recordId: String,
    type: String,
    scopeId: String?,
    revision: Int,
    keyId: String,
): ByteArray = listOf(VaultProtocol.AAD_VERSION, accountId, recordId, type, scopeId ?: "", revision.toString(), keyId)
    .joinToString("|")
    .toByteArray(Charsets.UTF_8)

/** What a keyring entry is wrapped for; part of the AAD so one wrapped key cannot pose as another. */
enum class VaultKeyringPurpose(val id: String) { PASSPHRASE("passphrase"), RECOVERY("recovery"), INBOX("inbox") }

/** `pnv1|keyring|<purpose>` */
fun vaultKeyringAad(purpose: VaultKeyringPurpose): ByteArray =
    "${VaultProtocol.AAD_VERSION}|keyring|${purpose.id}".toByteArray(Charsets.UTF_8)

/**
 * The passphrase exactly as it is fed to the KDF: Unicode NFKC, then UTF-8. Whitespace is kept. An
 * accent typed as one character or as letter + combining mark then derives the same key on every client.
 */
fun normalizedVaultPassphrase(raw: String): ByteArray =
    Normalizer.normalize(raw, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)

/**
 * Whether a new passphrase is long enough: at least 12 characters as a person counts them (an accented
 * letter is one), after the same normalisation the KDF applies.
 */
fun isAcceptableVaultPassphrase(raw: String): Boolean {
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(normalized)
    var count = 0
    while (iterator.next() != java.text.BreakIterator.DONE) count++
    return count >= VaultProtocol.MIN_PASSPHRASE_LENGTH
}

/**
 * The username as the server will see it: trimmed and lowercased, 3 to 64 characters of
 * `a-z 0-9 _ . -`, starting with a letter or digit. Null when it cannot be a valid username.
 */
fun normalizedVaultUsername(raw: String): String? {
    val name = raw.trim().lowercase(java.util.Locale.ROOT)
    return name.takeIf { USERNAME_RE.matches(it) }
}

private val USERNAME_RE = Regex("^[a-z0-9][a-z0-9_.-]{2,63}$")

/**
 * `https` only, with a host, and no credentials, query or fragment. The login hash and every record go
 * to this address, so plain `http` is refused. Returns the address without a trailing slash.
 */
fun validatedVaultServerUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrEmpty() || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
    return trimmed.trimEnd('/')
}

/** `{base}/v1/<segments…>`, appended to whatever path the server URL already has. */
fun vaultUrl(base: String, segments: List<String>, query: List<Pair<String, String>> = emptyList()): String {
    val encoded = segments.joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    val q = if (query.isEmpty()) "" else query.joinToString("&", prefix = "?") { (k, v) ->
        "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
    }
    return "${base.trimEnd('/')}/v1/$encoded$q"
}

/** Whether key-derivation settings a server returned are safe to derive keys with. */
fun isAcceptableVaultKdf(kdf: VaultKdf): Boolean {
    if (kdf.alg != "argon2id") return false
    if (kdf.opslimit !in VaultProtocol.MIN_KDF_OPS_LIMIT..VaultProtocol.MAX_KDF_OPS_LIMIT) return false
    if (kdf.memlimit !in VaultProtocol.MIN_KDF_MEM_LIMIT..VaultProtocol.MAX_KDF_MEM_LIMIT) return false
    val salt = decodeBase64OrNull(kdf.salt) ?: return false
    return salt.size == VaultProtocol.KDF_SALT_BYTES
}

internal fun decodeBase64OrNull(text: String): ByteArray? =
    runCatching { Base64.getDecoder().decode(text) }.getOrNull()

internal fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

// ── HTTP outcomes ────────────────────────────────────────────────────────────

/** What a vault response means to the app, so screens branch on a meaning and not on a status code. */
sealed interface VaultHttpOutcome {
    data object Success : VaultHttpOutcome
    /** 401 on a session call: the device key was revoked or the account erased. */
    data object Unauthorized : VaultHttpOutcome
    /** 401 on login, or `invalid_credentials`: wrong username or passphrase. */
    data object InvalidCredentials : VaultHttpOutcome
    data object UsernameTaken : VaultHttpOutcome
    data object RegistrationClosed : VaultHttpOutcome
    /** 429: too many attempts or the account is locked. Seconds to wait, when the server said. */
    data class TooManyAttempts(val retryAfterSeconds: Int?) : VaultHttpOutcome
    data object QuotaExceeded : VaultHttpOutcome
    data object RevisionConflict : VaultHttpOutcome
    data object TwoFactorRequired : VaultHttpOutcome
    data object InvalidTwoFactor : VaultHttpOutcome
    data object InvalidCode : VaultHttpOutcome
    data object KeyringConflict : VaultHttpOutcome
    data object NoPassphrase : VaultHttpOutcome
    data object TwoFactorStateChanged : VaultHttpOutcome
    data object ServerError : VaultHttpOutcome
    data class Other(val status: Int) : VaultHttpOutcome
}

/** Classifies a vault answer. [errorCode] is `detail.error` when the body carried one. */
fun classifyVaultHttp(status: Int, errorCode: String? = null, retryAfter: String? = null, isLogin: Boolean = false): VaultHttpOutcome =
    when {
        status in 200..299 -> VaultHttpOutcome.Success
        status == 401 && errorCode == "two_factor_required" -> VaultHttpOutcome.TwoFactorRequired
        status == 401 && errorCode == "invalid_two_factor" -> VaultHttpOutcome.InvalidTwoFactor
        // A wrong passphrase on a call made with a valid device key: not a revoked device.
        status == 401 && errorCode == "invalid_credentials" -> VaultHttpOutcome.InvalidCredentials
        status == 401 -> if (isLogin) VaultHttpOutcome.InvalidCredentials else VaultHttpOutcome.Unauthorized
        status == 400 && errorCode == "invalid_code" -> VaultHttpOutcome.InvalidCode
        status == 409 && errorCode == "keyring_conflict" -> VaultHttpOutcome.KeyringConflict
        status == 409 && errorCode == "no_passphrase" -> VaultHttpOutcome.NoPassphrase
        status == 409 && errorCode in TWO_FACTOR_STATE_ERRORS -> VaultHttpOutcome.TwoFactorStateChanged
        status == 403 && errorCode == "registration_closed" -> VaultHttpOutcome.RegistrationClosed
        status == 409 && errorCode == "username_taken" -> VaultHttpOutcome.UsernameTaken
        status == 409 && errorCode == "revision_conflict" -> VaultHttpOutcome.RevisionConflict
        status == 413 && errorCode == "quota_exceeded" -> VaultHttpOutcome.QuotaExceeded
        status == 429 -> VaultHttpOutcome.TooManyAttempts(retryAfter?.trim()?.toIntOrNull())
        status in 500..599 -> VaultHttpOutcome.ServerError
        else -> VaultHttpOutcome.Other(status)
    }

private val TWO_FACTOR_STATE_ERRORS = setOf("two_factor_enabled", "two_factor_disabled", "no_pending_setup")

// ── Two-factor code ──────────────────────────────────────────────────────────

/** What a person typed in the two-factor field: six digits, or a recovery code `xxxx-xxxx-xxxx`. */
sealed interface VaultTwoFactorCode {
    data class Totp(val code: String) : VaultTwoFactorCode
    data class Recovery(val code: String) : VaultTwoFactorCode

    companion object {
        /** Null when the text can be neither. */
        fun parse(raw: String): VaultTwoFactorCode? {
            val compact = raw.filterNot { it.isWhitespace() }
            if (compact.length == 6 && compact.all { it in '0'..'9' }) return Totp(compact)
            val letters = compact.lowercase(java.util.Locale.ROOT).filter { it != '-' }
            if (letters.length == 12 && letters.all { it in 'a'..'z' || it in '2'..'7' }) return Recovery(letters)
            return null
        }
    }
}
