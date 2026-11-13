// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.recognition.EntryIssue
import org.pocketnutrition.recognition.SkipReason

/**
 * Copy for the app, in whichever language is currently active.
 *
 * WHERE THESE COME FROM
 * Every requirement here is transcribed **verbatim** from `contract/data/strings/fr.json`'s `fr` and
 * `en` values — the copy deck all three clients share — and carries the contract key it came from. A
 * few entries are marked "client-authored": no sandbox equivalent exists to transcribe from, so both
 * languages were written directly in this file, candidates for upstreaming into the deck's `app`
 * scope in a future `CONTRACTS_VERSION` bump — the same path the whole `about.*` group once took.
 *
 * TWO IMPLEMENTATIONS, ONE INTERFACE, NOT TWO LOOSE OBJECTS
 * Before contract 2.0.0 this was a single `object Fr` of `const val`s — correct while there was only
 * one language. Splitting into [FrStrings]/[EnStrings] behind this interface keeps every property a
 * compile-time-checked member (a typo is still a build error, the property this design always had
 * over a runtime map lookup) while adding something the old object could not: the compiler now
 * *enforces* that [EnStrings] has every member [FrStrings] has. Call sites read `Strings.active.X`,
 * which re-resolves to the active language on every read — see [Strings] below. Member names keep
 * their original SCREAMING_SNAKE_CASE casing rather than being renamed to camelCase: they are no
 * longer `const val` (an interface member can't be), but renaming ~90 call sites for casing alone,
 * on top of the `Fr.` -> `Strings.active.` rename every one of them already needs, was not worth it.
 */
interface AppStrings {

    val BRAND: String

    // ── Journal ──────────────────────────────────────────────────────────────
    val JOURNAL_TITLE: String
    val JOURNAL_EMPTY: String
    val JOURNAL_ADD: String
    val JOURNAL_DELETE: String
    val JOURNAL_PRIVACY: String

    // ── Measured state and the cooking pill ──────────────────────────────────
    val MEASURED_BEFORE_LONG: String
    val MEASURED_AFTER_LONG: String
    val WEIGHED_LABEL: String
    val CHANGE_COOKING: String
    fun weighedInline(whenPart: String): String
    fun measuredWhen(state: String): String

    // ── About ────────────────────────────────────────────────────────────────
    val ABOUT_TITLE: String
    fun aboutVersion(version: String): String
    val ABOUT_PRIVACY_TITLE: String
    val ABOUT_PRIVACY_SUMMARY: String
    val ABOUT_PRIVACY_NETWORK: String
    val ABOUT_PRIVACY_LINK: String
    val ABOUT_SOURCES_TITLE: String
    val ABOUT_SOURCES_BODY: String
    val ABOUT_LICENCE_TITLE: String
    val ABOUT_LICENCE_BODY: String
    val ABOUT_SOURCE_LINK: String
    val ABOUT_THIRD_PARTY_TITLE: String
    val ABOUT_CLOSE: String

    // ── Settings ─────────────────────────────────────────────────────────────
    // Also the label of the "Settings" menu row; About's own row reuses ABOUT_TITLE the same way.
    val SETTINGS_TITLE: String

    // ── Language switcher (Settings screen) ──────────────────────────────────
    // Key namespace still says "about" — it predates the Settings screen split and renaming it
    // would be a contract change of its own — but the strings now render on the Settings screen.
    val ABOUT_LANGUAGE_TITLE: String
    val ABOUT_LANGUAGE_AUTO: String
    val ABOUT_LANGUAGE_FR: String
    val ABOUT_LANGUAGE_EN: String

    // ── Server override (Settings screen) ────────────────────────────────────
    val ABOUT_SERVER_TITLE: String
    val ABOUT_SERVER_BODY: String
    val ABOUT_SERVER_APPLY: String
    val ABOUT_SERVER_RESET: String
    val ABOUT_SERVER_INVALID: String

    // ── Search ───────────────────────────────────────────────────────────────
    val SEARCH_PLACEHOLDER: String
    val SEARCH_NO_RESULTS: String
    val MULTI_TERM_HINT: String
    fun confirmAdd(count: Int): String
    val SEARCH_ALREADY_ADDED: String
    val SEARCH_PRODUCT_BADGE: String
    val SEARCH_RECIPE_PREFIX: String
    val SEARCH_RECIPE_COMPONENT_COUNT: String
    val SEARCH_SECTION_RECIPES: String
    val SEARCH_SECTION_INGREDIENTS: String
    val SEARCH_SECTION_PRODUCTS: String
    val SEARCH_CANCEL: String

    // ── Meal builder ─────────────────────────────────────────────────────────
    val MEAL_HEADING: String
    val MEAL_EMPTY: String
    val MEAL_ANALYSING: String
    val MEAL_QUANTITY: String
    val MEAL_COOKING: String
    val MEAL_REMOVE: String
    fun mealAnalyse(count: Int): String

    // ── Meal slots, dishes, recents, quick add ───────────────────────────────
    // Client-authored, verbatim from the iOS app (EnStrings/FrStrings.swift); not yet in the deck.
    fun mealSlotName(slot: MealSlot): String
    val MEAL_CHANGE_TIME: String
    val MEAL_TIME_TITLE: String
    fun journalSameAsYesterday(items: String): String
    fun journalRepeatYesterdayA11y(slot: String): String
    val DISHES_TITLE: String
    fun recentsTitle(slot: MealSlot): String
    fun dishDetail(ingredients: Int, servings: Int): String
    fun recentDetail(grams: Double, method: String): String
    val SAVE_AS_DISH: String
    val DISH_NAME_FIELD: String
    fun dishServings(count: Int): String
    val DISH_SAVE: String
    fun dishUpdate(name: String): String
    val DISH_MENU_A11Y: String
    fun dishPartsUnit(servings: Int): String
    val PIN: String
    val UNPIN: String
    val DELETE_DISH: String
    fun quickAddRow(kcal: Int): String
    val QUICK_ADD_NOTE: String
    /** Copy deck `results.unavailable`: an item the server could not compute; its nutrients are unknown, not 0. */
    val UNRECOGNISED_NOTE: String
    /** Client-authored: a not-recognised item's label in the journal. */
    fun unrecognisedLabel(label: String): String
    /** Client-authored: under the results total, how many items it leaves out. */
    fun resultsNotCounted(count: Int): String
    /** Client-authored: the meal total's figure when no item could be computed — unknown, never 0. */
    val RESULTS_TOTAL_UNKNOWN: String
    fun shortcutAddA11y(name: String): String
    val LOG_AGAIN_NOW: String

    // ── Cooking methods ──────────────────────────────────────────────────────
    fun cookingMethod(id: String): String

    // ── Nutrients ────────────────────────────────────────────────────────────
    val NUTRIENT_ENERGY: String
    val NUTRIENT_PROTEIN: String
    val NUTRIENT_CARBS: String
    val NUTRIENT_FAT: String
    val NUTRIENT_PROTEIN_ABBR: String
    val NUTRIENT_CARBS_ABBR: String
    val NUTRIENT_FAT_ABBR: String

    // ── Results ──────────────────────────────────────────────────────────────
    val RESULTS_TOTAL: String
    val RESULTS_EDIT: String
    val RESULTS_SAVE: String
    fun resultsRowSummary(quantity: Double, unit: String, method: String, state: String): String
    fun measuredState(id: String): String

    // ── Scan (barcode) ───────────────────────────────────────────────────────
    val SCAN_BUTTON: String
    val SCAN_BUTTON_A11Y: String
    val SCAN_EXPLAIN_TITLE: String
    val SCAN_EXPLAIN_BODY: String
    val SCAN_EXPLAIN_CONTINUE: String
    val SCAN_EXPLAIN_CANCEL: String
    val SCAN_SETTINGS_TITLE: String
    val SCAN_SETTINGS_BODY: String
    val SCAN_SETTINGS_OPEN: String
    val SCAN_SETTINGS_CANCEL: String
    val SCAN_NOT_FOUND: String
    val SCAN_REPORT: String
    val SCAN_REPORT_SENT: String
    val SCAN_CLOSE: String

    // ── Feedback ─────────────────────────────────────────────────────────────
    val FEEDBACK_TITLE: String
    val FEEDBACK_INTRO: String
    val FEEDBACK_REFERENCE_WEIGHT: String
    val FEEDBACK_DISPLAY_UNIT: String
    val FEEDBACK_UNITS: String
    val FEEDBACK_COMMENT: String
    val FEEDBACK_ADD: String
    val FEEDBACK_REMOVE_CORRECTION: String
    val FEEDBACK_SUBMIT: String
    val FEEDBACK_SUBMITTING: String
    val FEEDBACK_SUCCESS: String
    val FEEDBACK_FAILURE: String
    val FEEDBACK_CLOSE: String
    val FEEDBACK_REPORT: String

    // ── Cooking method picker — yield hint ───────────────────────────────────
    // Client-authored, not yet in contract/data/strings/fr.json.
    fun yieldHint(display: String, heavier: Boolean): String

    // ── Voice capture ────────────────────────────────────────────────────────
    // Client-authored, not in contract/data/strings/fr.json: the recognition API is not part of the
    // pinned contract.
    fun journalCaptureTitle(time: String): String
    val DICTATION_START: String
    val PHOTO_START: String
    val CAPTURE_ADDED: String
    val CAPTURE_NOTHING: String
    val VOICE_SETTINGS_TITLE: String
    val VOICE_SETTINGS_BODY: String
    val VOICE_URL_LABEL: String
    val VOICE_KEY_LABEL: String
    val VOICE_KEY_STORED: String
    val VOICE_SAVE: String
    val VOICE_CLEAR: String
    val VOICE_URL_INVALID: String
    val VOICE_SAVE_FAILED: String
    fun voiceSaved(host: String): String
    val VOICE_KEY_INVALID: String
    val VOICE_KEY_REQUIRED_NEW_HOST: String
    val VOICE_DELETE_TITLE: String
    fun voiceDeleteBody(hasAudio: Boolean, ready: Boolean): String
    val VOICE_DELETE_CONFIRM: String
    fun voiceMissing(missingUrl: Boolean, missingKey: Boolean): String
    val VOICE_CANCEL: String
    val VOICE_SEND_NOW: String
    val VOICE_RESEND: String
    val VOICE_OPEN: String
    val VOICE_PENDING: String
    fun voiceProcessing(lastStatus: String?): String
    fun voiceReady(itemCount: Int): String
    fun voiceIssue(issue: EntryIssue, serverMessage: String?, audioKept: Boolean): String
    val VOICE_ERR_TOO_SHORT: String
    val VOICE_ERR_RECORDER: String
    val MIC_EXPLAIN_TITLE: String
    val MIC_EXPLAIN_BODY: String
    val MIC_EXPLAIN_CONTINUE: String
    val MIC_EXPLAIN_CANCEL: String
    val MIC_SETTINGS_TITLE: String
    val MIC_SETTINGS_BODY: String
    val MIC_SETTINGS_OPEN: String
    val MIC_SETTINGS_CANCEL: String
    val VOICE_REVIEW_TITLE: String
    val VOICE_TRANSCRIPT: String
    val PHOTO_TAKE: String
    val PHOTO_CHOOSE: String
    val PHOTO_DESCRIPTION: String
    val PHOTO_ERR_SAVE: String
    val PHOTO_ERR_CAMERA: String
    val PHOTO_ERR_CAMERA_DENIED: String
    val VOICE_SKIP_ITEM: String
    val VOICE_ADD_TO_MEAL: String
    val VOICE_NO_ITEMS: String
    val VOICE_NO_CANDIDATES: String
    val VOICE_BACK: String
    val VOICE_IMPORTING: String
    val VOICE_SKIPPED_HEADING: String
    fun voiceSkipped(rawText: String, reason: SkipReason): String
    val VOICE_CONTINUE: String
    val VOICE_NOTHING_ADDED: String

    // ── Errors ───────────────────────────────────────────────────────────────
    fun error(message: String): String
    val RETRY: String
}

/**
 * Resolves [AppStrings] to whatever [org.pocketnutrition.api.LanguageSettings] says is active right
 * now. Re-resolved on every read — like `LanguageSettings.effective` itself, this is never cached, so
 * a call site's very next read reflects a switch made a moment ago.
 */
object Strings {
    val active: AppStrings
        get() = if (org.pocketnutrition.api.LanguageSettings.effective == "en") EnStrings else FrStrings
}
