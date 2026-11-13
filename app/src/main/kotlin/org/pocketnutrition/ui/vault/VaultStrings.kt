// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.vault

import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.vault.VaultAccountError

// Copy for the cloud vault. Client-authored in both languages, not yet in the shared deck: the same wording
// as iOS (`EnStrings.swift` / `FrStrings.swift`, "Cloud vault" section) so both apps say the same thing.

interface VaultCopy {
    val title: String
    val settingsBody: String
    val open: String
    val close: String
    val heroTitle: String
    val benefitSync: String
    val benefitPrivate: String
    val connecting: String
    val retry: String
    val advanced: String
    val advancedHint: String
    val serverLabel: String
    val checkServer: String
    fun serverFound(name: String): String
    fun officialServer(host: String): String
    val registrationClosed: String
    val usernameLabel: String
    val passphraseLabel: String
    val passphraseConfirmLabel: String
    val passphraseHint: String
    val generatedHint: String
    val ownPassphrase: String
    val generatePassphrase: String
    fun transferHint(meals: Int): String
    val createAccount: String
    val createVault: String
    val login: String
    val twoFactorLoginHint: String
    val twoFactorCodeOrRecovery: String
    val recoveryTitle: String
    val recoveryBody: String
    val recoveryKeyLabel: String
    val kitShare: String
    val recoveryCopy: String
    val recoveryCopied: String
    val kitOnce: String
    val kitSaveFirst: String
    fun kitWordLabel(position: Int): String
    val kitWholeLabel: String
    val kitRetypeWrong: String
    val recoveryContinue: String
    fun kitText(host: String, username: String, passphrase: String?, recoveryKey: String): String
    val cancel: String
    fun signedInAs(username: String, host: String): String
    val syncing: String
    fun synced(meals: Int): String
    val syncOffline: String
    val syncFull: String
    val syncFailed: String
    fun syncUnsupported(count: Int): String
    val syncNow: String
    val logout: String
    val logoutUnreached: String
    val fingerprintTitle: String
    val fingerprintBody: String
    val fingerprintNotPassphrase: String
    val changeTitle: String
    val changeBody: String
    val changeGenerate: String
    val changeOwn: String
    val resetNoLock: String
    val resetLockTitle: String
    val resetLockBody: String
    val resetKitTitle: String
    val resetKitBody: String
    val resetFinish: String
    val deleteAccount: String
    val deleteBody: String
    val deleteConfirm: String
    val journalPrivacyLinked: String
    fun error(error: VaultAccountError): String
}

object VaultStrings {
    val active: VaultCopy get() = if (LanguageSettings.effective == "en") VaultEn else VaultFr
}

object VaultEn : VaultCopy {
    override val title = "Cloud vault"
    override val settingsBody = "Keep an encrypted copy of your journal and targets, and use them on your other devices. Optional, no email."
    override val open = "Open"
    override val close = "Close"
    override val heroTitle = "Your private vault"
    override val benefitSync = "Your whole journal is saved there and stays in step on all your devices."
    override val benefitPrivate = "Your data is encrypted before it leaves your phone and stays encrypted in the cloud. Nobody else can read it, not even us."
    override val connecting = "Connecting to the vault…"
    override val retry = "Try again"
    override val advanced = "Advanced"
    override val advancedHint = "Most people never need this. Use your own vault server instead of the official one."
    override val serverLabel = "Server address"
    override val checkServer = "Check server"
    override fun serverFound(name: String) = "Server found: $name"
    override fun officialServer(host: String) = "Official vault: $host"
    override val registrationClosed = "This server is not accepting new accounts. You can only log in to it."
    override val usernameLabel = "Username"
    override val passphraseLabel = "Passphrase"
    override val passphraseConfirmLabel = "Confirm passphrase"
    override val passphraseHint = "At least 12 characters. It never leaves your phone and nobody can recover it for you."
    override val generatedHint = "We generate a strong passphrase for you. You will keep it in an emergency kit on the next screen."
    override val ownPassphrase = "Choose my own passphrase"
    override val generatePassphrase = "Generate one for me instead"
    override fun transferHint(meals: Int) = "Your $meals meal${if (meals == 1) "" else "s"} will be saved to the vault right away."
    override val createAccount = "Create account"
    override val createVault = "Create my vault"
    override val login = "Log in"
    override val twoFactorLoginHint = "This account uses two-factor. Enter the code from your authenticator app, or a recovery code."
    override val twoFactorCodeOrRecovery = "Code or recovery code"
    override val recoveryTitle = "Your emergency kit"
    override val recoveryBody = "This is how you get back into your vault on a new phone or if you forget your passphrase. We cannot recover any of it. Send it to yourself or save it in your password manager, not only on this phone."
    override val recoveryKeyLabel = "Recovery key"
    override val kitShare = "Send to myself"
    override val recoveryCopy = "Copy kit"
    override val recoveryCopied = "Kit copied"
    override val kitOnce = "The passphrase cannot be shown again; without it or the recovery key a new device cannot be linked."
    override val kitSaveFirst = "Copy the kit or send it to yourself first."
    override fun kitWordLabel(position: Int) = "Type word $position of your passphrase"
    override val kitWholeLabel = "Type your passphrase"
    override val kitRetypeWrong = "That does not match. Check your saved kit."
    override val recoveryContinue = "Finish creating the account"
    override fun kitText(host: String, username: String, passphrase: String?, recoveryKey: String) = buildList {
        add("Pocket Nutrition vault: emergency kit")
        add("Server: $host")
        add("Username: $username")
        passphrase?.let { add("Passphrase: $it") }
        add("Recovery key: $recoveryKey")
        add("")
        add("Keep this somewhere safe and private. Nobody, including us, can recover it for you.")
    }.joinToString("\n")
    override val cancel = "Cancel"
    override fun signedInAs(username: String, host: String) = "Logged in as $username on $host"
    override val syncing = "Syncing your journal…"
    override fun synced(meals: Int) = "Journal synced: $meals meal${if (meals == 1) "" else "s"}"
    override val syncOffline = "Offline. Your meals will sync as soon as the vault can be reached."
    override val syncFull = "Your vault is full. Free some space to keep syncing."
    override val syncFailed = "The sync ran into a problem. It will try again."
    override fun syncUnsupported(count: Int) =
        "$count item${if (count == 1) " was" else "s were"} saved by a newer version of the app. Update the app to see ${if (count == 1) "it" else "them"}; ${if (count == 1) "it is" else "they are"} left untouched meanwhile."
    override val syncNow = "Sync now"
    override val logout = "Log out of this device"
    override val logoutUnreached = "This device is logged out, but the server could not be told. Revoke it from another logged-in device."
    override val fingerprintTitle = "Fingerprint phrase"
    override val fingerprintNotPassphrase = "This is not your passphrase: it only checks that another device has the same keys."
    override val changeTitle = "Change passphrase"
    override val changeBody = "Your old passphrase is not needed: this phone holds your keys. A new passphrase and a new recovery key will be created. Your other devices and the web viewer will be logged out, and the old recovery key will stop working. Your data stays as it is."
    override val changeGenerate = "Generate a passphrase (recommended)"
    override val changeOwn = "Choose my own"
    override val resetNoLock = "Set a screen lock on this device first (PIN, pattern or password). It protects this step."
    override val resetLockTitle = "Confirm it is you"
    override val resetLockBody = "Unlock to set a new vault passphrase."
    override val resetKitTitle = "Your new emergency kit"
    override val resetKitBody = "Your passphrase has been replaced. Log in again on your other devices with this new kit. The old passphrase and the old recovery key no longer work."
    override val resetFinish = "Done"
    override val fingerprintBody = "This phrase names your account's keys. It is the same on all your devices; if another device shows different words, it was not given your real keys."
    override val deleteAccount = "Delete my account and data"
    override val deleteBody = "Permanently erases the account and everything stored on the server. Enter your passphrase to confirm."
    override val deleteConfirm = "Delete permanently"
    override val journalPrivacyLinked = "Your meals are stored on this device. With a vault account, an encrypted copy is kept there too."
    override fun error(error: VaultAccountError) = when (error) {
        VaultAccountError.InvalidServerUrl -> "Invalid address. Use a full https:// URL, for example https://example.org"
        VaultAccountError.NotAVault -> "This address does not answer like a vault server."
        VaultAccountError.Offline -> "Could not reach the server. Check your connection and try again."
        VaultAccountError.InvalidUsername -> "Invalid username: 3 to 64 characters from a-z, 0-9, \"_\", \".\" and \"-\"."
        VaultAccountError.PassphraseTooShort -> "The passphrase must be at least 12 characters."
        VaultAccountError.PassphraseMismatch -> "The two passphrases do not match."
        VaultAccountError.UsernameTaken -> "That username is already taken."
        VaultAccountError.RegistrationClosed -> "This server is not accepting new accounts."
        VaultAccountError.InvalidCredentials -> "Wrong username or passphrase."
        is VaultAccountError.TooManyAttempts ->
            error.retryAfterSeconds?.let { "Too many attempts. Try again in ${maxOf(1, (it + 59) / 60)} min." } ?: "Too many attempts. Try again later."
        VaultAccountError.UnsafeKdf -> "The server asked for security settings that are too weak. Login refused as a precaution."
        VaultAccountError.KeyringUnreadable -> "The server answered, but your keys could not be opened by this version of the app."
        VaultAccountError.InvalidTwoFactor -> "Wrong or already used code."
        VaultAccountError.ServerError -> "The server had a problem. Try again later."
        VaultAccountError.Storage -> "Could not save the session in the device's secure storage."
        VaultAccountError.Crypto -> "Key derivation failed on this device."
    }
}

object VaultFr : VaultCopy {
    override val title = "Coffre-fort"
    override val settingsBody = "Gardez une copie chiffrée de votre journal et de vos repères, et retrouvez-les sur vos autres appareils. Facultatif, sans e-mail."
    override val open = "Ouvrir"
    override val close = "Fermer"
    override val heroTitle = "Votre coffre-fort privé"
    override val benefitSync = "Tout votre journal y est sauvegardé et reste à jour sur tous vos appareils."
    override val benefitPrivate = "Vos données sont chiffrées avant de quitter votre téléphone et restent chiffrées dans le cloud. Personne d'autre ne peut les lire, nous non plus."
    override val connecting = "Connexion au coffre-fort…"
    override val retry = "Réessayer"
    override val advanced = "Avancé"
    override val advancedHint = "La plupart des gens n'en ont jamais besoin. Utilisez votre propre serveur de coffre-fort au lieu du serveur officiel."
    override val serverLabel = "Adresse du serveur"
    override val checkServer = "Vérifier le serveur"
    override fun serverFound(name: String) = "Serveur trouvé : $name"
    override fun officialServer(host: String) = "Coffre-fort officiel : $host"
    override val registrationClosed = "Ce serveur n'accepte pas de nouveaux comptes. Vous pouvez seulement vous y connecter."
    override val usernameLabel = "Nom d'utilisateur"
    override val passphraseLabel = "Phrase secrète"
    override val passphraseConfirmLabel = "Confirmer la phrase secrète"
    override val passphraseHint = "12 caractères minimum. Elle ne quitte jamais votre téléphone et personne ne peut la récupérer pour vous."
    override val generatedHint = "Nous générons pour vous une phrase secrète solide. Vous la conserverez dans un kit de secours, à l'écran suivant."
    override val ownPassphrase = "Choisir ma propre phrase secrète"
    override val generatePassphrase = "En générer une pour moi"
    override fun transferHint(meals: Int) = "Vos $meals repas seront sauvegardés dans le coffre-fort tout de suite."
    override val createAccount = "Créer un compte"
    override val createVault = "Créer mon coffre-fort"
    override val login = "Se connecter"
    override val twoFactorLoginHint = "Ce compte utilise la double authentification. Saisissez le code de votre app d'authentification, ou un code de secours."
    override val twoFactorCodeOrRecovery = "Code ou code de secours"
    override val recoveryTitle = "Votre kit de secours"
    override val recoveryBody = "C'est ce qui vous permet de retrouver votre coffre-fort sur un nouveau téléphone ou si vous oubliez votre phrase secrète. Nous ne pouvons rien récupérer. Envoyez-le-vous ou enregistrez-le dans votre gestionnaire de mots de passe, pas seulement sur ce téléphone."
    override val recoveryKeyLabel = "Clé de récupération"
    override val kitShare = "Me l'envoyer"
    override val recoveryCopy = "Copier le kit"
    override val recoveryCopied = "Kit copié"
    override val kitOnce = "La phrase secrète ne pourra plus être affichée ; sans elle ni la clé de récupération, aucun nouvel appareil ne pourra être relié."
    override val kitSaveFirst = "Copiez le kit ou envoyez-le-vous d'abord."
    override fun kitWordLabel(position: Int) = "Saisissez le mot $position de votre phrase secrète"
    override val kitWholeLabel = "Tapez votre phrase secrète"
    override val kitRetypeWrong = "Ça ne correspond pas. Vérifiez votre kit enregistré."
    override val recoveryContinue = "Terminer la création du compte"
    override fun kitText(host: String, username: String, passphrase: String?, recoveryKey: String) = buildList {
        add("Coffre-fort Pocket Nutrition : kit de secours")
        add("Serveur : $host")
        add("Identifiant : $username")
        passphrase?.let { add("Phrase secrète : $it") }
        add("Clé de récupération : $recoveryKey")
        add("")
        add("Gardez ceci en lieu sûr et privé. Personne, nous non plus, ne peut le récupérer pour vous.")
    }.joinToString("\n")
    override val cancel = "Annuler"
    override fun signedInAs(username: String, host: String) = "Connecté en tant que $username sur $host"
    override val syncing = "Synchronisation du journal…"
    override fun synced(meals: Int) = "Journal synchronisé : $meals repas"
    override val syncOffline = "Hors ligne. Vos repas se synchroniseront dès que le coffre-fort sera joignable."
    override val syncFull = "Votre coffre-fort est plein. Libérez de la place pour continuer à synchroniser."
    override val syncFailed = "La synchronisation a rencontré un problème. Elle va réessayer."
    override fun syncUnsupported(count: Int) =
        if (count == 1) "1 élément a été enregistré par une version plus récente de l'app. Mettez l'app à jour pour le voir ; il reste intact en attendant."
        else "$count éléments ont été enregistrés par une version plus récente de l'app. Mettez l'app à jour pour les voir ; ils restent intacts en attendant."
    override val syncNow = "Synchroniser maintenant"
    override val logout = "Se déconnecter de cet appareil"
    override val logoutUnreached = "Cet appareil est déconnecté, mais le serveur n'a pas pu être prévenu. Révoquez-le depuis un autre appareil connecté."
    override val fingerprintTitle = "Phrase d'empreinte"
    override val fingerprintNotPassphrase = "Ce n'est pas votre phrase secrète : elle sert seulement à vérifier qu'un autre appareil a les mêmes clés."
    override val changeTitle = "Changer la phrase secrète"
    override val changeBody = "Votre ancienne phrase secrète n'est pas nécessaire : ce téléphone détient vos clés. Une nouvelle phrase secrète et une nouvelle clé de récupération seront créées. Vos autres appareils et la visionneuse web seront déconnectés, et l'ancienne clé de récupération ne fonctionnera plus. Vos données restent intactes."
    override val changeGenerate = "Générer une phrase (recommandé)"
    override val changeOwn = "Choisir la mienne"
    override val resetNoLock = "Définissez d'abord un verrouillage de l'écran sur cet appareil (code, schéma ou mot de passe). Il protège cette étape."
    override val resetLockTitle = "Confirmez que c'est vous"
    override val resetLockBody = "Déverrouillez pour définir une nouvelle phrase secrète du coffre-fort."
    override val resetKitTitle = "Votre nouveau kit de secours"
    override val resetKitBody = "Votre phrase secrète a été remplacée. Reconnectez vos autres appareils avec ce nouveau kit. L'ancienne phrase secrète et l'ancienne clé de récupération ne fonctionnent plus."
    override val resetFinish = "Terminé"
    override val fingerprintBody = "Cette phrase identifie les clés de votre compte. Elle est la même sur tous vos appareils ; si un autre appareil affiche d'autres mots, il n'a pas reçu vos vraies clés."
    override val deleteAccount = "Supprimer mon compte et mes données"
    override val deleteBody = "Efface définitivement le compte et tout ce qui est stocké sur le serveur. Saisissez votre phrase secrète pour confirmer."
    override val deleteConfirm = "Supprimer définitivement"
    override val journalPrivacyLinked = "Vos repas sont enregistrés sur cet appareil. Avec un compte coffre-fort, une copie chiffrée y est aussi conservée."
    override fun error(error: VaultAccountError) = when (error) {
        VaultAccountError.InvalidServerUrl -> "Adresse invalide. Utilisez une URL https:// complète, par exemple https://exemple.org"
        VaultAccountError.NotAVault -> "Cette adresse ne répond pas comme un serveur coffre-fort."
        VaultAccountError.Offline -> "Impossible de joindre le serveur. Vérifiez la connexion et réessayez."
        VaultAccountError.InvalidUsername -> "Nom d'utilisateur invalide : 3 à 64 caractères parmi a-z, 0-9, « _ », « . » et « - »."
        VaultAccountError.PassphraseTooShort -> "La phrase secrète doit contenir au moins 12 caractères."
        VaultAccountError.PassphraseMismatch -> "Les deux phrases secrètes ne correspondent pas."
        VaultAccountError.UsernameTaken -> "Ce nom d'utilisateur est déjà pris."
        VaultAccountError.RegistrationClosed -> "Ce serveur n'accepte pas de nouveaux comptes."
        VaultAccountError.InvalidCredentials -> "Nom d'utilisateur ou phrase secrète incorrect."
        is VaultAccountError.TooManyAttempts ->
            error.retryAfterSeconds?.let { "Trop de tentatives. Réessayez dans ${maxOf(1, (it + 59) / 60)} min." } ?: "Trop de tentatives. Réessayez plus tard."
        VaultAccountError.UnsafeKdf -> "Le serveur impose des paramètres de sécurité trop faibles. Connexion refusée par précaution."
        VaultAccountError.KeyringUnreadable -> "Le serveur a répondu, mais vos clés n'ont pas pu être ouvertes sur cette version de l'app."
        VaultAccountError.InvalidTwoFactor -> "Code incorrect ou déjà utilisé."
        VaultAccountError.ServerError -> "Le serveur a rencontré un problème. Réessayez plus tard."
        VaultAccountError.Storage -> "Impossible d'enregistrer la session dans le stockage sécurisé de l'appareil."
        VaultAccountError.Crypto -> "Le calcul des clés a échoué sur cet appareil."
    }
}
