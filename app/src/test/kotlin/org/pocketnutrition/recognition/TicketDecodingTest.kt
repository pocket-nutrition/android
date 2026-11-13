// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketDecodingTest {

    private val doneSample = """
        {
          "id": "3f2c9a6e-0000-4000-8000-000000000001",
          "status": "done",
          "created_at": 1791716110.95,
          "updated_at": 1791716113.95,
          "expires_at": 1791719710.95,
          "future_field": {"anything": true},
          "result": {
            "transcript": "deux oeufs au plat, ...",
            "language": "fr",
            "items": [
              {
                "raw_text": "deux oeufs au plat",
                "quantity": 2,
                "unit": "piece",
                "cooking_method": "fried",
                "candidates": [
                  { "ingredient_id": "oeuf_poule_entier_cuit", "name": "Egg, whole, cooked", "score": 0.93 }
                ]
              },
              { "raw_text": "du sel", "quantity": null, "unit": null, "cooking_method": null, "candidates": [] }
            ]
          },
          "error": null
        }
    """.trimIndent()

    @Test
    fun `the sample done ticket decodes with all fields`() {
        val ticket = decodeTicket(doneSample)

        assertEquals("3f2c9a6e-0000-4000-8000-000000000001", ticket.id)
        assertEquals("done", ticket.status)
        assertEquals(1791716110.95, ticket.createdAt, 0.0)
        assertEquals(1791719710.95, ticket.expiresAt, 0.0)
        val result = ticket.result
        assertNotNull(result)
        assertEquals("fr", result!!.language)
        assertEquals(2, result.items.size)
        val egg = result.items[0]
        assertEquals(2.0, egg.quantity!!, 0.0)
        assertEquals("piece", egg.unit)
        assertEquals("fried", egg.cookingMethod)
        assertEquals("oeuf_poule_entier_cuit", egg.candidates.single().ingredientId)
        assertEquals(0.93, egg.candidates.single().score, 0.0)
        assertNull(result.items[1].quantity)
        assertTrue(result.items[1].candidates.isEmpty())
    }

    @Test
    fun `a queued ticket has a null result and a null error`() {
        val ticket = decodeTicket(
            """{"id":"a","status":"queued","created_at":1.5,"updated_at":2.5,"expires_at":3.5,"result":null,"error":null}""",
        )
        assertNull(ticket.result)
        assertNull(ticket.error)
    }

    @Test
    fun `a failed ticket carries the server error string as received`() {
        val ticket = decodeTicket(
            """{"id":"a","status":"failed","created_at":1,"updated_at":2,"expires_at":3,"error":"transcription failed: bad audio"}""",
        )
        assertEquals("transcription failed: bad audio", ticket.error)
        assertTrue(isTerminalStatus(ticket.status))
    }

    @Test
    fun `an unknown status decodes and counts as in progress`() {
        val ticket = decodeTicket(
            """{"id":"a","status":"summarising","created_at":1,"updated_at":2,"expires_at":3}""",
        )
        assertEquals("summarising", ticket.status)
        assertFalse(isTerminalStatus(ticket.status))
    }

    @Test
    fun `terminal classification covers done and failed only`() {
        listOf("done", "failed").forEach { assertTrue(it, isTerminalStatus(it)) }
        listOf("queued", "transcribing", "extracting", "", "whatever", "gone", "expired").forEach {
            assertFalse(it, isTerminalStatus(it))
        }
    }

    @Test
    fun `a malformed body is a decode failure not a crash elsewhere`() {
        assertNull(decodeTicketOrNull("not json"))
        assertNull(decodeTicketOrNull("""{"status":"done"}"""))
    }

    @Test
    fun `a ticket id outside the safe alphabet or length is rejected`() {
        fun body(id: String) = """{"id":"$id","status":"queued","created_at":1,"updated_at":2,"expires_at":3}"""
        assertNull(decodeTicketOrNull(body("../etc")))
        assertNull(decodeTicketOrNull(body("a b")))
        assertNull(decodeTicketOrNull(body("a?x=1")))
        assertNull(decodeTicketOrNull(body("")))
        assertNull(decodeTicketOrNull(body("a".repeat(65))))
        assertNotNull(decodeTicketOrNull(body("a".repeat(64))))
        assertNotNull(decodeTicketOrNull(body("Ab_-9")))
    }

    @Test
    fun `untrusted server data is capped after decoding`() {
        val items = (1..80).joinToString(",") {
            """{"raw_text":"${"x".repeat(900)}","candidates":[${(1..30).joinToString(",") { c -> """{"ingredient_id":"i$c","name":"${"n".repeat(500)}","score":0.1}""" }}]}"""
        }
        val raw = """{"id":"a","status":"done","created_at":1,"updated_at":2,"expires_at":3,
            "error":"${"e".repeat(1000)}","result":{"transcript":"${"t".repeat(20000)}","items":[$items]}}"""
        val ticket = decodeTicket(raw)
        assertEquals(200, ticket.error!!.length)
        val result = ticket.result!!
        assertEquals(8 * 1024, result.transcript.length)
        assertEquals(50, result.items.size)
        assertEquals(200, result.items[0].rawText.length)
        assertEquals(10, result.items[0].candidates.size)
        assertEquals(200, result.items[0].candidates[0].name.length)
    }

    @Test
    fun `bounded reads stop once the cap is exceeded`() {
        assertEquals(10, readCapped(java.io.ByteArrayInputStream(ByteArray(10)), 10).size)
        org.junit.Assert.assertThrows(ResponseTooLargeException::class.java) {
            readCapped(java.io.ByteArrayInputStream(ByteArray(11)), 10)
        }
    }
}
