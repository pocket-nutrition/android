// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.core.formatNumber
import org.pocketnutrition.recognition.EntryIssue
import org.pocketnutrition.recognition.SkipReason

/**
 * English copy, added in contract 2.0.0. See [AppStrings] for where every entry comes from — the
 * contract-sourced ones are hand-translated against the French value and its context, not
 * machine-translated.
 */
object EnStrings : AppStrings {

    override val BRAND = "Pocket Nutrition"

    override val JOURNAL_TITLE = "My journal"
    override val JOURNAL_EMPTY = "No meals logged yet"
    override val JOURNAL_ADD = "Add a meal"
    override val JOURNAL_DELETE = "Delete"
    override val JOURNAL_PRIVACY = "Your meals are stored only on this device."

    override val MEASURED_BEFORE_LONG = "Before cooking"
    override val MEASURED_AFTER_LONG = "After cooking"
    override val WEIGHED_LABEL = "Weighed:"
    override val CHANGE_COOKING = "Change cooking method and weighed state"
    override fun weighedInline(whenPart: String) = "(weighed $whenPart cooking)"
    override fun measuredWhen(state: String) = if (state == "cooked") "after" else "before"

    override val ABOUT_TITLE = "About"
    override fun aboutVersion(version: String) = "Version $version"
    override val ABOUT_PRIVACY_TITLE = "Privacy"
    override val ABOUT_PRIVACY_SUMMARY =
        "Your journal stays on this device. No account, no ads, no trackers."
    override val ABOUT_PRIVACY_NETWORK =
        "A search sends the text you typed. An analysis sends the foods, quantities and cooking " +
            "methods. No identifier accompanies these requests."
    override val ABOUT_PRIVACY_LINK = "Read the privacy policy"
    override val ABOUT_SOURCES_TITLE = "Data sources"
    override val ABOUT_SOURCES_BODY =
        "Nutritional values: Ciqual 2025 (ANSES, Licence Ouverte), McCance & Widdowson's CoFID 2021 " +
            "(contains public sector information licensed under the Open Government Licence v3.0), " +
            "USDA FoodData Central and USDA cooking yields tables (public domain). " +
            "Products, brands and photos: Open Food Facts, under the ODbL licence."
    override val ABOUT_LICENCE_TITLE = "Licence"
    override val ABOUT_LICENCE_BODY =
        "This app is free software, released under the Mozilla Public License 2.0. " +
            "You may view, modify and redistribute its source code."
    override val ABOUT_SOURCE_LINK = "View the source code"
    override val ABOUT_THIRD_PARTY_TITLE = "Third-party components"
    override val ABOUT_CLOSE = "Close"

    override val SETTINGS_TITLE = "Settings"

    override val ABOUT_LANGUAGE_TITLE = "Language"
    override val ABOUT_LANGUAGE_AUTO = "Automatic"
    override val ABOUT_LANGUAGE_FR = "Français"
    override val ABOUT_LANGUAGE_EN = "English"

    override val ABOUT_SERVER_TITLE = "Server"
    override val ABOUT_SERVER_BODY = "Server address the app uses. Leave empty for the official server."
    override val ABOUT_SERVER_APPLY = "Use this address"
    override val ABOUT_SERVER_RESET = "Back to official server"
    override val ABOUT_SERVER_INVALID =
        "Invalid address. Use a full URL, for example https://example.org"

    override val SEARCH_PLACEHOLDER = "Search for a food…"
    override val SEARCH_NO_RESULTS = "No results"
    override val MULTI_TERM_HINT = "Several foods at once: “rice chicken apple”"
    override fun confirmAdd(count: Int) = "Add ($count)"
    override val SEARCH_ALREADY_ADDED = "Already added"
    override val SEARCH_PRODUCT_BADGE = "Product"
    override val SEARCH_RECIPE_PREFIX = "Recipe: %s"
    override val SEARCH_RECIPE_COMPONENT_COUNT = "%d ingredients"
    override val SEARCH_SECTION_RECIPES = "Recipes"
    override val SEARCH_SECTION_INGREDIENTS = "Ingredients"
    override val SEARCH_SECTION_PRODUCTS = "Branded products"
    override val SEARCH_CANCEL = "Cancel"

    override val MEAL_HEADING = "Build your meal"
    override val MEAL_EMPTY = "Search for a food above to get started"
    override val MEAL_ANALYSING = "Analyzing…"
    override val MEAL_QUANTITY = "Quantity"
    override val MEAL_COOKING = "Cooking"
    override val MEAL_REMOVE = "Remove"
    override fun mealAnalyse(count: Int) = "Analyze the meal ($count item${if (count != 1) "s" else ""})"

    override fun mealSlotName(slot: MealSlot) = when (slot) {
        MealSlot.BREAKFAST -> "Breakfast"
        MealSlot.LUNCH -> "Lunch"
        MealSlot.DINNER -> "Dinner"
        MealSlot.SNACK -> "Snack"
    }
    override val MEAL_CHANGE_TIME = "Change time…"
    override val MEAL_TIME_TITLE = "Meal time"
    override fun journalSameAsYesterday(items: String) = "Same as yesterday: $items"
    override fun journalRepeatYesterdayA11y(slot: String) = "Repeat yesterday's ${slot.lowercase()}"
    override val DISHES_TITLE = "My dishes"
    override fun recentsTitle(slot: MealSlot) = when (slot) {
        MealSlot.BREAKFAST -> "Often at breakfast"
        MealSlot.LUNCH -> "Often at lunch"
        MealSlot.DINNER -> "Often at dinner"
        MealSlot.SNACK -> "Often as a snack"
    }
    override fun dishDetail(ingredients: Int, servings: Int) =
        "$ingredients ingredient${if (ingredients == 1) "" else "s"}" + (if (servings > 1) " · makes $servings" else "")
    override fun recentDetail(grams: Double, method: String) =
        "${roundHalfUp(grams)} g" + (if (method.isEmpty() || method == "raw") "" else " · ${cookingMethod(method).lowercase()}")
    override val SAVE_AS_DISH = "Save as dish"
    override val DISH_NAME_FIELD = "Dish name"
    override fun dishServings(count: Int) = "Makes $count portion${if (count == 1) "" else "s"}"
    override val DISH_SAVE = "Save"
    override fun dishUpdate(name: String) = "Update “$name”"
    override val DISH_MENU_A11Y = "Dish options"
    override fun dishPartsUnit(servings: Int) = if (servings > 1) "of $servings portions" else "×"
    override val PIN = "Pin"
    override val UNPIN = "Unpin"
    override val DELETE_DISH = "Delete dish"
    override fun quickAddRow(kcal: Int) = "Add $kcal kcal"
    override val QUICK_ADD_NOTE = "Entered manually"
    override val UNRECOGNISED_NOTE = "Data unavailable for this food."
    override fun unrecognisedLabel(label: String) = "$label · $UNRECOGNISED_NOTE"
    override val RESULTS_TOTAL_UNKNOWN = "—"
    override fun resultsNotCounted(count: Int) =
        if (count == 1) "1 item not recognised, not counted in the total"
        else "$count items not recognised, not counted in the total"
    override fun shortcutAddA11y(name: String) = "Add $name"
    override val LOG_AGAIN_NOW = "Log again now"

    override fun cookingMethod(id: String) = when (id) {
        "raw" -> "Raw"
        "boiled" -> "Boiled"
        "steamed" -> "Steamed"
        "grilled" -> "Grilled"
        "roasted" -> "Roasted"
        "fried" -> "Fried"
        else -> "cooked (generic)"
    }

    override val NUTRIENT_ENERGY = "Energy"
    override val NUTRIENT_PROTEIN = "Protein"
    override val NUTRIENT_CARBS = "Carbs"
    override val NUTRIENT_FAT = "Fat"
    override val NUTRIENT_PROTEIN_ABBR = "P"
    override val NUTRIENT_CARBS_ABBR = "C"
    override val NUTRIENT_FAT_ABBR = "F"

    override val RESULTS_TOTAL = "Meal total"
    override val RESULTS_EDIT = "Edit the meal"
    override val RESULTS_SAVE = "Save this meal"
    override fun resultsRowSummary(quantity: Double, unit: String, method: String, state: String) =
        "${formatNumber(quantity, "en")} $unit · $method · weighed $state"
    override fun measuredState(id: String) = when (id) {
        "raw" -> "raw"
        "cooked" -> "cooked"
        else -> id
    }

    override val SCAN_BUTTON = "📷"
    override val SCAN_BUTTON_A11Y = "Scan a barcode"
    override val SCAN_EXPLAIN_TITLE = "Allow camera access"
    override val SCAN_EXPLAIN_BODY =
        "Pocket Nutrition uses the camera only to read a product's barcode and look up its " +
            "nutritional values. No photo is saved."
    override val SCAN_EXPLAIN_CONTINUE = "Continue"
    override val SCAN_EXPLAIN_CANCEL = "Not now"
    override val SCAN_SETTINGS_TITLE = "Camera disabled"
    override val SCAN_SETTINGS_BODY =
        "Camera access was denied. Allow it in the app's settings to scan a barcode."
    override val SCAN_SETTINGS_OPEN = "Open settings"
    override val SCAN_SETTINGS_CANCEL = "Cancel"
    override val SCAN_NOT_FOUND =
        "This barcode is not in our database yet. Search for its ingredients by name; you can then save " +
            "the meal as a dish to reuse it."
    override val SCAN_REPORT = "Report this product"
    override val SCAN_REPORT_SENT = "Thanks, reported"
    override val SCAN_CLOSE = "Close"

    override val FEEDBACK_TITLE = "Report an inaccuracy"
    override val FEEDBACK_INTRO = "Choose a category, then the specific error."
    override val FEEDBACK_REFERENCE_WEIGHT = "Reference weight"
    override val FEEDBACK_DISPLAY_UNIT = "Display unit"
    override val FEEDBACK_UNITS = "Units"
    override val FEEDBACK_COMMENT = "Comment (optional)"
    override val FEEDBACK_ADD = "Add"
    override val FEEDBACK_REMOVE_CORRECTION = "Remove this correction"
    override val FEEDBACK_SUBMIT = "Submit the report"
    override val FEEDBACK_SUBMITTING = "Sending…"
    override val FEEDBACK_SUCCESS = "Thanks, your report has been sent to the team."
    override val FEEDBACK_FAILURE = "Could not send right now. Try again later."
    override val FEEDBACK_CLOSE = "Close"
    override val FEEDBACK_REPORT = "Report"

    override fun yieldHint(display: String, heavier: Boolean) =
        "This food weighs ${display}x ${if (heavier) "heavier" else "lighter"} once cooked"

    override val VOICE_SETTINGS_TITLE = "Voice recognition server"
    override val VOICE_SETTINGS_BODY =
        "The recording is sent to the server configured here. It is kept on the phone until the result " +
            "has been received (7 days at most once sent), then deleted. A recording that was never sent stays on the phone " +
            "until it is sent or deleted."
    override val VOICE_URL_LABEL = "Server address (https)"
    override val VOICE_KEY_LABEL = "API key"
    override val VOICE_KEY_STORED = "A key is saved. Leave blank to keep it."
    override val VOICE_SAVE = "Save"
    override val VOICE_CLEAR = "Clear"
    override val VOICE_URL_INVALID = "Invalid address. Use a complete https URL."
    override val VOICE_SAVE_FAILED = "Could not store the key securely on this device."
    override fun voiceSaved(host: String) = "Saved. The key will be sent to $host."
    override val VOICE_KEY_INVALID = "Invalid key: visible ASCII characters only, no spaces or line breaks."
    override val VOICE_KEY_REQUIRED_NEW_HOST =
        "The server changed: enter the API key again. It is never sent to a different server without that."
    override val VOICE_DELETE_TITLE = "Delete this capture?"
    override fun voiceDeleteBody(hasAudio: Boolean, ready: Boolean) = when {
        ready -> "The result will be removed from this phone. This cannot be undone."
        hasAudio -> "The recording may not have been analysed yet: it will be removed from this phone. This cannot be undone."
        else -> "This cannot be undone."
    }
    override val VOICE_DELETE_CONFIRM = "Delete"
    override fun voiceMissing(missingUrl: Boolean, missingKey: Boolean) = when {
        missingUrl && missingKey -> "The server address and the API key are missing. Set them in Settings."
        missingUrl -> "The server address (https) is missing. Set it in Settings."
        else -> "The API key is missing. Set it in Settings."
    }
    override val VOICE_CANCEL = "Cancel"
    override val VOICE_SEND_NOW = "Send now"
    override val VOICE_RESEND = "Send again"
    override val VOICE_OPEN = "Open"
    override val VOICE_PENDING = "Waiting to be sent"
    override fun voiceProcessing(lastStatus: String?) = when (lastStatus) {
        "transcribing" -> "Transcribing…"
        "extracting" -> "Analysing…"
        else -> "Will be processed soon"
    }
    override fun voiceReady(itemCount: Int) = "Ready — $itemCount item${if (itemCount == 1) "" else "s"}"
    override fun voiceIssue(issue: EntryIssue, serverMessage: String?, audioKept: Boolean) = when (issue) {
        EntryIssue.OFFLINE -> "Waiting to be sent — offline"
        EntryIssue.SERVER_ERROR -> "Waiting to be sent — the server is not responding properly"
        EntryIssue.UNAUTHORIZED -> "API key rejected"
        EntryIssue.TOO_LARGE -> "Recording too large for the server"
        EntryIssue.UNSUPPORTED -> "Audio format refused by the server"
        EntryIssue.REJECTED -> "Upload refused by the server — check the server address"
        EntryIssue.REDIRECTED -> "The server redirected the request — check the server address"
        EntryIssue.SECURE_CONNECTION -> "Secure connection failed — check the server address and certificate"
        EntryIssue.EXPIRED, EntryIssue.GONE ->
            if (audioKept) "Expired — the recording is still on your phone" else "Result no longer available"
        EntryIssue.FAILED ->
            (serverMessage ?: "Analysis failed") + if (audioKept) " — the recording is still on your phone" else ""
        EntryIssue.AUDIO_LOST -> "The recording is no longer available on this phone"
    }
    override val VOICE_ERR_TOO_SHORT = "Recording too short."
    override val VOICE_ERR_RECORDER = "Could not start recording."
    override val MIC_EXPLAIN_TITLE = "Allow microphone access"
    override val MIC_EXPLAIN_BODY =
        "Pocket Nutrition uses the microphone only when you tap Record, to dictate a meal. The recording " +
            "is sent to the recognition server configured in Settings, and kept on the phone until the " +
            "result has been received (7 days at most once sent), then deleted. A recording that was never sent stays on the phone " +
            "until it is sent or deleted."
    override val MIC_EXPLAIN_CONTINUE = "Continue"
    override val MIC_EXPLAIN_CANCEL = "Not now"
    override val MIC_SETTINGS_TITLE = "Microphone disabled"
    override val MIC_SETTINGS_BODY =
        "Microphone access was denied. Allow it in the app's settings to dictate a meal."
    override val MIC_SETTINGS_OPEN = "Open settings"
    override val MIC_SETTINGS_CANCEL = "Cancel"
    override val VOICE_REVIEW_TITLE = "Review capture"
    override val VOICE_TRANSCRIPT = "Transcript"
    override val DICTATION_START = "Dictate a meal"
    override val PHOTO_START = "Photograph a meal"
    override val CAPTURE_ADDED = "Added from your dictation or photo."
    override val CAPTURE_NOTHING = "Nothing could be added. It is kept in your journal."
    override fun journalCaptureTitle(time: String) = "Meal in progress · $time"
    override val PHOTO_TAKE = "Take a photo"
    override val PHOTO_CHOOSE = "Choose a photo"
    override val PHOTO_DESCRIPTION = "What was seen"
    override val PHOTO_ERR_SAVE = "The photo could not be saved."
    override val PHOTO_ERR_CAMERA = "No camera app is available."
    override val PHOTO_ERR_CAMERA_DENIED = "Camera access is off. Allow it in Settings, or choose a photo instead."
    override val VOICE_SKIP_ITEM = "Skip"
    override val VOICE_ADD_TO_MEAL = "Add to meal"
    override val VOICE_NO_ITEMS = "No food recognised."
    override val VOICE_NO_CANDIDATES = "No match suggested"
    override val VOICE_BACK = "Back"
    override val VOICE_IMPORTING = "Looking up foods…"
    override val VOICE_SKIPPED_HEADING = "Not added:"
    override fun voiceSkipped(rawText: String, reason: SkipReason) = "\u201c$rawText\u201d — " + when (reason) {
        SkipReason.NO_CANDIDATE -> "no match"
        SkipReason.NOT_FOUND -> "food not found in the catalogue"
        SkipReason.NOT_BUILDABLE -> "the catalogue lacks the portion or cooking information needed to add it"
    }
    override val VOICE_CONTINUE = "Continue to the meal"
    override val VOICE_NOTHING_ADDED = "No food could be added."

    override fun error(message: String) = "Error: $message"
    override val RETRY = "Retry"
}
