// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.ui.graphics.Color
import org.pocketnutrition.core.design.PnColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the colour literals in `res/values/` still agree with the generated design tokens.
 *
 * WHY THIS EXISTS
 * `themes.xml` and `ic_launcher_colors.xml` both duplicate token values as hex literals, because
 * neither an XML theme nor a resource file can read a Kotlin constant. Both files carried a comment
 * saying "ThemeResourceTest asserts they still agree with the tokens" — and this class did not exist.
 * Four literals were free to drift from `contract/kotlin/Tokens.kt` while two comments claimed
 * otherwise, which is worse than an unguarded duplication: a reader would not think to check.
 *
 * The resources are read as files rather than through Android's resource system. These are plain JVM
 * unit tests with no Robolectric and no instrumentation, so `R.color` is unavailable — but the XML is
 * just a file in the module, and parsing it is what makes the assertion possible at all. `VectorCorpus`
 * takes the classpath route for the same reason: read the artefact, not a mock of it.
 */
class ThemeResourceTest {

    @Test
    fun `the launcher icon colours match the brand tokens`() {
        val colours = colourResources("ic_launcher_colors.xml")

        assertEquals(
            "ic_launcher_background must be color.brand.leafDeep",
            PnColor.BrandLeafDeep,
            colours.getValue("ic_launcher_background"),
        )
        assertEquals(
            "ic_launcher_leaf must be color.brand.leaf",
            PnColor.BrandLeaf,
            colours.getValue("ic_launcher_leaf"),
        )
        assertEquals(
            "ic_launcher_mark must be color.brand.carrot",
            PnColor.BrandCarrot,
            colours.getValue("ic_launcher_mark"),
        )
    }

    @Test
    fun `the XML theme window background matches the paper surface token`() {
        // The window background exists so the launch does not flash white on a paper-coloured app, so
        // a drifted literal shows up as a visible flash on every cold start — and nowhere else.
        val items = themeItems()

        assertEquals(
            "android:windowBackground must be color.surface.paper",
            PnColor.SurfacePaper,
            items.getValue("android:windowBackground"),
        )
        assertEquals(
            "android:statusBarColor must be color.surface.paper",
            PnColor.SurfacePaper,
            items.getValue("android:statusBarColor"),
        )
    }

    /**
     * Every literal in both files is covered by one of the assertions above.
     *
     * Without this, adding a fourth colour to `ic_launcher_colors.xml` would leave it unguarded while
     * the file's comment still promised otherwise — the exact failure this class was written to end.
     */
    @Test
    fun `no colour literal in these resources is left unasserted`() {
        assertEquals(
            "a colour was added to ic_launcher_colors.xml without an assertion above",
            setOf("ic_launcher_background", "ic_launcher_leaf", "ic_launcher_mark"),
            colourResources("ic_launcher_colors.xml").keys,
        )
        assertEquals(
            "a colour item was added to themes.xml without an assertion above",
            setOf("android:windowBackground", "android:statusBarColor"),
            themeItems().keys,
        )
    }

    private companion object {
        /** `<color name="x">#rrggbb</color>` pairs from a values file. */
        val COLOUR_RE = Regex("""<color\s+name="([^"]+)"\s*>\s*(#[0-9a-fA-F]{6,8})\s*</color>""")

        /** `<item name="x">#rrggbb</item>` pairs — only the ones whose value is a colour. */
        val ITEM_RE = Regex("""<item\s+name="([^"]+)"\s*>\s*(#[0-9a-fA-F]{6,8})\s*</item>""")

        fun valuesFile(name: String): File {
            // Unit tests run with the module directory as the working directory.
            val file = File("src/main/res/values/$name")
            assertTrue("$name not found at ${file.absolutePath}", file.isFile)
            return file
        }

        fun colourResources(name: String): Map<String, Color> =
            COLOUR_RE.findAll(valuesFile(name).readText())
                .associate { it.groupValues[1] to parseHex(it.groupValues[2]) }

        fun themeItems(): Map<String, Color> =
            ITEM_RE.findAll(valuesFile("themes.xml").readText())
                .associate { it.groupValues[1] to parseHex(it.groupValues[2]) }

        /** `#rrggbb` and `#aarrggbb` both accepted; the former is opaque, matching Android. */
        fun parseHex(hex: String): Color {
            val digits = hex.removePrefix("#")
            val argb = when (digits.length) {
                6 -> 0xFF000000UL or digits.toULong(16)
                8 -> digits.toULong(16)
                else -> error("unexpected colour literal $hex")
            }
            return Color(argb.toLong())
        }
    }
}
