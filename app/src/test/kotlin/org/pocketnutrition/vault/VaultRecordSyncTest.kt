// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.model.MealRecordItem
import org.pocketnutrition.model.MealRecordPayload
import org.pocketnutrition.model.MealRecordTotals
import org.pocketnutrition.model.ProfileRecordPayload
import org.pocketnutrition.model.ProfileVersionPayload
import org.pocketnutrition.model.TargetsRecordPayload
import org.pocketnutrition.model.TargetsVaultRecord
import org.pocketnutrition.model.WeightRecordPayload

/**
 * The two-way sync against a fake server: round trips, last-writer-wins, deletions, records from a newer
 * app version, and lost write races. Ports the cases of iOS `JournalSyncTests` and the targets sync rules of
 * spec 05 §3.
 */
class VaultRecordSyncTest {

    private val server = FakeVaultServer(pageSize = 2)
    private val keys = testCrypto.createAccount("correct horse battery staple").keys
    private val session: VaultStoredSession = runBlocking {
        val reply = server.register(VaultRegisterRequest("alice", testCrypto.newKdf(), "aGFzaA==", "{}", "test"))
        VaultStoredSession.of("https://v.example", "alice", reply.accountId, reply.apiKey, reply.keyId, testCrypto.newKdf(), keys)
    }
    private val account get() = session.accountId

    private fun meal(id: String, updatedAt: Double, kcal: Double = 500.0) = MealRecordPayload(
        id = id, savedAt = 1_762_400_000.0, updatedAt = updatedAt,
        items = listOf(MealRecordItem("0b5f2b7e-1f3a-4f7b-9c1e-3a2b1c0d9e8f", "rice", "Rice", 150.0, "boiled", "cooked")),
        totals = MealRecordTotals(kcal, 10.0, 80.0, 2.0), slot = "lunch",
    )

    private val idA = "11111111-1111-4111-8111-111111111111"
    private val idB = "22222222-2222-4222-8222-222222222222"
    private val idC = "33333333-3333-4333-8333-333333333333"

    private fun meals() = VaultRecordSync(session, server, MealRecordCodec, testCrypto)
    private fun targets() = VaultRecordSync(session, server, TargetsRecordCodec, testCrypto)

    /** Runs one pass and folds it into [state] as the coordinator does, with the local list unchanged. */
    private fun <P> pass(sync: VaultRecordSync<P>, codec: VaultRecordCodec<P>, local: List<P>, state: VaultSyncState): Pair<VaultSyncResult<P>, VaultSyncState> {
        val result = runBlocking { sync.run(local, state) }
        val edited = local.associate { codec.recordId(it) to codec.updatedAt(it) }
        val applicable = result.applicableUpserts(edited, codec)
        return result to state.merging(result, applicable, edited, codec)
    }

    private fun fresh() = VaultSyncState(account)

    // ── Meals ────────────────────────────────────────────────────────────────

    @Test
    fun `a first sync uploads every meal, encrypted and bound to its record`() {
        val local = listOf(meal(idA, 100.0), meal(idB, 100.0), meal(idC, 100.0))
        val (result, state) = pass(meals(), MealRecordCodec, local, fresh())
        assertNull(result.failure)
        assertEquals(setOf(idA, idB, idC), result.pushed.keys)
        assertEquals(mapOf(idA to 1, idB to 1, idC to 1), state.revisions)
        val stored = server.accounts.getValue("alice").records.getValue(idA)
        assertEquals("meal", stored.type)
        assertEquals("user", stored.keyId)
        assertFalse("ciphertext must not contain the plaintext", stored.ciphertext.contains("Rice"))
        val plaintext = checkNotNull(server.plaintextOf(account, keys.userKey, idA))
        assertTrue(plaintext.contains("\"v\":2") && plaintext.contains("\"label\":\"Rice\""))
        // A second pass has nothing to send.
        val before = server.putCount
        pass(meals(), MealRecordCodec, local, state)
        assertEquals(before, server.putCount)
    }

    @Test
    fun `a meal from another device comes down, across several pages`() {
        listOf(idA, idB, idC).forEach { server.writeAsOtherDevice(account, keys.userKey, it, "meal", MealRecordCodec.encode(meal(it, 200.0))) }
        val (result, state) = pass(meals(), MealRecordCodec, emptyList(), fresh())
        assertEquals(setOf(idA, idB, idC), result.upserts.map { it.id }.toSet())
        assertEquals(3L, state.cursor)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun `the later edit wins, whichever side made it`() {
        val (_, synced) = pass(meals(), MealRecordCodec, listOf(meal(idA, 100.0, kcal = 500.0)), fresh())
        // Newer remote edit replaces an untouched local copy.
        server.writeAsOtherDevice(account, keys.userKey, idA, "meal", MealRecordCodec.encode(meal(idA, 300.0, kcal = 700.0)))
        val (newer, s2) = pass(meals(), MealRecordCodec, listOf(meal(idA, 100.0, kcal = 500.0)), synced)
        assertEquals(700.0, newer.upserts.single().totals.energyKcal, 0.0)

        // A local edit newer than the remote one goes up instead of being replaced.
        server.writeAsOtherDevice(account, keys.userKey, idA, "meal", MealRecordCodec.encode(meal(idA, 400.0, kcal = 800.0)))
        val localEdit = meal(idA, 500.0, kcal = 900.0)
        val (mine, _) = pass(meals(), MealRecordCodec, listOf(localEdit), s2.markChanged(idA))
        assertTrue(mine.upserts.isEmpty())
        assertEquals(500.0, mine.pushed.getValue(idA), 0.0)
        assertTrue(checkNotNull(server.plaintextOf(account, keys.userKey, idA)).contains("900"))
    }

    @Test
    fun `a write race is retried after pulling, not lost`() {
        val (_, synced) = pass(meals(), MealRecordCodec, listOf(meal(idA, 100.0)), fresh())
        var raced = false
        server.beforeWrite = { id ->
            if (!raced && id == idA) {
                raced = true
                server.writeAsOtherDevice(account, keys.userKey, idA, "meal", MealRecordCodec.encode(meal(idA, 150.0, kcal = 600.0)))
            }
        }
        val (result, state) = pass(meals(), MealRecordCodec, listOf(meal(idA, 200.0, kcal = 650.0)), synced.markChanged(idA))
        assertNull(result.failure)
        assertEquals(3, state.revisions[idA])
        assertTrue(checkNotNull(server.plaintextOf(account, keys.userKey, idA)).contains("650"))
    }

    @Test
    fun `a deletion here reaches the server, and one from elsewhere removes the local copy`() {
        val (_, synced) = pass(meals(), MealRecordCodec, listOf(meal(idA, 100.0), meal(idB, 100.0)), fresh())
        val (result, state) = pass(meals(), MealRecordCodec, listOf(meal(idB, 100.0)), synced.markDeleted(idA))
        assertEquals(setOf(idA), result.deletionsDelivered)
        assertTrue(state.deleted.isEmpty())
        assertTrue(server.accounts.getValue("alice").records.getValue(idA).deleted)

        server.deleteAsOtherDevice(account, idB)
        val (remote, _) = pass(meals(), MealRecordCodec, listOf(meal(idB, 100.0)), state)
        // Our own earlier tombstone for A comes back in the feed too; removing an absent meal is a no-op.
        assertTrue(idB in remote.deletes)
    }

    @Test
    fun `a deletion elsewhere does not win over an edit made here`() {
        val (_, synced) = pass(meals(), MealRecordCodec, listOf(meal(idA, 100.0)), fresh())
        server.deleteAsOtherDevice(account, idA)
        val (result, _) = pass(meals(), MealRecordCodec, listOf(meal(idA, 300.0)), synced.markChanged(idA))
        assertFalse(idA in result.deletes)
        assertEquals(3, result.revisions[idA])
        assertTrue(checkNotNull(server.plaintextOf(account, keys.userKey, idA)).isNotEmpty())
    }

    @Test
    fun `a meal never uploaded needs no deletion`() {
        val state = fresh().markDeleted(idA)
        assertTrue(state.deleted.isEmpty())
    }

    @Test
    fun `a record from a newer app version is never overwritten, even in a later run`() {
        val future = """{"v":3,"id":"$idA","saved_at":1,"updated_at":1,"items":[],"totals":{"energy_kcal":0,"protein_g":0,"carbohydrates_g":0,"fat_g":0}}"""
        server.writeAsOtherDevice(account, keys.userKey, idA, "meal", future)
        val local = listOf(meal(idA, 999.0))
        val (first, s1) = pass(meals(), MealRecordCodec, local, fresh().markChanged(idA))
        assertEquals(setOf(idA), first.unsupported)
        assertTrue(first.upserts.isEmpty())
        // The cursor is past it now; an edit here must still not overwrite it, nor a deletion remove it.
        val (second, s2) = pass(meals(), MealRecordCodec, local, s1.markChanged(idA))
        assertTrue(second.pushed.isEmpty())
        pass(meals(), MealRecordCodec, emptyList(), s2.markDeleted(idA))
        val stored = server.accounts.getValue("alice").records.getValue(idA)
        assertEquals(1, stored.revision)
        assertFalse(stored.deleted)
    }

    @Test
    fun `records that do not open or name another record are skipped`() {
        // Encrypted under another key.
        val stranger = testCrypto.newKey()
        server.writeAsOtherDevice(account, stranger, idA, "meal", MealRecordCodec.encode(meal(idA, 1.0)))
        // Opens, but its payload claims to be another meal: a swapped record.
        server.writeAsOtherDevice(account, keys.userKey, idB, "meal", MealRecordCodec.encode(meal(idC, 1.0)))
        // Another type entirely: not this sync's business.
        server.writeAsOtherDevice(account, keys.userKey, "capture-1", "capture", "{}")
        val (result, _) = pass(meals(), MealRecordCodec, emptyList(), fresh())
        assertTrue(result.upserts.isEmpty())
        assertTrue(result.unsupported.isEmpty())
    }

    @Test
    fun `failures are reported without losing what was done`() {
        server.offline = true
        assertEquals(VaultSyncFailure.OFFLINE, runBlocking { meals().run(listOf(meal(idA, 1.0)), fresh()) }.failure)
        server.offline = false
        server.keys.clear()
        assertEquals(VaultSyncFailure.UNAUTHORIZED, runBlocking { meals().run(listOf(meal(idA, 1.0)), fresh()) }.failure)
    }

    @Test
    fun `an edit made while the sync ran stays dirty`() {
        val state = fresh().markChanged(idA)
        val result = runBlocking { meals().run(listOf(meal(idA, 100.0)), state) }
        // The person edited the meal again (updated_at 150) before the result came back.
        val merged = state.merging(result, emptyList(), mapOf(idA to 150.0), MealRecordCodec)
        assertTrue(idA in merged.dirty)
        val clean = state.merging(result, emptyList(), mapOf(idA to 100.0), MealRecordCodec)
        assertFalse(idA in clean.dirty)
    }

    // ── Daily targets: profile, targets, weight-YYYY-MM-DD ───────────────────

    private val profile = TargetsVaultRecord.Profile(
        ProfileRecordPayload(
            updatedAt = 1_762_400_000,
            versions = listOf(ProfileVersionPayload("2026-01-01", 1990, "female", "none", pal = 1.6, weightKg = 62.0)),
        ),
    )
    private val goals = TargetsVaultRecord.Targets(TargetsRecordPayload(updatedAt = 1_762_400_000))
    private fun weight(date: String, kg: Double, at: Long = 1_762_400_000) = TargetsVaultRecord.Weight(WeightRecordPayload(updatedAt = at, date = date, kg = kg))

    @Test
    fun `the three record kinds round-trip under their fixed ids and types`() {
        val local = listOf(profile, goals, weight("2026-11-05", 62.4), weight("2026-11-06", 62.1))
        val (result, state) = pass(targets(), TargetsRecordCodec, local, fresh())
        assertNull(result.failure)
        val stored = server.accounts.getValue("alice").records
        assertEquals("profile", stored.getValue("profile").type)
        assertEquals("targets", stored.getValue("targets").type)
        assertEquals("weight", stored.getValue("weight-2026-11-06").type)
        // Another phone reads them back exactly.
        val (other, _) = pass(targets(), TargetsRecordCodec, emptyList(), fresh())
        assertEquals(local.toSet(), other.upserts.toSet())
        assertEquals(4, state.revisions.size)
    }

    @Test
    fun `deleting a weight entry deletes its record`() {
        val local = listOf(weight("2026-11-05", 62.4), weight("2026-11-06", 62.1))
        val (_, synced) = pass(targets(), TargetsRecordCodec, local, fresh())
        val (result, _) = pass(targets(), TargetsRecordCodec, local.drop(1), synced.markDeleted("weight-2026-11-05"))
        assertEquals(setOf("weight-2026-11-05"), result.deletionsDelivered)
        assertTrue(server.accounts.getValue("alice").records.getValue("weight-2026-11-05").deleted)
        // And the other phone removes it.
        val (other, _) = pass(targets(), TargetsRecordCodec, emptyList(), fresh())
        assertEquals(listOf(weight("2026-11-06", 62.1)), other.upserts)
    }

    @Test
    fun `targets records follow last writer wins on updated_at`() {
        val (_, synced) = pass(targets(), TargetsRecordCodec, listOf(weight("2026-11-06", 62.0, at = 100)), fresh())
        server.writeAsOtherDevice(account, keys.userKey, "weight-2026-11-06", "weight", TargetsVaultRecord.Weight(WeightRecordPayload(updatedAt = 200, date = "2026-11-06", kg = 61.5)).encoded())
        val (newer, _) = pass(targets(), TargetsRecordCodec, listOf(weight("2026-11-06", 62.0, at = 100)), synced)
        assertEquals(listOf(weight("2026-11-06", 61.5, at = 200)), newer.upserts)
    }

    @Test
    fun `a weight whose date is not its id, or a profile under the wrong id, is refused`() {
        server.writeAsOtherDevice(account, keys.userKey, "weight-2026-11-06", "weight", weight("2026-11-07", 60.0).encoded())
        server.writeAsOtherDevice(account, keys.userKey, "targets", "profile", profile.encoded())
        val (result, _) = pass(targets(), TargetsRecordCodec, emptyList(), fresh())
        assertTrue(result.upserts.isEmpty())
    }

    @Test
    fun `a newer profile version is reported and left alone, unknown fields are ignored`() {
        server.writeAsOtherDevice(account, keys.userKey, "profile", "profile", """{"version":2,"updated_at":1,"versions":[]}""")
        server.writeAsOtherDevice(account, keys.userKey, "targets", "targets", """{"version":1,"updated_at":5,"overrides":[],"later":{"x":1}}""")
        val (result, _) = pass(targets(), TargetsRecordCodec, listOf(profile), fresh().markChanged("profile"))
        assertEquals(setOf("profile"), result.unsupported)
        assertTrue(result.pushed.isEmpty())
        assertEquals(listOf("targets"), result.upserts.map { it.recordId })
        assertTrue(checkNotNull(server.plaintextOf(account, keys.userKey, "profile")).contains("\"version\":2"))
    }
}
