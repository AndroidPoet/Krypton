package io.krypton.protocol.bridge

import io.krypton.core.result.*
import io.krypton.core.types.DeviceId
import io.krypton.core.types.IdentityKeyPair
import io.krypton.core.types.KeyPair
import io.krypton.core.types.PreKey
import io.krypton.core.types.PrivateKey
import io.krypton.core.types.ProtocolAddress
import io.krypton.core.types.PublicKey
import io.krypton.core.types.RegistrationId
import io.krypton.core.types.SignedPreKey
import io.krypton.protocol.models.CiphertextMessage
import io.krypton.protocol.models.CiphertextMessageType
import io.krypton.protocol.models.PreKeyBundle
import io.krypton.storage.api.IdentityKeyStore as KryptonIdentityKeyStore
import io.krypton.storage.api.PreKeyStore as KryptonPreKeyStore
import io.krypton.storage.api.SenderKeyStore as KryptonSenderKeyStore
import io.krypton.storage.api.SessionStore as KryptonSessionStore
import org.signal.libsignal.protocol.IdentityKey as SignalIdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair as SignalIdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import org.signal.libsignal.protocol.state.PreKeyBundle as LibsignalPreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore as SignalIdentityKeyStore
import java.time.Instant
import java.util.UUID

/**
 * Production bridge that uses the real libsignal-client Java SDK
 * (which calls into native Rust via JNI).
 */
public class RealBridge(
    identityKeyStore: KryptonIdentityKeyStore,
    sessionStore: KryptonSessionStore,
    preKeyStore: KryptonPreKeyStore,
    senderKeyStore: KryptonSenderKeyStore,
    identityKeyPair: IdentityKeyPair,
    registrationId: RegistrationId,
) : Bridge(identityKeyStore, sessionStore, preKeyStore, senderKeyStore, identityKeyPair, registrationId) {

    private val signalStores: SignalProtocolStoreAdapter

    init {
        val ecPub = ECPublicKey(identityKeyPair.identityKey.publicKey.bytes)
        val ecPriv = ECPrivateKey(identityKeyPair.privateKey.bytes)
        val signalIdentityKey = SignalIdentityKey(ecPub)
        val signalIdentityKeyPair = SignalIdentityKeyPair(signalIdentityKey, ecPriv)

        signalStores = SignalProtocolStoreAdapter(
            identityKeyStore = identityKeyStore,
            sessionStore = sessionStore,
            preKeyStore = preKeyStore,
            senderKeyStore = senderKeyStore,
            localIdentityKeyPair = signalIdentityKeyPair,
            localRegistrationId = registrationId.value,
        )
    }

    private fun ensureLoaded() {
        // libsignal-client JAR loads its native libs automatically
        // via a static initializer in Native.java
    }

    override suspend fun processPreKeyBundle(bundle: PreKeyBundle): CryptoResult<ByteArray> =
        CryptoResult.catching {
            ensureLoaded()

            val remoteAddress = SignalProtocolAddress(bundle.sender.name, bundle.sender.deviceId.value)

            // Reconstruct the Kyber public key from the serialized bytes in the bundle.
            // The sender signed this Kyber key with their identity key before publishing.
            val kyberPublicKey = if (bundle.kyberPreKeyPublic.isNotEmpty()) {
                KEMPublicKey(bundle.kyberPreKeyPublic)
            } else {
                // Fallback for bundles without Kyber — generate a dummy key signed
                // locally (won't verify against the sender's identity, but required
                // for the libsignal API). In practice, modern bundles always include Kyber.
                val fallback = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
                fallback.publicKey
            }

            val signalBundle = LibsignalPreKeyBundle(
                bundle.registrationId.value,
                bundle.deviceId.value,
                bundle.preKeyId ?: LibsignalPreKeyBundle.NULL_PRE_KEY_ID,
                if (bundle.preKeyPublic != null) ECPublicKey(bundle.preKeyPublic.bytes) else null,
                bundle.signedPreKeyId,
                ECPublicKey(bundle.signedPreKeyPublic.bytes),
                bundle.signedPreKeySignature,
                SignalIdentityKey(ECPublicKey(bundle.identityKey.publicKey.bytes)),
                bundle.kyberPreKeyId,
                kyberPublicKey,
                bundle.kyberPreKeySignature,
            )

            val builder = SessionBuilder(signalStores, remoteAddress)
            @Suppress("DEPRECATION")
            builder.process(signalBundle, Instant.now())

            val sessionRecord = signalStores.loadSession(remoteAddress)
            sessionRecord.serialize()
        }

    override suspend fun encrypt(
        recipient: ProtocolAddress,
        plaintext: ByteArray,
    ): CryptoResult<CiphertextMessage> = CryptoResult.catching {
        ensureLoaded()

        val remoteAddress = SignalProtocolAddress(recipient.name, recipient.deviceId.value)
        val cipher = SessionCipher(signalStores, remoteAddress)

        val result = cipher.encrypt(plaintext, Instant.now())

        val rawType = result.type
        val type = when (rawType) {
            org.signal.libsignal.protocol.message.CiphertextMessage.PREKEY_TYPE -> CiphertextMessageType.PRE_KEY
            org.signal.libsignal.protocol.message.CiphertextMessage.WHISPER_TYPE -> CiphertextMessageType.MESSAGE
            org.signal.libsignal.protocol.message.CiphertextMessage.SENDERKEY_TYPE -> CiphertextMessageType.SENDER_KEY
            else -> CiphertextMessageType.MESSAGE
        }

        CiphertextMessage(type = type, serialized = result.serialize())
    }

    override suspend fun decrypt(
        sender: ProtocolAddress,
        message: CiphertextMessage,
    ): CryptoResult<ByteArray> = CryptoResult.catching {
        ensureLoaded()

        val remoteAddress = SignalProtocolAddress(sender.name, sender.deviceId.value)
        val cipher = SessionCipher(signalStores, remoteAddress)

        when (message.type) {
            CiphertextMessageType.PRE_KEY -> {
                val preKeyMsg = org.signal.libsignal.protocol.message.PreKeySignalMessage(message.serialized)
                cipher.decrypt(preKeyMsg)
            }
            CiphertextMessageType.MESSAGE -> {
                val signalMsg = org.signal.libsignal.protocol.message.SignalMessage(message.serialized)
                cipher.decrypt(signalMsg)
            }
            CiphertextMessageType.SENDER_KEY ->
                throw CryptoException(CryptoError("SenderKey decryption not yet wired"))
            CiphertextMessageType.PLAINTEXT ->
                message.serialized
        }
    }

    override fun generatePreKeys(startKeyId: Int, count: Int): CryptoResult<List<PreKey>> =
        CryptoResult.catching {
            ensureLoaded()
            (startKeyId until startKeyId + count).map { id ->
                val ecKeyPair = ECKeyPair.generate()
                val pub = PublicKey(ecKeyPair.publicKey.serialize())
                val priv = PrivateKey(ecKeyPair.privateKey.serialize())
                PreKey(id, KeyPair(pub, priv))
            }
        }

    override fun generateSignedPreKey(signedKeyId: Int): CryptoResult<SignedPreKey> =
        CryptoResult.catching {
            ensureLoaded()
            val ecKeyPair = ECKeyPair.generate()
            val pub = PublicKey(ecKeyPair.publicKey.serialize())
            val priv = PrivateKey(ecKeyPair.privateKey.serialize())

            // Sign the public key of the signed pre-key with our identity private key.
            // libsignal's SessionBuilder.process() verifies this signature during the
            // X3DH handshake — a fake signature will be rejected with "invalid signature".
            val identityPrivKey = ECPrivateKey(identityKeyPair.privateKey.bytes)
            val realSignature = identityPrivKey.calculateSignature(ecKeyPair.publicKey.serialize())

            SignedPreKey(signedKeyId, KeyPair(pub, priv), realSignature)
        }

    override fun generateKyberPreKey(keyId: Int): CryptoResult<KyberPreKeyResult> =
        CryptoResult.catching {
            ensureLoaded()
            val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            val kyberPubBytes = kyberKeyPair.publicKey.serialize()
            // Sign the Kyber public key with our identity key (XEd25519)
            val identityPrivKey = ECPrivateKey(identityKeyPair.privateKey.bytes)
            val signature = identityPrivKey.calculateSignature(kyberPubBytes)

            // Store the Kyber pre-key in the adapter so that loadKyberPreKey
            // can find it when libsignal needs it during PreKeySignalMessage decryption.
            val record = KyberPreKeyRecord(keyId, System.currentTimeMillis(), kyberKeyPair, signature)
            signalStores.storeKyberPreKey(keyId, record)

            KyberPreKeyResult(keyId, kyberPubBytes, signature)
        }

    override fun computeSafetyNumber(
        localStableId: ByteArray,
        remoteStableId: ByteArray,
        remoteIdentityKey: ByteArray,
        iterations: Int,
    ): CryptoResult<io.krypton.protocol.models.SafetyNumber> =
        CryptoResult.catching {
            val generator = org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator(iterations)
            val localIdentity = SignalIdentityKey(ECPublicKey(identityKeyPair.identityKey.publicKey.bytes))
            val remoteIdentity = SignalIdentityKey(ECPublicKey(remoteIdentityKey))
            val fingerprint = generator.createFor(
                /* version = */ FINGERPRINT_VERSION,
                localStableId, localIdentity,
                remoteStableId, remoteIdentity,
            )
            io.krypton.protocol.models.SafetyNumber(
                displayText = fingerprint.displayableFingerprint.displayText,
                scannable = fingerprint.scannableFingerprint.serialized,
            )
        }

    override suspend fun sealedSenderEncrypt(
        localUuid: String,
        localDeviceId: Int,
        destination: ProtocolAddress,
        senderCertificate: ByteArray,
        paddedPlaintext: ByteArray,
    ): CryptoResult<ByteArray> =
        CryptoResult.catching {
            val cipher = org.signal.libsignal.metadata.SealedSessionCipher(
                signalStores, UUID.fromString(localUuid), null, localDeviceId,
            )
            val cert = org.signal.libsignal.metadata.certificate.SenderCertificate(senderCertificate)
            val destAddress = SignalProtocolAddress(destination.name, destination.deviceId.value)
            cipher.encrypt(destAddress, cert, paddedPlaintext)
        }

    override suspend fun sealedSenderDecrypt(
        localUuid: String,
        localDeviceId: Int,
        trustRoot: ByteArray,
        sealedMessage: ByteArray,
        timestampMillis: Long,
    ): CryptoResult<io.krypton.protocol.models.SealedSenderMessage> =
        CryptoResult.catching {
            val cipher = org.signal.libsignal.metadata.SealedSessionCipher(
                signalStores, UUID.fromString(localUuid), null, localDeviceId,
            )
            val validator = org.signal.libsignal.metadata.certificate.CertificateValidator(ECPublicKey(trustRoot))
            val result = cipher.decrypt(validator, sealedMessage, timestampMillis)
            io.krypton.protocol.models.SealedSenderMessage(
                senderUuid = result.senderUuid,
                senderDeviceId = result.deviceId,
                message = result.paddedMessage,
            )
        }

    private companion object {
        // Fingerprint format version (Signal uses 0).
        const val FINGERPRINT_VERSION: Int = 0
    }
}

/**
 * Combined store adapter that implements all of libsignal's callback interfaces.
 */
internal class SignalProtocolStoreAdapter(
    private val identityKeyStore: KryptonIdentityKeyStore,
    private val sessionStore: KryptonSessionStore,
    private val preKeyStore: KryptonPreKeyStore,
    private val senderKeyStore: KryptonSenderKeyStore,
    private val localIdentityKeyPair: SignalIdentityKeyPair,
    private val localRegistrationId: Int,
    private val kyberPreKeyRecords: MutableMap<Int, KyberPreKeyRecord> = mutableMapOf(),
) : SignalIdentityKeyStore,
    org.signal.libsignal.protocol.state.SessionStore,
    org.signal.libsignal.protocol.state.PreKeyStore,
    org.signal.libsignal.protocol.state.SignedPreKeyStore,
    KyberPreKeyStore,
    org.signal.libsignal.protocol.state.SignalProtocolStore {

    // ── IdentityKeyStore ─────────────────────────────────────────────────

    override fun getIdentityKeyPair(): SignalIdentityKeyPair = localIdentityKeyPair
    override fun getLocalRegistrationId(): Int = localRegistrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: SignalIdentityKey): SignalIdentityKeyStore.IdentityChange {
        val kryptonAddr = ProtocolAddress(address.name, DeviceId(address.deviceId))
        val kryptonKey = PublicKey(identityKey.publicKey.serialize())
        runBlocking { identityKeyStore.saveIdentity(kryptonAddr, kryptonKey, true) }
        return SignalIdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: SignalIdentityKey,
        direction: SignalIdentityKeyStore.Direction
    ): Boolean {
        val kryptonAddr = ProtocolAddress(address.name, DeviceId(address.deviceId))
        val kryptonKey = PublicKey(identityKey.publicKey.serialize())
        val result: io.krypton.core.result.CryptoResult<Boolean> = runBlocking { identityKeyStore.isTrustedIdentity(kryptonAddr, kryptonKey) }
        return result.getOrElse { true }
    }

    override fun getIdentity(address: SignalProtocolAddress): SignalIdentityKey? {
        val kryptonAddr = ProtocolAddress(address.name, DeviceId(address.deviceId))
        val result: io.krypton.core.result.CryptoResult<PublicKey?> = runBlocking { identityKeyStore.getIdentity(kryptonAddr) }
        val publicKey: PublicKey? = result.getOrNull()
        return if (publicKey != null) {
            try {
                SignalIdentityKey(ECPublicKey(publicKey.bytes))
            } catch (e: Exception) { null }
        } else null
    }

    // ── SessionStore ─────────────────────────────────────────────────────

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val kryptonAddr = ProtocolAddress(address.name, DeviceId(address.deviceId))
        val result = runBlocking { sessionStore.loadSession(kryptonAddr) }
        val data = result.getOrNull()
        return if (data != null) SessionRecord(data) else SessionRecord()
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>?): MutableList<SessionRecord> {
        val results = mutableListOf<SessionRecord>()
        if (addresses != null) {
            for (addr in addresses) {
                val kryptonAddr = ProtocolAddress(addr.name, DeviceId(addr.deviceId))
                val result = runBlocking { sessionStore.loadSession(kryptonAddr) }
                val data = result.getOrNull()
                if (data != null) results.add(SessionRecord(data))
            }
        }
        return results
    }

    override fun getSubDeviceSessions(name: String?): MutableList<Int> = mutableListOf()

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        val kryptonAddr = ProtocolAddress(address.name, DeviceId(address.deviceId))
        runBlocking { sessionStore.storeSession(kryptonAddr, record.serialize()) }
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        val kryptonAddr = ProtocolAddress(address.name, DeviceId(address.deviceId))
        val result: io.krypton.core.result.CryptoResult<Boolean> = runBlocking { sessionStore.containsSession(kryptonAddr) }
        return result.getOrElse { false }
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        val kryptonAddr = ProtocolAddress(address.name, DeviceId(address.deviceId))
        runBlocking { sessionStore.deleteSession(kryptonAddr) }
    }

    override fun deleteAllSessions(name: String?) {
        runBlocking { sessionStore.deleteAllSessions(name ?: "") }
    }

    // ── PreKeyStore ──────────────────────────────────────────────────────

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val result = runBlocking { preKeyStore.loadPreKey(preKeyId) }
        val pk = result.getOrNull()
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("PreKey $preKeyId not found")
        val keyPair = ECKeyPair(
            ECPublicKey(pk.keyPair.publicKey.bytes),
            ECPrivateKey(pk.keyPair.privateKey.bytes),
        )
        return PreKeyRecord(preKeyId, keyPair)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        val keyPair = KeyPair(
            PublicKey(record.keyPair.publicKey.serialize()),
            PrivateKey(record.keyPair.privateKey.serialize()),
        )
        runBlocking { preKeyStore.storePreKey(preKeyId, PreKey(preKeyId, keyPair)) }
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        val result = runBlocking { preKeyStore.loadPreKey(preKeyId) }
        return result.isSuccess
    }

    override fun removePreKey(preKeyId: Int) {
        runBlocking { preKeyStore.removePreKey(preKeyId) }
    }

    // ── SignedPreKeyStore ────────────────────────────────────────────────

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val result = runBlocking { preKeyStore.loadSignedPreKey(signedPreKeyId) }
        val spk = result.getOrNull()
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("SignedPreKey $signedPreKeyId not found")
        val keyPair = ECKeyPair(
            ECPublicKey(spk.keyPair.publicKey.bytes),
            ECPrivateKey(spk.keyPair.privateKey.bytes),
        )
        return SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, spk.signature)
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = mutableListOf()

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {}

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = true

    override fun removeSignedPreKey(signedPreKeyId: Int) {}

    // ── KyberPreKeyStore ─────────────────────────────────────────────────

    override fun loadKyberPreKey(keyId: Int): KyberPreKeyRecord {
        return kyberPreKeyRecords[keyId]
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("KyberPreKey $keyId not found")
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> =
        kyberPreKeyRecords.values.toMutableList()

    override fun storeKyberPreKey(keyId: Int, record: KyberPreKeyRecord) {
        kyberPreKeyRecords[keyId] = record
    }

    override fun containsKyberPreKey(keyId: Int): Boolean =
        kyberPreKeyRecords.containsKey(keyId)

    override fun markKyberPreKeyUsed(keyId: Int, deviceId: Int, pubKey: ECPublicKey?) {
        kyberPreKeyRecords.remove(keyId)
    }

    // ── SenderKeyStore (required by SignalProtocolStore) ─────────────────

    override fun storeSenderKey(
        address: SignalProtocolAddress?,
        distributionId: UUID?,
        record: org.signal.libsignal.protocol.groups.state.SenderKeyRecord?
    ) {}

    override fun loadSenderKey(
        address: SignalProtocolAddress?,
        distributionId: UUID?
    ): org.signal.libsignal.protocol.groups.state.SenderKeyRecord? = null
}

/**
 * Helper: run a suspend function blocking the current thread.
 * Called from libsignal's JNI callbacks running on arbitrary native threads.
 */
internal fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
}
