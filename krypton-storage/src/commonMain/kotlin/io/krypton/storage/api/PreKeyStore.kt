package io.krypton.storage.api

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.PreKey
import io.krypton.core.types.SignedPreKey

/**
 * Persistent store for one-time pre-keys and signed pre-keys.
 */
public interface PreKeyStore {

    /** Saves a [SignedPreKey] as the current one. */
    public suspend fun saveSignedPreKey(keyId: Int, record: SignedPreKey): CryptoResult<Unit>

    /** Loads the signed pre-key with [keyId]. */
    public suspend fun loadSignedPreKey(keyId: Int): CryptoResult<SignedPreKey>

    /** Returns the key ID of the current signed pre-key. */
    public suspend fun getCurrentSignedPreKeyId(): CryptoResult<Int>

    /** Stores a one-time [PreKey]. */
    public suspend fun storePreKey(keyId: Int, record: PreKey): CryptoResult<Unit>

    /** Loads (and removes) a one-time pre-key. */
    public suspend fun loadPreKey(keyId: Int): CryptoResult<PreKey>

    /** Removes a one-time pre-key after use. */
    public suspend fun removePreKey(keyId: Int): CryptoResult<Unit>

    /** Returns the count of remaining one-time pre-keys. */
    public suspend fun preKeyCount(): CryptoResult<Int>
}
