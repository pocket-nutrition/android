// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.pocketnutrition.api.ApiException
import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.api.PocketNutritionApi
import org.pocketnutrition.core.MealIngredient
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.recognition.Entry
import org.pocketnutrition.recognition.ImportPlan
import org.pocketnutrition.recognition.ItemSelection
import org.pocketnutrition.recognition.defaultSelection
import org.pocketnutrition.recognition.resolveImport

/**
 * Review of a `READY` entry, entirely from local data: the transcript, then each item with its ranked
 * candidates as a single-choice list (best preselected) and a skip toggle.
 *
 * "Add to meal" resolves each chosen candidate through `GET /ingredients/{id}`. An item that cannot be
 * resolved or built is skipped and listed with the reason — nothing is invented or name-searched. When
 * something was skipped the user sees that list before continuing; otherwise the rows go straight to
 * the existing meal builder.
 */
@Composable
fun VoiceReviewScreen(
    entry: Entry,
    onBack: () -> Unit,
    onHandOff: (List<MealIngredient>) -> Unit,
) {
    val result = entry.result
    val items = result?.items ?: emptyList()
    val selections = remember { mutableStateListOf<ItemSelection>().apply { addAll(items.map(::defaultSelection)) } }
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    val shape = RoundedCornerShape(PnRadius.Default)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(PnColor.SurfacePaper),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Strings.active.VOICE_REVIEW_TITLE,
                color = PnColor.InkDefault,
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack, enabled = !importing) {
                Text(Strings.active.VOICE_BACK, color = PnColor.BrandLeafDeep, fontSize = 14.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(if (entry.isPhoto) Strings.active.PHOTO_DESCRIPTION else Strings.active.VOICE_TRANSCRIPT) { Paragraph(result?.transcript ?: "") }

            if (items.isEmpty()) Paragraph(Strings.active.VOICE_NO_ITEMS)

            items.forEachIndexed { index, item ->
                val selection = selections.getOrNull(index) ?: defaultSelection(item)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PnColor.SurfaceCard, shape)
                        .border(1.dp, PnColor.InkLine, shape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(item.rawText, color = PnColor.InkDefault, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (item.candidates.isEmpty()) {
                        Paragraph(Strings.active.VOICE_NO_CANDIDATES)
                    }
                    item.candidates.forEachIndexed { candidateIndex, candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !selection.skipped) {
                                    selections[index] = selection.copy(candidateIndex = candidateIndex)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selection.candidateIndex == candidateIndex,
                                onClick = null,
                                enabled = !selection.skipped,
                            )
                            Text(
                                candidate.name.ifBlank { candidate.ingredientId },
                                color = if (selection.skipped) PnColor.InkSoft else PnColor.InkDefault,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.clickable { selections[index] = selection.copy(skipped = !selection.skipped) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selection.skipped, onCheckedChange = null)
                        Text(
                            Strings.active.VOICE_SKIP_ITEM,
                            color = PnColor.InkSoft,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            errorMessage?.let { Text(it, color = PnColor.StatusBad, fontSize = 13.sp) }

            plan?.let { current ->
                Section(Strings.active.VOICE_SKIPPED_HEADING) {
                    current.skipped.forEach { Paragraph(Strings.active.voiceSkipped(it.rawText, it.reason)) }
                    if (current.rows.isEmpty()) Paragraph(Strings.active.VOICE_NOTHING_ADDED)
                }
            }
        }

        Column(modifier = Modifier.padding(20.dp)) {
            val pending = plan
            if (pending != null && pending.rows.isNotEmpty()) {
                PrimaryButton(Strings.active.VOICE_CONTINUE, onClick = { onHandOff(pending.rows) })
            } else {
                PrimaryButton(
                    text = if (importing) Strings.active.VOICE_IMPORTING else Strings.active.VOICE_ADD_TO_MEAL,
                    enabled = !importing && items.isNotEmpty(),
                    onClick = {
                        importing = true
                        errorMessage = null
                        plan = null
                        scope.launch {
                            try {
                                val resolved = resolveImport(
                                    items = items,
                                    selections = selections.toList(),
                                    lang = LanguageSettings.effective,
                                    lookup = { PocketNutritionApi.ingredientDetail(it) },
                                )
                                if (resolved.skipped.isEmpty() && resolved.rows.isNotEmpty()) {
                                    onHandOff(resolved.rows)
                                } else {
                                    plan = resolved
                                }
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (failure: Exception) {
                                errorMessage = Strings.active.error(
                                    (failure as? ApiException)?.frenchMessage ?: failure.message ?: "",
                                )
                            } finally {
                                importing = false
                            }
                        }
                    },
                )
            }
        }
    }
}
