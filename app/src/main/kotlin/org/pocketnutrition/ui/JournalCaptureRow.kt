// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius
import org.pocketnutrition.recognition.Entry
import org.pocketnutrition.recognition.EntryIssue
import org.pocketnutrition.recognition.EntryState

/**
 * A dictation or meal photo that is not a meal yet, shown in the journal at the time it was taken.
 *
 * Drawn as an outline (dashed border on the page colour) beside the solid cards of saved meals: the same
 * place in the day, visibly not finished. Its status line says what happens next; the action button is
 * the one that state offers.
 */
@Composable
fun JournalCaptureRow(
    entry: Entry,
    onOpen: () -> Unit,
    onSendNow: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = Strings.active
    val outline = PnColor.InkSoft.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PnColor.SurfacePaper, RoundedCornerShape(PnRadius.Default))
            .drawBehind {
                val radius = PnRadius.Default.toPx()
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            .then(if (entry.state == EntryState.READY) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (entry.isPhoto) "📷" else "🎙️", fontSize = 15.sp)
            Text(
                strings.journalCaptureTitle(timeOf(entry.createdAtEpochMs)),
                color = PnColor.InkDefault,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (entry.state == EntryState.PROCESSING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PnColor.BrandLeaf)
            }
        }
        Text(
            captureStatus(entry),
            color = if (entry.state == EntryState.NEEDS_RETRY || entry.state == EntryState.UNRECOVERABLE) {
                PnColor.StatusBad
            } else {
                PnColor.InkSoft
            },
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (entry.state) {
                EntryState.PENDING_UPLOAD -> Action(strings.VOICE_SEND_NOW, onSendNow)
                EntryState.READY -> Action(strings.VOICE_OPEN, onOpen)
                EntryState.NEEDS_RETRY -> Action(strings.VOICE_RESEND, onResend)
                EntryState.PROCESSING, EntryState.UNRECOVERABLE -> Unit
            }
            Action(strings.JOURNAL_DELETE, onDelete, muted = true)
        }
    }
}

/** What the entry is doing, in one line. Moved from the former voice screen's row. */
internal fun captureStatus(entry: Entry): String {
    val strings = Strings.active
    return when (entry.state) {
        EntryState.PENDING_UPLOAD -> entry.issue?.let { strings.voiceIssue(it, entry.error, entry.audioFile != null) }
            ?: strings.VOICE_PENDING
        EntryState.PROCESSING -> entry.issue
            ?.takeIf { it == EntryIssue.REJECTED || it == EntryIssue.SERVER_ERROR }
            ?.let { strings.voiceIssue(it, entry.error, entry.audioFile != null) }
            ?: strings.voiceProcessing(entry.lastStatus)
        EntryState.READY -> strings.voiceReady(entry.result?.items?.size ?: 0)
        EntryState.NEEDS_RETRY, EntryState.UNRECOVERABLE ->
            strings.voiceIssue(entry.issue ?: EntryIssue.GONE, entry.error, entry.audioFile != null)
    }
}

private fun timeOf(epochMs: Long): String {
    val locale = if (LanguageSettings.effective == "en") Locale.US else Locale.FRANCE
    return DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(epochMs))
}

@Composable
private fun Action(label: String, onClick: () -> Unit, muted: Boolean = false) {
    TextButton(onClick = onClick) {
        Text(
            label,
            color = if (muted) PnColor.InkSoft else PnColor.BrandLeafDeep,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
