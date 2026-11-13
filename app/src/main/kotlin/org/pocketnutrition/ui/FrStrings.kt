// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.core.formatNumber
import org.pocketnutrition.recognition.EntryIssue
import org.pocketnutrition.recognition.SkipReason

/** French copy. See [AppStrings] for where every entry comes from and what it means. */
object FrStrings : AppStrings {

    override val BRAND = "Pocket Nutrition"

    override val JOURNAL_TITLE = "Mon journal"
    override val JOURNAL_EMPTY = "Aucun repas enregistré pour le moment"
    override val JOURNAL_ADD = "Ajouter un repas"
    override val JOURNAL_DELETE = "Supprimer"
    override val JOURNAL_PRIVACY = "Vos repas sont enregistrés uniquement sur cet appareil."

    override val MEASURED_BEFORE_LONG = "Avant cuisson"
    override val MEASURED_AFTER_LONG = "Après cuisson"
    override val WEIGHED_LABEL = "Pesé :"
    override val CHANGE_COOKING = "Changer la cuisson et l'état de pesée"
    override fun weighedInline(whenPart: String) = "(pesé $whenPart cuisson)"
    override fun measuredWhen(state: String) = if (state == "cooked") "après" else "avant"

    override val ABOUT_TITLE = "À propos"
    override fun aboutVersion(version: String) = "Version $version"
    override val ABOUT_PRIVACY_TITLE = "Confidentialité"
    override val ABOUT_PRIVACY_SUMMARY =
        "Votre journal reste sur cet appareil. Aucun compte, aucune publicité, aucun traceur."
    override val ABOUT_PRIVACY_NETWORK =
        "Une recherche envoie le texte saisi. Une analyse envoie les aliments, les quantités et les " +
            "cuissons. Aucun identifiant n’accompagne ces requêtes."
    override val ABOUT_PRIVACY_LINK = "Lire la politique de confidentialité"
    override val ABOUT_SOURCES_TITLE = "Sources des données"
    override val ABOUT_SOURCES_BODY =
        "Valeurs nutritionnelles : Ciqual 2025 (ANSES, Licence Ouverte), CoFID 2021 de McCance & Widdowson " +
            "(contient des informations du secteur public sous Open Government Licence v3.0), " +
            "USDA FoodData Central et table des rendements de cuisson USDA (domaine public). " +
            "Produits, marques et photos : Open Food Facts, sous licence ODbL."
    override val ABOUT_LICENCE_TITLE = "Licence"
    override val ABOUT_LICENCE_BODY =
        "Cette application est un logiciel libre, publié sous licence Mozilla Public License 2.0. " +
            "Vous pouvez en consulter, en modifier et en redistribuer le code source."
    override val ABOUT_SOURCE_LINK = "Voir le code source"
    override val ABOUT_THIRD_PARTY_TITLE = "Composants tiers"
    override val ABOUT_CLOSE = "Fermer"

    override val SETTINGS_TITLE = "Paramètres"

    override val ABOUT_LANGUAGE_TITLE = "Langue"
    override val ABOUT_LANGUAGE_AUTO = "Automatique"
    override val ABOUT_LANGUAGE_FR = "Français"
    override val ABOUT_LANGUAGE_EN = "English"

    override val ABOUT_SERVER_TITLE = "Serveur"
    override val ABOUT_SERVER_BODY = "Adresse du serveur utilisé par l'application. Laissez vide pour rester sur le serveur officiel."
    override val ABOUT_SERVER_APPLY = "Utiliser cette adresse"
    override val ABOUT_SERVER_RESET = "Revenir au serveur officiel"
    override val ABOUT_SERVER_INVALID =
        "Adresse invalide. Utilisez une URL complète, par exemple https://exemple.org"

    override val SEARCH_PLACEHOLDER = "Rechercher un aliment…"
    override val SEARCH_NO_RESULTS = "Aucun résultat"
    override val MULTI_TERM_HINT = "Plusieurs aliments d'un coup : « riz poulet pomme »"
    override fun confirmAdd(count: Int) = "Ajouter ($count)"
    override val SEARCH_ALREADY_ADDED = "Déjà ajouté"
    override val SEARCH_PRODUCT_BADGE = "Produit"
    override val SEARCH_RECIPE_PREFIX = "Recette : %s"
    override val SEARCH_RECIPE_COMPONENT_COUNT = "%d ingrédients"
    override val SEARCH_SECTION_RECIPES = "Recettes"
    override val SEARCH_SECTION_INGREDIENTS = "Ingrédients"
    override val SEARCH_SECTION_PRODUCTS = "Produits de marque"
    override val SEARCH_CANCEL = "Annuler"

    override val MEAL_HEADING = "Composez votre repas"
    override val MEAL_EMPTY = "Recherchez un aliment ci-dessus pour commencer"
    override val MEAL_ANALYSING = "Analyse en cours…"
    override val MEAL_QUANTITY = "Quantité"
    override val MEAL_COOKING = "Cuisson"
    override val MEAL_REMOVE = "Retirer"
    override fun mealAnalyse(count: Int) = "Analyser le repas ($count aliment${if (count > 1) "s" else ""})"

    override fun mealSlotName(slot: MealSlot) = when (slot) {
        MealSlot.BREAKFAST -> "Petit-déjeuner"
        MealSlot.LUNCH -> "Déjeuner"
        MealSlot.DINNER -> "Dîner"
        MealSlot.SNACK -> "En-cas"
    }
    override val MEAL_CHANGE_TIME = "Changer l'heure…"
    override val MEAL_TIME_TITLE = "Heure du repas"
    override fun journalSameAsYesterday(items: String) = "Comme hier : $items"
    override fun journalRepeatYesterdayA11y(slot: String) = "Refaire le ${slot.lowercase()} d'hier"
    override val DISHES_TITLE = "Mes plats"
    override fun recentsTitle(slot: MealSlot) = when (slot) {
        MealSlot.BREAKFAST -> "Souvent au petit-déjeuner"
        MealSlot.LUNCH -> "Souvent au déjeuner"
        MealSlot.DINNER -> "Souvent au dîner"
        MealSlot.SNACK -> "Souvent en en-cas"
    }
    override fun dishDetail(ingredients: Int, servings: Int) =
        "$ingredients ingrédient${if (ingredients > 1) "s" else ""}" + (if (servings > 1) " · pour $servings" else "")
    override fun recentDetail(grams: Double, method: String) =
        "${roundHalfUp(grams)} g" + (if (method.isEmpty() || method == "raw") "" else " · ${cookingMethod(method).lowercase()}")
    override val SAVE_AS_DISH = "Enregistrer comme plat"
    override val DISH_NAME_FIELD = "Nom du plat"
    override fun dishServings(count: Int) = "Pour $count portion${if (count > 1) "s" else ""}"
    override val DISH_SAVE = "Enregistrer"
    override fun dishUpdate(name: String) = "Mettre à jour « $name »"
    override val DISH_MENU_A11Y = "Options du plat"
    override fun dishPartsUnit(servings: Int) = if (servings > 1) "sur $servings portions" else "×"
    override val PIN = "Épingler"
    override val UNPIN = "Désépingler"
    override val DELETE_DISH = "Supprimer le plat"
    override fun quickAddRow(kcal: Int) = "Ajouter $kcal kcal"
    override val QUICK_ADD_NOTE = "Saisi manuellement"
    override val UNRECOGNISED_NOTE = "Données indisponibles pour cet aliment."
    override fun unrecognisedLabel(label: String) = "$label · $UNRECOGNISED_NOTE"
    override val RESULTS_TOTAL_UNKNOWN = "—"
    override fun resultsNotCounted(count: Int) =
        if (count == 1) "1 aliment non reconnu, non compté dans le total"
        else "$count aliments non reconnus, non comptés dans le total"
    override fun shortcutAddA11y(name: String) = "Ajouter $name"
    override val LOG_AGAIN_NOW = "Refaire maintenant"

    override fun cookingMethod(id: String) = when (id) {
        "raw" -> "Cru"
        "boiled" -> "Bouilli"
        "steamed" -> "Vapeur"
        "grilled" -> "Grillé"
        "roasted" -> "Rôti"
        "fried" -> "Frit"
        else -> "cuit (générique)"
    }

    override val NUTRIENT_ENERGY = "Énergie"
    override val NUTRIENT_PROTEIN = "Protéines"
    override val NUTRIENT_CARBS = "Glucides"
    override val NUTRIENT_FAT = "Lipides"
    override val NUTRIENT_PROTEIN_ABBR = "P"
    override val NUTRIENT_CARBS_ABBR = "G"
    override val NUTRIENT_FAT_ABBR = "L"

    override val RESULTS_TOTAL = "Total du repas"
    override val RESULTS_EDIT = "Modifier le repas"
    override val RESULTS_SAVE = "Enregistrer ce repas"
    override fun resultsRowSummary(quantity: Double, unit: String, method: String, state: String) =
        "${formatNumber(quantity, "fr")} $unit · $method · pesé $state"
    override fun measuredState(id: String) = when (id) {
        "raw" -> "cru"
        "cooked" -> "cuit"
        else -> id
    }

    override val SCAN_BUTTON = "📷"
    override val SCAN_BUTTON_A11Y = "Scanner un code-barres"
    override val SCAN_EXPLAIN_TITLE = "Autoriser la caméra"
    override val SCAN_EXPLAIN_BODY =
        "Pocket Nutrition utilise la caméra uniquement pour lire le code-barres d'un produit et " +
            "retrouver ses valeurs nutritionnelles. Aucune photo n'est enregistrée."
    override val SCAN_EXPLAIN_CONTINUE = "Continuer"
    override val SCAN_EXPLAIN_CANCEL = "Pas maintenant"
    override val SCAN_SETTINGS_TITLE = "Caméra désactivée"
    override val SCAN_SETTINGS_BODY =
        "L'accès à la caméra a été refusé. Autorisez-le dans les réglages de l'application pour " +
            "scanner un code-barres."
    override val SCAN_SETTINGS_OPEN = "Ouvrir les réglages"
    override val SCAN_SETTINGS_CANCEL = "Annuler"
    override val SCAN_NOT_FOUND =
        "Ce code-barres n'est pas encore dans notre base. Recherchez ses ingrédients par nom ; vous pourrez " +
            "ensuite enregistrer le repas comme plat pour le réutiliser."
    override val SCAN_REPORT = "Signaler ce produit"
    override val SCAN_REPORT_SENT = "Merci, signalé"
    override val SCAN_CLOSE = "Fermer"

    override val FEEDBACK_TITLE = "Signaler une imprécision"
    override val FEEDBACK_INTRO = "Choisissez une catégorie, puis l'erreur concernée."
    override val FEEDBACK_REFERENCE_WEIGHT = "Poids de référence"
    override val FEEDBACK_DISPLAY_UNIT = "Unité d'affichage"
    override val FEEDBACK_UNITS = "Unités"
    override val FEEDBACK_COMMENT = "Commentaire (optionnel)"
    override val FEEDBACK_ADD = "Ajouter"
    override val FEEDBACK_REMOVE_CORRECTION = "Retirer cette correction"
    override val FEEDBACK_SUBMIT = "Envoyer le signalement"
    override val FEEDBACK_SUBMITTING = "Envoi…"
    override val FEEDBACK_SUCCESS = "Merci, votre signalement a été transmis à l'équipe."
    override val FEEDBACK_FAILURE = "Envoi impossible pour le moment. Réessayez plus tard."
    override val FEEDBACK_CLOSE = "Fermer"
    override val FEEDBACK_REPORT = "Signaler"

    override fun yieldHint(display: String, heavier: Boolean) =
        "Cet aliment pèse ${display}x ${if (heavier) "plus" else "moins"} lourd une fois cuit"

    override val VOICE_SETTINGS_TITLE = "Serveur de reconnaissance vocale"
    override val VOICE_SETTINGS_BODY =
        "L'enregistrement est envoyé au serveur configuré ici. Il est conservé sur le téléphone jusqu'à " +
            "la réception du résultat (7 jours au maximum après l'envoi), puis supprimé. Un enregistrement jamais envoyé reste sur " +
            "le téléphone jusqu'à son envoi ou sa suppression."
    override val VOICE_URL_LABEL = "Adresse du serveur (https)"
    override val VOICE_KEY_LABEL = "Clé d'API"
    override val VOICE_KEY_STORED = "Une clé est enregistrée. Laissez vide pour la conserver."
    override val VOICE_SAVE = "Enregistrer"
    override val VOICE_CLEAR = "Effacer"
    override val VOICE_URL_INVALID = "Adresse invalide. Utilisez une URL https complète."
    override val VOICE_SAVE_FAILED = "Impossible de stocker la clé de façon sécurisée sur cet appareil."
    override fun voiceSaved(host: String) = "Enregistré. La clé sera envoyée à $host."
    override val VOICE_KEY_INVALID = "Clé invalide : caractères ASCII visibles uniquement, sans espace ni retour à la ligne."
    override val VOICE_KEY_REQUIRED_NEW_HOST =
        "Le serveur a changé : saisissez à nouveau la clé d'API, elle ne sera jamais envoyée à un autre serveur sans cela."
    override val VOICE_DELETE_TITLE = "Supprimer cette saisie ?"
    override fun voiceDeleteBody(hasAudio: Boolean, ready: Boolean) = when {
        ready -> "Le résultat sera supprimé de ce téléphone. Cette action est définitive."
        hasAudio -> "L'enregistrement n'a peut-être pas encore été analysé : il sera supprimé de ce téléphone. Cette action est définitive."
        else -> "Cette action est définitive."
    }
    override val VOICE_DELETE_CONFIRM = "Supprimer"
    override fun voiceMissing(missingUrl: Boolean, missingKey: Boolean) = when {
        missingUrl && missingKey -> "Il manque l'adresse du serveur et la clé d'API. Renseignez-les dans les paramètres."
        missingUrl -> "Il manque l'adresse du serveur (https). Renseignez-la dans les paramètres."
        else -> "Il manque la clé d'API. Renseignez-la dans les paramètres."
    }
    override val VOICE_CANCEL = "Annuler"
    override val VOICE_SEND_NOW = "Envoyer maintenant"
    override val VOICE_RESEND = "Renvoyer"
    override val VOICE_OPEN = "Ouvrir"
    override val VOICE_PENDING = "En attente d'envoi"
    override fun voiceProcessing(lastStatus: String?) = when (lastStatus) {
        "transcribing" -> "Transcription…"
        "extracting" -> "Analyse…"
        else -> "Sera traité prochainement"
    }
    override fun voiceReady(itemCount: Int) = "Prêt — $itemCount élément${if (itemCount > 1) "s" else ""}"
    override fun voiceIssue(issue: EntryIssue, serverMessage: String?, audioKept: Boolean) = when (issue) {
        EntryIssue.OFFLINE -> "En attente d'envoi — hors ligne"
        EntryIssue.SERVER_ERROR -> "En attente d'envoi — le serveur ne répond pas correctement"
        EntryIssue.UNAUTHORIZED -> "Clé d'API refusée"
        EntryIssue.TOO_LARGE -> "Enregistrement trop volumineux pour le serveur"
        EntryIssue.UNSUPPORTED -> "Format audio refusé par le serveur"
        EntryIssue.REJECTED -> "Envoi refusé par le serveur — vérifiez l'adresse du serveur"
        EntryIssue.REDIRECTED -> "Le serveur redirige la requête — vérifiez l'adresse du serveur"
        EntryIssue.SECURE_CONNECTION -> "Connexion sécurisée impossible — vérifiez l'adresse et le certificat du serveur"
        EntryIssue.EXPIRED, EntryIssue.GONE ->
            if (audioKept) "Expiré — l'enregistrement est toujours sur votre téléphone"
            else "Résultat plus disponible"
        EntryIssue.FAILED ->
            (serverMessage ?: "Échec de l'analyse") +
                if (audioKept) " — l'enregistrement est toujours sur votre téléphone" else ""
        EntryIssue.AUDIO_LOST -> "L'enregistrement n'est plus disponible sur le téléphone"
    }
    override val VOICE_ERR_TOO_SHORT = "Enregistrement trop court."
    override val VOICE_ERR_RECORDER = "Impossible de démarrer l'enregistrement."
    override val MIC_EXPLAIN_TITLE = "Autoriser le microphone"
    override val MIC_EXPLAIN_BODY =
        "Pocket Nutrition utilise le microphone uniquement quand vous appuyez sur Enregistrer, pour " +
            "dicter un repas. L'enregistrement est envoyé au serveur de reconnaissance configuré dans " +
            "les paramètres, et conservé sur le téléphone jusqu'à la réception du résultat (7 jours " +
            "au maximum après l'envoi), puis supprimé. Un enregistrement jamais envoyé reste sur " +
            "le téléphone jusqu'à son envoi ou sa suppression."
    override val MIC_EXPLAIN_CONTINUE = "Continuer"
    override val MIC_EXPLAIN_CANCEL = "Pas maintenant"
    override val MIC_SETTINGS_TITLE = "Microphone désactivé"
    override val MIC_SETTINGS_BODY =
        "L'accès au microphone a été refusé. Autorisez-le dans les paramètres de l'application pour dicter un repas."
    override val MIC_SETTINGS_OPEN = "Ouvrir les paramètres"
    override val MIC_SETTINGS_CANCEL = "Annuler"
    override val VOICE_REVIEW_TITLE = "Vérifier la saisie"
    override val VOICE_TRANSCRIPT = "Transcription"
    override val DICTATION_START = "Dicter un repas"
    override val PHOTO_START = "Photographier un repas"
    override val CAPTURE_ADDED = "Ajouté depuis votre dictée ou votre photo."
    override val CAPTURE_NOTHING = "Rien n'a pu être ajouté : vous le retrouverez dans votre journal."
    override fun journalCaptureTitle(time: String) = "Repas en cours · $time"
    override val PHOTO_TAKE = "Prendre une photo"
    override val PHOTO_CHOOSE = "Choisir une photo"
    override val PHOTO_DESCRIPTION = "Ce qui a été vu"
    override val PHOTO_ERR_SAVE = "La photo n'a pas pu être enregistrée."
    override val PHOTO_ERR_CAMERA = "Aucune application appareil photo n'est disponible."
    override val PHOTO_ERR_CAMERA_DENIED = "L'accès à l'appareil photo est désactivé. Autorisez-le dans les Réglages, ou choisissez plutôt une photo."
    override val VOICE_SKIP_ITEM = "Ignorer"
    override val VOICE_ADD_TO_MEAL = "Ajouter au repas"
    override val VOICE_NO_ITEMS = "Aucun aliment reconnu."
    override val VOICE_NO_CANDIDATES = "Aucune correspondance proposée"
    override val VOICE_BACK = "Retour"
    override val VOICE_IMPORTING = "Recherche des aliments…"
    override val VOICE_SKIPPED_HEADING = "Non ajoutés :"
    override fun voiceSkipped(rawText: String, reason: SkipReason) = "« $rawText » — " + when (reason) {
        SkipReason.NO_CANDIDATE -> "aucune correspondance"
        SkipReason.NOT_FOUND -> "aliment introuvable dans le catalogue"
        SkipReason.NOT_BUILDABLE -> "le catalogue ne donne pas assez d'informations (portion ou cuisson) pour l'ajouter"
    }
    override val VOICE_CONTINUE = "Continuer vers le repas"
    override val VOICE_NOTHING_ADDED = "Aucun aliment n'a pu être ajouté."

    override fun error(message: String) = "Erreur : $message"
    override val RETRY = "Réessayer"
}
