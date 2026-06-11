package io.krypton.doubleratchet

import io.krypton.doubleratchet.api.DoubleRatchet
import kotlin.test.Test
import kotlin.test.assertTrue

class DoubleRatchetTest {

    private val ratchet = DoubleRatchet()

    // The standalone DoubleRatchet is intentionally not implemented — the real
    // ratchet runs inside libsignal via KryptonProtocol. These ops must fail
    // loudly rather than return fake key material.

    @Test
    fun `symmetric ratchet is not implemented and fails loudly`() {
        val result = ratchet.symmetricRatchet(ByteArray(32) { 7 })
        assertTrue(result.isFailure, "symmetricRatchet must not return a fake key")
    }

    @Test
    fun `dh ratchet is not implemented and fails loudly`() {
        val result = ratchet.dhRatchet(
            rootKey = ByteArray(32) { 1 },
            ourPrivate = io.krypton.core.types.PrivateKey(ByteArray(32) { 2 }),
            theirPublic = io.krypton.core.types.PublicKey(ByteArray(32) { 3 }),
        )
        assertTrue(result.isFailure, "dhRatchet must not leak the private key as a chain key")
    }
}
