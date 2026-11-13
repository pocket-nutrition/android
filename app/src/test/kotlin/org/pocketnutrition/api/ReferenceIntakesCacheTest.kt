// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.api

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.core.ReferenceRow

/** Spec 05 §1: refetch after 24 h, keep the cache on failure, never invent a table. */
class ReferenceIntakesCacheTest {

    private val row = ReferenceRow(
        nutrient = "Iron", nutrientKey = "ironMg", population = "Adults", populationDescription = "Adults",
        ageFrom = 18.0, ageTo = 999.0, ageUnit = "y", isMale = true, isFemale = false, valueKind = "pri",
        valueMin = 11.0, valueMax = 11.0, isIncrement = false, unit = "mg/day", sourceLink = "efsa/iron",
    )
    private val response = ReferenceIntakesResponse("EFSA", "https://example.org", listOf(row))

    private fun tempFile(): File = Files.createTempDirectory("ri").toFile().resolve("reference_intakes.json")

    private class Fetcher(var result: () -> ReferenceIntakesResponse) {
        var calls = 0
        suspend fun fetch(): ReferenceIntakesResponse {
            calls++
            return result()
        }
    }

    @Test
    fun `no cache and no connection gives no table`() = runBlocking {
        val fetcher = Fetcher { throw ApiException.Offline }
        val cache = ReferenceIntakesCache(tempFile(), clock = { 0L }, fetch = fetcher::fetch)
        cache.refreshIfStale()
        assertNull(cache.current)
        assertEquals(1, fetcher.calls)
    }

    @Test
    fun `a fetched table is written to disk and read back by a new instance`() = runBlocking {
        val file = tempFile()
        ReferenceIntakesCache(file, clock = { 1_000L }, fetch = Fetcher { response }::fetch).refreshIfStale()
        val reread = ReferenceIntakesCache(file, clock = { 2_000L }, fetch = Fetcher { error("must not fetch") }::fetch)
        assertNull("nothing is read on construction (main thread)", reread.current)
        reread.ensureLoaded()
        assertEquals(listOf(row), reread.current?.response?.values)
        assertEquals(1_000L, reread.current?.fetchedAtEpochMs)
    }

    @Test
    fun `a cache younger than 24 hours is not refetched, an older one is`() = runBlocking {
        val file = tempFile()
        var now = 0L
        val fetcher = Fetcher { response }
        val cache = ReferenceIntakesCache(file, clock = { now }, fetch = fetcher::fetch)
        cache.refreshIfStale()
        now = ReferenceIntakesCache.MAX_AGE_MS - 1
        cache.refreshIfStale()
        assertEquals(1, fetcher.calls)
        now = ReferenceIntakesCache.MAX_AGE_MS + 1
        cache.refreshIfStale()
        assertEquals(2, fetcher.calls)
        assertEquals(now, cache.current?.fetchedAtEpochMs)
    }

    @Test
    fun `a failed refresh keeps the stale cache`() = runBlocking {
        val file = tempFile()
        var now = 0L
        val fetcher = Fetcher { response }
        val cache = ReferenceIntakesCache(file, clock = { now }, fetch = fetcher::fetch)
        cache.refreshIfStale()
        fetcher.result = { throw ApiException.BadStatus(503) }
        now = ReferenceIntakesCache.MAX_AGE_MS * 3
        cache.refreshIfStale()
        assertEquals(listOf(row), cache.current?.response?.values)
        assertEquals(0L, cache.current?.fetchedAtEpochMs)
    }

    @Test
    fun `the response decodes from the API shape, ignoring fields the calculation does not read`() {
        val text = """{"source":"EFSA","sourceUrl":"u","values":[{"nutrient":"Iron","nutrientKey":"ironMg",
            "classification":"Minerals","population":"Adults","populationDescription":"Adults","ageFrom":18,
            "ageTo":999,"ageUnit":"y","ageLabel":"≥ 18 years","pal":null,"sex":"Male","valueKind":"pri","raw":"11",
            "valueMin":11,"valueMax":11,"unit":"mg/day","sourceLink":"efsa/iron","note":null,
            "isMale":true,"isFemale":false,"isIncrement":false}]}"""
        val decoded = ReferenceIntakesClient.decode(text)
        assertEquals(listOf(row), decoded.values)
        assertTrue(decoded.values.single().isMale)
    }
}
