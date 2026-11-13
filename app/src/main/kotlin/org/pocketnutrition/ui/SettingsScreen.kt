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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pocketnutrition.api.ApiSettings
import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.ui.targets.DailyTargetsSettingsSection
import org.pocketnutrition.ui.vault.VaultStrings

/**
 * Language and the backend server — the two every-day-adjustable knobs, split out of [AboutScreen]
 * so the journal's menu can offer About and Settings as two distinct entries instead of one screen
 * that mixed licence/attribution obligations with things a user actually comes back to change.
 *
 * The iOS client mirrors this split with its own `SettingsView`.
 */
@Composable
fun SettingsScreen(onClose: () -> Unit, onOpenDailyTargets: () -> Unit = {}, onOpenVault: () -> Unit = {}) {
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
                text = Strings.active.SETTINGS_TITLE,
                color = PnColor.InkDefault,
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text(Strings.active.ABOUT_CLOSE, color = PnColor.BrandLeafDeep, fontSize = 14.sp)
            }
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LanguageSection()
            VaultSettingsSection(onOpen = onOpenVault)
            DailyTargetsSettingsSection(onOpen = onOpenDailyTargets)
            ServerSection()
            VoiceSettingsSection()
        }
    }
}

/**
 * Automatique/Français/English — an every-user setting, unlike [ServerSection]'s contributor-only
 * escape hatch, which is why this sits above it.
 */
@Composable
private fun LanguageSection() {
    var selection by remember { mutableStateOf(LanguageSettings.override) }

    Section(Strings.active.ABOUT_LANGUAGE_TITLE) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageOption(Strings.active.ABOUT_LANGUAGE_AUTO, selected = selection == null) {
                selection = null
                LanguageSettings.setOverride(null)
            }
            LanguageOption(Strings.active.ABOUT_LANGUAGE_FR, selected = selection == "fr") {
                selection = "fr"
                LanguageSettings.setOverride("fr")
            }
            LanguageOption(Strings.active.ABOUT_LANGUAGE_EN, selected = selection == "en") {
                selection = "en"
                LanguageSettings.setOverride("en")
            }
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PnRadius.Default)
    Text(
        text = label,
        color = if (selected) PnColor.InkOnInverse else PnColor.InkDefault,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(if (selected) PnColor.BrandLeafDeep else PnColor.SurfaceCard, shape)
            .border(1.dp, PnColor.InkLine, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * A contributor-facing escape hatch: point the app at a self-hosted server instead of the official
 * one. Not something the average user needs, which is why it is the last section on this screen.
 */
@Composable
private fun ServerSection() {
    var urlInput by remember { mutableStateOf(ApiSettings.customRawValue ?: "") }
    var invalid by remember { mutableStateOf(false) }

    Section(Strings.active.ABOUT_SERVER_TITLE) {
        Paragraph(Strings.active.ABOUT_SERVER_BODY)

        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                invalid = false
            },
            // The current server doubles as the placeholder: greyed out and gone the moment an
            // override exists, rather than restated in a line of its own above the field.
            placeholder = { Text(ApiSettings.baseUrl) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Uri,
            ),
            // An icon on the field it acts on, not a second full-width text button below — "reset"
            // reads faster as a glyph beside the value it clears than as a sentence.
            trailingIcon = {
                IconButton(
                    onClick = {
                        ApiSettings.reset()
                        urlInput = ""
                        invalid = false
                    },
                    modifier = Modifier.semantics { contentDescription = Strings.active.ABOUT_SERVER_RESET },
                ) {
                    Text("↺", color = PnColor.InkSoft, fontSize = 18.sp)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (invalid) {
            Text(text = Strings.active.ABOUT_SERVER_INVALID, color = PnColor.StatusBad, fontSize = 12.sp)
        }

        PrimaryButton(
            text = Strings.active.ABOUT_SERVER_APPLY,
            onClick = {
                invalid = !ApiSettings.setCustomBaseUrl(urlInput)
                if (!invalid) urlInput = urlInput.trim()
            },
        )
    }
}

/** The cloud vault, one row: the screen itself holds the account. */
@Composable
private fun VaultSettingsSection(onOpen: () -> Unit) {
    Section(VaultStrings.active.title) {
        Paragraph(VaultStrings.active.settingsBody)
        PrimaryButton(text = VaultStrings.active.open, onClick = onOpen)
    }
}
