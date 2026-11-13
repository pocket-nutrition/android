// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.targets

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.model.DailyTargetsFlag

/** Settings → "Daily targets (preview)": the feature flag, and the way into the feature's own settings. */
@Composable
fun DailyTargetsSettingsSection(onOpen: () -> Unit) {
    val s = TargetStrings.active
    TargetsCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.flagLabel, color = PnColor.BrandLeafDeep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(checked = DailyTargetsFlag.enabled, onCheckedChange = { DailyTargetsFlag.set(it) })
        }
        Note(s.flagBody)
        if (DailyTargetsFlag.enabled) LinkAction(s.openTargets + " ›", onClick = onOpen)
    }
}
