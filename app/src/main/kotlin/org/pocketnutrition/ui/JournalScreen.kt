// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.ui.vault.VaultStrings
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.formatNumber
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.model.JournalStore
import org.pocketnutrition.model.SavedMeal
import org.pocketnutrition.model.TimelineItem
import org.pocketnutrition.model.journalTimeline
import org.pocketnutrition.model.MealShortcutsStore
import org.pocketnutrition.model.groupBySlot
import org.pocketnutrition.model.repeatMeal
import org.pocketnutrition.model.withToday
import org.pocketnutrition.model.yesterdaysMeal
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.mealSlotAt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import java.time.LocalDate
import org.pocketnutrition.recognition.Entry
import org.pocketnutrition.recognition.EntryState
import androidx.compose.material3.AlertDialog
import java.time.ZoneId

/**
 * Home screen: the meals kept on this device, newest day first.
 *
 * No counterpart in the sandbox — the prototype persists nothing. It exists because a food diary that
 * forgets is not a diary, and it is why the app needs its own privacy copy rather than the sandbox's
 * "aucune donnée personnelle n'est conservée".
 */
@Composable
fun JournalScreen(
    store: JournalStore,
    /** Saved dishes: a journal meal can be saved as one from its long-press menu. */
    shortcuts: MealShortcutsStore,
    onAddMeal: () -> Unit,
    /** "+ Breakfast" on today's empty main meal: a new meal already filed under that slot. */
    onAddMealInSlot: (MealSlot) -> Unit,
    onEditMeal: (SavedMeal) -> Unit,
    onShowAbout: () -> Unit,
    onShowSettings: () -> Unit,
    /** Dictations and meal photos not made into meals yet; shown in their day as meals in progress. */
    captures: List<Entry>,
    onOpenCapture: (Entry) -> Unit,
    onSendCapture: (Entry) -> Unit,
    onResendCapture: (Entry) -> Unit,
    onDeleteCapture: (Entry) -> Unit,
    /** Shown under each day header (daily-targets rings); null when the feature is off. */
    dayExtras: (@Composable (LocalDate) -> Unit)? = null,
    /** With a linked vault the "only on this device" sentence would be false, so its vault variant shows. */
    isVaultLinked: Boolean = false,
) {
    // Closed by default: About (licence/attribution/privacy) and Settings (language, server
    // override) are two distinct destinations, not one screen wearing two hats — see AboutScreen's
    // and SettingsScreen's own doc comments for why they were split.
    var menuExpanded by remember { mutableStateOf(false) }
    // A recording or photo cannot be brought back: deleting one is confirmed, as the voice screen did.
    var captureToDelete by remember { mutableStateOf<Entry?>(null) }
    // The meal being saved as a dish, from its long-press menu.
    var dishSource by remember { mutableStateOf<SavedMeal?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(PnColor.SurfacePaper)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Strings.active.JOURNAL_TITLE,
                color = PnColor.InkDefault,
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Text("⋯", color = PnColor.InkSoft, fontSize = 20.sp)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(Strings.active.ABOUT_TITLE) },
                        leadingIcon = { Text("ℹ️", fontSize = 16.sp) },
                        onClick = {
                            menuExpanded = false
                            onShowAbout()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(Strings.active.SETTINGS_TITLE) },
                        leadingIcon = { Text("⚙️", fontSize = 16.sp) },
                        onClick = {
                            menuExpanded = false
                            onShowSettings()
                        },
                    )
                }
            }
        }

        if (store.meals.isEmpty() && captures.isEmpty()) {
            dayExtras?.invoke(LocalDate.now(ZoneId.systemDefault()))
            EmptyJournal(onAddMeal = onAddMeal, isVaultLinked = isVaultLinked, modifier = Modifier.weight(1f))
        } else {
            MealList(
                store = store,
                captures = captures,
                onEditMeal = onEditMeal,
                mealActions = MealActions(
                    addInSlot = onAddMealInSlot,
                    repeat = { meal, slot -> repeatMeal(store, meal, slot) },
                    saveAsDish = { dishSource = it },
                ),
                captureActions = CaptureActions(onOpenCapture, onSendCapture, onResendCapture) { captureToDelete = it },
                dayExtras = dayExtras,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(text = Strings.active.JOURNAL_ADD, onClick = onAddMeal)
        }
    }

    dishSource?.let { meal ->
        SaveDishDialog(
            items = meal.items,
            onSave = { name, servings -> shortcuts.saveDish(name, servings, meal.items) },
            onDismiss = { dishSource = null },
        )
    }

    captureToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { captureToDelete = null },
            title = { Text(Strings.active.VOICE_DELETE_TITLE, color = PnColor.InkDefault) },
            text = {
                Text(
                    Strings.active.voiceDeleteBody(hasAudio = target.audioFile != null, ready = target.state == EntryState.READY),
                    color = PnColor.InkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    captureToDelete = null
                    onDeleteCapture(target)
                }) { Text(Strings.active.VOICE_DELETE_CONFIRM, color = PnColor.StatusBad) }
            },
            dismissButton = {
                TextButton(onClick = { captureToDelete = null }) {
                    Text(Strings.active.VOICE_CANCEL, color = PnColor.InkSoft)
                }
            },
            containerColor = PnColor.SurfaceCard,
        )
    }
}

@Composable
private fun EmptyJournal(onAddMeal: () -> Unit, isVaultLinked: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🥗", fontSize = 48.sp)
        Text(
            text = Strings.active.JOURNAL_EMPTY,
            color = PnColor.InkSoft,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = Strings.active.JOURNAL_ADD, onClick = onAddMeal)
        // Shown on the empty state rather than buried in a settings screen: the one moment the user is
        // deciding whether to trust the app with a diary is before they start one.
        Text(
            text = if (isVaultLinked) VaultStrings.active.journalPrivacyLinked else Strings.active.JOURNAL_PRIVACY,
            color = PnColor.InkSoft,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private class CaptureActions(
    val open: (Entry) -> Unit,
    val sendNow: (Entry) -> Unit,
    val resend: (Entry) -> Unit,
    val delete: (Entry) -> Unit,
)

private class MealActions(
    val addInSlot: (MealSlot) -> Unit,
    val repeat: (SavedMeal, MealSlot) -> Unit,
    val saveAsDish: (SavedMeal) -> Unit,
)

@Composable
private fun MealList(
    store: JournalStore,
    captures: List<Entry>,
    onEditMeal: (SavedMeal) -> Unit,
    mealActions: MealActions,
    captureActions: CaptureActions,
    dayExtras: (@Composable (LocalDate) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Grouped by calendar day for the section headers. Done here rather than in the store because it
    // is a presentation concern and depends on the device's current time zone.
    val formatter = dayFormatter(LanguageSettings.effective)
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val meals = store.meals
    val days = withToday(journalTimeline(meals, { it.savedAtEpochMs }, captures, zone), today)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { group ->
            val day = formatter.format(Date.from(group.day.atStartOfDay(zone).toInstant()))
            val isToday = group.day == today
            item(key = "header-${group.day}") { DayHeader(day, dayKcal(group)) }
            if (dayExtras != null) item(key = "targets-${group.day}") { dayExtras(group.day) }
            groupBySlot(group, { it.effectiveSlot(zone) }, { it.savedAtEpochMs }, isToday, zone).forEach { slotGroup ->
                item(key = "slot-${group.day}-${slotGroup.slot.id}") { SlotHeader(slotGroup.slot) }
                if (slotGroup.items.isEmpty()) {
                    item(key = "empty-${group.day}-${slotGroup.slot.id}") {
                        EmptySlotRow(
                            slot = slotGroup.slot,
                            yesterday = yesterdaysMeal(meals, slotGroup.slot, today, zone),
                            onAdd = { mealActions.addInSlot(slotGroup.slot) },
                            onRepeat = { mealActions.repeat(it, slotGroup.slot) },
                        )
                    }
                }
                items(slotGroup.items, key = { item ->
                    when (item) {
                        is TimelineItem.Meal -> item.meal.id
                        is TimelineItem.Capture -> "capture-" + item.entry.localId
                    }
                }) { item ->
                    when (item) {
                        is TimelineItem.Meal -> MealCard(
                            meal = item.meal,
                            onClick = { onEditMeal(item.meal) },
                            onDelete = { store.delete(item.meal) },
                            onLogAgain = { mealActions.repeat(item.meal, mealSlotAt(System.currentTimeMillis(), zone)) },
                            onSaveAsDish = { mealActions.saveAsDish(item.meal) },
                        )
                        is TimelineItem.Capture -> JournalCaptureRow(
                            entry = item.entry,
                            onOpen = { captureActions.open(item.entry) },
                            onSendNow = { captureActions.sendNow(item.entry) },
                            onResend = { captureActions.resend(item.entry) },
                            onDelete = { captureActions.delete(item.entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MealCard(
    meal: SavedMeal,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLogAgain: () -> Unit,
    onSaveAsDish: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(Strings.active.LOG_AGAIN_NOW) },
                leadingIcon = { Text("↺", fontSize = 16.sp) },
                onClick = {
                    menuOpen = false
                    onLogAgain()
                },
            )
            DropdownMenuItem(
                text = { Text(Strings.active.SAVE_AS_DISH) },
                enabled = meal.items.any { !it.isQuickAdd },
                onClick = {
                    menuOpen = false
                    onSaveAsDish()
                },
            )
        }
        MealCardBody(meal, onClick = onClick, onLongClick = { menuOpen = true }, onDelete = onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealCardBody(meal: SavedMeal, onClick: () -> Unit, onLongClick: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = meal.items.joinToString(" · ") { it.journalLabel() },
                color = PnColor.InkDefault,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDelete) {
                Text(Strings.active.JOURNAL_DELETE, color = PnColor.InkSoft, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // roundHalfUp rather than toInt(): the corpus rule, so the phone and the web never
            // disagree about which side of a .5 a number lands on.
            Text(
                text = "${roundHalfUp(meal.totals.energyKcal)} kcal",
                color = PnColor.BrandLeafDeep,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Macro(Strings.active.NUTRIENT_PROTEIN_ABBR, meal.totals.proteinG)
            Macro(Strings.active.NUTRIENT_CARBS_ABBR, meal.totals.carbohydratesG)
            Macro(Strings.active.NUTRIENT_FAT_ABBR, meal.totals.fatG)
        }
    }
}

@Composable
private fun Macro(abbreviation: String, grams: Double) {
    Text(
        text = "$abbreviation ${formatNumber(Math.round(grams * 10) / 10.0, LanguageSettings.effective)} g",
        color = PnColor.InkSoft,
        fontSize = 12.sp,
    )
}

/**
 * A date formatter *is* the right tool here, unlike for numbers. The corpus pins number rendering
 * because a separator changes the value a user reads; a date header has no such contract, and the
 * platform's own formatting for the active language is what a user expects.
 *
 * A function, not a top-level `val`, because the active language can change at runtime — a cached
 * formatter built once at first access would keep rendering in whichever language was active then.
 */
private fun dayFormatter(lang: String): SimpleDateFormat =
    SimpleDateFormat("EEEE d MMMM", if (lang == "en") Locale.US else Locale.FRANCE)

/** The one filled button in the app, from the design tokens rather than Material defaults. */
@Composable
/**
 * The filled primary action, including its disabled appearance.
 *
 * The disabled pairing was a real legibility bug: an [PnColor.InkOnInverse] label (#FAF7F0, near-white)
 * on an [PnColor.InkLine] fill, which is a 14%-opacity dark over paper and composites to #DBDAD3. That
 * is **1.31:1** — barely distinguishable from the background, and it sat on the screen's main call to
 * action, whose label is "Analyser le repas (0 aliment)". The count is the very thing telling you why
 * the button cannot be pressed, so it is the last text that should be unreadable.
 *
 * Disabled is now a muted fill with dark text — cream + inkSoft, **6.12:1**. It still reads as inert,
 * because the fill drops from deep green to pale cream and the label stops being white, but it stays
 * readable. iOS carries the identical pairing in PrimaryButtonStyle.
 *
 * The design deck defines no disabled state for `.btn-primary`, so this is a local decision that should
 * go back into pocket-nutrition-design before a third client needs it.
 */
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(PnRadius.Default)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (enabled) PnColor.BrandLeafDeep else PnColor.SurfaceCream, shape)
            // A border on the disabled state only. Enabled, the deep-green fill is 6.85:1 against paper
            // and the shape is unmistakable. Disabled, the cream fill measures 1.12:1 against the paper
            // behind it — so while the label is a comfortable 6.12:1, the button had no perceivable edge
            // and read as a stray line of text rather than a control you cannot press yet. iOS carries
            // the same treatment in PrimaryButtonStyle.
            .then(if (enabled) Modifier else Modifier.border(1.dp, PnColor.InkLine, shape))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, enabled = enabled) {
            Text(
                text = text,
                color = if (enabled) PnColor.InkOnInverse else PnColor.InkSoft,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
