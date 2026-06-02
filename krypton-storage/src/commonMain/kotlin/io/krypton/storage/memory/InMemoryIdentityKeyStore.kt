package io.krypton.storage.memory

import io.krypton.core.result.CryptoError
import io.krypton.core.result.CryptoResult
import io.krypton.core.types.IdentityKeyPair
import io.krypton.core.types.ProtocolAddress
import io.krypton.core.types.PublicKey
import io.krypton.storage.api.IdentityKeyStore

/**
 * In-memory implementation of [IdentityKeyStore].
 *
 * **Not persisted across app restarts.** Perfect for testing and prototyping.
 * Replace with [EncryptedDatabaseStore] or [KeychainStore] for production.
 */
public class InMemoryIdentityKeyStore(
    override val identityKeyPair: CryptoResult<IdentityKeyPair>,
    override val localRegistrationId: CryptoResult<Int>,
) : IdentityKeyStore {

    private val identities = mutableMapOf<ProtocolAddress, TrustedKey>()

    private data class TrustedKey(
        val publicKey: PublicKey,
        val trusted: Boolean = true,
    )

    override suspend fun saveIdentity(
        address: ProtocolAddress,
        identityKey: PublicKey,
        trusted: Boolean,
    ): CryptoResult<Unit> = CryptoResult.catching {
        identities[address] = TrustedKey(identityKey, trusted)
    }

    override suspend fun getIdentity(address: ProtocolAddress): CryptoResult<PublicKey?> =
        CryptoResult.Success(identities[address]?.publicKey)

    override suspend fun isTrustedIdentity(
        address: ProtocolAddress,
        identityKey: PublicKey,
    ): CryptoResult<Boolean> = CryptoResult.catching {
        val stored = identities[address]
        when {
            stored == null -> true  // No prior key = trusted by default
            stored.publicKey == identityKey && stored.trusted -> true
            stored.publicKey == identityKey && !stored.trusted -> false
            else -> false  // Key changed = untrusted (key change detected)
        }
    }
}
