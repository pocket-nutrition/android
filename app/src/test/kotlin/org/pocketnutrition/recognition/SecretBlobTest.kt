// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretBlobTest {

    @Test
    fun `pack then unpack returns the same iv and ciphertext`() {
        val iv = ByteArray(12) { it.toByte() }
        val ct = ByteArray(40) { (it * 3).toByte() }
        val unpacked = SecretBlob.unpack(SecretBlob.pack(iv, ct))!!
        assertArrayEquals(iv, unpacked.iv)
        assertArrayEquals(ct, unpacked.ciphertext)
    }

    @Test
    fun `garbage unpacks to null instead of throwing`() {
        listOf("", "nocolon", ":", "a:", ":b", "!!!:???", "AAAA:BBBB:CCCC", "1:AAAA").forEach {
            assertNull("'$it'", SecretBlob.unpack(it))
        }
    }

    @Test
    fun `an empty ciphertext is rejected`() {
        assertNull(SecretBlob.unpack(SecretBlob.pack(ByteArray(12), ByteArray(0))))
    }

    @Test
    fun `fake secret store behind the interface behaves like a store`() {
        val store: SecretStore = FakeSecretStore()
        assertNull(store.read())
        assertEquals(true, store.write("key"))
        assertEquals("key", store.read())
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun `only definitive decryption failures clear the stored secret`() {
        assertEquals(true, shouldClearSecretAfter(javax.crypto.AEADBadTagException()))
        assertEquals(true, shouldClearSecretAfter(java.security.UnrecoverableKeyException()))
        assertEquals(true, shouldClearSecretAfter(UnusableSecretException("x")))
        assertEquals(false, shouldClearSecretAfter(java.io.IOException()))
        assertEquals(false, shouldClearSecretAfter(RuntimeException()))
        assertEquals(false, shouldClearSecretAfter(java.security.KeyStoreException()))
    }

    private class FakeSecretStore : SecretStore {
        private var value: String? = null
        override fun read() = value
        override fun write(value: String): Boolean { this.value = value; return true }
        override fun clear() { value = null }
    }
}
