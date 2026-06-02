package io.krypton.protocol

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.*
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.storage.memory.InMemoryPreKeyStore
import io.krypton.storage.memory.InMemorySessionStore
import kotlin.test.*

class KryptonConfiguratorTest {

    private val identity = IdentityKeyPair(
        IdentityKey(PublicKey(ByteArray(32) { 1 }), 0),
        PrivateKey(ByteArray(32) { 2 }),
    )

    @Test
    fun `configurator builds with required fields`() {
        val protocol = KryptonConfigurator().apply {
            identityKeyPair = identity
            registrationId = RegistrationId(1234)
            bridge = FakeBridge(
                identityKeyPair = identity,
                registrationId = RegistrationId(1234),
            )
        }.build()

        assertEquals(identity, protocol.identityKeyPair)
        assertEquals(RegistrationId(1234), protocol.registrationId)
    }

    @Test
    fun `configurator auto-generates identity key pair if not provided`() {
        val protocol = KryptonConfigurator().apply {
            registrationId = RegistrationId(1234)
        }.build()

        assertNotNull(protocol.identityKeyPair)
    }

    @Test
    fun `configurator auto-generates registration id if not provided`() {
        val protocol = KryptonConfigurator().apply {
            bridge = FakeBridge(
                identityKeyPair = identity,
                registrationId = RegistrationId(1234),
            )
        }.build()

        assertNotNull(protocol.registrationId)
    }

    @Test
    fun `configurator accepts custom stores`() {
        val customSessionStore = InMemorySessionStore()
        val customPreKeyStore = InMemoryPreKeyStore()

        val protocol = KryptonConfigurator().apply {
            identityKeyPair = identity
            registrationId = RegistrationId(1234)
            sessionStore = customSessionStore
            preKeyStore = customPreKeyStore
            bridge = FakeBridge(
                identityKeyPair = identity,
                registrationId = RegistrationId(1234),
            )
        }.build()

        assertNotNull(protocol)
    }

    @Test
    fun `configurator accepts custom bridge`() {
        val fakeBridge = FakeBridge()

        val protocol = KryptonConfigurator().apply {
            identityKeyPair = identity
            registrationId = RegistrationId(1234)
            bridge = fakeBridge
        }.build()

        assertNotNull(protocol)
    }
}
