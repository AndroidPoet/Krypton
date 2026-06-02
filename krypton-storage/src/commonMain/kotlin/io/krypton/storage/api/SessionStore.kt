package io.krypton.storage.api

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.ProtocolAddress

/**
 * Persistent store for Signal Protocol sessions.
 *
 * Sessions are serialised byte arrays; the protocol layer handles
 * the encode/decode. This store just persists them by address.
 */
public interface SessionStore {

    /** Persists a session record for the given [address]. */
    public suspend fun storeSession(
        address: ProtocolAddress,
        record: ByteArray,
    ): CryptoResult<Unit>

    /** Loads the session record for [address], or `null` if none exists. */
    public suspend fun loadSession(address: ProtocolAddress): CryptoResult<ByteArray?>

    /** Returns `true` if a session exists for [address]. */
    public suspend fun containsSession(address: ProtocolAddress): CryptoResult<Boolean>

    /** Deletes the session for [address]. */
    public suspend fun deleteSession(address: ProtocolAddress): CryptoResult<Unit>

    /** Deletes all sessions for the given [name] (all devices). */
    public suspend fun deleteAllSessions(name: String): CryptoResult<Unit>

    /** Returns all addresses that have sessions stored. */
    public suspend fun getAllAddresses(): CryptoResult<List<ProtocolAddress>>
}
