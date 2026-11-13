// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.pocketnutrition.api.ApiSettings
import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.core.MealIngredient
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.defaultTimeFor
import org.pocketnutrition.core.mealSlotAt
import org.pocketnutrition.core.sumNutrients
import org.pocketnutrition.model.JournalStore
import org.pocketnutrition.model.MealShortcutsStore
import org.pocketnutrition.model.MealItem
import org.pocketnutrition.model.SavedMeal
import org.pocketnutrition.model.asMealIngredient
import org.pocketnutrition.ui.AboutScreen
import org.pocketnutrition.ui.JournalScreen
import org.pocketnutrition.ui.MealBuilderScreen
import org.pocketnutrition.ui.PocketNutritionTheme
import org.pocketnutrition.ui.ResultsScreen
import org.pocketnutrition.recognition.EntryState
import org.pocketnutrition.recognition.EntryStore
import org.pocketnutrition.recognition.RecognitionApi
import org.pocketnutrition.recognition.RecognitionSettings
import org.pocketnutrition.recognition.refreshOnce
import org.pocketnutrition.recognition.settleReadyTicket
import org.pocketnutrition.recognition.CaptureCoordinator
import org.pocketnutrition.recognition.defaultSelection
import org.pocketnutrition.recognition.deleteCapture
import org.pocketnutrition.recognition.driveStoredCapture
import org.pocketnutrition.recognition.resendCapture
import org.pocketnutrition.recognition.resolveImport
import org.pocketnutrition.api.PocketNutritionApi
import org.pocketnutrition.ui.findActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import org.pocketnutrition.ui.SettingsScreen
import org.pocketnutrition.ui.VoiceReviewScreen
import java.time.LocalDate
import org.pocketnutrition.api.ReferenceIntakesCache
import org.pocketnutrition.model.DailyTargetsController
import org.pocketnutrition.model.DailyTargetsFlag
import org.pocketnutrition.model.TargetsStore
import org.pocketnutrition.ui.targets.DayDetailScreen
import org.pocketnutrition.ui.targets.DayTargetsHeader
import org.pocketnutrition.ui.targets.ExplanationScreen
import org.pocketnutrition.ui.targets.QuestionnaireScreen
import org.pocketnutrition.ui.targets.TargetsEditorScreen
import org.pocketnutrition.ui.targets.TargetsHubScreen
import org.pocketnutrition.ui.targets.TrendsScreen
import org.pocketnutrition.ui.targets.WeekScreen
import org.pocketnutrition.ui.targets.WeightScreen
import org.pocketnutrition.ui.vault.VaultScreen
import org.pocketnutrition.vault.JournalSyncLocal
import org.pocketnutrition.vault.MealRecordCodec
import org.pocketnutrition.vault.TargetsRecordCodec
import org.pocketnutrition.vault.TargetsSyncLocal
import org.pocketnutrition.vault.VaultAccountModel
import org.pocketnutrition.vault.VaultClient
import org.pocketnutrition.vault.VaultCrypto
import org.pocketnutrition.vault.VaultSettings
import org.pocketnutrition.vault.VaultSyncCoordinator
import org.pocketnutrition.vault.VaultSyncStateFile

/**
 * Hosts the whole app.
 *
 * Four screens, so navigation is a sealed interface and a remembered value rather than a navigation
 * library: adding one would mean a dependency, a route DSL and a back-stack model for destinations that
 * never take an argument beyond what is already held here.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run before anything calls PocketNutritionApi — see ApiSettings' own doc comment for
        // why this is an explicit call here rather than an Application subclass.
        ApiSettings.init(applicationContext)
        LanguageSettings.init(applicationContext)
        RecognitionSettings.init(applicationContext)
        DailyTargetsFlag.init(applicationContext)
        VaultSettings.init(applicationContext)
        // Constructed once here rather than per-composable: the store reads its file in init, so this
        // keeps that to a single read at launch.
        val store = JournalStore(File(filesDir, "journal.json"))
        // Entries and recordings of voice captures; both are excluded from backup. Retention (7 days)
        // and the orphan-audio sweep run here, once, at launch.
        val voiceStore = EntryStore(File(filesDir, "recognition_entries.json"), File(filesDir, "recognition_audio"))
        // Saved dishes and pins: local only, excluded from backup like the journal.
        val shortcuts = MealShortcutsStore(File(filesDir, "meal_shortcuts.json"))
        // Daily targets: the profile, overrides and weights (health data, excluded from backup) and the
        // cached EFSA table from GET /reference-intakes (never bundled).
        val targetsStore = TargetsStore(File(filesDir, "daily_targets.json"))
        val targets = DailyTargetsController(
            journal = store,
            store = targetsStore,
            reference = ReferenceIntakesCache(File(filesDir, "reference_intakes.json")),
        )
        // Vault sync bookkeeping (record ids and revisions, no content), one file per kind of record.
        val syncFiles = VaultSyncFiles(
            journal = VaultSyncStateFile(File(filesDir, "journal_vault_sync.json")),
            targets = VaultSyncStateFile(File(filesDir, "targets_vault_sync.json")),
        )
        setContent { PocketNutritionTheme { PocketNutritionApp(store, voiceStore, shortcuts, targets, targetsStore, syncFiles) } }
    }
}

@Serializable
private sealed interface Screen {
    @Serializable data object Journal : Screen
    /** `editingMeal` set means an existing entry is being edited, rather than a new one composed. */
    @Serializable data class Building(
        val editingMeal: SavedMeal? = null,
        /** Rows handed over by a voice capture; kept in spoken order. */
        val importedRows: List<MealIngredient> = emptyList(),
        /** The voice entry the rows came from; removed only once the meal is actually saved. */
        val voiceEntryId: String? = null,
        /** When the meal was eaten (the capture's time, the edited meal's, a slot's default); null = now. */
        val atEpochMs: Long? = null,
        /** Its part of the day; null = guessed from the time. */
        val slot: MealSlot? = null,
    ) : Screen
    @Serializable data class Results(
        val profiles: List<NutritionalProfile>,
        val items: List<MealItem>,
        val editingMealId: String?,
        val voiceEntryId: String? = null,
        val atEpochMs: Long? = null,
        val slot: MealSlot? = null,
    ) : Screen
    @Serializable data object About : Screen
    @Serializable data object Settings : Screen
    @Serializable data object Vault : Screen
    @Serializable data class VoiceReview(val localId: String) : Screen

    // Daily targets. `day` is an ISO local date.
    @Serializable data object TargetsHub : Screen
    @Serializable data class Questionnaire(val fromJournal: Boolean) : Screen
    @Serializable data object TargetsEditor : Screen
    @Serializable data class Explanation(val day: String) : Screen
    @Serializable data class Weight(val fromJournal: Boolean) : Screen
    @Serializable data class DayDetail(val day: String) : Screen
    @Serializable data class Week(val day: String) : Screen
    @Serializable data class Trends(val key: String, val day: String) : Screen
}

/** Where system back goes: the journal, except inside the daily-targets screens, which nest. */
private fun Screen.parent(): Screen = when (this) {
    Screen.TargetsHub -> Screen.Settings
    Screen.Vault -> Screen.Settings
    is Screen.Questionnaire -> if (fromJournal) Screen.Journal else Screen.TargetsHub
    Screen.TargetsEditor -> Screen.TargetsHub
    is Screen.Explanation -> Screen.DayDetail(day)
    is Screen.Weight -> if (fromJournal) Screen.Journal else Screen.TargetsHub
    is Screen.Week -> Screen.DayDetail(day)
    is Screen.Trends -> Screen.DayDetail(day)
    else -> Screen.Journal
}

private val screenJson = Json { ignoreUnknownKeys = true }

private val ScreenSaver = Saver<Screen, String>(
    save = { screenJson.encodeToString(Screen.serializer(), it) },
    restore = { runCatching { screenJson.decodeFromString(Screen.serializer(), it) }.getOrNull() },
)

private class VaultSyncFiles(val journal: VaultSyncStateFile, val targets: VaultSyncStateFile)

@Composable
private fun PocketNutritionApp(
    store: JournalStore,
    voiceStore: EntryStore,
    shortcuts: MealShortcutsStore,
    targets: DailyTargetsController,
    targetsStore: TargetsStore,
    syncFiles: VaultSyncFiles,
) {
    // Saved across recreation (a system language change recreates the activity, and `Locale` is not
    // observable state, so recreating is what makes the new language apply). The meal rows handed over
    // by a voice capture are part of this state, so they survive too.
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Journal) }

    // The cloud vault: the account, and one sync per kind of record. With no linked account the syncs do
    // nothing. Daily-targets records sync only while that feature's preview switch is on, as on iOS.
    val vault = remember { VaultAccountModel(VaultSettings.deviceLabel, ::VaultClient, { VaultCrypto() }, VaultSettings.sessions, VaultSettings::rememberServerUrl) }
    val journalSync = remember {
        VaultSyncCoordinator(MealRecordCodec, JournalSyncLocal(store), syncFiles.journal, VaultSettings.sessions::load, ::VaultClient, { VaultCrypto() }, scope)
            .also { sync -> store.onChange = sync::noteChange }
    }
    val targetsSync = remember {
        VaultSyncCoordinator(TargetsRecordCodec, TargetsSyncLocal(targetsStore), syncFiles.targets, VaultSettings.sessions::load, ::VaultClient, { VaultCrypto() }, scope)
            .also { sync -> targetsStore.onChange = { if (DailyTargetsFlag.enabled) sync.noteChange(it) } }
    }
    val syncAll = {
        journalSync.syncSoon(0)
        if (DailyTargetsFlag.enabled) targetsSync.syncSoon(0)
    }

    // System back returns to the journal from anywhere else, rather than closing the app mid-compose.
    BackHandler(enabled = screen != Screen.Journal) {
        screen = screen.parent()
    }

    // The reference table, refreshed when older than 24 h and only while the feature is on: with the flag
    // off nothing of it is fetched. The request carries no parameter.
    LaunchedEffect(DailyTargetsFlag.enabled, screen == Screen.Journal) {
        if (DailyTargetsFlag.enabled) targets.reference.refreshIfStale()
    }

    // Captures (dictations, meal photos) move on whichever screen is showing, while the app is in the
    // foreground: sent, then their tickets followed. Background and the loops end; foreground resumes them.
    val captures = remember {
        CaptureCoordinator(
            scope = scope,
            entries = { voiceStore.entries },
            drive = { id, onOffline -> driveStoredCapture(voiceStore, id, onOffline) },
        )
    }
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findActivity() as? LifecycleOwner }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    captures.start()
                    syncAll()
                }
                Lifecycle.Event.ON_STOP -> captures.stop()
                else -> Unit
            }
        }
        // A late observer is replayed the events up to the current state, so ON_START arrives here too.
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            captures.stop()
        }
    }
    // A ready capture tapped in the journal is being looked up: a second tap does nothing meanwhile.
    var openingCapture by remember { mutableStateOf<String?>(null) }

    // Once each time the Journal appears: one plain read of every processing voice capture, so the
    // menu badge is current without any background work. Local state decides the badge; this only
    // refreshes it. Skipped when the recognition server is not configured.
    LaunchedEffect(screen == Screen.Journal) {
        if (screen != Screen.Journal) return@LaunchedEffect
        // Off the main thread: reading the config may decrypt the key through the Keystore.
        val config = withContext(Dispatchers.IO) { RecognitionSettings.config } ?: return@LaunchedEffect
        voiceStore.entries.forEach { entry ->
            var current = entry
            val refreshed = refreshOnce(
                entry = entry,
                fetch = { id, wait, known -> RecognitionApi.fetch(config, id, wait, known) },
            )
            if (refreshed != null) {
                // Persist first; only then touch the server ticket.
                if (!voiceStore.upsert(refreshed)) return@forEach
                current = refreshed
            }
            // A ready result whose server ticket was never deleted (loop cancelled, DELETE failed).
            settleReadyTicket(current) { id ->
                withContext(NonCancellable) { RecognitionApi.delete(config, id) }
            }?.let { voiceStore.upsert(it) }
        }
    }

    when (val current = screen) {
        Screen.Journal -> JournalScreen(
            store = store,
            shortcuts = shortcuts,
            onAddMeal = { screen = Screen.Building() },
            // "+ Breakfast" at 13:00: today, at breakfast time; the current slot: now.
            onAddMealInSlot = { slot ->
                val now = System.currentTimeMillis()
                val at = if (mealSlotAt(now) == slot) now else defaultTimeFor(slot, now)
                screen = Screen.Building(atEpochMs = at, slot = slot)
            },
            onEditMeal = { meal ->
                screen = Screen.Building(editingMeal = meal, atEpochMs = meal.savedAtEpochMs, slot = meal.effectiveSlot())
            },
            onShowAbout = { screen = Screen.About },
            onShowSettings = { screen = Screen.Settings },
            captures = voiceStore.entries,
            // Straight to the meal builder with the best matches, editable; the review only when the
            // result needs a look (nothing usable, an item that could not be resolved, a failed lookup).
            onOpenCapture = { entry ->
                if (openingCapture == null && entry.state == EntryState.READY) {
                    openingCapture = entry.localId
                    scope.launch {
                        val recognized = entry.result?.items.orEmpty()
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
                            null
                        } finally {
                            openingCapture = null
                        }
                        screen = if (plan != null && plan.skipped.isEmpty() && plan.rows.isNotEmpty()) {
                            Screen.Building(
                                importedRows = plan.rows,
                                voiceEntryId = entry.localId,
                                atEpochMs = entry.createdAtEpochMs,
                            )
                        } else {
                            Screen.VoiceReview(entry.localId)
                        }
                    }
                }
            },
            onSendCapture = { captures.sendNow(it.localId) },
            onResendCapture = { resendCapture(voiceStore, captures, it) },
            onDeleteCapture = { deleteCapture(voiceStore, captures, it.localId, scope) },
            isVaultLinked = vault.phase is VaultAccountModel.Phase.SignedIn,
            dayExtras = if (!DailyTargetsFlag.enabled) null else { day ->
                DayTargetsHeader(
                    controller = targets,
                    day = day,
                    onOpenDetail = { screen = Screen.DayDetail(it.toString()) },
                    onSetTargets = { screen = Screen.Questionnaire(fromJournal = true) },
                    onLogWeight = { screen = Screen.Weight(fromJournal = true) },
                )
            },
        )

        is Screen.Building -> MealBuilderScreen(
            initialItems = current.editingMeal?.items?.map { it.asMealIngredient() } ?: current.importedRows,
            sortInitialItems = current.editingMeal != null,
            captureStore = voiceStore,
            captureCoordinator = captures,
            initialAtEpochMs = current.atEpochMs ?: System.currentTimeMillis(),
            initialSlot = current.slot ?: mealSlotAt(current.atEpochMs ?: System.currentTimeMillis()),
            shortcuts = shortcuts,
            pastMeals = { store.meals },
            onCancel = { screen = Screen.Journal },
            onAnalysed = { profiles, items, meal ->
                screen = Screen.Results(
                    profiles, items, editingMealId = current.editingMeal?.id, voiceEntryId = current.voiceEntryId,
                    atEpochMs = meal.atEpochMs, slot = meal.slot,
                )
            },
        )

        is Screen.Results -> ResultsScreen(
            profiles = current.profiles,
            items = current.items,
            onSave = { totals ->
                val editingMealId = current.editingMealId
                // Every nutrient the engine returned, summed; unknown stays absent rather than 0.
                val nutrients = current.profiles.map { it.nutrients() }.fold(emptyMap(), ::sumNutrients)
                if (editingMealId != null) {
                    store.update(editingMealId, current.items, totals, current.atEpochMs, current.slot, nutrients)
                } else {
                    // A meal made from a capture is dated when it was dictated or photographed (the
                    // builder starts from that time), so it takes the place the capture had in the journal.
                    val capturedAt = current.voiceEntryId?.let { voiceStore.find(it)?.createdAtEpochMs }
                    val at = current.atEpochMs ?: capturedAt ?: System.currentTimeMillis()
                    store.save(current.items, totals, atEpochMs = at, slot = current.slot, nutrients = nutrients)
                }
                // The voice capture is consumed only now that the meal exists. Its server ticket is
                // normally gone already; if that DELETE is still pending, send it now, but only once
                // the local removal has been persisted.
                current.voiceEntryId?.let { id ->
                    val consumed = voiceStore.find(id)
                    val staleTicket = consumed?.takeIf { it.state == EntryState.READY && it.ticketDeletePending }?.ticketId
                    if (voiceStore.remove(id) && staleTicket != null) {
                        scope.launch {
                            val config = withContext(Dispatchers.IO) { RecognitionSettings.config } ?: return@launch
                            withContext(NonCancellable) { runCatching { RecognitionApi.delete(config, staleTicket) } }
                        }
                    }
                }
                screen = Screen.Journal
            },
            onEdit = {
                screen = if (current.voiceEntryId != null) {
                    Screen.Building(
                        importedRows = current.items.map { it.asMealIngredient() },
                        voiceEntryId = current.voiceEntryId,
                        atEpochMs = current.atEpochMs,
                        slot = current.slot,
                    )
                } else {
                    Screen.Building(atEpochMs = current.atEpochMs, slot = current.slot)
                }
            },
        )

        Screen.About -> AboutScreen(onClose = { screen = Screen.Journal })

        Screen.Settings -> SettingsScreen(
            onClose = { screen = Screen.Journal },
            onOpenDailyTargets = { screen = Screen.TargetsHub },
            onOpenVault = { screen = Screen.Vault },
        )

        Screen.Vault -> VaultScreen(
            model = vault,
            syncStatus = journalSync.status,
            unsupportedCount = journalSync.unsupportedCount + targetsSync.unsupportedCount,
            mealCount = store.meals.size,
            onSyncNow = syncAll,
            onLinked = syncAll,
            onUnlinked = {
                journalSync.unlinked()
                targetsSync.unlinked()
            },
            onClose = { screen = current.parent() },
        )

        Screen.TargetsHub -> TargetsHubScreen(
            controller = targets,
            onBack = { screen = current.parent() },
            onQuestionnaire = { screen = Screen.Questionnaire(fromJournal = false) },
            onTargets = { screen = Screen.TargetsEditor },
            onExplanation = { screen = Screen.Explanation(targets.today().toString()) },
            onWeight = { screen = Screen.Weight(fromJournal = false) },
        )

        is Screen.Questionnaire -> QuestionnaireScreen(
            controller = targets,
            onDone = { screen = current.parent() },
            onSkip = { screen = Screen.TargetsEditor },
        )

        Screen.TargetsEditor -> TargetsEditorScreen(targets, onBack = { screen = current.parent() })

        is Screen.Explanation -> ExplanationScreen(targets, LocalDate.parse(current.day), onBack = { screen = current.parent() })

        is Screen.Weight -> WeightScreen(targets, onBack = { screen = current.parent() })

        is Screen.DayDetail -> DayDetailScreen(
            controller = targets,
            day = LocalDate.parse(current.day),
            onBack = { screen = Screen.Journal },
            onOpenWeek = { screen = Screen.Week(current.day) },
            onOpenTrends = { key -> screen = Screen.Trends(key, current.day) },
            onOpenExplanation = { screen = Screen.Explanation(current.day) },
        )

        is Screen.Week -> WeekScreen(
            controller = targets,
            endDay = LocalDate.parse(current.day),
            onBack = { screen = current.parent() },
            onOpenTrends = { key -> screen = Screen.Trends(key, current.day) },
        )

        is Screen.Trends -> TrendsScreen(targets, current.key, LocalDate.parse(current.day), onBack = { screen = current.parent() })

        is Screen.VoiceReview -> {
            val entry = voiceStore.find(current.localId)
            if (entry == null) {
                // Removed under us (purged or handed off): go back rather than render nothing.
                LaunchedEffect(Unit) { screen = Screen.Journal }
            } else {
                VoiceReviewScreen(
                    entry = entry,
                    onBack = { screen = Screen.Journal },
                    // The entry stays until the meal is saved (see Screen.Results), so cancelling the
                    // builder or being killed loses nothing. Its server ticket was already deleted when
                    // the result arrived.
                    onHandOff = { rows ->
                        screen = Screen.Building(
                            importedRows = rows,
                            voiceEntryId = entry.localId,
                            atEpochMs = entry.createdAtEpochMs,
                        )
                    },
                )
            }
        }
    }
}
