// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loads the shared behavioural corpus vendored from pocket-nutrition-contracts.
 *
 * The corpus is the contract between the three clients: the sandbox, this app and iOS all assert the
 * same `input` / `expected` pairs, so behaviour cannot drift without a test going red somewhere.
 *
 * WHY THE TREE API RATHER THAN TYPED DECODING
 * The vectors are heterogeneous by design — `expected` is a number in one suite and a nested object in
 * another — so a typed decoder would need a sealed hierarchy per suite before a single assertion could
 * run. That work belongs with porting the functions themselves.
 *
 * WHY NOT org.json
 * It was the first choice, on the reasoning that it ships with the Android SDK and costs no dependency.
 * That is wrong for unit tests: local tests compile against a *stubbed* android.jar whose every method
 * throws "not mocked", so the whole suite failed at the first getJSONArray. kotlinx.serialization is
 * already a dependency of this module and is real code on the JVM.
 */
object VectorCorpus {

    /** One case, carrying the explanation the upstream author wrote for whoever ports it. */
    data class Case(val id: String, val why: String, val input: JsonObject, val expected: JsonElement)

    data class Suite(val name: String, val description: String, val cases: List<Case>)

    /** Suite name -> declared case count, from index.json. */
    data class Index(val suites: Map<String, Int>)

    private val json = Json { ignoreUnknownKeys = true }

    private fun read(resource: String): JsonObject {
        val stream = requireNotNull(VectorCorpus::class.java.getResourceAsStream(resource)) {
            "$resource is not on the test classpath. The corpus is vendored — run " +
                "./scripts/sync-contracts.sh --update."
        }
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        return json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.string(key: String): String =
        requireNotNull(this[key]) { "missing '$key'" }.jsonPrimitive.content

    fun load(suite: String): Suite {
        val root = read("/vectors/$suite.json")
        return Suite(
            name = root.string("suite"),
            description = root.string("description"),
            cases = requireNotNull(root["cases"]) { "$suite.json has no cases array" }
                .jsonArray
                .map { element ->
                    val case = element.jsonObject
                    Case(
                        id = case.string("id"),
                        why = case.string("why"),
                        input = requireNotNull(case["input"]) { "case has no input" }.jsonObject,
                        expected = requireNotNull(case["expected"]) { "case has no expected" },
                    )
                },
        )
    }

    /** What index.json declares. A consumer should iterate this rather than a hand-written list. */
    fun index(): Index {
        val suites = requireNotNull(read("/vectors/index.json")["suites"]) {
            "index.json has no suites array"
        }.jsonArray
        return Index(
            suites.associate { element ->
                val entry = element.jsonObject
                entry.string("suite") to requireNotNull(entry["cases"]).jsonPrimitive.int
            },
        )
    }
}
