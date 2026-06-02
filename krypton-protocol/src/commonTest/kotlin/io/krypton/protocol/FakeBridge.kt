package io.krypton.protocol

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.*
import io.krypton.protocol.bridge.Bridge
import io.krypton.protocol.models.CiphertextMessage
import io.krypton.protocol.models.CiphertextMessageType
import io.krypton.protocol.models.PreKeyBundle
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore
import io.krypton.storage.memory.InMemoryIdentityKeyStore
import io.krypton.storage.memory.InMemoryPreKeyStore
import io.krypton.storage.memory.InMemorySenderKeyStore
import io.krypton.storage.memory.InMemorySessionStore

/**
 * A fake [Bridge] that implements **real reversible encryption** for testing.
 *
 * Uses a deterministic XOR cipher derived from the session record:
 * - `encrypt`: XORs plaintext with an HKDF-like key from the session record
 * - `decrypt`: XORs ciphertext back to plaintext (same operation)
 * - `processPreKeyBundle`: Returns the bundle's identity key fingerprint as the session record
 *
 * This proves the **full architecture works**: session storage, encrypt/decrypt
 * roundtrip, error propagation — without requiring the native libsignal library.
 *
 * ## How encryption works
 * ```
 * sessionRecord = simpleKDF(bundle identity key)  // deterministic session
 * key = simpleKDF(sessionRecord)                   // derived key
 * ciphertext = plaintext XOR key                   // XOR cipher
 * ```
 *
 * This is NOT production-grade crypto — it's for **architectural testing only**.
 */
public class FakeBridge(
    identityKeyStore: IdentityKeyStore = InMemoryIdentityKeyStore(
        identityKeyPair = CryptoResult.Success(IdentityKeyPair(
            IdentityKey(PublicKey(ByteArray(32) { 1 }), 0),
            PrivateKey(ByteArray(32) { 2 }),
        )),
        localRegistrationId = CryptoResult.Success(1001),
    ),
    sessionStore: SessionStore = InMemorySessionStore(),
    preKeyStore: PreKeyStore = InMemoryPreKeyStore(),
    senderKeyStore: SenderKeyStore = InMemorySenderKeyStore(),
    identityKeyPair: IdentityKeyPair = IdentityKeyPair(
        IdentityKey(PublicKey(ByteArray(32) { 1 }), 0),
        PrivateKey(ByteArray(32) { 2 }),
    ),
    registrationId: RegistrationId = RegistrationId(1001),
) : Bridge(identityKeyStore, sessionStore, preKeyStore, senderKeyStore, identityKeyPair, registrationId) {

    /** Stores created sessions for later retrieval. */
    private val sessions = mutableMapOf<String, ByteArray>()

    override suspend fun processPreKeyBundle(
        bundle: PreKeyBundle,
    ): CryptoResult<ByteArray> = CryptoResult.catching {
        // Derive a deterministic "session record" from the bundle's identity key
        val sessionRecord = simpleKdf(bundle.identityKey.publicKey.bytes + identityKeyPair.identityKey.publicKey.bytes)
        sessions[bundle.sender.name] = sessionRecord
        sessionRecord
    }

    override suspend fun encrypt(
        recipient: ProtocolAddress,
        plaintext: ByteArray,
    ): CryptoResult<CiphertextMessage> = CryptoResult.catching {
        val record = sessions[recipient.name] ?: simpleKdf(recipient.name.encodeToByteArray() + ByteArray(32) { 42 })
        val key = simpleKdf(record)
        val ciphertext = xorBytes(plaintext, key)
        CiphertextMessage(
            type = CiphertextMessageType.MESSAGE,
            serialized = byteArrayOf(0x02) + ciphertext,
        )
    }

    override suspend fun decrypt(
        sender: ProtocolAddress,
        message: CiphertextMessage,
    ): CryptoResult<ByteArray> = CryptoResult.catching {
        val record = sessions[sender.name] ?: simpleKdf(sender.name.encodeToByteArray() + ByteArray(32) { 42 })
        val payload = message.serialized.drop(1).toByteArray()
        val key = simpleKdf(record)
        xorBytes(payload, key)
    }

    override fun generatePreKeys(startKeyId: Int, count: Int): CryptoResult<List<PreKey>> =
        CryptoResult.Success((startKeyId until startKeyId + count).map { id ->
            val seed = ByteArray(32) { i -> (id + i).toByte() }
            PreKey(id, KeyPair(
                PublicKey(simpleKdf(seed + byteArrayOf(0x01))),
                PrivateKey(simpleKdf(seed + byteArrayOf(0x02))),
            ))
        })

    override fun generateSignedPreKey(
        signedKeyId: Int,
    ): CryptoResult<SignedPreKey> = CryptoResult.catching {
        val seed = simpleKdf(identityKeyPair.identityKey.publicKey.bytes + byteArrayOf(signedKeyId.toByte()))
        SignedPreKey(
            keyId = signedKeyId,
            keyPair = KeyPair(
                PublicKey(simpleKdf(seed + byteArrayOf(0x01))),
                PrivateKey(simpleKdf(seed + byteArrayOf(0x02))),
            ),
            signature = simpleKdf(seed + byteArrayOf(0x03)),
        )
    }

    // ── Simple "KDF" using a deterministic algorithm ──────────────────────────

    internal fun simpleKdf(input: ByteArray): ByteArray {
        val state = IntArray(8) { i -> (0x6A09E667L + i.toLong() * 0x9E3779B9L).toInt() } // SHA-256 init vectors
        val words = input.toList().chunked(4) { chunk ->
            chunk.foldIndexed(0) { idx, acc, byte -> acc or ((byte.toInt() and 0xFF) shl (idx * 8)) }
        }

        // Absorb input into state
        for (w in words) {
            state[0] = state[0] xor w
            for (round in 0 until 16) {
                state[round % 8] = Integer.rotateLeft(state[round % 8] xor state[(round + 1) % 8], 7) +
                    (round * 0x9E3779B9).toInt()
                state[(round + 1) % 8] = state[(round + 1) % 8] xor Integer.rotateRight(state[round % 8], 13)
            }
        }

        // Final mix
        for (i in 0 until 8) {
            state[i] = state[i] xor Integer.rotateLeft(state[(i + 3) % 8], 17)
            state[(i + 5) % 8] = state[(i + 5) % 8] + Integer.rotateRight(state[i], 11)
        }

        return state.flatMap { int ->
            listOf(
                (int shr 24).toByte(),
                (int shr 16).toByte(),
                (int shr 8).toByte(),
                int.toByte(),
            )
        }.toByteArray()
    }

    private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i % b.size].toInt()).toByte() }
    }
}

/**
 * Verifies that [FakeBridge] actually works (encrypt → decrypt roundtrip).
 */
internal suspend fun FakeBridge.verifyRoundtrip(): Boolean {
    val plaintext = "Hello, Krypton! 🚀".encodeToByteArray()
    val fakeAddr = ProtocolAddress("test")

    val encryptResult = encrypt(fakeAddr, plaintext)
    if (encryptResult.isFailure) return false

    val ciphertextMsg = encryptResult.getOrNull() ?: return false
    val decryptResult = decrypt(fakeAddr, ciphertextMsg)
    if (decryptResult.isFailure) return false

    val decrypted = decryptResult.getOrNull() ?: return false
    return plaintext.contentEquals(decrypted)
}
