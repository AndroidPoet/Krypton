package io.krypton.storage.api

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.ProtocolAddress

/**
 * Persistent store for sender key records (used in group messaging).
 */
public interface SenderKeyStore {

    public suspend fun storeSenderKey(
        sender: ProtocolAddress,
        distributionId: String,
        record: ByteArray,
    ): CryptoResult<Unit>

    public suspend fun loadSenderKey(
        sender: ProtocolAddress,
        distributionId: String,
    ): CryptoResult<ByteArray?>
}
