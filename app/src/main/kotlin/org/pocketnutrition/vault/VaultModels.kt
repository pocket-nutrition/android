// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Wire types for the vault API (pocket-nutrition-vault `app/schemas.py`). Field names follow the server's
// snake_case JSON; decoding ignores keys it does not know, so a newer server never breaks this app.

/** Lenient on input (unknown keys), strict on output: nulls are written only where a field says so. */
internal val vaultJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Key derivation settings. Properties are declared in alphabetical order on purpose: the keyring blob
 * embeds this object, and the protocol wants its JSON keys sorted so every device saves the same bytes.
 */
@Serializable
data class VaultKdf(
    val alg: String = "argon2id",
    val memlimit: Long,
    val opslimit: Int,
    /** Base64, 16 bytes. */
    val salt: String,
)

@Serializable
data class VaultServerInfo(
    val name: String,
    val version: String,
    /** `open` or `closed`. */
    val registration: String,
    @SerialName("quota_bytes") val quotaBytes: Long = 0,
) {
    val isRegistrationOpen: Boolean get() = registration == "open"
}

@Serializable
data class VaultKeyring(val blob: String? = null, val version: Int)

/** What register and login both return: this device's API key plus the account's wrapped keys. */
@Serializable
data class VaultSession(
    @SerialName("account_id") val accountId: String,
    @SerialName("api_key") val apiKey: String,
    @SerialName("key_id") val keyId: String,
    val keyring: VaultKeyring,
)

@Serializable
data class VaultRegisterRequest(
    val username: String,
    val kdf: VaultKdf,
    @SerialName("auth_hash") val authHash: String,
    val keyring: String,
    @SerialName("device_label") val deviceLabel: String,
)

@Serializable
data class VaultLoginRequest(
    val username: String,
    @SerialName("auth_hash") val authHash: String,
    @SerialName("device_label") val deviceLabel: String,
    /** Only for an account with two-factor on; omitted from the JSON when null. */
    @SerialName("totp_code") val totpCode: String? = null,
    @SerialName("recovery_code") val recoveryCode: String? = null,
) {
    companion object {
        fun of(username: String, authHash: String, deviceLabel: String, twoFactor: VaultTwoFactorCode?) =
            VaultLoginRequest(
                username = username,
                authHash = authHash,
                deviceLabel = deviceLabel,
                totpCode = (twoFactor as? VaultTwoFactorCode.Totp)?.code,
                recoveryCode = (twoFactor as? VaultTwoFactorCode.Recovery)?.code,
            )
    }
}

@Serializable
data class VaultRecord(
    val id: String,
    val type: String,
    @SerialName("scope_id") val scopeId: String? = null,
    val revision: Int,
    @SerialName("key_id") val keyId: String,
    /** Base64 `nonce || AEAD` (user key) or a sealed box (inbox); empty for a tombstone. */
    val ciphertext: String,
    val deleted: Boolean,
    val seq: Long,
)

@Serializable
data class VaultSyncPage(
    val records: List<VaultRecord>,
    val cursor: Long,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
data class VaultRecordWrite(
    val type: String,
    // Omitted when null (vaultJson has explicitNulls = false): the server reads a missing scope as null.
    @SerialName("scope_id") val scopeId: String? = null,
    /** The new revision: the current one + 1, or 1 for a new record. */
    val revision: Int,
    @SerialName("key_id") val keyId: String,
    val ciphertext: String,
)

/** `POST /v1/account/passphrase/reset`: a forgotten passphrase replaced from a linked device. */
@Serializable
data class VaultResetPassphraseRequest(
    @SerialName("new_auth_hash") val newAuthHash: String,
    val kdf: VaultKdf,
    val keyring: String,
    @SerialName("base_version") val baseVersion: Int,
    /** Only for an account with two-factor on; omitted from the JSON when null. */
    @SerialName("totp_code") val totpCode: String? = null,
    @SerialName("recovery_code") val recoveryCode: String? = null,
) {
    override fun toString(): String = "VaultResetPassphraseRequest(…)"
}

@Serializable
data class VaultPassphraseChanged(
    val version: Int,
    @SerialName("revoked_devices") val revokedDevices: Int,
)
