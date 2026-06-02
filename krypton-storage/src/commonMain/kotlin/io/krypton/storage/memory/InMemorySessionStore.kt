package io.krypton.storage.memory

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.DeviceId
import io.krypton.core.types.ProtocolAddress
import io.krypton.storage.api.SessionStore

/**
 * In-memory implementation of [SessionStore].
 *
 * **Not persisted across app restarts.** Use for testing/prototyping.
 */
public class InMemorySessionStore : SessionStore {

    private val sessions = mutableMapOf<ProtocolAddress, ByteArray>()

    override suspend fun storeSession(address: ProtocolAddress, record: ByteArray): CryptoResult<Unit> =
        CryptoResult.catching { sessions[address] = record }

    override suspend fun loadSession(address: ProtocolAddress): CryptoResult<ByteArray?> =
        CryptoResult.Success(sessions[address])

    override suspend fun containsSession(address: ProtocolAddress): CryptoResult<Boolean> =
        CryptoResult.Success(sessions.containsKey(address))

    override suspend fun deleteSession(address: ProtocolAddress): CryptoResult<Unit> =
        CryptoResult.catching { sessions.remove(address) }

    override suspend fun deleteAllSessions(name: String): CryptoResult<Unit> =
        CryptoResult.catching {
            sessions.keys.removeAll { it.name == name }
        }

    override suspend fun getAllAddresses(): CryptoResult<List<ProtocolAddress>> =
        CryptoResult.Success(sessions.keys.toList())
}
