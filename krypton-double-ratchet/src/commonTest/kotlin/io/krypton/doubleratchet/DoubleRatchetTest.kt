package io.krypton.doubleratchet

import io.krypton.doubleratchet.api.DoubleRatchet
import io.krypton.doubleratchet.api.RatchetOutput
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DoubleRatchetTest {

    private val ratchet = DoubleRatchet()

    @Test
    fun `symmetric ratchet produces output`() {
        val key = ByteArray(32) { 7 }
        val result = ratchet.symmetricRatchet(key)
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `ratchet output structure`() {
        val rootKey = ByteArray(32) { 1 }
        val ourPrivate = io.krypton.core.types.PrivateKey(ByteArray(32) { 2 })
        val theirPublic = io.krypton.core.types.PublicKey(ByteArray(32) { 3 })

        val result = ratchet.dhRatchet(rootKey, ourPrivate, theirPublic)
        assertTrue(result.isSuccess)
        val output = result.getOrNull()
        assertNotNull(output)
    }
}
