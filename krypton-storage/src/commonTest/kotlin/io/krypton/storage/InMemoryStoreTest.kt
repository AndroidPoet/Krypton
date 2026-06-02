package io.krypton.storage

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.*
import io.krypton.storage.memory.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class InMemoryStoreTest {

    private val address = ProtocolAddress("alice", DeviceId.PRIMARY)
    private val identity = PublicKey(ByteArray(32) { 42 })

    // ── IdentityKeyStore ───────────────────────────────────────────────────

    @Test
    fun `identity store save and retrieve`() = runTest {
        val store = InMemoryIdentityKeyStore(
            identityKeyPair = CryptoResult.Success(
                IdentityKeyPair(IdentityKey(PublicKey(ByteArray(32) { 1 }), 0), PrivateKey(ByteArray(32) { 2 }))
            ),
            localRegistrationId = CryptoResult.Success(1234),
        )

        val saveResult = store.saveIdentity(address, identity)
        assertTrue(saveResult.isSuccess)

        val loaded = store.getIdentity(address)
        assertEquals(identity, loaded.getOrNull()!!)
    }

    @Test
    fun `identity store unknown returns null`() = runTest {
        val store = InMemoryIdentityKeyStore(
            identityKeyPair = CryptoResult.Success(
                IdentityKeyPair(IdentityKey(PublicKey(ByteArray(32) { 1 }), 0), PrivateKey(ByteArray(32) { 2 }))
            ),
            localRegistrationId = CryptoResult.Success(1234),
        )

        val loaded = store.getIdentity(ProtocolAddress("unknown"))
        assertNull(loaded.getOrNull())
    }

    @Test
    fun `identity store trusted by default`() = runTest {
        val store = InMemoryIdentityKeyStore(
            identityKeyPair = CryptoResult.Success(
                IdentityKeyPair(IdentityKey(PublicKey(ByteArray(32) { 1 }), 0), PrivateKey(ByteArray(32) { 2 }))
            ),
            localRegistrationId = CryptoResult.Success(1234),
        )

        val trusted = store.isTrustedIdentity(address, identity)
        assertTrue(trusted.getOrNull()!!)
    }

    @Test
    fun `changed identity key is untrusted`() = runTest {
        val store = InMemoryIdentityKeyStore(
            identityKeyPair = CryptoResult.Success(
                IdentityKeyPair(IdentityKey(PublicKey(ByteArray(32) { 1 }), 0), PrivateKey(ByteArray(32) { 2 }))
            ),
            localRegistrationId = CryptoResult.Success(1234),
        )

        store.saveIdentity(address, identity)
        val differentKey = PublicKey(ByteArray(32) { 99 })
        val trusted = store.isTrustedIdentity(address, differentKey)
        assertFalse(trusted.getOrNull()!!, "Changed key should not be trusted")
    }

    // ── PreKeyStore ────────────────────────────────────────────────────────

    @Test
    fun `pre key store save and load`() = runTest {
        val store = InMemoryPreKeyStore()
        val preKey = PreKey(1, KeyPair(PublicKey(ByteArray(32) { 1 }), PrivateKey(ByteArray(32) { 2 })))

        store.storePreKey(1, preKey)
        val loaded = store.loadPreKey(1)

        assertEquals(preKey, loaded.getOrNull()!!)
    }

    @Test
    fun `pre key store load removes key`() = runTest {
        val store = InMemoryPreKeyStore()
        val preKey = PreKey(1, KeyPair(PublicKey(ByteArray(32) { 1 }), PrivateKey(ByteArray(32) { 2 })))

        store.storePreKey(1, preKey)
        store.loadPreKey(1)

        val count = store.preKeyCount()
        assertEquals(0, count.getOrNull()!!)
    }

    @Test
    fun `pre key store missing key fails`() = runTest {
        val store = InMemoryPreKeyStore()
        val result = store.loadPreKey(999)
        assertTrue(result.isFailure)
    }

    @Test
    fun `signed pre key store and retrieve`() = runTest {
        val store = InMemoryPreKeyStore()
        val spk = SignedPreKey(1, KeyPair(PublicKey(ByteArray(32) { 1 }), PrivateKey(ByteArray(32) { 2 })), ByteArray(64) { 3 })

        store.saveSignedPreKey(1, spk)
        val loaded = store.loadSignedPreKey(1)

        assertEquals(spk, loaded.getOrNull()!!)
    }

    // ── SessionStore ───────────────────────────────────────────────────────

    @Test
    fun `session store save and load`() = runTest {
        val store = InMemorySessionStore()
        val sessionData = ByteArray(128) { 7 }

        store.storeSession(address, sessionData)
        val loaded = store.loadSession(address)

        assertContentEquals(sessionData, loaded.getOrNull()!!)
    }

    @Test
    fun `session store contains session`() = runTest {
        val store = InMemorySessionStore()
        store.storeSession(address, ByteArray(10) { 1 })

        val contains = store.containsSession(address)
        assertTrue(contains.getOrNull()!!)

        val notContains = store.containsSession(ProtocolAddress("unknown"))
        assertFalse(notContains.getOrNull()!!)
    }

    @Test
    fun `session store delete`() = runTest {
        val store = InMemorySessionStore()
        store.storeSession(address, ByteArray(10) { 1 })
        store.deleteSession(address)

        val loaded = store.loadSession(address)
        assertNull(loaded.getOrNull())
    }

    @Test
    fun `session store delete all for name`() = runTest {
        val store = InMemorySessionStore()
        store.storeSession(ProtocolAddress("alice", DeviceId(1)), ByteArray(1) { 1 })
        store.storeSession(ProtocolAddress("alice", DeviceId(2)), ByteArray(1) { 2 })
        store.storeSession(ProtocolAddress("bob", DeviceId.PRIMARY), ByteArray(1) { 3 })

        store.deleteAllSessions("alice")

        val addresses = store.getAllAddresses()
        assertEquals(1, addresses.getOrNull()!!.size)
    }

    // ── InMemoryStores convenience ─────────────────────────────────────────

    @Test
    fun `inMemoryStores creates all stores`() {
        val ikp = IdentityKeyPair(IdentityKey(PublicKey(ByteArray(32) { 1 }), 0), PrivateKey(ByteArray(32) { 2 }))
        val stores = InMemoryStores(ikp, 5678)

        assertNotNull(stores.identityKeyStore)
        assertNotNull(stores.preKeyStore)
        assertNotNull(stores.sessionStore)
        assertNotNull(stores.senderKeyStore)
    }
}
