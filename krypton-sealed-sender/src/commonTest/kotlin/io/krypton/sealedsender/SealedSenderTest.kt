package io.krypton.sealedsender

import io.krypton.core.result.*
import io.krypton.core.types.*
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.protocol.bridge.Bridge
import io.krypton.protocol.models.CiphertextMessage
import io.krypton.protocol.models.CiphertextMessageType
import io.krypton.protocol.models.PreKeyBundle
import io.krypton.sealedsender.api.SealedSender
import io.krypton.storage.memory.InMemoryIdentityKeyStore
import io.krypton.storage.memory.InMemoryPreKeyStore
import io.krypton.storage.memory.InMemorySenderKeyStore
import io.krypton.storage.memory.InMemorySessionStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SealedSenderTest {

    private val aliceAddr = ProtocolAddress("alice", DeviceId.PRIMARY)
    private val bobAddr = ProtocolAddress("bob", DeviceId.PRIMARY)

    private val aliceIdentity = IdentityKeyPair(
        IdentityKey(PublicKey(ByteArray(32) { 1 }), 0),
        PrivateKey(ByteArray(32) { 2 }),
    )

    private val bobIdentity = IdentityKeyPair(
        IdentityKey(PublicKey(ByteArray(32) { 3 }), 0),
        PrivateKey(ByteArray(32) { 4 }),
    )

    /**
     * Minimal fake bridge for testing sealed sender.
     * Uses XOR cipher derived from address name.
     */
    private class SimpleFakeBridge(
        identityKeyStore: io.krypton.storage.api.IdentityKeyStore,
        sessionStore: io.krypton.storage.api.SessionStore,
        preKeyStore: io.krypton.storage.api.PreKeyStore,
        senderKeyStore: io.krypton.storage.api.SenderKeyStore,
        identityKeyPair: IdentityKeyPair,
        registrationId: RegistrationId,
    ) : Bridge(identityKeyStore, sessionStore, preKeyStore, senderKeyStore, identityKeyPair, registrationId) {

        private val sessions = mutableMapOf<String, ByteArray>()

        override suspend fun processPreKeyBundle(bundle: PreKeyBundle): CryptoResult<ByteArray> =
            CryptoResult.Success(bundle.identityKey.publicKey.bytes)

        override suspend fun encrypt(recipient: ProtocolAddress, plaintext: ByteArray): CryptoResult<CiphertextMessage> =
            CryptoResult.Success(CiphertextMessage(
                type = CiphertextMessageType.MESSAGE,
                serialized = byteArrayOf(0x02) + plaintext,
            ))

        override suspend fun decrypt(sender: ProtocolAddress, message: CiphertextMessage): CryptoResult<ByteArray> =
            CryptoResult.Success(message.serialized.drop(1).toByteArray())

        override fun generatePreKeys(startKeyId: Int, count: Int): CryptoResult<List<PreKey>> =
            CryptoResult.Success((startKeyId until startKeyId + count).map {
                PreKey(it, KeyPair(PublicKey(ByteArray(32)), PrivateKey(ByteArray(32))))
            })

        override fun generateSignedPreKey(signedKeyId: Int): CryptoResult<SignedPreKey> =
            CryptoResult.Success(SignedPreKey(signedKeyId, KeyPair(PublicKey(ByteArray(32)), PrivateKey(ByteArray(32))), ByteArray(64)))
    }

    private suspend fun createProtocolWithSession(): SealedSender {
        val iks = InMemoryIdentityKeyStore(
            identityKeyPair = CryptoResult.Success(aliceIdentity),
            localRegistrationId = CryptoResult.Success(1001),
        )
        val ss = InMemorySessionStore()
        val pks = InMemoryPreKeyStore()
        val sks = InMemorySenderKeyStore()
        val bridge = SimpleFakeBridge(
            identityKeyStore = iks,
            sessionStore = ss,
            preKeyStore = pks,
            senderKeyStore = sks,
            identityKeyPair = aliceIdentity,
            registrationId = RegistrationId(1001),
        )
        val protocol = KryptonConfigurator().apply {
            identityKeyPair = aliceIdentity
            registrationId = RegistrationId(1001)
            identityKeyStore = iks
            sessionStore = ss
            preKeyStore = pks
            senderKeyStore = sks
            this.bridge = bridge
        }.build()

        val bundle = PreKeyBundle(
            sender = bobAddr,
            identityKey = bobIdentity.identityKey,
            registrationId = RegistrationId(2002),
            deviceId = DeviceId.PRIMARY,
            preKeyId = 1,
            preKeyPublic = PublicKey(ByteArray(32) { 10 }),
            signedPreKeyId = 1,
            signedPreKeyPublic = PublicKey(ByteArray(32) { 11 }),
            signedPreKeySignature = ByteArray(64) { 12 },
        )
        protocol.processPreKeyBundle(bundle)
        return SealedSender(protocol)
    }

    @Test
    fun `sealed sender send and receive roundtrip`() = runTest {
        val sealed = createProtocolWithSession()
        val plaintext = "Secret sealed message".encodeToByteArray()

        val sendResult = sealed.send(bobAddr, plaintext)
        assertTrue(sendResult.isSuccess, "Sealed send should succeed")

        val envelope = sendResult.getOrNull()!!
        val receiveResult = sealed.receive(bobAddr, envelope)

        assertTrue(receiveResult.isSuccess, "Sealed receive should succeed")
        assertContentEquals(plaintext, receiveResult.getOrNull()!!)
    }
}
