// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.vault

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.ui.Paragraph
import org.pocketnutrition.ui.PrimaryButton
import org.pocketnutrition.ui.Section
import org.pocketnutrition.vault.VaultAccountError
import org.pocketnutrition.vault.VaultAccountModel
import org.pocketnutrition.vault.VaultCrypto
import org.pocketnutrition.vault.VaultKitCheck
import org.pocketnutrition.vault.VaultPassphraseSuggestion
import org.pocketnutrition.vault.VaultProtocol
import org.pocketnutrition.vault.VaultServerInfo
import org.pocketnutrition.vault.VaultSettings
import org.pocketnutrition.vault.VaultSyncStatus
import org.pocketnutrition.vault.fingerprintPhrase
import org.pocketnutrition.vault.vaultNewPassphraseError
import org.pocketnutrition.vault.vaultHostLabel

/**
 * The cloud vault: create an account or log in (which links this phone), see who is logged in and how the
 * sync is doing, unlink, or erase the account. A port of iOS `VaultView`.
 *
 * The official vault is contacted as soon as the screen opens, so a new person only ever sees a username
 * and a passphrase; their own server is an option under "Advanced", never a step.
 *
 * Deferred from iOS (see the report): the rest of the account-security section (devices, turning
 * two-factor on or off; the fingerprint check and the passphrase change are here) and the biometric lock / session timeout. Logging in to an
 * account that already has two-factor on IS supported, since the server requires it to link.
 */
@Composable
fun VaultScreen(
    model: VaultAccountModel,
    syncStatus: VaultSyncStatus,
    unsupportedCount: Int,
    mealCount: Int,
    onSyncNow: () -> Unit,
    /** This phone was just linked: start the first sync. */
    onLinked: () -> Unit,
    /** This phone is no longer linked: forget the sync bookkeeping, keep the data. */
    onUnlinked: () -> Unit,
    onClose: () -> Unit,
) {
    val t = VaultStrings.active
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(VaultSettings.rawServerUrl) }
    var notice by remember { mutableStateOf<String?>(null) }

    // Opening the screen is enough to reach the vault the person last used (the official one by default).
    LaunchedEffect(Unit) {
        if (model.phase == VaultAccountModel.Phase.SignedOut && model.serverInfo == null) model.checkServer(serverUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(PnColor.SurfacePaper),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t.title,
                color = PnColor.InkDefault,
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (model.isWorking) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            TextButton(onClick = onClose) { Text(t.close, color = PnColor.BrandLeafDeep, fontSize = 14.sp) }
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val phase = model.phase) {
                is VaultAccountModel.Phase.SignedIn -> model.resetKit?.let { kit ->
                    EmergencyKit(
                        title = t.resetKitTitle,
                        body = t.resetKitBody,
                        host = vaultHostLabel(kit.serverUrl),
                        username = kit.username,
                        shownPassphrase = kit.passphrase.takeIf { kit.isGenerated },
                        passphrase = kit.passphrase,
                        recoveryKey = kit.recoveryKey,
                        finishLabel = t.resetFinish,
                        isWorking = model.isWorking,
                        onFinish = model::dismissResetKit,
                    )
                } ?: SignedInSections(
                    model = model,
                    username = phase.summary.username,
                    host = vaultHostLabel(phase.summary.serverUrl),
                    syncStatus = syncStatus,
                    unsupportedCount = unsupportedCount,
                    mealCount = mealCount,
                    onSyncNow = onSyncNow,
                    onLogout = {
                        scope.launch {
                            val reached = model.logout()
                            onUnlinked()
                            notice = if (reached) null else t.logoutUnreached
                        }
                    },
                    onDelete = { passphrase ->
                        scope.launch {
                            model.deleteAccount(passphrase)
                            if (model.phase == VaultAccountModel.Phase.SignedOut) onUnlinked()
                        }
                    },
                )
                VaultAccountModel.Phase.SignedOut -> {
                    val recoveryKey = model.pendingRecoveryKey
                    if (recoveryKey != null) {
                        KitSection(model, recoveryKey, serverUrl, onLinked)
                    } else {
                        HeroSection()
                        val info = model.serverInfo
                        if (info != null) {
                            AccountSection(model, info, mealCount, isOfficial = serverUrl.trim().trimEnd('/') == VaultProtocol.DEFAULT_SERVER_URL, onLinked)
                        } else {
                            Section(t.title) {
                                if (model.error == null) {
                                    Paragraph(t.connecting)
                                } else {
                                    PrimaryButton(t.retry, onClick = { scope.launch { model.checkServer(serverUrl) } }, enabled = !model.isWorking)
                                }
                            }
                        }
                        AdvancedSection(model, serverUrl, onChange = { serverUrl = it; model.clearError() })
                    }
                }
            }
            model.error?.let { Text(t.error(it), color = PnColor.StatusBad, fontSize = 13.sp, lineHeight = 19.sp) }
            notice?.let { Paragraph(it) }
        }
    }
}

@Composable
private fun HeroSection() {
    val t = VaultStrings.active
    Section(t.heroTitle) {
        Paragraph(t.benefitSync)
        Paragraph(t.benefitPrivate)
    }
}

@Composable
private fun AccountSection(model: VaultAccountModel, info: VaultServerInfo, mealCount: Int, isOfficial: Boolean, onLinked: () -> Unit) {
    val t = VaultStrings.active
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(info.isRegistrationOpen) }
    var username by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var twoFactorCode by remember { mutableStateOf("") }
    var useOwnPassphrase by remember { mutableStateOf(false) }

    Section(if (creating) t.createAccount else t.login) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(t.login, selected = !creating) {
                creating = false
                model.cancelTwoFactor()
                twoFactorCode = ""
            }
            ModeChip(t.createAccount, selected = creating) {
                creating = true
                model.cancelTwoFactor()
                twoFactorCode = ""
            }
        }
        if (!info.isRegistrationOpen) Paragraph(t.registrationClosed)

        PlainField(username, { username = it }, t.usernameLabel)
        if (!creating || useOwnPassphrase) SecretField(passphrase, { passphrase = it }, t.passphraseLabel)
        if (!creating && model.needsTwoFactor) {
            Paragraph(t.twoFactorLoginHint)
            PlainField(twoFactorCode, { twoFactorCode = it }, t.twoFactorCodeOrRecovery, monospace = true)
        }
        if (creating) {
            if (useOwnPassphrase) {
                SecretField(confirmation, { confirmation = it }, t.passphraseConfirmLabel)
                Paragraph(t.passphraseHint)
            } else {
                Paragraph(t.generatedHint)
            }
            if (mealCount > 0) Paragraph(t.transferHint(mealCount))
            LinkText(if (useOwnPassphrase) t.generatePassphrase else t.ownPassphrase) {
                useOwnPassphrase = !useOwnPassphrase
                passphrase = ""
                confirmation = ""
            }
        }

        PrimaryButton(
            text = if (creating) t.createVault else t.login,
            enabled = !model.isWorking && (!creating || info.isRegistrationOpen),
            onClick = {
                scope.launch {
                    if (creating) {
                        val phrase = if (useOwnPassphrase) passphrase else VaultPassphraseSuggestion.make()
                        model.prepareAccount(username, phrase, if (useOwnPassphrase) confirmation else phrase)
                        // The generated passphrase is shown once, in the kit; a chosen one is never repeated back.
                        KitDraft.username = username.trim().lowercase()
                        KitDraft.passphrase = phrase
                        KitDraft.isGenerated = !useOwnPassphrase
                    } else {
                        model.login(username, passphrase, if (model.needsTwoFactor) twoFactorCode else null)
                        if (model.phase is VaultAccountModel.Phase.SignedIn) {
                            passphrase = ""
                            twoFactorCode = ""
                            onLinked()
                        }
                    }
                }
            },
        )
        if (isOfficial) Text(t.officialServer(vaultHostLabel(VaultProtocol.DEFAULT_SERVER_URL)), color = PnColor.InkSoft, fontSize = 12.sp)
    }
}

/**
 * What the kit shows besides the recovery key, held only while the kit is on screen. Not Compose state on
 * purpose: it must not be saved into the activity's saved-instance bundle.
 */
private object KitDraft {
    var username: String = ""
    /** Shown in the kit only when generated; a chosen one is only checked by the retype step. */
    var passphrase: String = ""
    var isGenerated: Boolean = true

    fun clear() {
        username = ""
        passphrase = ""
        isGenerated = true
    }
}

@Composable
private fun KitSection(model: VaultAccountModel, recoveryKey: String, serverUrl: String, onLinked: () -> Unit) {
    val t = VaultStrings.active
    val scope = rememberCoroutineScope()
    EmergencyKit(
        title = t.recoveryTitle,
        body = t.recoveryBody,
        host = vaultHostLabel(serverUrl),
        username = KitDraft.username,
        shownPassphrase = KitDraft.passphrase.takeIf { KitDraft.isGenerated },
        passphrase = KitDraft.passphrase,
        recoveryKey = recoveryKey,
        finishLabel = t.recoveryContinue,
        isWorking = model.isWorking,
        onFinish = {
            scope.launch {
                model.registerPreparedAccount()
                if (model.phase is VaultAccountModel.Phase.SignedIn) {
                    KitDraft.clear()
                    onLinked()
                }
            }
        },
        onCancel = {
            KitDraft.clear()
            model.discardPendingAccount()
        },
    )
}

/**
 * An emergency kit shown once (account creation, passphrase change). "Finish" stays disabled until the kit
 * was copied or shared AND the person typed the passphrase back ([VaultKitCheck]): one random word of a
 * generated passphrase, or the whole of one they chose. A kit merely glanced at is how the maintainer lost
 * a vault. [shownPassphrase] is null for a chosen passphrase, which the kit does not repeat.
 */
@Composable
private fun EmergencyKit(
    title: String,
    body: String,
    host: String,
    username: String,
    shownPassphrase: String?,
    passphrase: String,
    recoveryKey: String,
    finishLabel: String,
    isWorking: Boolean,
    onFinish: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val t = VaultStrings.active
    val context = LocalContext.current
    var saved by remember(recoveryKey) { mutableStateOf(false) }
    var copied by remember(recoveryKey) { mutableStateOf(false) }
    var typed by remember(recoveryKey) { mutableStateOf("") }
    val wordIndex = remember(recoveryKey) { shownPassphrase?.let(VaultKitCheck::pickWordIndex) }
    val kit = t.kitText(host, username, shownPassphrase, recoveryKey)
    val retypeLabel = wordIndex?.let { t.kitWordLabel(it + 1) } ?: t.kitWholeLabel

    Section(title) {
        Paragraph(body)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PnColor.SurfaceCream, RoundedCornerShape(PnRadius.Default))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KitRow(t.serverLabel, host)
            KitRow(t.usernameLabel, username)
            shownPassphrase?.let { KitRow(t.passphraseLabel, it) }
            KitRow(t.recoveryKeyLabel, recoveryKey)
        }
        Text(t.kitOnce, color = PnColor.InkDefault, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Android cannot tell whether the share sheet sent anything, so opening it counts as saved.
            LinkText(t.kitShare) {
                shareText(context, kit)
                saved = true
            }
            LinkText(if (copied) t.recoveryCopied else t.recoveryCopy) {
                copySensitive(context, kit)
                copied = true
                saved = true
            }
        }
        if (!saved) Text(t.kitSaveFirst, color = PnColor.InkSoft, fontSize = 12.sp)
        if (wordIndex != null) {
            PlainField(typed, { typed = it }, retypeLabel, keyboardType = KeyboardType.Password, monospace = true)
        } else {
            SecretField(typed, { typed = it }, retypeLabel)
        }
        if (typed.isNotEmpty() && !VaultKitCheck.isRetypeCorrect(typed, passphrase, wordIndex)) {
            Text(t.kitRetypeWrong, color = PnColor.StatusBad, fontSize = 12.sp)
        }
        PrimaryButton(
            text = finishLabel,
            enabled = !isWorking && VaultKitCheck.canFinish(saved, typed, passphrase, wordIndex),
            onClick = {
                typed = ""
                onFinish()
            },
        )
        onCancel?.let { LinkText(t.cancel, color = PnColor.InkSoft, onClick = it) }
    }
}

@Composable
private fun KitRow(label: String, value: String) {
    Column {
        Text(label, color = PnColor.InkSoft, fontSize = 12.sp)
        Text(value, color = PnColor.InkDefault, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AdvancedSection(model: VaultAccountModel, serverUrl: String, onChange: (String) -> Unit) {
    val t = VaultStrings.active
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    LinkText((if (expanded) "▾ " else "▸ ") + t.advanced, color = PnColor.InkSoft) { expanded = !expanded }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Paragraph(t.advancedHint)
            PlainField(serverUrl, onChange, t.serverLabel, keyboardType = KeyboardType.Uri)
            model.serverInfo?.let { Text(t.serverFound(it.name), color = PnColor.BrandLeafDeep, fontSize = 13.sp) }
            LinkText(t.checkServer, color = PnColor.InkSoft) { scope.launch { model.checkServer(serverUrl) } }
        }
    }
}

@Composable
private fun SignedInSections(
    model: VaultAccountModel,
    username: String,
    host: String,
    syncStatus: VaultSyncStatus,
    unsupportedCount: Int,
    mealCount: Int,
    onSyncNow: () -> Unit,
    onLogout: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val t = VaultStrings.active
    var fingerprint by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var deletePassphrase by remember { mutableStateOf("") }

    // The fingerprint needs libsodium; computed once, off the main thread.
    LaunchedEffect(model.linkedSession?.accountId) {
        val session = model.linkedSession ?: return@LaunchedEffect
        fingerprint = withContext(Dispatchers.Default) {
            runCatching { VaultCrypto().fingerprintPhrase(session.accountId, session.keys.inbox.publicKey) }.getOrNull()?.joinToString("-")
        }
    }

    Section(t.title) {
        Text(t.signedInAs(username, host), color = PnColor.BrandLeafDeep, fontSize = 13.sp)
        Paragraph(
            when (syncStatus) {
                VaultSyncStatus.OFF, VaultSyncStatus.SYNCING -> t.syncing
                VaultSyncStatus.IDLE -> t.synced(mealCount)
                VaultSyncStatus.OFFLINE -> t.syncOffline
                VaultSyncStatus.FULL -> t.syncFull
                VaultSyncStatus.FAILED -> t.syncFailed
            },
        )
        if (unsupportedCount > 0) Paragraph(t.syncUnsupported(unsupportedCount))
        LinkText(t.syncNow, enabled = syncStatus != VaultSyncStatus.SYNCING, onClick = onSyncNow)
        // Logging out sits well apart from "sync now": the two used to be adjacent on iOS, an easy mis-tap.
        HorizontalDivider(color = PnColor.InkLine, modifier = Modifier.padding(vertical = 12.dp))
        PrimaryButton(t.logout, onClick = onLogout, enabled = !model.isWorking)
    }

    fingerprint?.let { phrase ->
        Section(t.fingerprintTitle) {
            Text(phrase, color = PnColor.InkDefault, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            // People mistook these words for their passphrase: say plainly that they are not.
            Text(t.fingerprintNotPassphrase, color = PnColor.InkDefault, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
            Paragraph(t.fingerprintBody)
        }
    }

    ChangePassphraseSection(model)

    Section(t.deleteAccount) {
        if (showDelete) {
            Paragraph(t.deleteBody)
            SecretField(deletePassphrase, { deletePassphrase = it }, t.passphraseLabel)
            LinkText(t.deleteConfirm, color = PnColor.StatusBad, enabled = deletePassphrase.isNotEmpty() && !model.isWorking) {
                onDelete(deletePassphrase)
            }
            LinkText(t.cancel, color = PnColor.InkSoft) {
                showDelete = false
                deletePassphrase = ""
            }
        } else {
            LinkText(t.deleteAccount, color = PnColor.StatusBad) { showDelete = true }
        }
    }
}

/**
 * "Change passphrase" on a linked device, the only passphrase entry: the old passphrase is never asked
 * (this phone holds the keys), the device's own lock is. Generated (recommended) or chosen, the new
 * passphrase always goes through the reset endpoint, so it comes with a new recovery key and a new kit.
 */
@Composable
private fun ChangePassphraseSection(model: VaultAccountModel) {
    val t = VaultStrings.active
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var chooseOwn by remember { mutableStateOf(false) }
    var own by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<VaultAccountError?>(null) }
    var lockMissing by remember { mutableStateOf(false) }
    var twoFactorCode by remember { mutableStateOf("") }
    fun reset() {
        expanded = false
        chooseOwn = false
        own = ""
        confirmation = ""
        formError = null
        lockMissing = false
        twoFactorCode = ""
    }
    val unlock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        scope.launch {
            if (chooseOwn) model.changePassphrase(own, confirmation) else model.changePassphrase()
            if (model.resetKit != null) reset()
        }
    }

    Section(t.changeTitle) {
        when {
            model.resetNeedsTwoFactor -> {
                Paragraph(t.twoFactorLoginHint)
                PlainField(twoFactorCode, { twoFactorCode = it }, t.twoFactorCodeOrRecovery, monospace = true)
                PrimaryButton(t.changeTitle, enabled = twoFactorCode.isNotBlank() && !model.isWorking, onClick = {
                    scope.launch {
                        model.changePassphrase(twoFactorCode = twoFactorCode)
                        if (model.resetKit != null) reset()
                    }
                })
                LinkText(t.cancel, color = PnColor.InkSoft) {
                    model.cancelReset()
                    reset()
                }
            }
            expanded -> {
                Paragraph(t.changeBody)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(t.changeGenerate, selected = !chooseOwn) { chooseOwn = false; formError = null }
                    ModeChip(t.changeOwn, selected = chooseOwn) { chooseOwn = true; formError = null }
                }
                if (chooseOwn) {
                    SecretField(own, { own = it; formError = null }, t.passphraseLabel)
                    SecretField(confirmation, { confirmation = it; formError = null }, t.passphraseConfirmLabel)
                    Paragraph(t.passphraseHint)
                }
                PrimaryButton(t.changeTitle, enabled = !model.isWorking, onClick = {
                    // Checked before the lock prompt, with the registration rule, so a typo costs no unlock.
                    formError = if (chooseOwn) vaultNewPassphraseError(own, confirmation) else null
                    if (formError != null) return@PrimaryButton
                    val intent = deviceCredentialIntent(context, t.resetLockTitle, t.resetLockBody)
                    lockMissing = intent == null
                    if (intent != null) unlock.launch(intent)
                })
                formError?.let { Text(t.error(it), color = PnColor.StatusBad, fontSize = 13.sp, lineHeight = 19.sp) }
                if (lockMissing) Text(t.resetNoLock, color = PnColor.StatusBad, fontSize = 13.sp, lineHeight = 19.sp)
                LinkText(t.cancel, color = PnColor.InkSoft) { reset() }
            }
            else -> LinkText(t.changeTitle) { expanded = true }
        }
    }
}

/**
 * The system screen-lock prompt (PIN, pattern, password or biometrics), or null when the device has no
 * secure lock. Deprecated since API 29 in favour of BiometricPrompt, which needs `androidx.biometric`: not
 * in the version catalogue, and a new dependency for one prompt is not worth it. The platform still
 * supports this intent on every release, so the deprecation is suppressed here and only here.
 */
@Suppress("DEPRECATION")
private fun deviceCredentialIntent(context: Context, title: String, description: String): Intent? {
    val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return null
    if (!keyguard.isDeviceSecure) return null
    return keyguard.createConfirmDeviceCredentialIntent(title, description)
}

// ── Small pieces ─────────────────────────────────────────────────────────────

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PnRadius.Default)
    Box(
        modifier = Modifier
            .background(if (selected) PnColor.BrandLeafDeep else PnColor.SurfaceCream, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) PnColor.InkOnInverse else PnColor.InkDefault,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LinkText(text: String, color: androidx.compose.ui.graphics.Color = PnColor.BrandLeafDeep, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (enabled) color else PnColor.InkSoft,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    monospace: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = if (monospace) androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace) else androidx.compose.ui.text.TextStyle.Default,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The system share sheet: the person picks where the kit goes (their notes, a password manager, mail). */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/**
 * A secret on the clipboard: flagged sensitive, so Android 13+ hides it from the paste preview and from
 * keyboard suggestions.
 */
private fun copySensitive(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Pocket Nutrition", text)
    clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    clipboard.setPrimaryClip(clip)
}

/** `ClipDescription.EXTRA_IS_SENSITIVE` (API 33), spelled out so older releases simply ignore it. */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
