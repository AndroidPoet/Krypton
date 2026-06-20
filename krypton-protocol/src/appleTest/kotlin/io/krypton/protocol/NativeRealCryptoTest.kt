package io.krypton.protocol

import io.krypton.core.types.DeviceId
import io.krypton.core.types.ProtocolAddress
import io.krypton.protocol.api.KryptonProtocol
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.protocol.api.decrypt
import io.krypton.protocol.api.encrypt
import io.krypton.storage.memory.InMemoryPreKeyStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real end-to-end crypto on Apple platforms — exercises the actual [NativeBridge]
 * against libsignal_ffi 0.86.5 (no fakes). Runs on every Apple target via `appleTest`.
 *
 * Both the **send path** (real Kyber pre-key + X3DH + encrypt) and the full
 * **receive path** (PreKey decrypt, full round-trip) are verified here against
 * real libsignal — matching the JVM/Android tracks.
 */
class NativeRealCryptoTest {

    private fun protocolWithPreKeys(): Pair<KryptonProtocol, InMemoryPreKeyStore> {
        val preKeyStore = InMemoryPreKeyStore()
        val protocol = KryptonConfigurator().apply {
            this.preKeyStore = preKeyStore
        }.build()

        val spk = protocol.generateSignedPreKey(1).getOrThrow()
        runBlocking { preKeyStore.saveSignedPreKey(1, spk).getOrThrow() }
        val preKeys = protocol.generatePreKeys(1, 5).getOrThrow()
        runBlocking { for (pk in preKeys) preKeyStore.storePreKey(pk.keyId, pk).getOrThrow() }

        return protocol to preKeyStore
    }

    /**
     * Proves the fix for the zeroed-Kyber placeholder bug: a real bundle now
     * carries a genuine ~1568-byte Kyber public key, Alice **verifies its identity
     * signature** while running X3DH (`processPreKeyBundle` succeeds), and `encrypt`
     * produces a real PreKey ciphertext — all on real libsignal_ffi.
     */
    @Test
    fun `real native Kyber bundle and X3DH and encrypt succeed on libsignal_ffi`() = runBlocking {
        val (alice, _) = protocolWithPreKeys()
        val (bob, _) = protocolWithPreKeys()
        val bobAddr = ProtocolAddress("bob", DeviceId.PRIMARY)

        // Real Kyber pre-key — was a 32-byte zeroed placeholder before the fix.
        val bobBundle = bob.getPreKeyBundle(bobAddr).getOrThrow()
        assertTrue(bobBundle.kyberPreKeyPublic.size > 1000,
            "Kyber public key must be real (~1568 bytes), got ${bobBundle.kyberPreKeyPublic.size}")
        assertTrue(bobBundle.kyberPreKeySignature.isNotEmpty(), "Kyber key must be signed")

        // Alice runs X3DH against Bob's bundle — this verifies the real Kyber
        // signature against Bob's identity key. Succeeding proves the key + sig are valid.
        val processResult = alice.processPreKeyBundle(bobBundle)
        assertTrue(processResult.isSuccess,
            "X3DH with real Kyber key should succeed: ${processResult.errorOrNull()}")

        // Encrypt produces a real PreKey ciphertext on the wire.
        val wire = alice.encrypt("bob", "hello").getOrThrow()
        assertTrue(wire.isNotEmpty(), "Encrypt should produce a wire payload")
    }

    /**
     * Full native round-trip: Alice runs X3DH against Bob's bundle, encrypts a
     * PreKey message, and Bob decrypts it back to plaintext — all on real
     * libsignal_ffi 0.86.5. (Previously failed with InvalidMessage/error 30 when
     * the bridge was built against a mismatched newer-API libsignal binary.)
     */
    @Test
    fun `real native full encrypt-decrypt round-trip`() = runBlocking {
        val (alice, _) = protocolWithPreKeys()
        val (bob, _) = protocolWithPreKeys()
        val bobAddr = ProtocolAddress("bob", DeviceId.PRIMARY)

        alice.processPreKeyBundle(bob.getPreKeyBundle(bobAddr).getOrThrow()).getOrThrow()
        val wire = alice.encrypt("bob", "Hello over real native crypto").getOrThrow()
        val text = bob.decrypt("alice", wire).getOrThrow()
        assertEquals("Hello over real native crypto", text)
    }
}
