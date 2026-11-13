// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.pocketnutrition.recognition.KeystoreSecretStore
import org.pocketnutrition.recognition.SecretStore

/**
 * Everything that makes this phone a linked device of one vault account, stored as ONE secret so the
 * pieces can never disagree (a device key without its keys, or the reverse). Mirrors iOS
 * `VaultStoredSession`. Keys are base64 inside the JSON, and the JSON itself is only ever written
 * through [SecretStore], i.e. AES-GCM under an Android Keystore key.
 */
@Serializable
data class VaultStoredSession(
    @SerialName("server_url") val serverUrl: String,
    val username: String,
    @SerialName("account_id") val accountId: String,
    /** This device's API key. */
    @SerialName("api_key") val apiKey: String,
    @SerialName("key_id") val keyId: String,
    /**
     * The KDF settings this account was linked with. Later operations use THESE, never what the server
     * says now: that is what stops a server from downgrading the derivation after the fact.
     */
    val kdf: VaultKdf,
    @SerialName("user_key") val userKeyB64: String,
    @SerialName("inbox_public_key") val inboxPublicKeyB64: String,
    @SerialName("inbox_secret_key") val inboxSecretKeyB64: String,
) {
    val userKey: ByteArray get() = checkNotNull(decodeBase64OrNull(userKeyB64))
    val keys: VaultUnlockedKeys
        get() = VaultUnlockedKeys(
            userKey,
            VaultInboxKeyPair(checkNotNull(decodeBase64OrNull(inboxPublicKeyB64)), checkNotNull(decodeBase64OrNull(inboxSecretKeyB64))),
        )

    /** Never prints the device key or a key. */
    override fun toString(): String = "VaultStoredSession(account=…)"

    companion object {
        fun of(
            serverUrl: String,
            username: String,
            accountId: String,
            apiKey: String,
            keyId: String,
            kdf: VaultKdf,
            keys: VaultUnlockedKeys,
        ) = VaultStoredSession(
            serverUrl, username, accountId, apiKey, keyId, kdf,
            keys.userKey.toBase64(), keys.inbox.publicKey.toBase64(), keys.inbox.secretKey.toBase64(),
        )
    }
}

/** Where the linked session is kept, behind [SecretStore] so tests can use memory. */
class VaultSessionStore(private val secrets: SecretStore) {

    /** Null when not linked, or when the stored session can no longer be decrypted (e.g. Keystore reset). */
    fun load(): VaultStoredSession? {
        val text = secrets.read() ?: return null
        return runCatching { vaultJson.decodeFromString(VaultStoredSession.serializer(), text) }.getOrNull()
            ?.takeIf { s -> listOf(s.userKeyB64, s.inboxPublicKeyB64, s.inboxSecretKeyB64).all { decodeBase64OrNull(it)?.size == VaultProtocol.KEY_BYTES } }
    }

    /** False when it could not be stored; nothing is stored in that case. */
    fun save(session: VaultStoredSession): Boolean =
        secrets.write(vaultJson.encodeToString(VaultStoredSession.serializer(), session))

    fun clear() = secrets.clear()
}

/**
 * The app's vault wiring: the server address (a plain preference, not a secret) and the session (one
 * Keystore-encrypted blob). Both live in the `vault_settings` prefs file, which is excluded from backup:
 * a restored blob could not be decrypted elsewhere anyway, since the Keystore key never leaves the phone.
 *
 * Unlike iOS there is no "fresh install" marker: on Android, uninstalling removes both the prefs file and
 * the Keystore key, so a reinstall can never resurrect a session.
 *
 * [init] runs once from MainActivity.onCreate, like the other settings objects.
 */
object VaultSettings {
    const val PREFS_NAME = "vault_settings"
    private const val KEY_URL = "server_url"
    private const val KEY_ALIAS = "pocket_nutrition_vault_session_key"
    private const val BLOB_KEY = "session_blob"

    private var prefs: SharedPreferences? = null
    private var store: VaultSessionStore? = null

    fun init(context: Context) {
        if (prefs != null) return
        val shared = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = shared
        val secrets = KeystoreSecretStore(shared, keyAlias = KEY_ALIAS, blobKey = BLOB_KEY)
        store = VaultSessionStore(secrets)
        // Warm the in-memory cache off the main thread, so composition never waits on the Keystore.
        Thread { secrets.read() }.apply { isDaemon = true }.start()
    }

    val sessions: VaultSessionStore get() = checkNotNull(store) { "VaultSettings.init was not called" }

    /** What the server field starts with: the last address checked, or the official vault. */
    val rawServerUrl: String get() = prefs?.getString(KEY_URL, null) ?: VaultProtocol.DEFAULT_SERVER_URL

    fun rememberServerUrl(url: String) = prefs?.edit { putString(KEY_URL, url) }

    /** How this phone is named in the account's device list. */
    val deviceLabel: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.distinct()
            .joinToString(" ").take(MAX_LABEL).ifBlank { "Android" }

    private const val MAX_LABEL = 128
}

/** The host of an address as typed, for display; the address itself when it has none. */
fun vaultHostLabel(raw: String): String = runCatching { java.net.URI(raw.trim()).host }.getOrNull() ?: raw
