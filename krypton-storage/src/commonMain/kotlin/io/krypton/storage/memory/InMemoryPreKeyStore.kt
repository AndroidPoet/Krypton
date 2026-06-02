package io.krypton.storage.memory

import io.krypton.core.result.CryptoError
import io.krypton.core.result.CryptoResult
import io.krypton.core.types.PreKey
import io.krypton.core.types.SignedPreKey
import io.krypton.storage.api.PreKeyStore

/**
 * In-memory implementation of [PreKeyStore].
 *
 * **Not persisted across app restarts.** Use for testing/prototyping.
 */
public class InMemoryPreKeyStore : PreKeyStore {

    private val signedPreKeys = mutableMapOf<Int, SignedPreKey>()
    private var currentSignedPreKeyId: Int = 0
    private val oneTimePreKeys = mutableMapOf<Int, PreKey>()

    override suspend fun saveSignedPreKey(keyId: Int, record: SignedPreKey): CryptoResult<Unit> =
        CryptoResult.catching {
            signedPreKeys[keyId] = record
            currentSignedPreKeyId = keyId
        }

    override suspend fun loadSignedPreKey(keyId: Int): CryptoResult<SignedPreKey> =
        signedPreKeys[keyId]?.let { CryptoResult.Success(it) }
            ?: CryptoResult.Failure(CryptoError.notFound("Signed pre-key $keyId not found"))

    override suspend fun getCurrentSignedPreKeyId(): CryptoResult<Int> =
        CryptoResult.Success(currentSignedPreKeyId)

    override suspend fun storePreKey(keyId: Int, record: PreKey): CryptoResult<Unit> =
        CryptoResult.catching { oneTimePreKeys[keyId] = record }

    override suspend fun loadPreKey(keyId: Int): CryptoResult<PreKey> =
        oneTimePreKeys.remove(keyId)?.let { CryptoResult.Success(it) }
            ?: CryptoResult.Failure(CryptoError.notFound("One-time pre-key $keyId not found"))

    override suspend fun removePreKey(keyId: Int): CryptoResult<Unit> =
        CryptoResult.catching { oneTimePreKeys.remove(keyId) }

    override suspend fun preKeyCount(): CryptoResult<Int> =
        CryptoResult.Success(oneTimePreKeys.size)
}
