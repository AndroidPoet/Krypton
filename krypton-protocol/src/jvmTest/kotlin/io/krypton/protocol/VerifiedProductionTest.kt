package io.krypton.protocol

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.*
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.protocol.api.KryptonProtocol
import io.krypton.protocol.api.encryptString
import io.krypton.protocol.api.decryptString
import io.krypton.protocol.api.processBundleAndEncrypt
import io.krypton.protocol.models.CiphertextMessageType
import io.krypton.protocol.models.PreKeyBundle
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.memory.InMemoryIdentityKeyStore
import io.krypton.storage.memory.InMemoryPreKeyStore
import io.krypton.storage.memory.InMemorySenderKeyStore
import io.krypton.storage.memory.InMemorySessionStore
import org.signal.libsignal.metadata.certificate.ServerCertificate
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Production verification test that exercises libsignal's actual native crypto
 * via the Krypton abstraction layer.
 *
 * This test creates two protocol instances (Alice and Bob), each backed by
 * the real libsignal Rust crypto (loaded via JNI), and runs a full:
 *   X3DH handshake -> session establishment -> Double Ratchet encrypt/decrypt
 *
 * If this test passes, then the entire Krypton protocol stack is wired
 * correctly against the real libsignal native library on this platform.
 */
class VerifiedProductionTest {

    /**
     * Helper: creates a protocol instance with real libsignal and pre-populated
     * pre-keys so that [KryptonProtocol.getPreKeyBundle] can succeed.
     */
    private suspend fun createProtocolWithPreKeys(): Pair<KryptonProtocol, InMemoryPreKeyStore> {
        val preKeyStore = InMemoryPreKeyStore()
        val protocol = KryptonConfigurator().apply {
            this.preKeyStore = preKeyStore
        }.build()

        // Generate and store a signed pre-key (required by getPreKeyBundle)
        val spk = protocol.generateSignedPreKey(1).getOrNull()!!
        preKeyStore.saveSignedPreKey(1, spk).getOrThrow()

        // Generate and store some one-time pre-keys
        val preKeys = protocol.generatePreKeys(1, 10).getOrNull()!!
        for (pk in preKeys) {
            preKeyStore.storePreKey(pk.keyId, pk).getOrThrow()
        }

        return Pair(protocol, preKeyStore)
    }

    @Test
    fun `full X3DH plus Double Ratchet encrypt-decrypt roundtrip`() = runBlocking {
        // ── 1. Create Alice with pre-keys ────────────────────────────────────
        val (alice, _) = createProtocolWithPreKeys()
        val aliceAddress = ProtocolAddress("alice", DeviceId(1))

        // ── 2. Create Bob with pre-keys ──────────────────────────────────────
        val (bob, _) = createProtocolWithPreKeys()
        val bobAddress = ProtocolAddress("bob", DeviceId(1))

        // ── 3. Alice builds a bundle for Bob ─────────────────────────────────
        val aliceBundleResult = alice.getPreKeyBundle(aliceAddress)
        assertTrue(aliceBundleResult.isSuccess, "Alice getPreKeyBundle should succeed, got: ${aliceBundleResult.errorOrNull()}")
        val aliceBundle = aliceBundleResult.getOrNull()!!
        assertEquals(aliceAddress, aliceBundle.sender)

        // ── 4. Bob processes Alice's bundle (X3DH handshake) ─────────────────
        val processResult = bob.processPreKeyBundle(aliceBundle)
        assertTrue(processResult.isSuccess, "Bob should successfully process Alice's bundle: ${processResult.errorOrNull()}")

        // ── 5. Verify session exists ─────────────────────────────────────────
        val hasSession = bob.hasSession(aliceAddress)
        assertTrue(hasSession.isSuccess && hasSession.getOrNull() == true,
            "Bob should have a session with Alice after processing her bundle")

        // ── 6. Bob encrypts a message for Alice ──────────────────────────────
        val originalMessage = "Hello Alice, this is Bob! 🚀"
        val encryptResult = bob.encryptString(aliceAddress, originalMessage)
        assertTrue(encryptResult.isSuccess, "Bob should encrypt successfully: ${encryptResult.errorOrNull()}")
        val ciphertext = encryptResult.getOrNull()!!
        assertTrue(ciphertext.type == CiphertextMessageType.PRE_KEY || ciphertext.type == CiphertextMessageType.MESSAGE,
            "Ciphertext should be PRE_KEY or MESSAGE type, got ${ciphertext.type}")
        assertTrue(ciphertext.serialized.isNotEmpty())

        // ── 7. Alice decrypts Bob's message ──────────────────────────────────
        val decryptResult = alice.decryptString(bobAddress, ciphertext)
        assertTrue(decryptResult.isSuccess, "Alice should decrypt successfully: ${decryptResult.errorOrNull()}")
        val decryptedMessage = decryptResult.getOrNull()!!
        assertEquals(originalMessage, decryptedMessage, "Decrypted message should match original")
    }

    @Test
    fun `two-way conversation between Alice and Bob`() = runBlocking {
        val (alice, _) = createProtocolWithPreKeys()
        val (bob, _) = createProtocolWithPreKeys()

        val aliceAddress = ProtocolAddress("alice", DeviceId(1))
        val bobAddress = ProtocolAddress("bob", DeviceId(1))

        // Alice establishes session: process Alice's bundle on Bob's side
        bob.processPreKeyBundle(
            alice.getPreKeyBundle(aliceAddress).getOrNull()!!
        ).getOrThrow()

        // Bob establishes session: process Bob's bundle on Alice's side
        alice.processPreKeyBundle(
            bob.getPreKeyBundle(bobAddress).getOrNull()!!
        ).getOrThrow()

        // Bob to Alice
        val bobMsg = "Hey Alice, what's up?"
        val ciphertextToAlice = bob.encryptString(aliceAddress, bobMsg).getOrNull()!!
        val aliceReceived = alice.decryptString(bobAddress, ciphertextToAlice).getOrNull()!!
        assertEquals(bobMsg, aliceReceived)

        // Alice to Bob (nested ratchet step)
        val aliceMsg = "Hi Bob! All good here."
        val ciphertextToBob = alice.encryptString(bobAddress, aliceMsg).getOrNull()!!
        val bobReceived = bob.decryptString(aliceAddress, ciphertextToBob).getOrNull()!!
        assertEquals(aliceMsg, bobReceived)

        // Second round (ratchet advances)
        val bobMsg2 = "Glad to hear it!"
        val ciphertext2 = bob.encryptString(aliceAddress, bobMsg2).getOrNull()!!
        val aliceReceived2 = alice.decryptString(bobAddress, ciphertext2).getOrNull()!!
        assertEquals(bobMsg2, aliceReceived2)
    }

    @Test
    fun `pre-key generation with real libsignal`() = runBlocking {
        val protocol = KryptonConfigurator().build()

        val preKeys = protocol.generatePreKeys(1, 10)
        assertTrue(preKeys.isSuccess, "Pre-key generation should succeed")
        val keys = preKeys.getOrNull()!!
        assertEquals(10, keys.size)
        assertEquals(1, keys.first().keyId)
        assertEquals(10, keys.last().keyId)

        // Verify keys have valid 33-byte public keys (ECPublicKey.serialize()
        // returns 33 bytes: type prefix + 32-byte coordinate)
        for (key in keys) {
            assertEquals(33, key.keyPair.publicKey.bytes.size,
                "Public key should be 33 bytes (type prefix + coordinate)")
            assertEquals(32, key.keyPair.privateKey.bytes.size,
                "Private key should be 32 bytes")
        }
    }

    @Test
    fun `signed pre-key generation with real libsignal`() = runBlocking {
        val protocol = KryptonConfigurator().build()

        val spkResult = protocol.generateSignedPreKey(1)
        assertTrue(spkResult.isSuccess, "Signed pre-key generation should succeed")
        val spk = spkResult.getOrNull()!!
        assertEquals(1, spk.keyId)
        assertEquals(33, spk.keyPair.publicKey.bytes.size)
        assertEquals(32, spk.keyPair.privateKey.bytes.size)
        assertEquals(64, spk.signature.size)
    }

    @Test
    fun `identity key pair is auto-generated with real Curve25519 keys`() = runBlocking {
        val protocol = KryptonConfigurator().build()

        // Verify that the identity key contains real Curve25519 keys
        // ECPublicKey.serialize() returns 33 bytes (0x05 + 32-byte X coordinate)
        assertEquals(33, protocol.identityKeyPair.identityKey.publicKey.bytes.size,
            "Identity public key should be 33 bytes")
        assertEquals(32, protocol.identityKeyPair.privateKey.bytes.size,
            "Identity private key should be 32 bytes")

        // Registration ID should be in valid range
        assertTrue(protocol.registrationId.value in 1..0x3FFFFFFF,
            "Registration ID should be in valid range [1, 0x3FFFFFFF]")
    }

    @Test
    fun `encrypt fails when no session exists`() = runBlocking {
        val protocol = KryptonConfigurator().build()
        val stranger = ProtocolAddress("stranger", DeviceId(1))
        val result = protocol.encryptString(stranger, "hello")
        assertTrue(result.isFailure, "Encrypt should fail when no session exists")
    }

    @Test
    fun `can encrypt string extension function with real bridge`() = runBlocking {
        val (alice, _) = createProtocolWithPreKeys()
        val (bob, _) = createProtocolWithPreKeys()

        val aliceAddress = ProtocolAddress("alice", DeviceId(1))
        val bobAddress = ProtocolAddress("bob", DeviceId(1))

        // Establish sessions
        bob.processPreKeyBundle(
            alice.getPreKeyBundle(aliceAddress).getOrNull()!!
        ).getOrThrow()

        alice.processPreKeyBundle(
            bob.getPreKeyBundle(bobAddress).getOrNull()!!
        ).getOrThrow()

        // Use extension function
        val ciphertext = bob.encryptString(aliceAddress, "Hello from extension!")
        assertTrue(ciphertext.isSuccess)

        val plaintext = alice.decryptString(bobAddress, ciphertext.getOrNull()!!)
        assertTrue(plaintext.isSuccess)
        assertEquals("Hello from extension!", plaintext.getOrNull())
    }

    @Test
    fun `processBundleAndEncrypt extension function`() = runBlocking {
        val (alice, _) = createProtocolWithPreKeys()
        val (bob, _) = createProtocolWithPreKeys()

        val aliceAddress = ProtocolAddress("alice", DeviceId(1))

        // Bob uses the convenience function to process and encrypt in one call
        val bundle = alice.getPreKeyBundle(aliceAddress).getOrNull()!!
        val result = bob.processBundleAndEncrypt(bundle, aliceAddress, "Quick start!".encodeToByteArray())
        assertTrue(result.isSuccess, "processBundleAndEncrypt should succeed: ${result.errorOrNull()}")
        val ciphertext = result.getOrNull()!!
        assertTrue(ciphertext.serialized.isNotEmpty())
    }

    @Test
    fun `safety number is identical for both parties with real libsignal`() = runBlocking {
        val alice = KryptonConfigurator().build()
        val bob = KryptonConfigurator().build()
        val aliceId = alice.identityKeyPair.identityKey.publicKey.bytes
        val bobId = bob.identityKeyPair.identityKey.publicKey.bytes

        // Alice's view: local = Alice, remote = Bob.
        val fromAlice = alice.safetyNumber("alice".encodeToByteArray(), "bob".encodeToByteArray(), bobId)
        assertTrue(fromAlice.isSuccess, "safetyNumber should succeed: ${fromAlice.errorOrNull()}")
        val snAlice = fromAlice.getOrNull()!!
        assertTrue(snAlice.displayText.isNotEmpty(), "Safety number text should not be empty")

        // Bob's view: local = Bob, remote = Alice. Signal safety numbers are the
        // same for both participants — so the display text must match.
        val fromBob = bob.safetyNumber("bob".encodeToByteArray(), "alice".encodeToByteArray(), aliceId)
        assertTrue(fromBob.isSuccess, "safetyNumber should succeed: ${fromBob.errorOrNull()}")
        assertEquals(
            snAlice.displayText, fromBob.getOrNull()!!.displayText,
            "Safety number must be identical on both sides of the conversation",
        )
    }

    @Test
    fun `sealed sender roundtrip with real libsignal and server certificate`() = runBlocking {
        // Sealed sender is UUID-based.
        val aliceUuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val bobUuid = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

        val (alice, _) = createProtocolWithPreKeys()
        val (bob, _) = createProtocolWithPreKeys()
        val bobAddress = ProtocolAddress(bobUuid, DeviceId(1))

        // The sender (Alice) needs a session with the destination (Bob).
        alice.processPreKeyBundle(bob.getPreKeyBundle(bobAddress).getOrNull()!!).getOrThrow()

        // Build a trust root + server cert + Alice's sender cert with real libsignal.
        // Generate the trust-root and server keypairs via Krypton, then rebuild EC keys.
        val trustRootKp = alice.generatePreKeys(100, 1).getOrNull()!!.first().keyPair
        val serverKp = alice.generatePreKeys(101, 1).getOrNull()!!.first().keyPair
        val trustRootPriv = ECPrivateKey(trustRootKp.privateKey.bytes)
        val trustRootPub = ECPublicKey(trustRootKp.publicKey.bytes)
        val serverPriv = ECPrivateKey(serverKp.privateKey.bytes)
        val serverPub = ECPublicKey(serverKp.publicKey.bytes)

        val serverCert = ServerCertificate(trustRootPriv, 1, serverPub)
        val aliceIdentityPub = ECPublicKey(alice.identityKeyPair.identityKey.publicKey.bytes)
        val expiration = 10_000L
        val senderCert = serverCert.issue(serverPriv, aliceUuid, Optional.empty(), 1, aliceIdentityPub, expiration)

        // Alice sealed-sends to Bob — the server can't see it's from Alice.
        val plaintext = "anonymous hello".encodeToByteArray()
        val sealedResult = alice.sealedSenderEncrypt(aliceUuid, 1, bobAddress, senderCert.serialized, plaintext)
        assertTrue(sealedResult.isSuccess, "Sealed encrypt should succeed: ${sealedResult.errorOrNull()}")
        val sealed = sealedResult.getOrNull()!!

        // Bob opens it (timestamp before expiration), validating against the trust root.
        val openedResult = bob.sealedSenderDecrypt(bobUuid, 1, trustRootPub.serialize(), sealed, 5_000L)
        assertTrue(openedResult.isSuccess, "Sealed decrypt should succeed: ${openedResult.errorOrNull()}")
        val opened = openedResult.getOrNull()!!

        assertEquals(aliceUuid, opened.senderUuid, "Sender identity should be revealed on decrypt")
        assertEquals(1, opened.senderDeviceId)
        assertContentEquals(plaintext, opened.message, "Recovered plaintext should match")
    }
}

/**
 * Simple runBlocking helper for JVM tests (no dependency on kotlinx-coroutines-test).
 */
private fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
}
