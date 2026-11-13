// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.model.Dish
import org.pocketnutrition.model.MealItem
import org.pocketnutrition.model.MealShortcutsStore
import org.pocketnutrition.model.RecentFood
import org.pocketnutrition.model.suggestedDishName

// The meal-logging shortcuts of the meal builder and the journal: the slot chip, the empty meal's dishes
// and recents, the typed shortcuts, a dish's group header, a quick-add row and the save-as-dish dialog.
// Kept out of MealBuilderScreen so the builder only wires them. Mirrors iOS `MealShortcutsViews.swift`.

private const val MAX_SERVINGS = 12
private const val ONE_HOUR_MS = 3_600_000L

/** A dashed outline on the page colour: something not filled in yet (a capture, an empty meal). */
fun Modifier.dashedOutline(): Modifier = this
    .background(PnColor.SurfacePaper, RoundedCornerShape(PnRadius.Default))
    .drawBehind {
        val radius = PnRadius.Default.toPx()
        drawRoundRect(
            color = PnColor.InkSoft.copy(alpha = 0.5f),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
        )
    }

/** "12:40" today, "Mon 2 Nov 12:40" on another day, in the active language. */
fun mealTimeLabel(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val locale = if (LanguageSettings.effective == "en") Locale.US else Locale.FRANCE
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val formatter = if (time.toLocalDate() == LocalDate.now(zone)) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    } else {
        DateTimeFormatter.ofPattern("EEE d MMM HH:mm", locale)
    }
    return formatter.format(time)
}

/**
 * The chip under the builder's title: "Déjeuner · 12:40 ▾". A menu of the four slots (picking another
 * one moves the time into its band, same day — the caller decides) and "Change time…", a date then time
 * picker after which the slot follows the time.
 */
@Composable
fun SlotChip(slot: MealSlot, atEpochMs: Long, onPickSlot: (MealSlot) -> Unit, onPickTime: (Long) -> Unit) {
    val strings = Strings.active
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val label = strings.mealSlotName(slot) + " · " + mealTimeLabel(atEpochMs)
    Box {
        Text(
            text = "$label ▾",
            color = PnColor.BrandLeafDeep,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(vertical = 2.dp)
                .semantics { contentDescription = strings.mealSlotName(slot) + ", " + mealTimeLabel(atEpochMs) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MealSlot.inDayOrder.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text((if (candidate == slot) "✓ " else "") + strings.mealSlotName(candidate)) },
                    onClick = {
                        expanded = false
                        onPickSlot(candidate)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(strings.MEAL_CHANGE_TIME) },
                onClick = {
                    expanded = false
                    pickDateTime(context, atEpochMs, onPickTime)
                },
            )
        }
    }
}

/** The platform's date picker then time picker, capped at an hour from now like iOS. */
private fun pickDateTime(context: Context, fromEpochMs: Long, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(fromEpochMs).atZone(zone).toLocalDateTime()
    val dateDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked = LocalDateTime.of(year, month + 1, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
                    onPicked(picked.coerceAtMost(System.currentTimeMillis() + ONE_HOUR_MS))
                },
                start.hour,
                start.minute,
                DateFormat.is24HourFormat(context),
            ).apply { setTitle(Strings.active.MEAL_TIME_TITLE) }.show()
        },
        start.year,
        start.monthValue - 1,
        start.dayOfMonth,
    )
    dateDialog.datePicker.maxDate = System.currentTimeMillis() + ONE_HOUR_MS
    dateDialog.setTitle(Strings.active.MEAL_TIME_TITLE)
    dateDialog.show()
}

/**
 * One line of the meal suggestions: a dish or a past food, added in one tap. A long press opens
 * [menu] (pin/unpin, delete for a dish) when one is given.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutRow(
    title: String,
    detail: String,
    isPinned: Boolean,
    onAdd: () -> Unit,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onAdd, onLongClick = if (menu != null) ({ menuOpen = true }) else null)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isPinned) Text("★", color = PnColor.BrandLeaf, fontSize = 12.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = PnColor.InkDefault, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = PnColor.InkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onAdd) {
                Text(
                    "+",
                    color = PnColor.BrandLeafDeep,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { contentDescription = Strings.active.shortcutAddA11y(title) },
                )
            }
        }
        if (menu != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menu { menuOpen = false }
            }
        }
    }
}

@Composable
private fun PinMenuItem(store: MealShortcutsStore, key: String, dismiss: () -> Unit) {
    DropdownMenuItem(
        text = { Text(if (store.isPinned(key)) Strings.active.UNPIN else Strings.active.PIN) },
        onClick = {
            dismiss()
            store.togglePin(key)
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = PnColor.InkSoft,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
    )
}

private fun Modifier.shortcutCard(): Modifier = this
        .fillMaxWidth()
        .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))

/**
 * The empty meal's suggestions: saved dishes, then what is usually eaten at this time of day, each added
 * in one tap. Pinned ones first. Long press to pin or unpin, and to delete a dish.
 */
@Composable
fun QuickPicks(
    store: MealShortcutsStore,
    recents: List<RecentFood>,
    slot: MealSlot,
    onApplyDish: (Dish) -> Unit,
    onAddRecent: (RecentFood) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = Strings.active
    val dishes = store.dishesByRelevance
    val sortedRecents = recents.sortedByDescending { store.isPinned(it.key) }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (dishes.isNotEmpty()) {
            item(key = "dishes-title") { SectionTitle(strings.DISHES_TITLE) }
            items(dishes, key = { "dish-" + it.id }) { dish ->
                val key = MealShortcutsStore.dishKey(dish.id)
                Box(modifier = Modifier.shortcutCard()) {
                    ShortcutRow(
                        title = dish.name,
                        detail = strings.dishDetail(dish.items.size, dish.servings),
                        isPinned = store.isPinned(key),
                        onAdd = { onApplyDish(dish) },
                    ) { dismiss ->
                        PinMenuItem(store, key, dismiss)
                        DropdownMenuItem(
                            text = { Text(strings.DELETE_DISH, color = PnColor.StatusBad) },
                            onClick = {
                                dismiss()
                                store.delete(dish.id)
                            },
                        )
                    }
                }
            }
        }
        if (sortedRecents.isNotEmpty()) {
            item(key = "recents-title") { SectionTitle(strings.recentsTitle(slot)) }
            items(sortedRecents, key = { "recent-" + it.key }) { food ->
                Box(modifier = Modifier.shortcutCard()) {
                    ShortcutRow(
                        title = food.item.label,
                        detail = strings.recentDetail(food.item.quantityG, food.item.cookingMethod),
                        isPinned = store.isPinned(food.key),
                        onAdd = { onAddRecent(food) },
                    ) { dismiss -> PinMenuItem(store, food.key, dismiss) }
                }
            }
        }
    }
}

/** Folds case and accents, so "creme" finds "Crème". */
fun foldForSearch(text: String): String =
    java.text.Normalizer.normalize(text.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/**
 * While typing: a bare calorie figure offers a quick add, and saved dishes or past foods that match (at
 * most three each, from two characters) come before the catalogue results.
 */
@Composable
fun TypedShortcuts(
    quickAddKcal: Int?,
    dishes: List<Dish>,
    foods: List<RecentFood>,
    store: MealShortcutsStore,
    onQuickAdd: (Int) -> Unit,
    onApplyDish: (Dish) -> Unit,
    onAddRecent: (RecentFood) -> Unit,
) {
    if (quickAddKcal == null && dishes.isEmpty() && foods.isEmpty()) return
    val strings = Strings.active
    Column(
        modifier = Modifier.shortcutCard().border(1.dp, PnColor.InkLine, RoundedCornerShape(PnRadius.Default)),
    ) {
        quickAddKcal?.let { kcal ->
            ShortcutRow(strings.quickAddRow(kcal), strings.QUICK_ADD_NOTE, isPinned = false, onAdd = { onQuickAdd(kcal) })
        }
        dishes.forEach { dish ->
            ShortcutRow(
                title = dish.name,
                detail = strings.dishDetail(dish.items.size, dish.servings),
                isPinned = store.isPinned(MealShortcutsStore.dishKey(dish.id)),
                onAdd = { onApplyDish(dish) },
            )
        }
        foods.forEach { food ->
            ShortcutRow(
                title = food.item.label,
                detail = strings.recentDetail(food.item.quantityG, food.item.cookingMethod),
                isPinned = store.isPinned(food.key),
                onAdd = { onAddRecent(food) },
            )
        }
    }
}

/** A quick-add row in the meal: an energy figure typed by the person, with no ingredient behind it. */
@Composable
fun QuickAddMealRow(kcal: Double, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.shortcutCard().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("⚡", fontSize = 12.sp)
        Text("${roundHalfUp(kcal)} kcal", color = PnColor.InkDefault, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(Strings.active.QUICK_ADD_NOTE, color = PnColor.InkSoft, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onRemove) {
            Text(Strings.active.MEAL_REMOVE, color = PnColor.InkSoft, fontSize = 12.sp)
        }
    }
}

/** Portion steps offered on a dish applied to the meal. */
private val DISH_PART_STEPS = listOf(0.5, 1.0, 1.5, 2.0)

private fun partLabel(step: Double): String = when (step) {
    0.5 -> "½"
    1.5 -> "1½"
    else -> step.toInt().toString()
}

/**
 * The head of a dish applied to the meal: its name, the portion chips that rescale every ingredient at
 * once, and (folded) the names of its ingredients. Tapping it shows or hides the ingredient rows, which
 * stay editable. No energy figure: unlike iOS, this builder has no live per-row nutrition until
 * "Analyse", so the group's kcal is not known yet.
 */
@Composable
fun DishGroupHeader(
    name: String,
    summary: String,
    servings: Int,
    parts: Double,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onParts: (Double) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.shortcutCard().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (isExpanded) "▾" else "▸", color = PnColor.InkSoft, fontSize = 13.sp)
            Text(
                name,
                color = PnColor.InkDefault,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove) {
                Text(Strings.active.MEAL_REMOVE, color = PnColor.InkSoft, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DISH_PART_STEPS.forEach { step ->
                val selected = step == parts
                Text(
                    partLabel(step),
                    color = if (selected) PnColor.BrandLeafDeep else PnColor.InkDefault,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .background(
                            if (selected) PnColor.BrandLeaf.copy(alpha = 0.18f) else PnColor.SurfacePaper,
                            RoundedCornerShape(50),
                        )
                        .clickable { onParts(step) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Text(Strings.active.dishPartsUnit(servings), color = PnColor.InkSoft, fontSize = 12.sp)
        }
        if (!isExpanded) {
            Text(summary, color = PnColor.InkSoft, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Names a meal and saves it as a dish. The name is suggested from the main ingredients; "makes N
 * portions" is what lets a batch (a gratin for 4) be applied a quarter at a time later.
 */
@Composable
fun SaveDishDialog(items: List<MealItem>, onSave: (name: String, servings: Int) -> Unit, onDismiss: () -> Unit) {
    val strings = Strings.active
    var name by remember { mutableStateOf(suggestedDishName(items)) }
    var servings by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.SAVE_AS_DISH, color = PnColor.InkDefault) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.DISH_NAME_FIELD) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.dishServings(servings), color = PnColor.InkDefault, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { servings = (servings - 1).coerceAtLeast(1) }, enabled = servings > 1) {
                        Text("−", fontSize = 18.sp)
                    }
                    TextButton(onClick = { servings = (servings + 1).coerceAtMost(MAX_SERVINGS) }, enabled = servings < MAX_SERVINGS) {
                        Text("+", fontSize = 18.sp)
                    }
                }
                Text(
                    items.filterNot { it.isQuickAdd }.joinToString(" · ") { it.label },
                    color = PnColor.InkSoft,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name, servings)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text(strings.DISH_SAVE, color = PnColor.BrandLeafDeep) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.SEARCH_CANCEL, color = PnColor.InkSoft) }
        },
        containerColor = PnColor.SurfaceCard,
    )
}
