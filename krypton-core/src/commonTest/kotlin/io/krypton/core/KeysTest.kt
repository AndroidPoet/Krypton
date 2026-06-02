package io.krypton.core

import io.krypton.core.types.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeysTest {

    @Test
    fun `public key requires 32 bytes`() {
        assertFailsWith<IllegalArgumentException> { PublicKey(ByteArray(31)) }
        assertFailsWith<IllegalArgumentException> { PublicKey(ByteArray(33)) }
        PublicKey(ByteArray(32)) // should not throw
    }

    @Test
    fun `public key equality is content-based`() {
        val a = PublicKey(ByteArray(32) { 1 })
        val b = PublicKey(ByteArray(32) { 1 })
        val c = PublicKey(ByteArray(32) { 2 })
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(false, a == c)
    }

    @Test
    fun `registration id range`() {
        assertFailsWith<IllegalArgumentException> { RegistrationId(0) }
        assertFailsWith<IllegalArgumentException> { RegistrationId(0x4000) }
        RegistrationId(1)    // min
        RegistrationId(0x3FFF) // max
    }

    @Test
    fun `protocol address string representation`() {
        val addr = ProtocolAddress("alice", DeviceId.PRIMARY)
        assertEquals("alice.1", addr.toString())
    }

    @Test
    fun `device id primary constant`() {
        assertEquals(1, DeviceId.PRIMARY.value)
    }
}
