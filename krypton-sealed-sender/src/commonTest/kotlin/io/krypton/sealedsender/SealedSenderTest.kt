package io.krypton.sealedsender

import io.krypton.core.result.*
import io.krypton.core.types.*
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.protocol.bridge.Bridge
import io.krypton.protocol.models.CiphertextMessage
import io.krypton.protocol.models.CiphertextMessageType
import io.krypton.protocol.models.PreKeyBundle
import io.krypton.protocol.models.SealedSenderMessage
import io.krypton.sealedsender.api.SealedSender
import io.krypton.storage.memory.InMemoryIdentityKeyStore
import io.krypton.storage.memory.InMemoryPreKeyStore
import io.krypton.storage.memory.InMemorySenderKeyStore
import io.krypton.storage.memory.InMemorySessionStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SealedSenderTest {

    private val bobAddr = ProtocolAddress("bob", DeviceId.PRIMARY)

    private val aliceIdentity = IdentityKeyPair(
        IdentityKey(PublicKey(ByteArray(32) { 1 }), 0),
        PrivateKey(ByteArray(32) { 2 }),
    )

    /**
     * Fake bridge with a trivial, reversible sealed-sender scheme so we can test
     * that [SealedSender] passes its arguments through and round-trips. (Real
     * libsignal sealed sender is exercised in krypton-protocol's jvmTest.)
     */
    private class FakeSealedBridge(
        identityKeyStore: io.krypton.storage.api.IdentityKeyStore,
        sessionStore: io.krypton.storage.api.SessionStore,
        preKeyStore: io.krypton.storage.api.PreKeyStore,
        senderKeyStore: io.krypton.storage.api.SenderKeyStore,
        identityKeyPair: IdentityKeyPair,
        registrationId: RegistrationId,
    ) : Bridge(identityKeyStore, sessionStore, preKeyStore, senderKeyStore, identityKeyPair, registrationId) {

        override suspend fun processPreKeyBundle(bundle: PreKeyBundle): CryptoResult<ByteArray> =
            CryptoResult.Success(bundle.identityKey.publicKey.bytes)

        override suspend fun encrypt(recipient: ProtocolAddress, plaintext: ByteArray): CryptoResult<CiphertextMessage> =
            CryptoResult.Success(CiphertextMessage(CiphertextMessageType.MESSAGE, byteArrayOf(0x02) + plaintext))

        override suspend fun decrypt(sender: ProtocolAddress, message: CiphertextMessage): CryptoResult<ByteArray> =
            CryptoResult.Success(message.serialized.drop(1).toByteArray())

        override fun generatePreKeys(startKeyId: Int, count: Int): CryptoResult<List<PreKey>> =
            CryptoResult.Success((startKeyId until startKeyId + count).map {
                PreKey(it, KeyPair(PublicKey(ByteArray(32)), PrivateKey(ByteArray(32))))
            })

        override fun generateSignedPreKey(signedKeyId: Int): CryptoResult<SignedPreKey> =
            CryptoResult.Success(SignedPreKey(signedKeyId, KeyPair(PublicKey(ByteArray(32)), PrivateKey(ByteArray(32))), ByteArray(64)))

        override fun generateKyberPreKey(keyId: Int): CryptoResult<io.krypton.protocol.bridge.KyberPreKeyResult> =
            CryptoResult.Success(io.krypton.protocol.bridge.KyberPreKeyResult(keyId, ByteArray(32), ByteArray(64)))

        // Trivial reversible sealed sender: header "uuid|device|" + plaintext.
        override suspend fun sealedSenderEncrypt(
            localUuid: String,
            localDeviceId: Int,
            destination: ProtocolAddress,
            senderCertificate: ByteArray,
            paddedPlaintext: ByteArray,
        ): CryptoResult<ByteArray> =
            CryptoResult.Success("$localUuid|$localDeviceId|".encodeToByteArray() + paddedPlaintext)

        override suspend fun sealedSenderDecrypt(
            localUuid: String,
            localDeviceId: Int,
            trustRoot: ByteArray,
            sealedMessage: ByteArray,
            timestampMillis: Long,
        ): CryptoResult<SealedSenderMessage> {
            val full = sealedMessage.decodeToString()
            val first = full.indexOf('|')
            val second = full.indexOf('|', first + 1)
            val uuid = full.substring(0, first)
            val device = full.substring(first + 1, second).toInt()
            val headerBytes = "$uuid|$device|".encodeToByteArray().size
            val body = sealedMessage.copyOfRange(headerBytes, sealedMessage.size)
            return CryptoResult.Success(SealedSenderMessage(uuid, device, body))
        }
    }

    private fun createSealedSender(): SealedSender {
        val iks = InMemoryIdentityKeyStore(
            identityKeyPair = CryptoResult.Success(aliceIdentity),
            localRegistrationId = CryptoResult.Success(1001),
        )
        val bridge = FakeSealedBridge(
            identityKeyStore = iks,
            sessionStore = InMemorySessionStore(),
            preKeyStore = InMemoryPreKeyStore(),
            senderKeyStore = InMemorySenderKeyStore(),
            identityKeyPair = aliceIdentity,
            registrationId = RegistrationId(1001),
        )
        val protocol = KryptonConfigurator().apply {
            identityKeyPair = aliceIdentity
            registrationId = RegistrationId(1001)
            identityKeyStore = iks
            this.bridge = bridge
        }.build()

        return SealedSender(
            protocol = protocol,
            localUuid = "11111111-1111-1111-1111-111111111111",
            localDeviceId = 1,
            senderCertificate = ByteArray(8) { 9 },
            trustRoot = ByteArray(33) { 5 },
        )
    }

    @Test
    fun `sealed sender send and receive roundtrip via the wrapper`() = runTest {
        val sealed = createSealedSender()
        val plaintext = "Secret sealed message".encodeToByteArray()

        val sendResult = sealed.send(bobAddr, plaintext)
        assertTrue(sendResult.isSuccess, "Sealed send should succeed")

        val envelope = sendResult.getOrNull()!!
        val received = sealed.receive(envelope, timestampMillis = 1_000L)
        assertTrue(received.isSuccess, "Sealed receive should succeed")

        val opened = received.getOrNull()!!
        assertEquals("11111111-1111-1111-1111-111111111111", opened.senderUuid)
        assertEquals(1, opened.senderDeviceId)
        assertContentEquals(plaintext, opened.message)
    }
}
