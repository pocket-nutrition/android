// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import org.pocketnutrition.core.formatFrenchNumber

/**
 * French copy for the app. The product is French-only.
 *
 * WHERE THESE COME FROM
 * Every entry is transcribed **verbatim** from `contract/data/strings/fr.json`, the copy deck all three
 * clients share, and carries the contract key it came from. Nothing here is invented: the ten strings
 * this app once had to write for itself — the journal group, the single-add placeholder, and the labels
 * the sandbox has no equivalent for — went upstream in contracts 1.3.0, under a new `app` scope for
 * wording the native clients have and the prototype does not.
 *
 * Kotlin `const val` rather than `strings.xml`: the product ships one language by contract, and an
 * Android resource file would invite the platform to pick a different locale's copy at runtime. It
 * also keeps this file diffable against its iOS twin, which is the check that actually catches drift
 * between the two clients.
 *
 * THE DRIFT THIS DOES NOT CATCH
 * `sync-contracts.sh --verify` proves `contract/` matches upstream. It cannot prove this file matches
 * `contract/`, because these are Kotlin literals rather than a parsed resource. A deliberate MVP
 * trade — see the same note in the iOS Strings.swift.
 */
object Fr {

    // contract: app.brand — "Product name. Never translated."
    const val BRAND = "Pocket Nutrition"

    // ── Journal ──────────────────────────────────────────────────────────────
    // contract: journal.*, scope `app`. The sandbox persists nothing and so has no journal; this group
    // was written for the native clients and lives in the deck under the scope that says so.

    /** contract: journal.title */
    const val JOURNAL_TITLE = "Mon journal"

    /** contract: journal.empty */
    const val JOURNAL_EMPTY = "Aucun repas enregistré pour le moment"

    /** contract: journal.add */
    const val JOURNAL_ADD = "Ajouter un repas"

    /** contract: journal.delete */
    const val JOURNAL_DELETE = "Supprimer"

    /**
     * contract: journal.privacy — marked REQUIRED there rather than optional.
     *
     * The deck's own `welcome.privacy` says "Aucune donnée personnelle n'est conservée", which is false
     * for an app that persists a diary. Until contracts 1.3.0 the deck flagged that and offered no
     * replacement, so both clients invented their own line; this is now the shared one.
     */
    const val JOURNAL_PRIVACY = "Vos repas sont enregistrés uniquement sur cet appareil."

    // ── Measured state and the cooking pill ──────────────────────────────────
    //
    // contract: measuredState.* and ingredientCard.*. The deck's $comment on measuredState calls the
    // control these label "the most consequential control in the UI" — it multiplies or divides the
    // weight by the yield factor, so a wrong answer here is wrong by 15-40% while looking correct.

    /** contract: measuredState.beforeLong — paired with 🌿, per the deck's own context note. */
    const val MEASURED_BEFORE_LONG = "Avant cuisson"

    /** contract: measuredState.afterLong — paired with 🍽️. */
    const val MEASURED_AFTER_LONG = "Après cuisson"

    /**
     * contract: ingredientCard.weighedLabel — the source has a NON-BREAKING space before the colon, so
     * this carries U+00A0 rather than a plain space. French typography, and it is what the deck records.
     */
    const val WEIGHED_LABEL = "Pesé\u00A0:"

    /** contract: ingredientCard.changeCooking — accessibility label on the method pill. */
    const val CHANGE_COOKING = "Changer la cuisson et l'état de pesée"

    /**
     * contract: ingredientCard.weighedInline — "(pesé {when} cuisson)", shown inside the collapsed pill.
     * [when] is measuredState.before / .after.
     */
    fun weighedInline(whenPart: String) = "(pesé $whenPart cuisson)"

    /** contract: measuredState.before / .after — the short forms, used only by [weighedInline]. */
    fun measuredWhen(state: String) = if (state == "cooked") "après" else "avant"

    // ── About ────────────────────────────────────────────────────────────────
    //
    // contract: about.*, scope `app`. Three obligations meet on one screen — MPL-2.0 §3.2 (tell the
    // recipient of a binary what rights they have), the ODbL attribution the Open Food Facts data
    // carries, and the reachable privacy-policy URL both consoles demand before a listing is accepted.

    /** contract: about.title */
    const val ABOUT_TITLE = "À propos"

    /** contract: about.version — `{version}` substituted at the call site. */
    fun aboutVersion(version: String) = "Version $version"

    /** contract: about.privacyTitle */
    const val ABOUT_PRIVACY_TITLE = "Confidentialité"

    /** contract: about.privacySummary */
    const val ABOUT_PRIVACY_SUMMARY =
        "Votre journal reste sur cet appareil. Aucun compte, aucune publicité, aucun traceur."

    /**
     * contract: about.privacyNetwork — what keeps [ABOUT_PRIVACY_SUMMARY] from reading as "nothing is
     * ever sent", which would be false. Do not ship one without the other.
     */
    const val ABOUT_PRIVACY_NETWORK =
        "Une recherche envoie le texte saisi. Une analyse envoie les aliments, les quantités et les " +
            "cuissons. Aucun identifiant n’accompagne ces requêtes."

    /** contract: about.privacyLink */
    const val ABOUT_PRIVACY_LINK = "Lire la politique de confidentialité"

    /** contract: about.sourcesTitle */
    const val ABOUT_SOURCES_TITLE = "Sources des données"

    /** contract: about.sourcesBody — attribution required by the ODbL, not a courtesy credit. */
    const val ABOUT_SOURCES_BODY =
        "Valeurs nutritionnelles : table Ciqual (ANSES). Produits, marques et photos : " +
            "Open Food Facts, sous licence ODbL."

    /** contract: about.licenceTitle */
    const val ABOUT_LICENCE_TITLE = "Licence"

    /** contract: about.licenceBody */
    const val ABOUT_LICENCE_BODY =
        "Cette application est un logiciel libre, publié sous licence Mozilla Public License 2.0. " +
            "Vous pouvez en consulter, en modifier et en redistribuer le code source."

    /** contract: about.sourceLink */
    const val ABOUT_SOURCE_LINK = "Voir le code source"

    /** contract: about.thirdPartyTitle */
    const val ABOUT_THIRD_PARTY_TITLE = "Composants tiers"

    /** contract: about.close */
    const val ABOUT_CLOSE = "Fermer"

    // ── Search ───────────────────────────────────────────────────────────────

    /**
     * contract: search.placeholderSingle.
     *
     * Not `search.placeholder` — that one advertises the sandbox's multi-term search, "Ajouter un ou
     * plusieurs aliments… (riz poulet pomme)", and this app adds one food per tap. One field, two
     * wordings, because the two clients genuinely differ. Added to the deck in contracts 1.3.0.
     */
    const val SEARCH_PLACEHOLDER = "Rechercher un aliment…"

    /** contract: search.noResults */
    const val SEARCH_NO_RESULTS = "Aucun résultat"

    /**
     * contract: search.multiTermHint — added in contracts 1.8.0.
     *
     * Carries what `search.placeholder` cannot on a phone. That string is "Ajouter un ou plusieurs
     * aliments… (riz poulet pomme)" — 51 characters against a field that fits about 22, so the
     * parenthetical doing the teaching is exactly what gets truncated. This sits under the field instead.
     */
    const val MULTI_TERM_HINT = "Plusieurs aliments d'un coup : « riz poulet pomme »"

    /** contract: search.confirmAdd — "Ajouter ({count})". Multi-select confirm. */
    fun confirmAdd(count: Int) = "Ajouter ($count)"

    /** contract: search.alreadyAdded */
    const val SEARCH_ALREADY_ADDED = "Déjà ajouté"

    /** contract: search.productBadge */
    const val SEARCH_PRODUCT_BADGE = "Produit"

    /** contract: search.recipePrefix — note the space before the colon, French typography. */
    const val SEARCH_RECIPE_PREFIX = "Recette : %s"

    /**
     * contract: search.recipeComponentCount
     *
     * Not pluralised, so a one-component recipe reads "1 ingrédients". That is a defect in the source
     * the deck records verbatim, and it is transcribed verbatim here rather than quietly fixed: the
     * three clients must read alike, and correcting it belongs in the deck.
     */
    const val SEARCH_RECIPE_COMPONENT_COUNT = "%d ingrédients"

    /** contract: search.sectionRecipes */
    const val SEARCH_SECTION_RECIPES = "Recettes"

    /** contract: search.sectionIngredients */
    const val SEARCH_SECTION_INGREDIENTS = "Ingrédients"

    /** contract: search.sectionProducts */
    const val SEARCH_SECTION_PRODUCTS = "Produits de marque"

    /** contract: search.cancel */
    const val SEARCH_CANCEL = "Annuler"

    // ── Meal builder ─────────────────────────────────────────────────────────

    /** contract: mealBuilder.heading */
    const val MEAL_HEADING = "Composez votre repas"

    /** contract: mealBuilder.empty */
    const val MEAL_EMPTY = "Recherchez un aliment ci-dessus pour commencer"

    /** contract: mealBuilder.analysing — "Single-character ellipsis U+2026, not three dots." */
    const val MEAL_ANALYSING = "Analyse en cours…"

    /**
     * contract: mealBuilder.quantity.
     *
     * **Currently unused**, and kept deliberately rather than deleted. It labelled the bare grams field
     * that the portion ladder replaced: the ladder's own summary line ("≈ 2,3 portions" over the weight)
     * says what the number is, so a caption above it was repetition. Retained because the entry is in
     * the shared deck — added in contracts 1.3.0 for this very label — and a field caption is the kind of
     * thing that comes back. If it has not by the next deck revision, drop both together.
     */
    const val MEAL_QUANTITY = "Quantité"

    /**
     * contract: mealBuilder.cooking. The sandbox needs no such label — its method is implied by which
     * drop-lane a card sits in, and this app has no lanes.
     */
    const val MEAL_COOKING = "Cuisson"

    /** contract: mealBuilder.remove */
    const val MEAL_REMOVE = "Retirer"

    /**
     * contract: mealBuilder.analyse — "Analyser le repas ({count} aliment{s})".
     *
     * The `{s}` rule in the contract is French: plural from 2, so `count > 1` and not the English
     * `count != 1`. Built here rather than with frenchPlural because the corpus function pluralises a
     * word against a count, and what varies here is a bare suffix inside a sentence.
     */
    fun mealAnalyse(count: Int) = "Analyser le repas ($count aliment${if (count > 1) "s" else ""})"

    // ── Cooking methods ──────────────────────────────────────────────────────
    // contract: cookingMethods.*, keyed by the same ids as PnCooking.Methods in the design tokens.

    fun cookingMethod(id: String) = when (id) {
        "raw" -> "Cru"
        "boiled" -> "Bouilli"
        "steamed" -> "Vapeur"
        "grilled" -> "Grillé"
        "roasted" -> "Rôti"
        "fried" -> "Frit"
        // contract: cookingMethods.cookedGeneric. Reachable because the API may return a method the
        // token set does not know; showing the raw id would leak an English identifier into French UI.
        else -> "cuit (générique)"
    }

    // ── Nutrients ────────────────────────────────────────────────────────────
    // contract: nutrients.*

    const val NUTRIENT_ENERGY = "Énergie"
    const val NUTRIENT_PROTEIN = "Protéines"
    const val NUTRIENT_CARBS = "Glucides"
    const val NUTRIENT_FAT = "Lipides"

    // contract: nutrients.proteinAbbr / carbsAbbr / fatAbbr
    const val NUTRIENT_PROTEIN_ABBR = "P"
    const val NUTRIENT_CARBS_ABBR = "G"
    const val NUTRIENT_FAT_ABBR = "L"

    // ── Results ──────────────────────────────────────────────────────────────

    /** contract: results.total */
    const val RESULTS_TOTAL = "Total du repas"

    /** contract: results.edit */
    const val RESULTS_EDIT = "Modifier le repas"

    /** contract: results.save */
    const val RESULTS_SAVE = "Enregistrer ce repas"

    /**
     * contract: results.rowSummary — "{quantity} {unit} · {method} · pesé {state}".
     *
     * `quantity` goes through formatFrenchNumber so 150.0 renders "150" and not "150.0" — the corpus
     * rule, not a platform formatter.
     */
    fun resultsRowSummary(quantity: Double, unit: String, method: String, state: String) =
        "${formatFrenchNumber(quantity)} $unit · $method · pesé $state"

    // contract: measuredState.*
    fun measuredState(id: String) = when (id) {
        "raw" -> "cru"
        "cooked" -> "cuit"
        else -> id
    }

    // ── Scan (barcode) ───────────────────────────────────────────────────────
    // contract: none yet. Client-authored ahead of the shared deck, same precedent as JOURNAL_PRIVACY
    // and MULTI_TERM_HINT above — flag for upstreaming into the `app` scope alongside iOS's equivalent
    // additions so the two clients do not silently diverge on this copy.

    /**
     * The camera button beside the search field — emoji, not a Material icon: this app draws every
     * control glyph as emoji/text (⚙, ✓, ▾, +), and material-icons-extended for one glyph would be a
     * new dependency for a single character.
     */
    const val SCAN_BUTTON = "📷"

    /** Accessibility label on the camera button; TalkBack has nothing else to read off an emoji Text. */
    const val SCAN_BUTTON_A11Y = "Scanner un code-barres"

    const val SCAN_EXPLAIN_TITLE = "Autoriser la caméra"
    const val SCAN_EXPLAIN_BODY =
        "Pocket Nutrition utilise la caméra uniquement pour lire le code-barres d'un produit et " +
            "retrouver ses valeurs nutritionnelles. Aucune photo n'est enregistrée."
    const val SCAN_EXPLAIN_CONTINUE = "Continuer"
    const val SCAN_EXPLAIN_CANCEL = "Pas maintenant"

    const val SCAN_SETTINGS_TITLE = "Caméra désactivée"
    const val SCAN_SETTINGS_BODY =
        "L'accès à la caméra a été refusé. Autorisez-le dans les réglages de l'application pour " +
            "scanner un code-barres."
    const val SCAN_SETTINGS_OPEN = "Ouvrir les réglages"
    const val SCAN_SETTINGS_CANCEL = "Annuler"

    const val SCAN_NOT_FOUND = "Code-barres inconnu dans la base Open Food Facts"
    const val SCAN_CLOSE = "Fermer"

    // ── Feedback ─────────────────────────────────────────────────────────────
    // contract: feedback.*. The guided-correction taxonomy itself (categories/errors/option lists)
    // lives in core.FEEDBACK_CATEGORIES, transcribed from feedback.catalog — only the form's own
    // chrome is here.

    /** contract: feedback.title */
    const val FEEDBACK_TITLE = "Signaler une imprécision"

    /** contract: feedback.intro */
    const val FEEDBACK_INTRO = "Choisissez une catégorie, puis l'erreur concernée."

    /** contract: feedback.referenceWeight — current-value hint label. */
    const val FEEDBACK_REFERENCE_WEIGHT = "Poids de référence"

    /** contract: feedback.displayUnit */
    const val FEEDBACK_DISPLAY_UNIT = "Unité d'affichage"

    /** contract: feedback.units */
    const val FEEDBACK_UNITS = "Unités"

    /** contract: feedback.comment */
    const val FEEDBACK_COMMENT = "Commentaire (optionnel)"

    /** contract: feedback.add — adds one correction to the pending list. */
    const val FEEDBACK_ADD = "Ajouter"

    /** contract: feedback.removeCorrection */
    const val FEEDBACK_REMOVE_CORRECTION = "Retirer cette correction"

    /** contract: feedback.submit */
    const val FEEDBACK_SUBMIT = "Envoyer le signalement"

    /** contract: feedback.submitting */
    const val FEEDBACK_SUBMITTING = "Envoi…"

    /** contract: feedback.success */
    const val FEEDBACK_SUCCESS = "Merci, votre signalement a été transmis à l'équipe."

    /** contract: feedback.failure */
    const val FEEDBACK_FAILURE = "Envoi impossible pour le moment. Réessayez plus tard."

    /** contract: feedback.close */
    const val FEEDBACK_CLOSE = "Fermer"

    /**
     * Client-authored ahead of the shared deck, same precedent as JOURNAL_PRIVACY above: the
     * affordance that opens the feedback sheet from an expanded ingredient row.
     */
    const val FEEDBACK_REPORT = "Signaler"

    // ── Errors ───────────────────────────────────────────────────────────────

    /**
     * contract: errors.prefix — "Erreur : {message}". Note the space before the colon: French
     * typography, and it is in the contract deliberately.
     */
    fun error(message: String) = "Erreur : $message"

    /** contract: errors.retry */
    const val RETRY = "Réessayer"
}
