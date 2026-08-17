// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards the about screen's two claims that go stale by themselves.
 *
 * WHY THIS EXISTS
 * A licences list is the classic write-once artefact: it is correct the day it ships and silently
 * wrong the first time someone adds a dependency, because nothing about adding one prompts you to
 * open the about screen. Apache-2.0 §4(d) only obliges us to carry attribution for what we actually
 * ship — so the failure mode is not a missing legal notice for a library we dropped, it is a missing
 * one for a library we added.
 *
 * The catalogue is therefore read as a file and compared against the rendered list. Adding a
 * dependency group nobody has mapped fails here, naming the group, rather than shipping an
 * attribution list that quietly omits it.
 *
 * The second claim is subtler: [Fr.ABOUT_PRIVACY_SUMMARY] on its own reads as "nothing ever leaves
 * the phone", which is false — the app searches and analyses over the network. It is only honest
 * beside [Fr.ABOUT_PRIVACY_NETWORK]. A well-meaning edit that trims the screen to one line would
 * turn a true summary into a false privacy claim, so the pairing is asserted rather than trusted.
 */
class AboutScreenTest {

    /**
     * Gradle runs unit tests with the module directory as the working directory, which is what makes
     * reading the catalogue possible at all — the same route `ThemeResourceTest` takes to `res/`.
     */
    private val catalogue = File("../gradle/libs.versions.toml")

    /**
     * Group prefix → the word that must appear in the rendered attribution for it to count.
     *
     * Deliberately keyed on prefix rather than exact group: `androidx.compose.ui` and
     * `androidx.compose.material3` are one entry to a reader and should not become two lines in the
     * UI just because Gradle splits them.
     */
    private val attributionFor = mapOf(
        "androidx." to "AndroidX",
        "org.jetbrains.kotlinx" to "kotlinx.serialization",
        "io.coil-kt" to "Coil",
        "com.google.mlkit" to "ML Kit",
    )

    /**
     * Groups that exist in the catalogue but are not in the shipped APK, so they carry no attribution
     * obligation: JUnit is `testImplementation` and the Compose tooling inspector is
     * `debugImplementation`.
     */
    private val notShipped = setOf("junit")

    @Test
    fun `every dependency group in the version catalogue is attributed`() {
        val groups = declaredGroups()
        assertTrue("no [libraries] groups parsed — has the catalogue format changed?", groups.size > 3)

        val rendered = THIRD_PARTY_COMPONENTS.joinToString(" ")
        val unmapped = mutableListOf<String>()

        groups.filterNot { it in notShipped }.forEach { group ->
            val expected = attributionFor.entries.firstOrNull { group.startsWith(it.key) }?.value
            if (expected == null) {
                unmapped += group
            } else {
                assertTrue(
                    "dependency group '$group' ships in the APK but '$expected' does not appear in " +
                        "AboutScreen.THIRD_PARTY_COMPONENTS",
                    rendered.contains(expected),
                )
            }
        }

        if (unmapped.isNotEmpty()) {
            fail(
                "new dependency group(s) with no attribution decision: ${unmapped.sorted()}. " +
                    "Add a line to AboutScreen.THIRD_PARTY_COMPONENTS with the licence, or add the " +
                    "group to `notShipped` if it is test- or debug-only. Do not delete this test: " +
                    "Apache-2.0 §4(d) attribution covers what the binary actually ships.",
            )
        }
    }

    @Test
    fun `every attributed component names its licence`() {
        THIRD_PARTY_COMPONENTS.forEach { line ->
            assertTrue(
                "attribution line carries no licence name: '$line'",
                line.contains("License") || line.contains("Licence"),
            )
        }
    }

    @Test
    fun `the privacy summary is never shown without the network sentence`() {
        val screen = File("src/main/kotlin/org/pocketnutrition/ui/AboutScreen.kt").readText()

        assertTrue(
            "AboutScreen renders ABOUT_PRIVACY_SUMMARY but not ABOUT_PRIVACY_NETWORK. Alone, the " +
                "summary reads as 'nothing ever leaves the phone', which is false: searching and " +
                "analysing both send requests. Restore the pair.",
            !screen.contains("ABOUT_PRIVACY_SUMMARY") || screen.contains("ABOUT_PRIVACY_NETWORK"),
        )
    }

    /** Reads the `group = "…"` of every entry under `[libraries]`. */
    private fun declaredGroups(): List<String> {
        var inLibraries = false
        return catalogue.readLines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.startsWith("[")) {
                inLibraries = line == "[libraries]"
                return@mapNotNull null
            }
            if (!inLibraries) return@mapNotNull null
            GROUP_RE.find(line)?.groupValues?.get(1)
        }
    }

    private companion object {
        val GROUP_RE = Regex("""group\s*=\s*"([^"]+)"""")
    }
}
