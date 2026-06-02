package io.krypton.core

import io.krypton.core.encoding.Base64
import io.krypton.core.encoding.Hex
import io.krypton.core.encoding.Hex.hexToBytes
import io.krypton.core.encoding.Hex.toHex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EncodingTest {

    @Test
    fun `base64 roundtrip`() {
        val data = "Hello, Krypton! 🚀".encodeToByteArray()
        val encoded = Base64.encode(data)
        val decoded = Base64.decode(encoded)
        assertContentEquals(data, decoded)
    }

    @Test
    fun `base64 url safe roundtrip`() {
        val data = ByteArray(256) { it.toByte() }
        val encoded = Base64.encodeUrlSafe(data)
        val decoded = Base64.decodeUrlSafe(encoded)
        assertContentEquals(data, decoded)
    }

    @Test
    fun `hex roundtrip`() {
        val data = byteArrayOf(0, 0xFF.toByte(), 0xAB.toByte(), 0x12.toByte())
        val hex = Hex.encode(data)
        assertEquals("00ffab12", hex)
        assertContentEquals(data, Hex.decode(hex))
    }

    @Test
    fun `hex empty`() {
        assertEquals("", Hex.encode(ByteArray(0)))
        assertContentEquals(ByteArray(0), Hex.decode(""))
    }

    @Test
    fun `convenience extensions`() {
        val data = byteArrayOf(1, 2, 3)
        assertEquals("010203", data.toHex())
        assertContentEquals(data, "010203".hexToBytes())
    }
}
