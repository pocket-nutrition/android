// SPDX-License-Identifier: MPL-2.0
/*
 * GENERATED FILE — DO NOT EDIT.
 * Source: pocket-nutrition-contracts/tokens/{tokens,semantic-map,cooking-palette}.json
 * Regenerate: python3 scripts/emit_tokens.py
 *
 * Editing this by hand guarantees the two apps drift apart, which is the single thing the
 * contracts repository exists to prevent. Change the JSON and re-run.
 */
package org.pocketnutrition.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Raw palette. Prefer [PnRole] at call sites — a role survives a palette change. */
object PnColor {
    /** Application background. Warm off-white, not #fff — the whole system reads as paper. */
    val SurfacePaper = Color(0xFFFAF7F0)
    /** Secondary surface: badge backgrounds, progress track, .card-cream. */
    val SurfaceCream = Color(0xFFF1EAD9)
    /** Raised card surface. Pure white, deliberately brighter than paper so a card lifts off the background without needing a shadow. */
    val SurfaceCard = Color(0xFFFFFFFF)
    /** Dark surface (.card-dark, .btn-primary fill). Same value as color.brand.leafDeep, kept separate because this is a surface role rather than a brand accent. */
    val SurfaceInverse = Color(0xFF1F3D29)
    /** Primary text. A very dark desaturated green, not black. */
    val InkDefault = Color(0xFF1E2B22)
    /** Secondary text: .lead, table headers, field help, nav links. */
    val InkSoft = Color(0xFF4A5A4E)
    /** Text on a dark surface. Same value as surface.paper. */
    val InkOnInverse = Color(0xFFFAF7F0)
    /** Hairline borders. Deliberately an alpha of ink rather than an opaque grey, so it tints correctly over both paper and cream. A port must keep the alpha — flattening it to an opaque hex breaks it over cream. */
    val InkLine = Color(0x241E2B22)
    /** Primary brand green. Also emphasis italics, kickers, and input focus borders. */
    val BrandLeaf = Color(0xFF3E7A4E)
    /** Deep brand green: primary button fill, active pills, dark cards. */
    val BrandLeafDeep = Color(0xFF1F3D29)
    /** Accent orange: logo mark, accent buttons, and the focus ring. */
    val BrandCarrot = Color(0xFFE07A2F)
    /** Tint of carrot for accent backgrounds. */
    val BrandCarrotSoft = Color(0xFFF7E3CF)
    /** Good / high confidence. Currently identical to brand.leaf. */
    val StatusOk = Color(0xFF3E7A4E)
    val StatusOkSoft = Color(0xFFE2ECDF)
    /** Caution / medium confidence. Also the dashed border of .badge-outline. */
    val StatusWarn = Color(0xFFC9862E)
    val StatusWarnSoft = Color(0xFFF6E8CF)
    /** Error / low confidence. */
    val StatusBad = Color(0xFFB0402F)
    val StatusBadSoft = Color(0xFFF4DDD7)
    val HueSea = Color(0xFF3D6A7A)
    val HueSeaSoft = Color(0xFFDDE9EC)
    val HueGold = Color(0xFFB08A2E)
    val HueGoldSoft = Color(0xFFF2E9D0)
    val HuePlum = Color(0xFF7A4A66)
    val HuePlumSoft = Color(0xFFECDFE7)
    /** On status.okSoft (.badge-ok). Reuses brand.leafDeep. */
    val OnSoftOk = Color(0xFF1F3D29)
    /** On status.warnSoft (.badge-warn, .badge-outline). */
    val OnSoftWarn = Color(0xFF7A5116)
    /** On status.badSoft (.badge-bad). */
    val OnSoftBad = Color(0xFF7C2C1F)
    /** On hue.seaSoft (.badge-sea). */
    val OnSoftSea = Color(0xFF274B58)
    /** On hue.goldSoft (.badge-gold). */
    val OnSoftGold = Color(0xFF6E5518)
    /** On hue.plumSoft (.badge-plum). */
    val OnSoftPlum = Color(0xFF55304A)
    val ChartSeries1 = Color(0xFF6366F1)
    val ChartSeries2 = Color(0xFF10B981)
    val ChartSeries3 = Color(0xFFF59E0B)
    val ChartSeries4 = Color(0xFFEF4444)
    val ChartSeries5 = Color(0xFF8B5CF6)
    val ChartSeries6 = Color(0xFFF97316)
    val ChartSeries7 = Color(0xFF0EA5E9)
    val ChartSeries8 = Color(0xFFEC4899)
}

/**
 * Semantic roles, resolved from tokens/semantic-map.json. This is what UI code should
 * reference: a role records a decision, a palette entry is only a value.
 */
object PnRole {
    /** color.brand.leafDeep */
    val ButtonPrimaryBackground = Color(0xFF1F3D29)
    /** color.surface.paper */
    val ButtonPrimaryForeground = Color(0xFFFAF7F0)
    /** color.brand.leafDeep */
    val ButtonGhostForeground = Color(0xFF1F3D29)
    /** color.brand.leafDeep */
    val ButtonGhostBorderColor = Color(0xFF1F3D29)
    /** color.brand.leaf @ 0.08 */
    val ButtonGhostHoverBackground = Color(0x143E7A4E)
    /** color.brand.carrot */
    val ButtonAccentBackground = Color(0xFFE07A2F)
    /** color.surface.card */
    val CardDefaultBackground = Color(0xFFFFFFFF)
    /** color.ink.line */
    val CardDefaultBorderColor = Color(0x241E2B22)
    /** color.surface.cream */
    val CardCreamBackground = Color(0xFFF1EAD9)
    /** color.ink.line */
    val CardCreamBorderColor = Color(0x241E2B22)
    /** color.surface.inverse */
    val CardDarkBackground = Color(0xFF1F3D29)
    /** color.ink.onInverse */
    val CardDarkForeground = Color(0xFFFAF7F0)
    /** color.surface.cream */
    val BadgeNeutralBackground = Color(0xFFF1EAD9)
    /** color.ink.soft */
    val BadgeNeutralForeground = Color(0xFF4A5A4E)
    /** color.status.okSoft */
    val BadgeOkBackground = Color(0xFFE2ECDF)
    /** color.onSoft.ok */
    val BadgeOkForeground = Color(0xFF1F3D29)
    /** color.status.warnSoft */
    val BadgeWarnBackground = Color(0xFFF6E8CF)
    /** color.onSoft.warn */
    val BadgeWarnForeground = Color(0xFF7A5116)
    /** color.status.badSoft */
    val BadgeBadBackground = Color(0xFFF4DDD7)
    /** color.onSoft.bad */
    val BadgeBadForeground = Color(0xFF7C2C1F)
    /** color.hue.seaSoft */
    val BadgeSeaBackground = Color(0xFFDDE9EC)
    /** color.onSoft.sea */
    val BadgeSeaForeground = Color(0xFF274B58)
    /** color.hue.goldSoft */
    val BadgeGoldBackground = Color(0xFFF2E9D0)
    /** color.onSoft.gold */
    val BadgeGoldForeground = Color(0xFF6E5518)
    /** color.hue.plumSoft */
    val BadgePlumBackground = Color(0xFFECDFE7)
    /** color.onSoft.plum */
    val BadgePlumForeground = Color(0xFF55304A)
    /** color.status.warn */
    val BadgeOutlineBorderColor = Color(0xFFC9862E)
    /** color.onSoft.warn */
    val BadgeOutlineForeground = Color(0xFF7A5116)
    /** color.surface.card */
    val FilterPillIdleBackground = Color(0xFFFFFFFF)
    /** color.ink.soft */
    val FilterPillIdleForeground = Color(0xFF4A5A4E)
    /** color.ink.line */
    val FilterPillIdleBorderColor = Color(0x241E2B22)
    /** color.brand.leaf */
    val FilterPillHoverBorderColor = Color(0xFF3E7A4E)
    /** color.brand.leafDeep */
    val FilterPillHoverForeground = Color(0xFF1F3D29)
    /** color.brand.leafDeep */
    val FilterPillActiveBackground = Color(0xFF1F3D29)
    /** color.brand.leafDeep */
    val FilterPillActiveBorderColor = Color(0xFF1F3D29)
    /** color.surface.paper */
    val FilterPillActiveForeground = Color(0xFFFAF7F0)
    /** color.surface.card */
    val InputDefaultBackground = Color(0xFFFFFFFF)
    /** color.ink.default */
    val InputDefaultForeground = Color(0xFF1E2B22)
    /** color.ink.line */
    val InputDefaultBorderColor = Color(0x241E2B22)
    /** color.brand.leaf */
    val InputFocusBorderColor = Color(0xFF3E7A4E)
    /** color.brand.leaf @ 0.18 */
    val InputFocusRingColor = Color(0x2E3E7A4E)
    /** color.ink.default */
    val InputLabelForeground = Color(0xFF1E2B22)
    /** color.ink.soft */
    val InputHelpForeground = Color(0xFF4A5A4E)
    /** color.ink.soft */
    val TableHeaderForeground = Color(0xFF4A5A4E)
    /** color.ink.line */
    val TableHeaderBorderBottomColor = Color(0x241E2B22)
    /** color.ink.line */
    val TableCellBorderBottomColor = Color(0x241E2B22)
    /** color.brand.leaf @ 0.05 */
    val TableRowHoverBackground = Color(0x0D3E7A4E)
    /** color.surface.cream */
    val ProgressTrackBackground = Color(0xFFF1EAD9)
    /** color.brand.leaf */
    val ProgressSegmentOkBackground = Color(0xFF3E7A4E)
    /** color.brand.carrot */
    val ProgressSegmentWarnBackground = Color(0xFFE07A2F)
    /** color.status.bad */
    val ProgressSegmentBadBackground = Color(0xFFB0402F)
    /** color.surface.paper @ 0.9 */
    val NavBackground = Color(0xE6FAF7F0)
    /** color.ink.line */
    val NavBorderBottomColor = Color(0x241E2B22)
    /** color.ink.default */
    val NavLogoForeground = Color(0xFF1E2B22)
    /** color.brand.carrot */
    val NavLogoMarkForeground = Color(0xFFE07A2F)
    /** color.ink.soft */
    val NavLinkForeground = Color(0xFF4A5A4E)
    /** color.brand.leafDeep */
    val NavLinkActiveForeground = Color(0xFF1F3D29)
    /** color.brand.leaf */
    val TypographyEmphasisForeground = Color(0xFF3E7A4E)
    /** color.brand.leaf */
    val TypographyKickerForeground = Color(0xFF3E7A4E)
    /** color.ink.soft */
    val TypographyLeadForeground = Color(0xFF4A5A4E)
    /** color.ink.soft */
    val TypographyStatLabelForeground = Color(0xFF4A5A4E)
    /** focus.ringColor */
    val FocusRingColor = Color(0xFFE07A2F)
}

object PnRadius {
    /** Cards and panels. */
    val Default: Dp = 18.dp
    /** Inputs and selects. */
    val Small: Dp = 10.dp
    /** Buttons, badges, filter pills, progress track. A literal in the stylesheet rather than a :root variable; captured here because a port needs a real number, and 999px means 'fully rounded'. */
    val Pill: Dp = 999.dp
}

object PnMotion {
    /** Honour ANIMATOR_DURATION_SCALE: the design system disables all motion under
     *  reduced-motion, so these must collapse to 0 in that case. */
    const val FastMillis = 150
    const val CardMillis = 180
    val OutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

/** One focus treatment system-wide, in the ACCENT colour so focus never reads as selected. */
object PnFocus {
    val Ring = Color(0xFFE07A2F)
    val Width: Dp = 3.dp
    val Offset: Dp = 2.dp
}

/** Platform-default families. The design ships no webfont, so there is nothing to bundle. */
object PnFont {
    // display: Iowan Old Style, Palatino Linotype, Book Antiqua, Georgia, serif
    // body: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif
    // mono: ui-monospace, SF Mono, Menlo, monospace
}

/**
 * Confidence badge colours. The THRESHOLDS are contractual — a user comparing Android and
 * iOS on the same meal must see the same badge. Evaluated in order, lower bound inclusive.
 */
object PnConfidence {
    data class Level(
        val min: Float,
        val level: String,
        val background: Color,
        val foreground: Color,
    )

    val Levels = listOf(
        Level(0.8f, "ok", Color(0xFFE2ECDF), Color(0xFF1F3D29)),
        Level(0.6f, "warn", Color(0xFFF6E8CF), Color(0xFF7A5116)),
        Level(0.0f, "bad", Color(0xFFF4DDD7), Color(0xFF7C2C1F)),
    )

    fun forConfidence(value: Float): Level = Levels.first { value >= it.min }
}

/**
 * Cooking methods, in picker order. Labels are NOT here — they are translatable and live in
 * strings/fr.json under `cookingMethods`, keyed by [id].
 *
 * `onBase` is dark rather than white for grilled and roasted, because white on those fills is
 * only 3.0:1 and 3.2:1. An active chip must also keep a border: those two fills are below the
 * 3:1 that WCAG 1.4.11 wants against paper, so the border carries the component boundary.
 */
object PnCooking {
    data class Method(
        val id: String,
        val emoji: String,
        /** Fill of the active chip. */
        val base: Color,
        /** Inactive pill and lane drop-zone background. */
        val soft: Color,
        /** Label on [base]. */
        val onBase: Color,
        /** Label on [soft], and the lane header text. */
        val onSoft: Color,
    )

    val Methods = listOf(
        Method("raw", "🌿", Color(0xFF3E7A4E), Color(0xFFDBECDE), Color(0xFFFFFFFF), Color(0xFF275433)),
        Method("boiled", "🫧", Color(0xFF3D6A7A), Color(0xFFD5EBF4), Color(0xFFFFFFFF), Color(0xFF015166)),
        Method("steamed", "♨️", Color(0xFF28756E), Color(0xFFD5EDEA), Color(0xFFFFFFFF), Color(0xFF005650)),
        Method("grilled", "🔥", Color(0xFFE07A2F), Color(0xFFF6E3D7), Color(0xFF4C2303), Color(0xFF683C1F)),
        Method("roasted", "🫕", Color(0xFFB08A2E), Color(0xFFEFE6D4), Color(0xFF3A2500), Color(0xFF5B450F)),
        Method("fried", "🍳", Color(0xFFB9502D), Color(0xFFF7E1DA), Color(0xFFFFFFFF), Color(0xFF6B3A2A)),
    )

    /** Null for an unknown method — the API can add one, so do not throw. */
    fun byId(id: String): Method? = Methods.firstOrNull { it.id == id }
}
