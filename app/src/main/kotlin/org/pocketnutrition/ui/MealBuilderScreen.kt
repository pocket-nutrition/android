// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import org.pocketnutrition.recognition.CaptureCoordinator
import org.pocketnutrition.recognition.EntryState
import org.pocketnutrition.recognition.EntryStore
import org.pocketnutrition.recognition.defaultSelection
import org.pocketnutrition.recognition.deleteCapture
import org.pocketnutrition.recognition.resolveImport
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.pocketnutrition.api.ApiException
import org.pocketnutrition.api.FeedbackCorrection
import org.pocketnutrition.api.FeedbackRequest
import org.pocketnutrition.api.IngredientDetail
import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.api.PocketNutritionApi
import org.pocketnutrition.core.FEEDBACK_CATEGORIES
import org.pocketnutrition.core.FeedbackErrorDef
import org.pocketnutrition.core.FeedbackInput
import org.pocketnutrition.core.IngredientSuggestion
import org.pocketnutrition.core.MAX_SUGGESTIONS
import org.pocketnutrition.core.MealIngredient
import org.pocketnutrition.core.applyMethodChange
import org.pocketnutrition.core.CameraAccess
import org.pocketnutrition.core.buildMealIngredient
import org.pocketnutrition.core.deduplicateByLabel
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnCooking
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.design.PnRole
import org.pocketnutrition.core.feedbackCurrentValue
import org.pocketnutrition.core.foodGroupIcon
import org.pocketnutrition.core.formatNumber
import org.pocketnutrition.core.resolveYieldFactor
import org.pocketnutrition.core.shouldToggleSelection
import org.pocketnutrition.core.switchMeasuredState
import org.pocketnutrition.core.unpackRecipe
import org.pocketnutrition.model.BuilderEntry
import org.pocketnutrition.model.MealItem
import org.pocketnutrition.model.MealShortcutsStore
import org.pocketnutrition.model.SavedMeal
import org.pocketnutrition.model.asMealIngredient
import org.pocketnutrition.model.isQuickAddRow
import org.pocketnutrition.model.quickAddRow
import org.pocketnutrition.model.recentFoods
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.defaultTimeFor
import org.pocketnutrition.core.mealSlotAt
import org.pocketnutrition.core.parseQuickAddKcal
import androidx.compose.runtime.mutableLongStateOf
import org.pocketnutrition.model.asMealItem
import org.pocketnutrition.model.withAvailability
import org.pocketnutrition.model.asMealRow
import org.pocketnutrition.model.asRequest
import org.pocketnutrition.model.lockedToRawIfIndustrial
import org.pocketnutrition.model.summary

/**
 * Compose a meal, then analyse it.
 *
 * DELIBERATE DIVERGENCE FROM THE SANDBOX
 * The sandbox groups rows into one drop-lane per cooking method and you drag a card between lanes to
 * re-cook it. That is a mouse interaction; on a phone it competes with scrolling and hides the method
 * behind a gesture nobody discovers. Here each row carries its own method picker.
 *
 * The *behaviour* must not diverge, only the trigger — and it does not: selecting a method runs
 * [applyMethodChange] from the corpus, so the weight follows. Switch a grillé row to cru and the grams
 * move back to the raw equivalent, exactly as they do when a sandbox card is dragged between lanes.
 *
 *
 * MULTI-TERM SELECTION, and the one deliberate divergence in it. Typing more than one ingredient phrase
 * turns a tap into a checkbox and adds the set in one go, as the sandbox does. What is NOT ported is the
 * sandbox's "Voir plus" pagination: it reveals six rows per click, where this list scrolls a capped
 * window, because scrolling is the phone's default gesture and a load-more button costs a tap per step.
 * `search.showMore` was re-scoped to sandbox-only in contracts 1.8.0 to record that.
 * Rows are [MealIngredient] rather than [MealItem] because that is what the corpus functions operate
 * on. [MealItem] is still the journal's persisted shape; the conversion happens once, at the save
 * boundary — see model/MealRow.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealBuilderScreen(
    onCancel: () -> Unit,
    onAnalysed: (List<NutritionalProfile>, List<MealItem>, AnalysedMeal) -> Unit,
    initialItems: List<MealIngredient> = emptyList(),
    /** When the meal was eaten and its part of the day; the chip under the title changes either. */
    initialAtEpochMs: Long = System.currentTimeMillis(),
    initialSlot: MealSlot = mealSlotAt(initialAtEpochMs),
    /** Saved dishes and ★ pins; null hides the shortcuts. */
    shortcuts: MealShortcutsStore? = null,
    /** The journal's meals, for "often at <slot>" suggestions. */
    pastMeals: () -> List<SavedMeal> = { emptyList() },
    // False for rows imported from a voice capture, which stay in the order they were spoken.
    sortInitialItems: Boolean = true,
    /** Dictations and meal photos: the mic and camera beside the search field. */
    captureStore: EntryStore? = null,
    captureCoordinator: CaptureCoordinator? = null,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    val suggestions = remember { mutableStateListOf<IngredientSuggestion>() }
    // Sorted alphabetically only here, on reopening a saved meal: `initialItems` is non-empty
    // when editing (MainActivity supplies it from an editingMeal) or when a voice capture hands rows
    // over (those pass sortInitialItems = false and keep their spoken order), never when composing
    // a new one. The addition order is a fact about a past session the person is no longer holding in
    // mind, and a long meal reads faster alphabetically than in whatever sequence it happened to be
    // typed. A freshly composed meal does NOT sort — rows added via `items.addAll(rowsFor(...))` below
    // stay in the order they were added, matching the iOS twin.
    val items = remember {
        mutableStateListOf<MealIngredient>().apply {
            addAll(
                if (sortInitialItems) initialItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                else initialItems,
            )
        }
    }
    var mealAt by remember { mutableLongStateOf(initialAtEpochMs) }
    var slot by remember { mutableStateOf(initialSlot) }
    val dishes = remember { MealBuilderDishes(items) }
    var isSavingDish by remember { mutableStateOf(false) }
    // The barcode the catalogue did not know, so it can be reported once.
    var notFoundCode by remember { mutableStateOf<String?>(null) }
    var isReportSent by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var isAnalysing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Captures made from this screen. Each result is added to the meal as soon as it is in, with its best
    // matches, editable like any other row. One that cannot be added in full stays in the journal.
    val capturedHere = remember { mutableStateListOf<String>() }
    val handledCaptures = remember { mutableStateListOf<String>() }
    var captureNotice by remember { mutableStateOf<String?>(null) }
    // One collector, processing ready captures one after another: keyed on Unit so handling one (which
    // changes the ready list) never cancels a lookup still in flight.
    LaunchedEffect(Unit) {
        val store = captureStore ?: return@LaunchedEffect
        val coordinator = captureCoordinator ?: return@LaunchedEffect
        snapshotFlow {
            capturedHere.filter { id -> id !in handledCaptures && store.find(id)?.state == EntryState.READY }
        }.collect { ready ->
            for (id in ready) {
                handledCaptures += id
                val recognized = store.find(id)?.result?.items.orEmpty()
                val plan = try {
                    resolveImport(
                        items = recognized,
                        selections = recognized.map(::defaultSelection),
                        lang = LanguageSettings.effective,
                        lookup = { PocketNutritionApi.ingredientDetail(it) },
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    null // A lookup failed: the capture stays in the journal, to be opened from there.
                }
                if (plan == null || plan.rows.isEmpty()) {
                    captureNotice = Strings.active.CAPTURE_NOTHING
                    continue
                }
                items.addAll(plan.rows)
                if (plan.skipped.isEmpty()) deleteCapture(store, coordinator, id, scope)
                captureNotice = Strings.active.CAPTURE_ADDED
            }
        }
    }

    // How many ingredient phrases the server split the query into, from X-Segment-Count. 1 until a
    // response says otherwise, so a fresh screen behaves as single-add.
    var segmentCount by remember { mutableStateOf(1) }

    // Rows checked in multi-term mode, keyed by ingredientId. Cleared whenever the query changes, for the
    // same reason `suggestions` is: a selection made against one query's rows means nothing against
    // another's.
    val selectedIds = remember { mutableStateListOf<String>() }

    // Which row is expanded, lifted here rather than kept in each MealItemRow — matching the sandbox's
    // MealBuilder, which lifts the same state to the same effect: only one card can ever be open, so
    // expanding a second one collapses the first without either row needing to know the other exists.
    // New rows start collapsed, since this is null until a row is tapped.
    var expandedItemId by remember { mutableStateOf<String?>(null) }

    // Barcode scanning: the scanner replaces this composable's own content rather than swapping to a
    // sibling MainActivity Screen, so `items` above survives opening and closing the camera — see the
    // note on BarcodeScannerScreen for why a new Screen case would lose an in-progress meal.
    var showScanner by remember { mutableStateOf(false) }
    var permissionDialog by remember { mutableStateOf<CameraPermissionDialog?>(null) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showScanner = true }

    // Closes just the scanner on system back, before MainActivity's own BackHandler (which only fires
    // when the outer Screen is not Journal, and is therefore unrelated) ever sees the event — Compose
    // resolves the innermost enabled BackHandler first.
    BackHandler(enabled = showScanner) { showScanner = false }

    // Debounced by 300 ms — the same interval as the sandbox's useSearch, matched on purpose so a
    // keystroke that costs a request there costs one here too. LaunchedEffect keyed on the query
    // cancels the previous wait automatically, which is the whole debounce.
    //
    // One difference is deliberate and worth keeping: cancellation here aborts the in-flight request,
    // where the sandbox only clears its pending timer. An unaborted fetch can still resolve after a
    // newer one and overwrite it, which is a race clearing alone does not close.
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            suggestions.clear()
            isSearching = false
            // Back to one term, so the hint returns for the next search. Without this the count stays at
            // whatever the last response said, and an empty field after a multi-term search silently
            // withholds the hint from a user who has not necessarily understood the feature yet.
            segmentCount = 1
            selectedIds.clear()
            return@LaunchedEffect
        }
        isSearching = true
        // Drop the previous query's results *now*, not when the new ones land.
        //
        // Leaving them up was observed on the iOS simulator as the field reading "poulet" while the
        // list still offered "Poule" — hen, 252 g, an Open Food Facts product — because those rows were
        // the answer to a query the user had already moved on from. This client had it too. Not a
        // latency problem: the API answers in ~70 ms. The bug is that `suggestions` was only ever
        // rewritten after the await, so every in-flight moment rendered stale food with nothing marking
        // it stale. In a nutrition app that is the dangerous failure: not blank, not an error, just
        // confidently the wrong ingredient.
        //
        // Clearing costs a spinner-only gap rather than a flash of SEARCH_NO_RESULTS, because that
        // message is itself gated on `!isSearching`.
        //
        // NOT covered by a corpus vector, deliberately: the corpus pins pure functions, and "when may
        // these results be shown" is state-machine timing. Modelling it as a reducer neither client
        // actually calls would produce a green test that proves nothing. Verified on a running app
        // instead — type "poulet" and the list must never offer "Poule".
        suggestions.clear()
        selectedIds.clear()
        delay(300)
        // NOT runCatching. It catches Throwable, which includes the CancellationException thrown
        // every time this effect is superseded by the next keystroke — and the user would then see
        // "Erreur : The coroutine scope left the composition" on perfectly normal typing. Cancellation
        // is control flow, not failure: rethrow it and let the coroutine machinery unwind.
        try {
            val lang = LanguageSettings.effective
            val found = PocketNutritionApi.search(trimmed, lang = lang, limit = MAX_SUGGESTIONS)
            suggestions.clear()
            // The corpus rule, not a local tweak: collapses the duplicate labels the API returns, pins
            // recipes on top, and prefers the active language once there are enough same-language
            // results. Without it a search for "poulet" showed four rows all reading
            // "Poulet / 100 g / Produit".
            //
            // NOTE it reorders: recipes are lifted to the top and the preferred language floats up
            // above MIN_SAME_LANG. The server's "first N rows are one best match per term" ordering
            // therefore does NOT survive to here, which is why multi-select is a free-form checklist
            // rather than one slot per term. X-Segment-Count is a count, not a mapping.
            suggestions.addAll(deduplicateByLabel(found.rows, preferLang = lang))
            segmentCount = found.segmentCount
            errorMessage = null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            suggestions.clear()
            errorMessage = (failure as? ApiException)?.frenchMessage ?: failure.message
        }
        isSearching = false
    }

    if (showScanner) {
        BarcodeScannerScreen(
            onBarcodeDetected = detected@{ ean13 ->
                // `BarcodeScannerScreen`'s own `hasFired` flag guards against the decoder reporting the same
                // frame's result twice, but that flag lives on the camera composable — if it were ever
                // recreated mid-lookup (the iOS twin hit exactly this: a state change recreated its
                // camera view, and the still-framed product got scanned a second time before the first
                // lookup finished), a fresh `hasFired = false` would let a second call through. This
                // check does not depend on the camera composable at all: once `showScanner` is false,
                // any further call is a leftover from an already-handled detection and is ignored.
                if (!showScanner) return@detected
                scope.launch {
                    showScanner = false
                    errorMessage = null
                    try {
                        val found = PocketNutritionApi.lookupBarcode(ean13, LanguageSettings.effective)
                        if (found != null) {
                            items.addAll(rowsFor(found))
                        } else {
                            if (notFoundCode != ean13) isReportSent = false
                            notFoundCode = ean13
                            errorMessage = Strings.active.SCAN_NOT_FOUND
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        errorMessage = (failure as? ApiException)?.frenchMessage ?: failure.message
                    }
                }
            },
            onClose = { showScanner = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(PnColor.SurfacePaper)
            // A tap anywhere but the field itself blurs it and hides the IME. The field's own
            // BasicTextField installs its own press handling, which Compose resolves first, so this
            // never steals a tap meant to place the cursor or start editing.
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Strings.active.MEAL_HEADING,
                    color = PnColor.InkDefault,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                SlotChip(
                    slot = slot,
                    atEpochMs = mealAt,
                    // Another slot moves the time into its band (same day), unless it already falls there.
                    onPickSlot = { candidate ->
                        slot = candidate
                        if (mealSlotAt(mealAt) != candidate) mealAt = defaultTimeFor(candidate, mealAt)
                    },
                    onPickTime = { picked ->
                        mealAt = picked
                        slot = mealSlotAt(picked)
                    },
                )
            }
            if (shortcuts != null) {
                DishMenu(
                    canSave = items.any { !it.isQuickAddRow },
                    updatable = dishes.updatable(shortcuts),
                    onSave = { isSavingDish = true },
                    onUpdate = { dishes.writeBack(it, shortcuts) },
                )
            }
            TextButton(onClick = onCancel) {
                Text(Strings.active.SEARCH_CANCEL, color = PnColor.InkSoft, fontSize = 13.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SearchField(value = query, onValueChange = { query = it })
            }
            if (captureStore != null && captureCoordinator != null) {
                CaptureButtons(
                    store = captureStore,
                    coordinator = captureCoordinator,
                    onCaptured = { capturedHere += it },
                    onError = { errorMessage = it },
                )
            }
            IconButton(onClick = {
                val act = activity
                if (act == null) {
                    // No Activity to read a permission status from — nothing safer to do than skip.
                    return@IconButton
                }
                when (currentCameraAccess(act)) {
                    CameraAccess.GRANTED -> showScanner = true
                    CameraAccess.NOT_DETERMINED, CameraAccess.DENIED_RETRIABLE ->
                        permissionDialog = CameraPermissionDialog.Explain
                    CameraAccess.PERMANENTLY_DENIED -> permissionDialog = CameraPermissionDialog.Settings
                }
            }) {
                Text(
                    Strings.active.SCAN_BUTTON,
                    fontSize = 20.sp,
                    modifier = Modifier.semantics { contentDescription = Strings.active.SCAN_BUTTON_A11Y },
                )
            }
        }

        // What the captures made here are doing; ready ones are added to the meal and leave this list.
        capturedHere.filter { it !in handledCaptures }.forEach { id ->
            captureStore?.find(id)?.let { entry ->
                Text(
                    text = (if (entry.isPhoto) "📷 " else "🎙️ ") + captureStatus(entry),
                    color = PnColor.InkSoft,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        captureNotice?.let { notice ->
            Text(notice, color = PnColor.BrandLeafDeep, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }

        // Teaches multi-term search, which is otherwise undiscoverable — nothing about a search field
        // suggests that typing three words behaves differently. A line under the field rather than the
        // deck's `search.placeholder`, because that string is 51 characters against a field that fits
        // about 22, so the "(riz poulet pomme)" doing the teaching is exactly what gets truncated.
        // Hidden as soon as the server reports a second term: the person is already doing it.
        if (segmentCount <= 1) {
            Text(
                text = Strings.active.MULTI_TERM_HINT,
                color = PnColor.InkSoft,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.width(20.dp))
        }

        if (shortcuts != null) {
            val needle = foldForSearch(query)
            TypedShortcuts(
                quickAddKcal = parseQuickAddKcal(query),
                dishes = if (needle.length < 2) emptyList() else
                    shortcuts.dishesByRelevance.filter { foldForSearch(it.name).contains(needle) }.take(3),
                foods = if (needle.length < 2) emptyList() else
                    recentFoods(pastMeals(), slot, RECENTS_SEARCH_LIMIT)
                        .filter { foldForSearch(it.item.label).contains(needle) }.take(3),
                store = shortcuts,
                onQuickAdd = { kcal ->
                    items.add(quickAddRow(kcal.toDouble()))
                    query = ""
                },
                onApplyDish = { dishes.apply(it, shortcuts); query = "" },
                onAddRecent = { items.add(it.item.asMealIngredient()); query = "" },
            )
        }

        if (suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SUGGESTION_ROW_HEIGHT * 5)
                    .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))
                    .border(1.dp, PnColor.InkLine, RoundedCornerShape(PnRadius.Default)),
            ) {
                items(suggestions, key = { "${it.ingredientId}#${it.label}" }) { suggestion ->
                    val alreadyAdded = items.any { it.ingredientId == suggestion.ingredientId }
                    // The decision is the corpus predicate, not a local `segmentCount > 1`, because of
                    // the branch that is easy to miss: a RECIPE always adds immediately even on a
                    // three-term query, since it unpacks into a whole meal rather than being one of those
                    // terms. Mutation-tested — dropping it fails `multi-term-recipe` by name.
                    val selectable = shouldToggleSelection(segmentCount, suggestion.productType)
                    SuggestionRow(
                        suggestion = suggestion,
                        alreadyAdded = alreadyAdded,
                        // null, not false, in single-term mode: the checkbox is ABSENT rather than
                        // unchecked, so the row keeps the 60 dp height the list's cap is a multiple of.
                        checked = if (selectable) suggestion.ingredientId in selectedIds else null,
                    ) {
                        if (alreadyAdded) return@SuggestionRow
                        if (selectable) {
                            if (suggestion.ingredientId in selectedIds) {
                                selectedIds.remove(suggestion.ingredientId)
                            } else {
                                selectedIds.add(suggestion.ingredientId)
                            }
                        } else {
                            items.addAll(rowsFor(suggestion))
                            query = ""
                            suggestions.clear()
                        }
                    }
                }
            }
        } else if (query.isNotBlank() && !isSearching) {
            Text(Strings.active.SEARCH_NO_RESULTS, color = PnColor.InkSoft, fontSize = 13.sp)
        }

        // Cancel / confirm for a multi-term selection, shown only while something is checked. Confirm goes
        // through the same rowsFor path a single tap takes, so a checked recipe still unpacks.
        if (selectedIds.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { selectedIds.clear(); query = "" }) {
                    Text(Strings.active.SEARCH_CANCEL, color = PnColor.InkSoft, fontSize = 13.sp)
                }
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(
                        text = Strings.active.confirmAdd(selectedIds.size),
                        onClick = {
                            val chosen = suggestions.filter { it.ingredientId in selectedIds }
                            selectedIds.clear()
                            chosen.forEach { items.addAll(rowsFor(it)) }
                            query = ""
                            suggestions.clear()
                        },
                    )
                }
            }
        }

        val quickPicksRecents = if (shortcuts != null && items.isEmpty() && suggestions.isEmpty()) {
            recentFoods(pastMeals(), slot, RECENTS_EMPTY_LIMIT)
        } else emptyList()
        if (shortcuts != null && items.isEmpty() && suggestions.isEmpty() &&
            (shortcuts.dishes.isNotEmpty() || quickPicksRecents.isNotEmpty())
        ) {
            QuickPicks(
                store = shortcuts,
                recents = quickPicksRecents,
                slot = slot,
                onApplyDish = { dishes.apply(it, shortcuts) },
                onAddRecent = { items.add(it.item.asMealIngredient()) },
                modifier = Modifier.weight(1f),
            )
        } else if (items.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Hidden while the suggestion list is open, matching iOS. MEAL_EMPTY says "search for a
                // food above to begin" — advice the person is visibly already following — so with
                // results on screen it reads as though the app has not noticed them, and two
                // empty-looking states stacked down one screen is worse than one. The Column keeps its
                // weight either way so the layout does not jump as results arrive and go.
                if (suggestions.isEmpty()) {
                    Text("🥗", fontSize = 40.sp)
                    Text(
                        text = Strings.active.MEAL_EMPTY,
                        color = PnColor.InkSoft,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(dishes.entries, key = { it.key }) { entry ->
                    when (entry) {
                        is BuilderEntry.Group -> DishGroupHeader(
                            name = entry.group.name,
                            summary = dishes.members(entry.group).joinToString(" · ") { it.label },
                            servings = entry.group.servings,
                            parts = entry.group.parts,
                            isExpanded = dishes.isExpanded(entry.group),
                            onToggle = { dishes.toggle(entry.group) },
                            onParts = { dishes.setParts(entry.group, it) },
                            onRemove = { dishes.removeGroup(entry.group) },
                        )
                        is BuilderEntry.Row -> items.firstOrNull { it.id == entry.rowId }?.let { item ->
                            if (item.isQuickAddRow) {
                                QuickAddMealRow(kcal = item.quantity, onRemove = { dishes.removeRow(item.id) })
                            } else {
                                MealItemRow(
                                    item = item,
                                    isExpanded = expandedItemId == item.id,
                                    onToggleExpand = {
                                        expandedItemId = if (expandedItemId == item.id) null else item.id
                                    },
                                    // By id rather than index: a dish's head shifts the list's positions.
                                    onChange = { changed ->
                                        val index = items.indexOfFirst { it.id == item.id }
                                        if (index >= 0) items[index] = changed
                                    },
                                    onRemove = { dishes.removeRow(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Both hidden while the suggestion list is competing for vertical room and nothing is checked
        // yet, so a short screen does not squeeze the (already height-capped) suggestion list to fit
        // this section underneath it too. Kept once something is checked, because that is exactly when
        // the confirm/cancel row above is needed and visible.
        if (selectedIds.isNotEmpty() || (suggestions.isEmpty() && (items.isNotEmpty() || errorMessage != null))) {
            errorMessage?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PnColor.StatusBadSoft, RoundedCornerShape(PnRadius.Small))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Strings.active.error(message), color = PnColor.StatusBad, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
                val missingCode = notFoundCode
                if (missingCode != null && message == Strings.active.SCAN_NOT_FOUND) {
                    TextButton(
                        enabled = !isReportSent,
                        onClick = {
                            isReportSent = true
                            scope.launch { reportMissingBarcode(missingCode) }
                        },
                    ) {
                        Text(
                            if (isReportSent) Strings.active.SCAN_REPORT_SENT else Strings.active.SCAN_REPORT,
                            color = if (isReportSent) PnColor.InkSoft else PnColor.BrandLeafDeep,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Hidden rather than merely disabled for an empty meal: a "0 aliment" button the user
            // cannot press yet is not a call to action, just noise on a screen whose one real task
            // right now is the search field above it.
            if (items.isNotEmpty()) {
                PrimaryButton(
                    text = if (isAnalysing) Strings.active.MEAL_ANALYSING else Strings.active.mealAnalyse(items.size),
                    enabled = !isAnalysing,
                    onClick = {
                        isAnalysing = true
                        errorMessage = null
                        scope.launch {
                            // Same reason as the search effect: cancellation is rethrown rather than
                            // shown. Here it fires when the screen is left mid-request.
                            try {
                                // Quick-add rows carry their own energy and are never sent; the answers are
                                // put back in row order so profiles stay aligned with items.
                                val snapshot = items.toList()
                                val sent = snapshot.filterNot { it.isQuickAddRow }
                                val answers = if (sent.isEmpty()) emptyList() else
                                    PocketNutritionApi.analyse(sent.map { it.asRequest() })
                                val profiles = mergeQuickAdd(snapshot, answers)
                                onAnalysed(
                                    profiles,
                                    withAvailability(snapshot.map { it.asMealItem() }, profiles),
                                    AnalysedMeal(mealAt, slot),
                                )
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (failure: Exception) {
                                errorMessage = (failure as? ApiException)?.frenchMessage ?: failure.message
                            }
                            isAnalysing = false
                        }
                    },
                )
            }
        }
    }

    if (isSavingDish && shortcuts != null) {
        val mealItems = items.map { it.asMealItem() }
        SaveDishDialog(
            items = mealItems,
            onSave = { name, servings -> shortcuts.saveDish(name, servings, mealItems) },
            onDismiss = { isSavingDish = false },
        )
    }

    when (permissionDialog) {
        CameraPermissionDialog.Explain -> CameraPrePermissionDialog(
            onContinue = {
                activity?.let(::markCameraPermissionRequested)
                permissionDialog = null
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { permissionDialog = null },
        )
        CameraPermissionDialog.Settings -> CameraSettingsDialog(
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
                permissionDialog = null
            },
            onDismiss = { permissionDialog = null },
        )
        null -> Unit
    }
}

/**
 * The rows one tap on a suggestion produces: several for a recipe, one for anything else.
 *
 * This screen used to call [asMealRow] unconditionally, so tapping "Recette : salade niçoise" added a
 * single row named after the recipe — with the recipe's own servingG as its weight and no cooking method
 * of its own — instead of the six ingredients it stands for. [unpackRecipe] existed in core, was
 * vector-tested by the `unpack-recipe` suite, and had no caller anywhere in the UI. iOS did this
 * correctly, which is why the divergence went unnoticed: both clients passed the same vectors.
 *
 * Components go through [buildMealIngredient] rather than [asMealRow] because the recipe supplies each
 * one's method and weight, so there is nothing to fall back to — and a component it refuses is dropped,
 * matching iOS. That drop is silent, which is a shared question worth revisiting: a recipe rendering
 * five of its six ingredients is not obviously better than refusing the whole recipe. It is not
 * introduced here, and changing it belongs in both clients at once.
 */
internal fun rowsFor(suggestion: IngredientSuggestion): List<MealIngredient> {
    if (!suggestion.isRecipe) return listOf(suggestion.asMealRow())
    return unpackRecipe(suggestion).mapNotNull { component ->
        buildMealIngredient(component, UUID.randomUUID().toString())
            ?.lockedToRawIfIndustrial(component.suggestion.isProduct)
    }
}

/**
 * The search field, rendered from the design system's `input` role.
 *
 * WHAT IT REPLACES, AND WHY THAT WAS WRONG
 * A bare Material3 `TextField` with no colours, which therefore took its container from the theme's
 * `surfaceContainerHighest` — `SurfaceCream`. Measured from an emulator screenshot rather than reasoned
 * about: the field's cream fill is **1.12:1** against `SurfacePaper`. Material's filled field draws an
 * indicator on the bottom edge only, so three of the four sides had no boundary whatsoever and the
 * control was, in practice, invisible. WCAG 1.4.11 asks for 3:1 on a component's boundary.
 *
 * It was also visibly a different control from the iOS one — cream where iOS is white, square-topped
 * where iOS is rounded — for no reason anybody chose. Both clients now read the same role
 * (`PnRole.inputDefault*` / `inputFocus*`, `PnRadius.Small`), which existed in the generated tokens from
 * the first emit and had never been used by either.
 *
 * Focus is brand green rather than the carrot accent ring every other focusable element uses. That is
 * not an oversight to tidy up: `semantic-map.json` records the asymmetry deliberately and asks a port
 * to keep it.
 *
 * STILL SHORT OF 1.4.11, recorded rather than papered over: the unfocused border is `ink.line`, 14%
 * ink, which measures 1.23:1 on paper. 14% cannot reach 3:1 on any of these backgrounds, so closing
 * that gap needs a stronger line token in `pocket-nutrition-design` — a design-owner decision, not a
 * colour to invent in a client.
 */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(PnRadius.Small)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The ring sits outside the border, drawn as the outer of two borders on a padded box, so
            // it cannot tint the field's own fill and change the background the text sits on.
            .then(if (focused) Modifier.border(3.dp, PnRole.InputFocusRingColor, shape) else Modifier)
            .padding(if (focused) 3.dp else 0.dp)
            .background(PnRole.InputDefaultBackground, shape)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) PnRole.InputFocusBorderColor else PnRole.InputDefaultBorderColor,
                shape = shape,
            ),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = PnRole.InputDefaultForeground, fontSize = 15.sp),
            cursorBrush = SolidColor(PnRole.InputFocusBorderColor),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            decorationBox = { inner ->
                // The placeholder colour is named explicitly. The deck's `.input` rule specifies a fill,
                // a border, a radius and a text colour but NO placeholder colour, so each platform fell
                // through to its own default — Android happened to land on `onSurfaceVariant`, which the
                // theme maps to InkSoft and which measures a comfortable 6.12:1, while iOS landed on a
                // near-white 1.72:1. Naming it here removes the accident on both sides.
                if (value.isEmpty()) {
                    Text(Strings.active.SEARCH_PLACEHOLDER, color = PnColor.InkSoft, fontSize = 15.sp)
                }
                inner()
            },
        )
    }
}

@Composable
private fun SuggestionRow(
    suggestion: IngredientSuggestion,
    alreadyAdded: Boolean,
    /**
     * `null` in single-term mode, so the checkbox is **absent rather than unchecked**.
     *
     * The distinction matters twice: [SUGGESTION_ROW_HEIGHT] is what the list's height cap is a multiple
     * of, so anything changing the row height starts slicing the last row; and an always-present checkbox
     * would tell a single-term user there is a selection to make when there is not.
     */
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SUGGESTION_ROW_HEIGHT)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            Text(
                text = if (checked) "☑" else "☐",
                color = if (checked) PnColor.BrandLeafDeep else PnColor.InkSoft,
                fontSize = 20.sp,
                modifier = Modifier
                    .width(26.dp)
                    // `selected` is aria-pressed's equivalent: without it TalkBack reads a row and never
                    // says whether it is in the selection.
                    .semantics { selected = checked },
            )
        }
        SuggestionThumbnail(suggestion)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (suggestion.isRecipe) {
                    Strings.active.SEARCH_RECIPE_PREFIX.format(suggestion.label)
                } else {
                    suggestion.label
                },
                color = if (alreadyAdded) PnColor.InkSoft else PnColor.InkDefault,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitleFor(suggestion)?.let {
                Text(
                    text = it,
                    color = PnColor.InkSoft,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            suggestion.isRecipe -> Text(
                text = Strings.active.SEARCH_RECIPE_COMPONENT_COUNT
                    .format(suggestion.recipeComponents.orEmpty().size),
                color = PnColor.OnSoftPlum,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(PnColor.HuePlumSoft, RoundedCornerShape(PnRadius.Pill))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            suggestion.isProduct -> Text(
                text = Strings.active.SEARCH_PRODUCT_BADGE,
                color = PnColor.HueSea,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(PnColor.HueSeaSoft, RoundedCornerShape(PnRadius.Pill))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        TextButton(onClick = onClick, enabled = !alreadyAdded) {
            Text(
                text = if (alreadyAdded) Strings.active.SEARCH_ALREADY_ADDED else "+",
                color = PnColor.BrandLeafDeep,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * Fixed row height, named because the list caps itself on a row boundary.
 *
 * A cap that is not a multiple of this slices the last row's second line in half, and the list then
 * reads as broken rather than scrollable — which is what happened on iOS when the thumbnail and the
 * brand line made rows taller than the cap had been sized for.
 */
private val SUGGESTION_ROW_HEIGHT = 60.dp

/**
 * The always-filled 44dp slot at the head of a row.
 *
 * Open Food Facts supplies a photo for roughly four fifths of its products; the knowledge base has no
 * image field at all. Drawing only [IngredientSuggestion.imageUrl] would therefore give every packaged
 * product a photograph and every curated ingredient an empty square, making the industrial result look
 * more authoritative than the reference one — so the fallback is the food-group glyph, and a group with
 * no glyph (`sauce`, `seaweed`, `other`, all deliberate) gets a neutral mark. Every row draws something.
 *
 * The URL is used as served. Open Food Facts also publishes .100.jpg and .200.jpg variants at the same
 * path, but deriving one means pattern-matching their filename scheme, and a wrong guess is a broken
 * image rather than a smaller one. LazyColumn only composes visible rows, so the cost is bounded.
 */
@Composable
private fun SuggestionThumbnail(suggestion: IngredientSuggestion) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        // SurfaceCream, not SurfaceCard. The row sits on a white card, so a white slot was white
        // on white — 1.00:1, an invisible container that left the glyph floating while photo rows
        // showed a solid block. Cream is the design system's recessed surface.
        modifier = Modifier.size(44.dp).background(PnColor.SurfaceCream, shape).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        val url = suggestion.imageUrl
        if (suggestion.isRecipe) {
            Text("🍲", fontSize = 22.sp)
        } else {
            // The glyph is drawn first and the photo over it, rather than switched between. Coil's
            // AsyncImage takes Painters for its error and placeholder slots, not composables, so a
            // glyph fallback would otherwise mean SubcomposeAsyncImage — and subcomposition per row is
            // a real cost in a list for something this small. Layering gets the same three outcomes
            // free: while loading nothing covers the glyph, on failure nothing ever does, and on
            // success the photo hides it. OFF image URLs do 404 — products are deleted and images
            // replaced — so the failure path is not hypothetical.
            GroupGlyph(suggestion)
            if (url != null) {
                AsyncImage(
                    model = url,
                    // Decorative: the label beside it already names the food, so describing the image
                    // would make a screen reader announce every row twice.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun GroupGlyph(suggestion: IngredientSuggestion) {
    Text(text = foodGroupIcon(suggestion.foodGroup) ?: "🍽️", fontSize = 22.sp)
}

/**
 * Brand first because it is the discriminator, reference weight second. A recipe lists its components
 * instead, which is what tells you whether this is the raclette you meant.
 */
private fun subtitleFor(suggestion: IngredientSuggestion): String? {
    if (suggestion.isRecipe) {
        val names = suggestion.recipeComponents.orEmpty().map { it.ingredient.label }
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
    val grams = suggestion.servingG?.let { "${formatNumber(it, LanguageSettings.effective)} g" }
    return listOfNotNull(suggestion.brand, grams).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * One row of the meal: collapsed to a name, a one-line portion summary and the weight until tapped
 * open, then the cooking pill, a remove action and the full portion ladder. The method picker and
 * weighed-state toggle live in a bottom sheet opened from the pill, below.
 *
 * THE ACCORDION MATCHES THE SANDBOX (`docs/SPECIFICATION.md` §7.6-7.8), which this row used not to:
 * every ingredient used to show its full portion ladder at once, so composing a five-item meal meant
 * scrolling past five stepper rows to reach the sixth ingredient. Expanding is [MealBuilderScreen]'s
 * `expandedItemId`, not local state, because only one row may be open at a time, same as the sandbox's
 * `expandedId`.
 *
 * THE METHOD CHIPS ARE ALSO COLLAPSED, as they are in the sandbox. Seven always-visible chips cost a
 * horizontal scroller and most of the row's height to answer a question the API's `typicalState`
 * usually gets right. Collapsing them behind a pill that *states the current answer* trades one tap for
 * the room the weighed-state toggle needs, and that toggle matters far more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealItemRow(
    item: MealIngredient,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onChange: (MealIngredient) -> Unit,
    onRemove: () -> Unit,
) {
    // Closed by default: the pill's whole purpose is that the row stays short until asked otherwise.
    var pickingMethod by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Only reachable from the expanded row, same visibility rule as the sandbox's "Signaler" button
    // in IngredientCard: it belongs with the detail view, not the compact summary line.
    var reportingFeedback by remember { mutableStateOf(false) }
    val feedbackSheetState = rememberModalBottomSheetState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))
            .clickable(enabled = !isExpanded, onClick = onToggleExpand)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isExpanded) {
            // Name, the cooking pill, remove and a collapse chevron — docs/SPECIFICATION.md §7.8. The
            // remove action moved here from a text button beside the label: collapsed rows delete via
            // MealBuilderScreen's LazyColumn item, but an expanded row is no longer a single compact
            // line, so it needs its own explicit affordance instead of losing delete while open.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.label,
                    color = PnColor.InkDefault,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                MethodPill(item = item, expanded = pickingMethod, onClick = { pickingMethod = true })
                TextButton(onClick = { reportingFeedback = true }) {
                    Text(Strings.active.FEEDBACK_REPORT, color = PnColor.InkSoft, fontSize = 12.sp)
                }
                TextButton(onClick = onRemove) {
                    Text(Strings.active.MEAL_REMOVE, color = PnColor.InkSoft, fontSize = 12.sp)
                }
                TextButton(onClick = onToggleExpand) {
                    Text("▴", color = PnColor.InkSoft, fontSize = 14.sp)
                }
            }

            PortionLadder(row = item, onChange = onChange)
        } else {
            // "Riz blanc cuit" / "1 part" / "165g" / ⌄ — docs/SPECIFICATION.md §7.6. The caption is
            // summary(), the same corpus-backed output the expanded ladder used to show trailing its
            // stepper row; shown here instead of duplicated, since a collapsed row is exactly the "one
            // line a user reads without expanding anything" that function was written for.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        color = PnColor.InkDefault,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val caption = item.summary(LanguageSettings.effective).primary
                    if (caption.isNotEmpty()) {
                        Text(
                            text = caption,
                            color = PnColor.InkSoft,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${formatNumber(item.quantity, LanguageSettings.effective)} ${if (item.displayUnit == "ml") "mL" else "g"}",
                    color = PnColor.InkSoft,
                    fontSize = 12.sp,
                )
                Text("⌄", color = PnColor.InkSoft, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    // The method picker that replaces the sandbox's drag-between-lanes, and replaces this row's own
    // former inline chip-scroller: a sheet gives the seven options and the weighed-state toggle the full
    // screen width instead of squeezing both into whatever room the row had left, and — being modal —
    // it also means only one row's picker can ever be open at a time, so opening a search or another
    // row's picker cannot leave a stale one expanded behind it.
    //
    // Selecting an option runs applyMethodChange, so the weight follows the method: switch a grillé row
    // to cru and the grams move back to the raw equivalent. That is the corpus function, vector-verified,
    // rather than arithmetic repeated here. The sheet stays open after a tap — closing it is a separate,
    // deliberate action — because the weighed-state toggle below the list is often the next thing to
    // check, and dismissing on every method tap would cost a re-open for that.
    //
    // No generic "cuit (générique)" row: Knowledge now names a specific method for every ingredient, and
    // asMealRow's own fallback defaults to "grilled" rather than that retired bucket, so an item's
    // cookingMethod is always one of PnCooking.Methods here.
    if (pickingMethod) {
        ModalBottomSheet(
            onDismissRequest = { pickingMethod = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = Strings.active.MEAL_COOKING,
                    color = PnColor.InkDefault,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                PnCooking.Methods.forEach { method ->
                    MethodOptionRow(
                        emoji = method.emoji,
                        label = Strings.active.cookingMethod(method.id),
                        subtitle = yieldHint(item, method.id),
                        selected = method.id == item.cookingMethod,
                        onClick = { onChange(applyMethodChange(item, method.id)) },
                    )
                }

                // Whether the weight is a pre- or post-cooking measurement. The copy deck calls this
                // "the most consequential control in the UI": it is the only control in the product
                // that can be wrong by 15-40% while every screen still looks correct. Listed as two
                // rows rather than the two side-by-side pills this used to be, so it reads the same way
                // as the method list above it instead of switching shape halfway down the sheet. Hidden
                // for raw rows, where there is nothing to convert and the question has no answer.
                if (item.cookingMethod != "raw") {
                    Spacer(Modifier.height(8.dp))
                    Text(Strings.active.WEIGHED_LABEL, color = PnColor.InkSoft, fontSize = 12.sp)
                    MethodOptionRow(
                        emoji = "🌿",
                        label = Strings.active.MEASURED_BEFORE_LONG,
                        selected = item.measuredState == "raw",
                        onClick = { onChange(switchMeasuredState(item, "raw")) },
                    )
                    MethodOptionRow(
                        emoji = "🍽️",
                        label = Strings.active.MEASURED_AFTER_LONG,
                        selected = item.measuredState == "cooked",
                        onClick = { onChange(switchMeasuredState(item, "cooked")) },
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // The guided-correction form: pick a category, pick the specific error, fill the typed value it
    // needs, repeat, then send the pending list plus an optional free comment. Mirrors the sandbox's
    // FeedbackDialog accordion — see FEEDBACK_CATEGORIES for the taxonomy this walks.
    if (reportingFeedback) {
        ModalBottomSheet(
            onDismissRequest = { reportingFeedback = false },
            sheetState = feedbackSheetState,
        ) {
            FeedbackSheetContent(item = item, onClose = { reportingFeedback = false })
        }
    }
}

private enum class FeedbackStatus { IDLE, SENDING, DONE, ERROR }

/**
 * Body of the feedback bottom sheet: header, pending corrections list, the category accordion, a
 * comment field and the submit/close row. Kept as its own composable, rather than inlined in
 * [MealItemRow] like `methodSheet`'s content is, because this one owns a small state machine
 * (accordion + draft + pending corrections + send status) that would otherwise crowd the row.
 */
@Composable
private fun FeedbackSheetContent(item: MealIngredient, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()

    // Fills in the nutrition/identity hints feedbackCurrentValue cannot answer from `item` alone —
    // see that function's doc comment. A nice-to-have enrichment, not a gate: the sheet renders and
    // works immediately regardless of whether or when this resolves, and a failure (offline, unknown
    // id) just leaves those hints absent rather than surfacing an error the reporter did not ask about.
    var ingredientDetail by remember { mutableStateOf<IngredientDetail?>(null) }
    LaunchedEffect(Unit) {
        ingredientDetail = runCatching { PocketNutritionApi.ingredientDetail(item.ingredientId) }.getOrNull()
    }

    var expandedCategory by remember { mutableStateOf<String?>(null) }
    var draftCategory by remember { mutableStateOf<String?>(null) }
    var draftError by remember { mutableStateOf<FeedbackErrorDef?>(null) }
    var draftValue by remember { mutableStateOf("") }
    var draftUnit by remember { mutableStateOf("") }
    val corrections = remember { mutableStateListOf<FeedbackCorrection>() }
    var comment by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(FeedbackStatus.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun selectError(categoryCode: String, error: FeedbackErrorDef) {
        draftCategory = categoryCode
        draftError = error
        when (val input = error.input) {
            is FeedbackInput.Select -> {
                draftValue = input.options.firstOrNull()?.code ?: ""
                draftUnit = ""
            }
            is FeedbackInput.NumberUnit -> {
                draftValue = ""
                draftUnit = input.units.first()
            }
            else -> {
                draftValue = ""
                draftUnit = ""
            }
        }
    }

    fun addCorrection() {
        val error = draftError ?: return
        val categoryCode = draftCategory ?: return
        val value = draftValue.trim()
        if (value.isEmpty()) return
        corrections.add(
            FeedbackCorrection(
                category = categoryCode,
                code = error.code,
                value = value,
                unit = if (error.input is FeedbackInput.NumberUnit) draftUnit else null,
            ),
        )
        draftError = null
        draftCategory = null
        draftValue = ""
        if (status == FeedbackStatus.ERROR) status = FeedbackStatus.IDLE
    }

    val canSend = (corrections.isNotEmpty() || comment.trim().isNotEmpty()) &&
        status != FeedbackStatus.SENDING && status != FeedbackStatus.DONE

    fun submit() {
        if (!canSend) return
        status = FeedbackStatus.SENDING
        scope.launch {
            // Same reason as the analyse/search calls in the parent screen: cancellation is rethrown
            // rather than shown, since it fires only when the sheet is dismissed mid-request.
            try {
                PocketNutritionApi.submitFeedback(
                    FeedbackRequest(
                        name = item.label,
                        corrections = corrections.toList(),
                        comment = comment.trim().takeIf { it.isNotEmpty() },
                        cookingMethod = item.cookingMethod,
                        measuredState = item.measuredState,
                        // No `source` at this call site: that only exists on a prediction result,
                        // which this row does not hold. Omitted rather than guessed — see
                        // FeedbackRequest's own doc comment.
                    ),
                )
                status = FeedbackStatus.DONE
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                errorMessage = (failure as? ApiException)?.frenchMessage ?: failure.message
                status = FeedbackStatus.ERROR
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = item.label,
            color = PnColor.InkDefault,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (status == FeedbackStatus.DONE) {
            Text(Strings.active.FEEDBACK_SUCCESS, color = PnColor.InkDefault, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        } else {
            Text(Strings.active.FEEDBACK_INTRO, color = PnColor.InkSoft, fontSize = 12.sp)

            if (corrections.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    corrections.forEachIndexed { index, correction ->
                        val category = FEEDBACK_CATEGORIES.first { it.code == correction.category }
                        val error = category.errors.first { it.code == correction.code }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PnColor.StatusBadSoft, RoundedCornerShape(PnRadius.Small))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val valueText = correction.value?.let {
                                " : $it${correction.unit?.let { unit -> " $unit" } ?: ""}"
                            } ?: ""
                            Text(
                                text = "${category.fr} › ${error.fr}$valueText",
                                color = PnColor.StatusBad,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "×",
                                color = PnColor.StatusBad,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { corrections.removeAt(index) }
                                    .semantics { contentDescription = Strings.active.FEEDBACK_REMOVE_CORRECTION }
                                    .padding(4.dp),
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FEEDBACK_CATEGORIES.forEach { category ->
                    val open = expandedCategory == category.code
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PnColor.InkSoft.copy(alpha = 0.25f), RoundedCornerShape(PnRadius.Small)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedCategory = if (open) null else category.code
                                    draftError = null
                                    draftCategory = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(category.fr, color = PnColor.InkDefault, fontSize = 14.sp)
                            Text(if (open) "−" else "+", color = PnColor.InkSoft, fontSize = 14.sp)
                        }
                        if (open) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                category.errors.forEach { error ->
                                    val active = draftCategory == category.code && draftError?.code == error.code
                                    val current = feedbackCurrentValue(error.hint, item, ingredientDetail)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = error.fr + (current?.let { " · actuel : $it" } ?: ""),
                                            color = if (active) PnColor.BrandLeafDeep else PnColor.InkSoft,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectError(category.code, error) }
                                                .padding(vertical = 4.dp),
                                        )
                                        if (active) {
                                            FeedbackDraftInput(
                                                error = error,
                                                value = draftValue,
                                                unit = draftUnit,
                                                onValue = { draftValue = it },
                                                onUnit = { draftUnit = it },
                                            )
                                            TextButton(
                                                onClick = { addCorrection() },
                                                enabled = draftValue.isNotBlank(),
                                            ) {
                                                Text(Strings.active.FEEDBACK_ADD)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = {
                    if (it.length <= 2000) {
                        comment = it
                        if (status == FeedbackStatus.ERROR) status = FeedbackStatus.IDLE
                    }
                },
                label = { Text(Strings.active.FEEDBACK_COMMENT) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            if (status == FeedbackStatus.ERROR) {
                Text(
                    text = Strings.active.error(errorMessage ?: Strings.active.FEEDBACK_FAILURE),
                    color = PnColor.StatusBad,
                    fontSize = 12.sp,
                )
            }

            PrimaryButton(
                text = if (status == FeedbackStatus.SENDING) Strings.active.FEEDBACK_SUBMITTING else Strings.active.FEEDBACK_SUBMIT,
                enabled = canSend,
                onClick = { submit() },
            )
        }

        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.active.FEEDBACK_CLOSE, color = PnColor.InkSoft)
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** The typed value an error needs, switched on [FeedbackErrorDef.input]. Mirrors `DraftInput` in the sandbox. */
@Composable
private fun FeedbackDraftInput(
    error: FeedbackErrorDef,
    value: String,
    unit: String,
    onValue: (String) -> Unit,
    onUnit: (String) -> Unit,
) {
    when (val input = error.input) {
        is FeedbackInput.Select -> {
            var expanded by remember { mutableStateOf(false) }
            val label = input.options.firstOrNull { it.code == value }?.fr
                ?: input.options.firstOrNull()?.fr.orEmpty()
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PnColor.InkSoft.copy(alpha = 0.4f), RoundedCornerShape(PnRadius.Small))
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = PnColor.InkDefault, fontSize = 13.sp)
                    Text("▾", color = PnColor.InkSoft, fontSize = 13.sp)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    input.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.fr) },
                            onClick = {
                                onValue(option.code)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
        is FeedbackInput.NumberUnit -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValue,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .border(1.dp, PnColor.InkSoft.copy(alpha = 0.4f), RoundedCornerShape(PnRadius.Small))
                            .clickable { expanded = true }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(unit, color = PnColor.InkDefault, fontSize = 13.sp)
                        Text("▾", color = PnColor.InkSoft, fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        input.units.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = {
                                    onUnit(u)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        is FeedbackInput.Number -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValue,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                input.suffix?.let { Text(it, color = PnColor.InkSoft, fontSize = 12.sp) }
            }
        }
        is FeedbackInput.Text -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                placeholder = if (input.placeholder != null) {
                    { Text(input.placeholder) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Why the weight moves when this method is chosen — "Cet aliment pèse 2,4x plus lourd une fois cuit"
 * for rice, which gains water as it boils, or the "moins lourd" counterpart for a food that loses
 * moisture, like grilled meat. Answers the question the picker itself cannot: which of these six is
 * worth tapping.
 *
 * Goes through [resolveYieldFactor], the same corpus function [applyMethodChange] uses, rather than
 * reading `yieldFactors` directly — so this always describes the ratio that will actually apply,
 * generic-`cooked`-key fallback included. Null rather than "1x" when Knowledge has no data at all: a
 * fabricated "no change" reads as a measured fact, and the corpus does not invent yields.
 */
private fun yieldHint(item: MealIngredient, methodId: String): String? {
    val factor = resolveYieldFactor(item.yieldFactors, methodId)
    if (factor == 1.0) return null
    val ratio = if (factor > 1) factor else 1 / factor
    val lang = LanguageSettings.effective
    val display = formatNumber(kotlin.math.round(ratio * 10) / 10, lang)
    return Strings.active.yieldHint(display = display, heavier = factor > 1)
}

/**
 * One row of the sheet's method list: emoji, label, an optional yield subtitle, and a trailing check
 * mark on the current answer.
 */
@Composable
private fun MethodOptionRow(
    emoji: String,
    label: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = PnColor.InkDefault, fontSize = 15.sp)
            if (subtitle != null) {
                Text(text = subtitle, color = PnColor.InkSoft, fontSize = 11.sp)
            }
        }
        if (selected) {
            Text(text = "✓", color = PnColor.BrandLeafDeep, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * States the current cooking answer rather than being a bare chevron — "🔥 Grillé (pesé après cuisson)".
 * A disclosure arrow alone would hide two decisions that change the numbers, and the point of
 * collapsing is to save room, not to keep the user from knowing.
 *
 * Absent entirely for an Open Food Facts product: the backend always analyses it at its own values
 * regardless of cooking method, so there is no cooking-method state worth showing.
 */
@Composable
private fun MethodPill(item: MealIngredient, expanded: Boolean, onClick: () -> Unit) {
    if (item.isIndustrialProduct) return
    val method = PnCooking.Methods.firstOrNull { it.id == item.cookingMethod }
    val emoji = if (item.cookingMethod == "raw") "🌿" else (method?.emoji ?: "🍲")
    // The weighed state only means something once something has been cooked: a raw weight of a raw food
    // is the same number either way, so appending "(pesé avant cuisson)" there would be noise dressed as
    // information. The sandbox and iOS make the same call.
    val text = if (item.cookingMethod == "raw") {
        "$emoji ${Strings.active.cookingMethod(item.cookingMethod)}"
    } else {
        "$emoji ${Strings.active.cookingMethod(item.cookingMethod)} " +
            Strings.active.weighedInline(Strings.active.measuredWhen(item.measuredState))
    }

    Box(
        modifier = Modifier
            .background(method?.soft ?: PnColor.SurfaceCream, RoundedCornerShape(PnRadius.Pill))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${Strings.active.CHANGE_COOKING} — $text" }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = "$text ${if (expanded) "▴" else "▾"}",
            color = method?.onSoft ?: PnColor.InkSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

