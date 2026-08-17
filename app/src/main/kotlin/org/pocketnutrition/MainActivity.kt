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
import androidx.compose.runtime.setValue
import java.io.File
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.model.JournalStore
import org.pocketnutrition.model.MealItem
import org.pocketnutrition.model.SavedMeal
import org.pocketnutrition.model.asMealIngredient
import org.pocketnutrition.ui.AboutScreen
import org.pocketnutrition.ui.JournalScreen
import org.pocketnutrition.ui.MealBuilderScreen
import org.pocketnutrition.ui.PocketNutritionTheme
import org.pocketnutrition.ui.ResultsScreen

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
        // Constructed once here rather than per-composable: the store reads its file in init, so this
        // keeps that to a single read at launch.
        val store = JournalStore(File(filesDir, "journal.json"))
        setContent { PocketNutritionTheme { PocketNutritionApp(store) } }
    }
}

private sealed interface Screen {
    data object Journal : Screen
    /** `editingMeal` set means an existing entry is being edited, rather than a new one composed. */
    data class Building(val editingMeal: SavedMeal? = null) : Screen
    data class Results(
        val profiles: List<NutritionalProfile>,
        val items: List<MealItem>,
        val editingMealId: String?,
    ) : Screen
    data object About : Screen
}

@Composable
private fun PocketNutritionApp(store: JournalStore) {
    var screen by remember { mutableStateOf<Screen>(Screen.Journal) }

    // System back returns to the journal from anywhere else, rather than closing the app mid-compose.
    BackHandler(enabled = screen != Screen.Journal) { screen = Screen.Journal }

    when (val current = screen) {
        Screen.Journal -> JournalScreen(
            store = store,
            onAddMeal = { screen = Screen.Building() },
            onEditMeal = { meal -> screen = Screen.Building(editingMeal = meal) },
            onShowAbout = { screen = Screen.About },
        )

        is Screen.Building -> MealBuilderScreen(
            initialItems = current.editingMeal?.items?.map { it.asMealIngredient() } ?: emptyList(),
            onCancel = { screen = Screen.Journal },
            onAnalysed = { profiles, items ->
                screen = Screen.Results(profiles, items, editingMealId = current.editingMeal?.id)
            },
        )

        is Screen.Results -> ResultsScreen(
            profiles = current.profiles,
            items = current.items,
            onSave = { totals ->
                val editingMealId = current.editingMealId
                if (editingMealId != null) {
                    store.update(editingMealId, current.items, totals)
                } else {
                    store.save(current.items, totals)
                }
                screen = Screen.Journal
            },
            onEdit = { screen = Screen.Building() },
        )

        Screen.About -> AboutScreen(onClose = { screen = Screen.Journal })
    }
}
