package io.krypton.storage.api

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.IdentityKeyPair
import io.krypton.core.types.ProtocolAddress
import io.krypton.core.types.PublicKey

/**
 * Persistent store for identity keys and trust decisions.
 *
 * Each (address, identityKey) pair records whether we *trust* that
 * identity key for a given user. The local identity key pair is also
 * held here.
 */
public interface IdentityKeyStore {

    /** This device's permanent identity key pair. */
    public val identityKeyPair: CryptoResult<IdentityKeyPair>

    /** The local device's registration ID (unique per install). */
    public val localRegistrationId: CryptoResult<Int>

    /**
     * Saves a trust decision for a remote identity key.
     * @param address    The remote address.
     * @param identityKey The remote public identity key.
     * @param trusted     Whether this key is trusted (true) or untrusted (false).
     */
    public suspend fun saveIdentity(
        address: ProtocolAddress,
        identityKey: PublicKey,
        trusted: Boolean = true,
    ): CryptoResult<Unit>

    /**
     * Returns the stored identity key for [address], or `null` if unknown.
     */
    public suspend fun getIdentity(address: ProtocolAddress): CryptoResult<PublicKey?>

    /**
     * Returns `true` if we have a stored (and trusted) identity for [address].
     */
    public suspend fun isTrustedIdentity(
        address: ProtocolAddress,
        identityKey: PublicKey,
    ): CryptoResult<Boolean>
}
