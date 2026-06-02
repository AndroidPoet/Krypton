package io.krypton.storage.memory

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.IdentityKeyPair
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore

/**
 * Convenience holder for all in-memory stores.
 * Perfect for getting started quickly — just swap individual stores
 * for production implementations when you're ready.
 *
 * ```
 * val stores = InMemoryStores(myIdentityKeyPair, registrationId)
 * val krypton = Krypton.create { stores = stores }
 * ```
 */
public class InMemoryStores(
    identityKeyPair: IdentityKeyPair,
    registrationId: Int,
) {
    public val identityKeyStore: IdentityKeyStore = InMemoryIdentityKeyStore(
        identityKeyPair = CryptoResult.Success(identityKeyPair),
        localRegistrationId = CryptoResult.Success(registrationId),
    )
    public val preKeyStore: PreKeyStore = InMemoryPreKeyStore()
    public val sessionStore: SessionStore = InMemorySessionStore()
    public val senderKeyStore: SenderKeyStore = InMemorySenderKeyStore()
}
