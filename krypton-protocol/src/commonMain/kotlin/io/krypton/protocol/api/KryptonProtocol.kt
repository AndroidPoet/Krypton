package io.krypton.protocol.api

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.*
import io.krypton.protocol.models.CiphertextMessage
import io.krypton.protocol.models.PreKeyBundle
import io.krypton.protocol.models.SafetyNumber

/**
 * The core Signal Protocol operations exposed by Krypton.
 *
 * This is the main interface you'll interact with for end-to-end encryption.
 * It handles session establishment (X3DH), message encryption/decryption
 * (Double Ratchet), and pre-key management.
 *
 * ## Getting started
 * ```
 * val protocol = Krypton.protocol {
 *     identityKeyPair = myIdentity
 *     registrationId = 1234
 *     stores = InMemoryStores(myIdentity, 1234)
 * }
 *
 * // Encrypt a message
 * val ciphertext = protocol.encrypt(alice, plaintext)
 *     .onSuccess { sendToServer(it) }
 *     .onFailure { log.error("Encryption failed", it) }
 * ```
 */
public interface KryptonProtocol : AutoCloseable {

    // ── Identity ───────────────────────────────────────────────────────────

    /** This device's identity key pair. */
    public val identityKeyPair: IdentityKeyPair

    /** This device's registration ID. */
    public val registrationId: RegistrationId

    // ── Session lifecycle ──────────────────────────────────────────────────

    /**
     * Processes a [PreKeyBundle] received from a remote user and establishes
     * a new session (X3DH handshake).
     *
     * Call this when you receive a pre-key bundle from a user you haven't
     * talked to before.
     */
    public suspend fun processPreKeyBundle(
        bundle: PreKeyBundle
    ): CryptoResult<Unit>

    /**
     * Returns `true` if a session exists with the given [address].
     */
    public suspend fun hasSession(address: ProtocolAddress): CryptoResult<Boolean>

    /**
     * Deletes the session with [address], forcing a fresh handshake next time.
     */
    public suspend fun deleteSession(address: ProtocolAddress): CryptoResult<Unit>

    // ── Encrypt / Decrypt ──────────────────────────────────────────────────

    /**
     * Encrypts [plaintext] for [recipient] using the established session.
     *
     * @return A [CiphertextMessage] containing the encrypted payload and type.
     */
    public suspend fun encrypt(
        recipient: ProtocolAddress,
        plaintext: ByteArray,
    ): CryptoResult<CiphertextMessage>

    /**
     * Decrypts a [CiphertextMessage] from [sender].
     *
     * @return The original plaintext bytes.
     */
    public suspend fun decrypt(
        sender: ProtocolAddress,
        message: CiphertextMessage,
    ): CryptoResult<ByteArray>

    // ── Pre-key management ─────────────────────────────────────────────────

    /**
     * Generates a batch of one-time pre-keys for publishing to the server.
     */
    public fun generatePreKeys(startKeyId: Int, count: Int): CryptoResult<List<PreKey>>

    /**
     * Generates a new signed pre-key, signed with the identity key.
     */
    public fun generateSignedPreKey(signedKeyId: Int): CryptoResult<SignedPreKey>

    /**
     * Builds a [PreKeyBundle] for a remote user to establish a session with us.
     *
     * @param localAddress Our own address (so the bundle knows who published it).
     */
    public suspend fun getPreKeyBundle(localAddress: ProtocolAddress): CryptoResult<PreKeyBundle>

    // ── Sender key (group messaging) ───────────────────────────────────────

    /**
     * Encrypts [plaintext] for a group using the sender key distribution.
     */
    public suspend fun groupEncrypt(
        distributionId: String,
        plaintext: ByteArray,
    ): CryptoResult<ByteArray>

    /**
     * Decrypts a group message using sender key distribution.
     */
    public suspend fun groupDecrypt(
        distributionId: String,
        sender: ProtocolAddress,
        ciphertext: ByteArray,
    ): CryptoResult<ByteArray>

    // ── Identity verification (safety numbers) ─────────────────────────────

    /**
     * Computes the safety number between this device's identity and a remote
     * identity, so two users can verify out-of-band that there is no MITM
     * (Signal's "safety numbers" / fingerprint).
     *
     * @param localStableId   A stable identifier for us (e.g. our user id bytes).
     * @param remoteStableId  A stable identifier for the remote (their user id bytes).
     * @param remoteIdentityKey The remote party's identity public key bytes.
     * @param iterations      Hash iterations; Signal uses 5200 (the default).
     */
    public fun safetyNumber(
        localStableId: ByteArray,
        remoteStableId: ByteArray,
        remoteIdentityKey: ByteArray,
        iterations: Int = 5200,
    ): CryptoResult<SafetyNumber>
}
