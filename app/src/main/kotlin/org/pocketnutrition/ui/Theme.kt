// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import org.pocketnutrition.core.design.PnColor

/**
 * Maps the shared design tokens onto Material3's colour slots.
 *
 * WHY THIS EXISTS
 * Without it, every Material3 component that is not hand-coloured renders in M3's *default baseline*
 * palette — which is purple. Nothing in `contract/kotlin/Tokens.kt` is purple. The symptom was visible
 * rather than theoretical: the meal builder's search field had a lavender container and a purple focus
 * underline, sitting inside a cream-and-green screen, because `TextField` was never given colours and
 * fell through to the default. Every future `Slider`, `Switch`, `Checkbox`, `RadioButton` and
 * `Snackbar` would have done the same.
 *
 * The tokens are still the source of truth; this only teaches Material3 where to find them, so a
 * component picks up the palette by default instead of each call site remembering to.
 *
 * LIGHT ONLY, ON PURPOSE
 * There is one `lightColorScheme` and no dark twin. The design direction is a warm paper palette —
 * `SurfacePaper` is a cream, not a neutral — and the tokens define no dark counterparts. Adding a
 * `darkColorScheme` here would mean inventing eleven colours the design system has not chosen, which
 * is how a product ends up with a dark mode nobody designed. When the tokens grow a dark set, this is
 * the one place that changes.
 *
 * Dynamic colour is deliberately not used for the same reason: it would let the wallpaper repaint a
 * nutrition app whose colours carry meaning (`StatusOk` green, `StatusBad` red, the six cooking-method
 * hues).
 */
private val PocketNutritionColors = lightColorScheme(
    primary = PnColor.BrandLeafDeep,
    onPrimary = PnColor.InkOnInverse,
    primaryContainer = PnColor.SurfaceInverse,
    onPrimaryContainer = PnColor.InkOnInverse,

    secondary = PnColor.BrandLeaf,
    onSecondary = PnColor.InkOnInverse,
    secondaryContainer = PnColor.StatusOkSoft,
    onSecondaryContainer = PnColor.InkDefault,

    // The accent, used for the few places that want warmth rather than green.
    tertiary = PnColor.BrandCarrot,
    onTertiary = PnColor.InkOnInverse,
    tertiaryContainer = PnColor.BrandCarrotSoft,
    onTertiaryContainer = PnColor.InkDefault,

    background = PnColor.SurfacePaper,
    onBackground = PnColor.InkDefault,
    surface = PnColor.SurfaceCard,
    onSurface = PnColor.InkDefault,
    // Where a filled field or a chip sits: the cream, not a grey.
    surfaceVariant = PnColor.SurfaceCream,
    onSurfaceVariant = PnColor.InkSoft,
    surfaceContainerHighest = PnColor.SurfaceCream,

    outline = PnColor.InkSoft,
    outlineVariant = PnColor.InkLine,

    error = PnColor.StatusBad,
    onError = PnColor.InkOnInverse,
    errorContainer = PnColor.StatusBadSoft,
    onErrorContainer = PnColor.InkDefault,

    inverseSurface = PnColor.SurfaceInverse,
    inverseOnSurface = PnColor.InkOnInverse,
)

/** Wraps the app so Material3 components inherit the tokens instead of M3's defaults. */
@Composable
fun PocketNutritionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PocketNutritionColors, content = content)
}
