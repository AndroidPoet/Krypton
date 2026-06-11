package io.krypton.zkgroup

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.IdentityKey
import io.krypton.core.types.IdentityKeyPair
import io.krypton.core.types.KeyPair
import io.krypton.core.types.PrivateKey
import io.krypton.core.types.PublicKey
import io.krypton.core.types.RegistrationId
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.protocol.bridge.Bridge
import io.krypton.protocol.models.GroupSecretParamsResult
import io.krypton.storage.memory.InMemoryIdentityKeyStore
import io.krypton.storage.memory.InMemoryPreKeyStore
import io.krypton.storage.memory.InMemorySenderKeyStore
import io.krypton.storage.memory.InMemorySessionStore
import io.krypton.zkgroup.api.ZkGroup
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [ZkGroup] passes its arguments straight through to the protocol
 * bridge. The *real* zkgroup crypto (byte-for-byte vs libsignal) is exercised in
 * krypton-protocol's jvmTest; here we only lock the wrapper plumbing, with a fake
 * bridge that echoes its inputs deterministically.
 */
class ZkGroupTest {

    private val identity = IdentityKeyPair(
        IdentityKey(PublicKey(ByteArray(32) { 1 }), 0),
        PrivateKey(ByteArray(32) { 2 }),
    )

    /** Fake bridge: deterministic, reversible zkgroup ops so we can assert pass-through. */
    private class FakeZkBridge(
        identityKeyStore: io.krypton.storage.api.IdentityKeyStore,
        sessionStore: io.krypton.storage.api.SessionStore,
        preKeyStore: io.krypton.storage.api.PreKeyStore,
        senderKeyStore: io.krypton.storage.api.SenderKeyStore,
        identityKeyPair: IdentityKeyPair,
        registrationId: RegistrationId,
    ) : Bridge(identityKeyStore, sessionStore, preKeyStore, senderKeyStore, identityKeyPair, registrationId) {

        override suspend fun processPreKeyBundle(bundle: io.krypton.protocol.models.PreKeyBundle): CryptoResult<ByteArray> =
            CryptoResult.Success(bundle.identityKey.publicKey.bytes)

        override suspend fun encrypt(
            recipient: io.krypton.core.types.ProtocolAddress,
            plaintext: ByteArray,
        ): CryptoResult<io.krypton.protocol.models.CiphertextMessage> =
            CryptoResult.Success(
                io.krypton.protocol.models.CiphertextMessage(
                    io.krypton.protocol.models.CiphertextMessageType.MESSAGE, plaintext,
                ),
            )

        override suspend fun decrypt(
            sender: io.krypton.core.types.ProtocolAddress,
            message: io.krypton.protocol.models.CiphertextMessage,
        ): CryptoResult<ByteArray> =
            CryptoResult.Success(message.serialized)

        override fun generatePreKeys(startKeyId: Int, count: Int) =
            CryptoResult.Success(emptyList<io.krypton.core.types.PreKey>())

        override fun generateSignedPreKey(signedKeyId: Int) =
            CryptoResult.Success(
                io.krypton.core.types.SignedPreKey(
                    signedKeyId, KeyPair(PublicKey(ByteArray(32)), PrivateKey(ByteArray(32))), ByteArray(64),
                ),
            )

        override fun generateKyberPreKey(keyId: Int) =
            CryptoResult.Success(io.krypton.protocol.bridge.KyberPreKeyResult(keyId, ByteArray(32), ByteArray(64)))

        override fun deriveGroupSecretParams(masterKey: ByteArray): CryptoResult<GroupSecretParamsResult> =
            CryptoResult.Success(
                GroupSecretParamsResult(
                    secretParams = masterKey + 0xAA.toByte(),
                    publicParams = masterKey + 0xBB.toByte(),
                    groupIdentifier = masterKey + 0xCC.toByte(),
                ),
            )

        override fun deriveProfileKeyAccessKey(profileKey: ByteArray): CryptoResult<ByteArray> =
            CryptoResult.Success(profileKey.copyOfRange(0, 16))

        override fun profileKeyVersion(profileKey: ByteArray, aciUuid: String): CryptoResult<String> =
            CryptoResult.Success("$aciUuid:${profileKey.size}")

        override fun profileKeyCommitment(profileKey: ByteArray, aciUuid: String): CryptoResult<ByteArray> =
            CryptoResult.Success(aciUuid.encodeToByteArray() + profileKey)

        // Reversible fakes: prepend the secret params so we can assert pass-through.
        override fun groupEncryptServiceId(groupSecretParams: ByteArray, serviceId: String): CryptoResult<ByteArray> =
            CryptoResult.Success(groupSecretParams + serviceId.encodeToByteArray())

        override fun groupDecryptServiceId(groupSecretParams: ByteArray, uuidCiphertext: ByteArray): CryptoResult<String> =
            CryptoResult.Success(uuidCiphertext.copyOfRange(groupSecretParams.size, uuidCiphertext.size).decodeToString())

        override fun groupEncryptProfileKey(groupSecretParams: ByteArray, profileKey: ByteArray, aciUuid: String): CryptoResult<ByteArray> =
            CryptoResult.Success(profileKey + aciUuid.encodeToByteArray())

        override fun groupDecryptProfileKey(groupSecretParams: ByteArray, profileKeyCiphertext: ByteArray, aciUuid: String): CryptoResult<ByteArray> =
            CryptoResult.Success(profileKeyCiphertext.copyOfRange(0, 32))

        override fun groupEncryptBlob(groupSecretParams: ByteArray, plaintext: ByteArray): CryptoResult<ByteArray> =
            CryptoResult.Success(byteArrayOf(0x7F) + plaintext)

        override fun groupDecryptBlob(groupSecretParams: ByteArray, blob: ByteArray): CryptoResult<ByteArray> =
            CryptoResult.Success(blob.copyOfRange(1, blob.size))
    }

    private fun createZkGroup(): ZkGroup {
        val iks = InMemoryIdentityKeyStore(
            identityKeyPair = CryptoResult.Success(identity),
            localRegistrationId = CryptoResult.Success(1001),
        )
        val bridge = FakeZkBridge(
            identityKeyStore = iks,
            sessionStore = InMemorySessionStore(),
            preKeyStore = InMemoryPreKeyStore(),
            senderKeyStore = InMemorySenderKeyStore(),
            identityKeyPair = identity,
            registrationId = RegistrationId(1001),
        )
        val protocol = KryptonConfigurator().apply {
            identityKeyPair = identity
            registrationId = RegistrationId(1001)
            identityKeyStore = iks
            this.bridge = bridge
        }.build()
        return ZkGroup(protocol)
    }

    @Test
    fun `zkgroup wrapper delegates every operation to the bridge`() {
        val zk = createZkGroup()
        val masterKey = ByteArray(32) { it.toByte() }
        val profileKey = ByteArray(32) { (it + 1).toByte() }
        val aci = "84fd7196-b3fa-4d4d-bbf8-8f1cd1d75f3a"

        val params = zk.deriveGroupSecretParams(masterKey)
        assertTrue(params.isSuccess)
        assertContentEquals(masterKey + 0xCC.toByte(), params.getOrNull()!!.groupIdentifier)

        val access = zk.deriveProfileKeyAccessKey(profileKey)
        assertTrue(access.isSuccess)
        assertEquals(16, access.getOrNull()!!.size)

        val version = zk.profileKeyVersion(profileKey, aci)
        assertEquals("$aci:32", version.getOrNull())

        val commitment = zk.profileKeyCommitment(profileKey, aci)
        assertContentEquals(aci.encodeToByteArray() + profileKey, commitment.getOrNull())
    }

    @Test
    fun `zkgroup wrapper delegates group cipher operations to the bridge`() {
        val zk = createZkGroup()
        val secretParams = ByteArray(289) { it.toByte() }
        val profileKey = ByteArray(32) { (it + 1).toByte() }
        val aci = "84fd7196-b3fa-4d4d-bbf8-8f1cd1d75f3a"

        val sidCt = zk.encryptServiceId(secretParams, aci)
        assertTrue(sidCt.isSuccess)
        assertEquals(aci, zk.decryptServiceId(secretParams, sidCt.getOrNull()!!).getOrNull())

        val pkCt = zk.encryptProfileKey(secretParams, profileKey, aci)
        assertTrue(pkCt.isSuccess)
        assertContentEquals(profileKey, zk.decryptProfileKey(secretParams, pkCt.getOrNull()!!, aci).getOrNull())

        val blob = zk.encryptBlob(secretParams, "hi".encodeToByteArray())
        assertContentEquals("hi".encodeToByteArray(), zk.decryptBlob(secretParams, blob.getOrNull()!!).getOrNull())
    }
}
