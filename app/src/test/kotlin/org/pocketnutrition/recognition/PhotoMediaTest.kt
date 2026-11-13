// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMediaTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a jpg file uploads as a photo, anything else as a voice clip`() {
        assertEquals("image/jpeg", mediaFor("abc.jpg").contentType)
        assertEquals("meal.jpg", mediaFor("abc.JPG").fileName)
        assertEquals("audio/mp4", mediaFor("abc.m4a").contentType)
        assertEquals("audio/mp4", mediaFor("no-extension").contentType)
    }

    @Test
    fun `a photo entry is marked as such and the flag survives a round trip`() {
        val photo = Entry.pendingUpload("p", 0, "x.jpg")
        assertTrue(photo.isPhoto)
        assertFalse(Entry.pendingUpload("v", 0, "x.m4a").isPhoto)
        assertTrue(json.decodeFromString(Entry.serializer(), json.encodeToString(Entry.serializer(), photo)).isPhoto)
    }

    @Test
    fun `entries written before photos existed decode as voice`() {
        val old = """{"localId":"a","createdAtEpochMs":1,"audioFile":"a.m4a","state":"PENDING_UPLOAD"}"""
        assertFalse(json.decodeFromString(Entry.serializer(), old).isPhoto)
    }

    @Test
    fun `photos are scaled down to the longest side, never up`() {
        assertEquals(1024 to 768, photoTargetSize(4032, 3024))
        assertEquals(768 to 1024, photoTargetSize(3024, 4032))
        assertEquals(640 to 480, photoTargetSize(640, 480))
        assertNull(photoTargetSize(0, 10))
    }

    @Test
    fun `decode subsampling keeps enough pixels for the target`() {
        assertEquals(2, photoSampleSize(4032, 3024))
        assertEquals(1, photoSampleSize(1500, 1000))
        assertEquals(4, photoSampleSize(4096, 3072))
    }
}
