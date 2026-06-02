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
}
