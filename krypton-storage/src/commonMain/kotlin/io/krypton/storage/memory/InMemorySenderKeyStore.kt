package io.krypton.storage.memory

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.ProtocolAddress
import io.krypton.storage.api.SenderKeyStore

/**
 * In-memory implementation of [SenderKeyStore].
 */
public class InMemorySenderKeyStore : SenderKeyStore {

    private data class SenderKeyKey(val sender: ProtocolAddress, val distributionId: String)
    private val keys = mutableMapOf<SenderKeyKey, ByteArray>()

    override suspend fun storeSenderKey(
        sender: ProtocolAddress,
        distributionId: String,
        record: ByteArray,
    ): CryptoResult<Unit> = CryptoResult.catching {
        keys[SenderKeyKey(sender, distributionId)] = record
    }

    override suspend fun loadSenderKey(
        sender: ProtocolAddress,
        distributionId: String,
    ): CryptoResult<ByteArray?> = CryptoResult.Success(
        keys[SenderKeyKey(sender, distributionId)]
    )
}
