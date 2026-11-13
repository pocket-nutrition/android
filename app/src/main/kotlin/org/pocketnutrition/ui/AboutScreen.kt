// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import org.pocketnutrition.BuildConfig
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.design.PnRadius

/**
 * What the app owes the person holding it.
 *
 * Not a polish item — three separate obligations land on this one screen, each a shipping blocker:
 *
 * 1. **MPL-2.0 §3.2.** The licence covers the *executable*, not only the repository. Someone who
 *    installed the app from Play is a recipient of covered software and must be told they may read,
 *    modify and redistribute the source. A LICENSE file in the source repo reaches developers; it
 *    does not reach them.
 * 2. **ODbL attribution.** Product names, brands and photographs come from Open Food Facts, whose
 *    Open Database License requires attribution wherever the database is publicly used. Showing that
 *    data with no credit anywhere in the product is a licence breach.
 * 3. **A reachable privacy policy.** Play Console refuses a listing without one.
 *
 * WHAT THIS SCREEN MUST NOT DO
 * The copy is a *summary* of the hosted policy; if the two disagree, the app is making a privacy
 * claim nobody reviewed. [Strings.active.ABOUT_PRIVACY_SUMMARY] is deliberately paired with
 * [Strings.active.ABOUT_PRIVACY_NETWORK]: the first sentence alone reads as "nothing ever leaves the phone",
 * which is false — searching and analysing both make requests. Ship the pair or neither.
 *
 * The language switcher and server override used to live here too; contract 2.1.0 split them into
 * [SettingsScreen] so the journal's menu can offer About and Settings as two distinct entries.
 *
 * The iOS client renders the same sections from the same contract keys.
 */
@Composable
fun AboutScreen(onClose: () -> Unit) {
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
                text = Strings.active.ABOUT_TITLE,
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = Strings.active.BRAND,
                    color = PnColor.InkDefault,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                // From BuildConfig rather than a literal, so it cannot disagree with what Play shows.
                Text(
                    text = Strings.active.aboutVersion(BuildConfig.VERSION_NAME),
                    color = PnColor.InkSoft,
                    fontSize = 13.sp,
                )
            }

            Section(Strings.active.ABOUT_PRIVACY_TITLE) {
                Paragraph(Strings.active.ABOUT_PRIVACY_SUMMARY)
                Paragraph(Strings.active.ABOUT_PRIVACY_NETWORK)
                LinkRow(Strings.active.ABOUT_PRIVACY_LINK, PRIVACY_POLICY_URL)
            }

            Section(Strings.active.ABOUT_SOURCES_TITLE) {
                Paragraph(Strings.active.ABOUT_SOURCES_BODY)
            }

            Section(Strings.active.ABOUT_LICENCE_TITLE) {
                Paragraph(Strings.active.ABOUT_LICENCE_BODY)
                LinkRow(Strings.active.ABOUT_SOURCE_LINK, SOURCE_URL)

                Text(
                    text = Strings.active.ABOUT_THIRD_PARTY_TITLE,
                    color = PnColor.InkDefault,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                THIRD_PARTY_COMPONENTS.forEach { Paragraph(it) }
            }
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PnColor.SurfaceCard, RoundedCornerShape(PnRadius.Default))
            // The border is not decoration: SurfaceCard is #FFFFFF over SurfacePaper #FAF7F0, a
            // contrast ratio of 1.07:1. Without it the card has no perceivable edge.
            .border(1.dp, PnColor.InkLine, RoundedCornerShape(PnRadius.Default))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = PnColor.BrandLeafDeep,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
internal fun Paragraph(text: String) {
    Text(text = text, color = PnColor.InkSoft, fontSize = 13.sp, lineHeight = 19.sp)
}

/** Whole row is the target, so it clears the 48 dp minimum without the caller thinking about it. */
@Composable
private fun LinkRow(label: String, url: String) {
    val context = LocalContext.current
    Text(
        text = "$label ↗",
        color = PnColor.BrandLeafDeep,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable {
                // A device with no browser is unusual but possible (kiosk images, stripped ROMs).
                // Letting the exception through would crash the about screen of all places.
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: ActivityNotFoundException) {
                    // Nothing sensible to fall back to: the link is the whole action.
                }
            }
            .padding(vertical = 14.dp),
    )
}

/** The same URL the Play listing declares. */
private const val PRIVACY_POLICY_URL = "https://pocketnutrition.org/privacy.html"

private const val SOURCE_URL = "https://github.com/pocket-nutrition/android"

/**
 * Component names and licence identifiers are proper nouns, not translatable copy, so they live here
 * rather than in the deck — which is also why iOS carries a single `thirdPartyNone` string instead:
 * that target links nothing beyond the system frameworks.
 *
 * Everything the release build ships is Apache-2.0, which requires attribution of the licence but not
 * reproduction of its full text. Keep this list in step with `gradle/libs.versions.toml` —
 * `AboutScreenTest` asserts every runtime dependency group there is represented here.
 */
internal val THIRD_PARTY_COMPONENTS = listOf(
    "AndroidX et Jetpack Compose (dont CameraX) — Apache License 2.0",
    "Kotlin et kotlinx.serialization — Apache License 2.0",
    "Coil — Apache License 2.0",
    "OkHttp — Apache License 2.0",
    "ZXing — Apache License 2.0",
    "libsodium — ISC License",
    "Lazysodium — Mozilla Public License 2.0",
    "JNA (Java Native Access) — Apache License 2.0",
    "EFF long wordlist — Creative Commons Attribution 3.0 US License",
)
