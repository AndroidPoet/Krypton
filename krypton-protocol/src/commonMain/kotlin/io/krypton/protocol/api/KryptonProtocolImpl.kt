package io.krypton.protocol.api

import io.krypton.core.result.*
import io.krypton.core.types.*
import io.krypton.protocol.bridge.Bridge
import io.krypton.protocol.bridge.createPlatformBridge
import io.krypton.protocol.models.CiphertextMessage
import io.krypton.protocol.models.PreKeyBundle
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore

/**
 * Full implementation of [KryptonProtocol] that wires together:
 *   - Native Rust crypto via a [Bridge] implementation (defaults to platform bridge)
 *   - Persistent stores via [IdentityKeyStore], [SessionStore], [PreKeyStore], [SenderKeyStore]
 *   - All high-level operations (encrypt, decrypt, session management)
 *
 * You don't create this directly — use [KryptonConfigurator] (via `Krypton.protocol { }`).
 *
 * For testing, you can inject a custom [bridge] implementation:
 * ```
 * val impl = KryptonProtocolImpl(identity, regId, stores, FakeBridge(...))
 * impl.encrypt(alice, data).onSuccess { ... }
 * ```
 */
public class KryptonProtocolImpl internal constructor(
    override val identityKeyPair: IdentityKeyPair,
    override val registrationId: RegistrationId,
    private val identityKeyStore: IdentityKeyStore,
    private val sessionStore: SessionStore,
    private val preKeyStore: PreKeyStore,
    private val senderKeyStore: SenderKeyStore,
    /** The native crypto bridge. Inject a [FakeBridge] in tests. Defaults to platform bridge. */
    private val bridge: Bridge,
) : KryptonProtocol {

    // ── Session lifecycle ──────────────────────────────────────────────────

    override suspend fun processPreKeyBundle(bundle: PreKeyBundle): CryptoResult<Unit> =
        identityKeyStore.saveIdentity(
            bundle.sender,
            bundle.identityKey.publicKey,
        ).flatMap {
            bridge.processPreKeyBundle(bundle)
        }.flatMap { sessionRecord ->
            sessionStore.storeSession(bundle.sender, sessionRecord)
        }

    override suspend fun hasSession(address: ProtocolAddress): CryptoResult<Boolean> =
        sessionStore.containsSession(address)

    override suspend fun deleteSession(address: ProtocolAddress): CryptoResult<Unit> =
        sessionStore.deleteSession(address)

    // ── Encrypt / Decrypt ──────────────────────────────────────────────────

    override suspend fun encrypt(
        recipient: ProtocolAddress,
        plaintext: ByteArray,
    ): CryptoResult<CiphertextMessage> =
        sessionStore.loadSession(recipient).flatMap { record ->
            when (record) {
                null -> CryptoResult.Failure(
                    io.krypton.core.result.CryptoError.notFound(
                        "No session with $recipient — call processPreKeyBundle first",
                        io.krypton.core.result.CryptoErrorOrigin.Protocol,
                    )
                )
                else -> bridge.encrypt(recipient, plaintext)
            }
        }

    override suspend fun decrypt(
        sender: ProtocolAddress,
        message: CiphertextMessage,
    ): CryptoResult<ByteArray> =
        sessionStore.loadSession(sender).flatMap { record ->
            when (record) {
                null -> CryptoResult.Failure(
                    io.krypton.core.result.CryptoError.notFound(
                        "No session with $sender",
                        io.krypton.core.result.CryptoErrorOrigin.Protocol,
                    )
                )
                else -> bridge.decrypt(sender, message)
            }
        }

    // ── Pre-key management ─────────────────────────────────────────────────

    override fun generatePreKeys(startKeyId: Int, count: Int): CryptoResult<List<PreKey>> =
        bridge.generatePreKeys(startKeyId, count)

    override fun generateSignedPreKey(signedKeyId: Int): CryptoResult<SignedPreKey> =
        bridge.generateSignedPreKey(signedKeyId)

    override suspend fun getPreKeyBundle(localAddress: ProtocolAddress): CryptoResult<PreKeyBundle> =
        preKeyStore.getCurrentSignedPreKeyId().flatMap { spkId ->
            preKeyStore.loadSignedPreKey(spkId).flatMap { spk ->
                preKeyStore.preKeyCount().flatMap { count ->
                    CryptoResult.Success(PreKeyBundle(
                        sender = localAddress,
                        identityKey = identityKeyPair.identityKey,
                        registrationId = registrationId,
                        deviceId = localAddress.deviceId,
                        preKeyId = null,
                        preKeyPublic = null,
                        signedPreKeyId = spk.keyId,
                        signedPreKeyPublic = spk.keyPair.publicKey,
                        signedPreKeySignature = spk.signature,
                    ))
                }
            }
        }

    // ── Sender key (group messaging) ───────────────────────────────────────

    override suspend fun groupEncrypt(
        distributionId: String,
        plaintext: ByteArray,
    ): CryptoResult<ByteArray> =
        CryptoResult.Failure(io.krypton.core.result.CryptoError.internal("Group encryption not yet wired"))

    override suspend fun groupDecrypt(
        distributionId: String,
        sender: ProtocolAddress,
        ciphertext: ByteArray,
    ): CryptoResult<ByteArray> =
        CryptoResult.Failure(io.krypton.core.result.CryptoError.internal("Group decryption not yet wired"))

    override fun close() {
        // Release any native resources if needed
    }
}
