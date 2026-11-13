// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.core.CalculationBasis
import org.pocketnutrition.core.LactationPeriod
import org.pocketnutrition.core.ProfileVersion
import org.pocketnutrition.core.Situation
import org.pocketnutrition.core.TargetOverride
import org.pocketnutrition.core.sameJson

/** The `profile`, `targets` and `weight` vault payloads of spec 05 §3, against the spec's own examples. */
class TargetRecordsTest {

    private val json = Json

    private fun parse(text: String): JsonElement = json.parseToJsonElement(text)

    private val profileExample = """
        { "version": 1, "updated_at": 1793000000,
          "versions": [ { "effective_from": "2026-11-06", "birth_year": 1996, "basis": "female",
                          "situation": "pregnant", "trimester": 2, "lactation": null, "postmenopausal": false,
                          "pal": 1.6, "weight_kg": 65.0, "phytate_mg_per_day": null } ] }
    """.trimIndent()

    private val targetsExample = """
        { "version": 1, "updated_at": 1793000000,
          "overrides": [ { "effective_from": "2026-11-06",
                           "values": { "energyKcal": { "min": 2200, "max": 2200 }, "sugarsG": { "min": null, "max": 50 } } } ],
          "watch_list": { "preset": "standard", "keys": ["energyKcal", "proteinG", "carbohydratesG", "fatG", "fiberG", "sugarsG"] } }
    """.trimIndent()

    private val weightExample = """{ "version": 1, "updated_at": 1793000000, "date": "2026-11-06", "kg": 70.5 }"""

    private fun <T> ok(read: RecordRead<T>): T = (read as? RecordRead.Ok<T>)?.payload ?: error("expected Ok, got $read")

    @Test
    fun `the spec's profile example decodes and re-encodes to the same JSON`() {
        val payload = ok(TargetRecords.decodeProfile(profileExample))
        assertEquals(1_793_000_000L, payload.updatedAt)
        val version = payload.versions.single().toCore()
        assertEquals(
            ProfileVersion(
                effectiveFrom = "2026-11-06", birthYear = 1996, basis = CalculationBasis.FEMALE,
                situation = Situation.PREGNANT, trimester = 2, lactation = null, postmenopausal = false,
                pal = 1.6, weightKg = 65.0, phytateMgPerDay = null,
            ),
            version,
        )
        assertTrue(sameJson(parse(profileExample), parse(TargetRecords.encode(payload))))
    }

    @Test
    fun `the spec's targets example decodes and re-encodes to the same JSON`() {
        val payload = ok(TargetRecords.decodeTargets(targetsExample))
        val overrides = payload.overrides.single().toCore()
        assertEquals(TargetOverride(2200.0, 2200.0), overrides.values["energyKcal"])
        assertEquals(TargetOverride(null, 50.0), overrides.values["sugarsG"])
        assertEquals("standard", payload.watchList?.preset)
        assertTrue(sameJson(parse(targetsExample), parse(TargetRecords.encode(payload))))
    }

    @Test
    fun `the spec's weight example decodes and re-encodes to the same JSON`() {
        val payload = ok(TargetRecords.decodeWeight(weightExample, "weight-2026-11-06"))
        assertEquals(70.5, payload.kg, 0.0)
        assertTrue(sameJson(parse(weightExample), parse(TargetRecords.encode(payload))))
    }

    @Test
    fun `core values round-trip through the payloads`() {
        val version = ProfileVersion(
            "2026-11-07", 1980, CalculationBasis.AVERAGE, Situation.LACTATING, lactation = LactationPeriod.AFTER_6_MONTHS,
            postmenopausal = true, pal = 1.8, weightKg = 70.2, phytateMgPerDay = 900.0,
        )
        val payload = ProfileRecordPayload(updatedAt = 5, versions = listOf(ProfileVersionPayload.of(version)))
        assertEquals(version, ok(TargetRecords.decodeProfile(TargetRecords.encode(payload))).versions.single().toCore())
    }

    @Test
    fun `unknown fields are ignored`() {
        val withExtra = weightExample.replace("\"kg\"", "\"note\": \"x\", \"kg\"")
        assertEquals(70.5, ok(TargetRecords.decodeWeight(withExtra, "weight-2026-11-06")).kg, 0.0)
    }

    @Test
    fun `a newer version is reported as unsupported, never decoded`() {
        assertEquals(RecordRead.UnsupportedVersion<Any>(2) as Any, TargetRecords.decodeProfile(profileExample.replace("\"version\": 1", "\"version\": 2")))
        assertEquals(RecordRead.UnsupportedVersion<Any>(3) as Any, TargetRecords.decodeTargets(targetsExample.replace("\"version\": 1", "\"version\": 3")))
        assertEquals(RecordRead.UnsupportedVersion<Any>(9) as Any, TargetRecords.decodeWeight(weightExample.replace("\"version\": 1", "\"version\": 9"), "weight-2026-11-06"))
    }

    @Test
    fun `malformed records are rejected whole`() {
        assertEquals(RecordRead.Malformed, TargetRecords.decodeProfile("nope"))
        assertEquals(RecordRead.Malformed, TargetRecords.decodeProfile(profileExample.replace("\"female\"", "\"unknown\"")))
        assertEquals(RecordRead.Malformed, TargetRecords.decodeProfile(profileExample.replace("\"trimester\": 2", "\"trimester\": 4")))
        assertEquals(RecordRead.Malformed, TargetRecords.decodeProfile(profileExample.replace("\"version\": 1", "\"version\": 0")))
        assertEquals(RecordRead.Malformed, TargetRecords.decodeTargets(targetsExample.replace("2200, \"max\"", "\"a\", \"max\"")))
        // A weight whose date does not match its record id.
        assertEquals(RecordRead.Malformed, TargetRecords.decodeWeight(weightExample, "weight-2026-11-07"))
        assertEquals(RecordRead.Malformed, TargetRecords.decodeWeight(weightExample.replace("70.5", "-1"), "weight-2026-11-06"))
    }

    @Test
    fun `a fractional updated_at from another client is read, not rejected`() {
        val payload = ok(TargetRecords.decodeWeight(weightExample.replace("1793000000", "1793000000.75"), "weight-2026-11-06"))
        assertEquals(1_793_000_000L, payload.updatedAt)
    }

    @Test
    fun `record ids follow the vault rule`() {
        assertTrue(TargetRecords.isValidId(TargetRecords.PROFILE_ID))
        assertTrue(TargetRecords.isValidId(TargetRecords.TARGETS_ID))
        assertEquals("weight-2026-11-06", TargetRecords.weightId("2026-11-06"))
        assertTrue(TargetRecords.isValidId(TargetRecords.weightId("2026-11-06")))
        assertFalse(TargetRecords.isValidId(""))
        assertFalse(TargetRecords.isValidId("weight 2026"))
        assertFalse(TargetRecords.isValidId("x".repeat(65)))
    }

    @Test
    fun `last writer wins on updated_at, and a newer version is never overwritten`() {
        assertEquals(SyncDecision.TAKE_REMOTE, TargetRecords.decide(localUpdatedAt = null, remote = RecordRead.Ok(1L)) { it })
        assertEquals(SyncDecision.TAKE_REMOTE, TargetRecords.decide(localUpdatedAt = 10, remote = RecordRead.Ok(11L)) { it })
        assertEquals(SyncDecision.KEEP_LOCAL, TargetRecords.decide(localUpdatedAt = 10, remote = RecordRead.Ok(10L)) { it })
        assertEquals(SyncDecision.KEEP_LOCAL, TargetRecords.decide(localUpdatedAt = 10, remote = RecordRead.Ok(9L)) { it })
        assertEquals(SyncDecision.UNSUPPORTED_VERSION, TargetRecords.decide(localUpdatedAt = 99, remote = RecordRead.UnsupportedVersion<Long>(2)) { it })
        assertEquals(SyncDecision.KEEP_LOCAL, TargetRecords.decide(localUpdatedAt = 10, remote = RecordRead.Malformed) { it: Long -> it })
    }
}
