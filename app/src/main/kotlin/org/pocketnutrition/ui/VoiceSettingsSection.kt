// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.recognition.RecognitionSettings
import org.pocketnutrition.recognition.hostOf

/**
 * The voice-recognition server: a URL (https only) and an API key. Entirely separate from the
 * nutrition-API override in [SettingsScreen]. The key field is masked and never prefilled; a stored
 * key is signalled by the placeholder, and leaving the field blank keeps it.
 */
@Composable
internal fun VoiceSettingsSection() {
    var url by remember { mutableStateOf(RecognitionSettings.rawUrl ?: "") }
    var key by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // The key lives behind the Keystore: never touched on the main thread.
    LaunchedEffect(Unit) { hasKey = withContext(Dispatchers.IO) { RecognitionSettings.hasApiKey } }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Section(Strings.active.VOICE_SETTINGS_TITLE) {
        Paragraph(Strings.active.VOICE_SETTINGS_BODY)

        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                message = null
            },
            label = { Text(Strings.active.VOICE_URL_LABEL) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Uri,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = key,
            onValueChange = {
                key = it
                message = null
            },
            label = { Text(Strings.active.VOICE_KEY_LABEL) },
            placeholder = if (hasKey) ({ Text(Strings.active.VOICE_KEY_STORED) }) else null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        message?.let {
            Text(text = it, color = if (isError) PnColor.StatusBad else PnColor.StatusOk, fontSize = 12.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                val urlToSave = url
                val keyToSave = key
                scope.launch {
                    val result = withContext(Dispatchers.IO) { RecognitionSettings.save(urlToSave, keyToSave) }
                    when (result) {
                        RecognitionSettings.SaveResult.SAVED -> {
                            url = urlToSave.trim()
                            key = ""
                            hasKey = withContext(Dispatchers.IO) { RecognitionSettings.hasApiKey }
                            isError = false
                            message = Strings.active.voiceSaved(hostOf(urlToSave) ?: urlToSave)
                        }
                        RecognitionSettings.SaveResult.KEY_INVALID -> {
                            isError = true
                            message = Strings.active.VOICE_KEY_INVALID
                        }
                        RecognitionSettings.SaveResult.KEY_REQUIRED_FOR_NEW_HOST -> {
                            isError = true
                            message = Strings.active.VOICE_KEY_REQUIRED_NEW_HOST
                        }
                        RecognitionSettings.SaveResult.URL_INVALID -> {
                            isError = true
                            message = Strings.active.VOICE_URL_INVALID
                        }
                        RecognitionSettings.SaveResult.STORAGE_FAILED -> {
                            isError = true
                            message = Strings.active.VOICE_SAVE_FAILED
                        }
                    }
                }
            }) {
                Text(Strings.active.VOICE_SAVE, color = PnColor.BrandLeafDeep, fontSize = 14.sp)
            }
            TextButton(onClick = {
                scope.launch {
                    // The Keystore lock may be held by a decrypt: never take it on the main thread.
                    withContext(Dispatchers.IO) { RecognitionSettings.clear() }
                    url = ""
                    key = ""
                    hasKey = false
                    isError = false
                    message = null
                }
            }) {
                Text(Strings.active.VOICE_CLEAR, color = PnColor.InkSoft, fontSize = 14.sp)
            }
        }
    }
}
