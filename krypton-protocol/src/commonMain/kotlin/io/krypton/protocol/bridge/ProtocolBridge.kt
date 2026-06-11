package io.krypton.protocol.bridge

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.*
import io.krypton.protocol.models.CiphertextMessage
import io.krypton.protocol.models.PreKeyBundle
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore

/**
 * Result of generating a Kyber pre-key (post-quantum).
 */
public data class KyberPreKeyResult(
    val keyId: Int,
    val publicKey: ByteArray,
    val signature: ByteArray,
)

/**
 * Contract for the native crypto operations that the protocol layer needs.
 *
 * Production uses [RealBridge] on JVM (which delegates to libsignal via JNI).
 * Tests use a [FakeBridge] that implements real reversible crypto.
 *
 * Unlike a simple interface, [Bridge] holds references to the store backends
 * because libsignal's native JNI/FFI requires callback-based store interfaces
 * that must be available during encryption/decryption calls.
 */
public abstract class Bridge(
    public val identityKeyStore: IdentityKeyStore,
    public val sessionStore: SessionStore,
    public val preKeyStore: PreKeyStore,
    public val senderKeyStore: SenderKeyStore,
    public val identityKeyPair: IdentityKeyPair,
    public val registrationId: RegistrationId,
) {

    /**
     * Process a remote party's pre-key bundle to establish a session.
     * Returns the serialized session record bytes that should be persisted.
     */
    public abstract suspend fun processPreKeyBundle(
        bundle: PreKeyBundle,
    ): CryptoResult<ByteArray>

    /**
     * Encrypt [plaintext] for [recipient] using the established session.
     */
    public abstract suspend fun encrypt(
        recipient: ProtocolAddress,
        plaintext: ByteArray,
    ): CryptoResult<CiphertextMessage>

    /**
     * Decrypt a [CiphertextMessage] from [sender].
     */
    public abstract suspend fun decrypt(
        sender: ProtocolAddress,
        message: CiphertextMessage,
    ): CryptoResult<ByteArray>

    /**
     * Generate a batch of one-time pre-keys.
     */
    public abstract fun generatePreKeys(startKeyId: Int, count: Int): CryptoResult<List<PreKey>>

    /**
     * Generate a signed pre-key.
     */
    public abstract fun generateSignedPreKey(
        signedKeyId: Int,
    ): CryptoResult<SignedPreKey>

    /**
     * Generate a Kyber pre-key (post-quantum) with a real signature from
     * the identity key. Required for modern libsignal PreKeyBundle.
     */
    public abstract fun generateKyberPreKey(
        keyId: Int,
    ): CryptoResult<KyberPreKeyResult>

    /**
     * Compute the safety number (fingerprint) between this device's identity and
     * a remote identity. Uses [identityKeyPair] as the local key.
     *
     * Open with a fail-loud default so platforms that haven't wired it yet report
     * clearly instead of returning a fake value. [RealBridge] (JVM/Android)
     * overrides this with libsignal's `NumericFingerprintGenerator`.
     */
    public open fun computeSafetyNumber(
        localStableId: ByteArray,
        remoteStableId: ByteArray,
        remoteIdentityKey: ByteArray,
        iterations: Int,
    ): CryptoResult<io.krypton.protocol.models.SafetyNumber> =
        CryptoResult.Failure(
            io.krypton.core.result.CryptoError.internal("Safety numbers are not implemented on this platform yet."),
        )

    /**
     * Sealed-sender encrypt: wrap [paddedPlaintext] for [destination] so the
     * server cannot see who sent it. [senderCertificate] is the server-issued
     * certificate proving our identity. [RealBridge] (JVM/Android) overrides this.
     */
    public open suspend fun sealedSenderEncrypt(
        localUuid: String,
        localDeviceId: Int,
        destination: ProtocolAddress,
        senderCertificate: ByteArray,
        paddedPlaintext: ByteArray,
    ): CryptoResult<ByteArray> =
        CryptoResult.Failure(
            io.krypton.core.result.CryptoError.internal("Sealed sender is not implemented on this platform yet."),
        )

    /**
     * Sealed-sender decrypt: open a sealed message, validating the sender's
     * certificate against [trustRoot] (the server's trust-root public key).
     */
    public open suspend fun sealedSenderDecrypt(
        localUuid: String,
        localDeviceId: Int,
        trustRoot: ByteArray,
        sealedMessage: ByteArray,
        timestampMillis: Long,
    ): CryptoResult<io.krypton.protocol.models.SealedSenderMessage> =
        CryptoResult.Failure(
            io.krypton.core.result.CryptoError.internal("Sealed sender is not implemented on this platform yet."),
        )

    // ── zkgroup (client-side primitives) ───────────────────────────────────

    /**
     * Derive a group's deterministic secret/public params and stable identifier
     * from its 32-byte master key. Pure client-side zkgroup — no server, no
     * randomness. [RealBridge] (JVM/Android) overrides with libsignal's
     * `GroupSecretParams.deriveFromMasterKey`.
     */
    public open fun deriveGroupSecretParams(
        masterKey: ByteArray,
    ): CryptoResult<io.krypton.protocol.models.GroupSecretParamsResult> =
        CryptoResult.Failure(
            io.krypton.core.result.CryptoError.internal("zkgroup is not implemented on this platform yet."),
        )

    /**
     * Derive the 16-byte access key from a 32-byte profile key — used to send
     * sealed-sender messages to people who aren't your contacts.
     */
    public open fun deriveProfileKeyAccessKey(
        profileKey: ByteArray,
    ): CryptoResult<ByteArray> =
        CryptoResult.Failure(
            io.krypton.core.result.CryptoError.internal("zkgroup is not implemented on this platform yet."),
        )

    /**
     * Compute the profile-key version string for [aciUuid] — the opaque handle
     * the server uses to serve a profile without learning the profile key.
     */
    public open fun profileKeyVersion(
        profileKey: ByteArray,
        aciUuid: String,
    ): CryptoResult<String> =
        CryptoResult.Failure(
            io.krypton.core.result.CryptoError.internal("zkgroup is not implemented on this platform yet."),
        )

    /**
     * Compute the profile-key commitment for [aciUuid] — uploaded with a profile
     * so the server can verify later proofs without seeing the profile key.
     */
    public open fun profileKeyCommitment(
        profileKey: ByteArray,
        aciUuid: String,
    ): CryptoResult<ByteArray> =
        CryptoResult.Failure(
            io.krypton.core.result.CryptoError.internal("zkgroup is not implemented on this platform yet."),
        )
}

/**
 * Platform-specific default bridge factory.
 *
 * Each platform provides an `actual` function that creates the appropriate
 * production bridge implementation. On unsupported platforms, it returns
 * a bridge that fails with a helpful error message.
 */
public expect fun createPlatformBridge(
    identityKeyStore: IdentityKeyStore,
    sessionStore: SessionStore,
    preKeyStore: PreKeyStore,
    senderKeyStore: SenderKeyStore,
    identityKeyPair: IdentityKeyPair,
    registrationId: RegistrationId,
): Bridge

/**
 * Platform-specific identity key pair generation.
 *
 * On JVM, this generates real Curve25519 keys via libsignal.
 * On unsupported platforms, returns deterministic test keys.
 */
public expect fun createPlatformIdentityKeyPair(): IdentityKeyPair

/**
 * Platform-specific registration ID generation.
 *
 * On all platforms, returns a random valid registration ID.
 */
public expect fun createPlatformRegistrationId(): RegistrationId

/**
 * A [Bridge] that always returns [CryptoResult.Failure] with the given message.
 *
 * Used as a fallback for platforms whose native libsignal library has not
 * been bundled yet. Override the methods you need to partially enable a platform.
 */
public open class NotImplementedBridge(
    identityKeyStore: IdentityKeyStore,
    sessionStore: SessionStore,
    preKeyStore: PreKeyStore,
    senderKeyStore: SenderKeyStore,
    identityKeyPair: IdentityKeyPair,
    registrationId: RegistrationId,
    private val message: String = "Not implemented on this platform",
) : Bridge(identityKeyStore, sessionStore, preKeyStore, senderKeyStore, identityKeyPair, registrationId) {

    override suspend fun processPreKeyBundle(bundle: PreKeyBundle): CryptoResult<ByteArray> =
        CryptoResult.Failure(io.krypton.core.result.CryptoError.internal(message))

    override suspend fun encrypt(recipient: ProtocolAddress, plaintext: ByteArray): CryptoResult<CiphertextMessage> =
        CryptoResult.Failure(io.krypton.core.result.CryptoError.internal(message))

    override suspend fun decrypt(sender: ProtocolAddress, msg: CiphertextMessage): CryptoResult<ByteArray> =
        CryptoResult.Failure(io.krypton.core.result.CryptoError.internal(message))

    override fun generatePreKeys(startKeyId: Int, count: Int): CryptoResult<List<PreKey>> =
        CryptoResult.Failure(io.krypton.core.result.CryptoError.internal(message))

    override fun generateSignedPreKey(signedKeyId: Int): CryptoResult<SignedPreKey> =
        CryptoResult.Failure(io.krypton.core.result.CryptoError.internal(message))

    override fun generateKyberPreKey(keyId: Int): CryptoResult<KyberPreKeyResult> =
        CryptoResult.Failure(io.krypton.core.result.CryptoError.internal(message))
}
