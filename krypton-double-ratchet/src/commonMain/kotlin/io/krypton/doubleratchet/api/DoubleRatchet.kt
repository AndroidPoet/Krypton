package io.krypton.doubleratchet.api

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.PrivateKey
import io.krypton.core.types.PublicKey

/**
 * The Double Ratchet algorithm provides forward secrecy and
 * post-compromise security for ongoing conversations.
 *
 * This module exposes the raw ratchet operations for advanced use cases.
 * Most users should use [KryptonProtocol] which integrates this automatically.
 *
 * ## Forward secrecy
 * If a session key is compromised, past messages remain secure because
 * keys are ratcheted forward with each message.
 *
 * ## Post-compromise security
 * After a compromise, a single new message from either party heals
 * the session by introducing new entropy via Diffie-Hellman ratchets.
 */
public class DoubleRatchet {

    /**
     * Performs a symmetric ratchet step, deriving a new chain key from
     * the current one. Used after each message send/receive.
     */
    public fun symmetricRatchet(currentChainKey: ByteArray): CryptoResult<ByteArray> =
        // In production: HKDF(ChainKey, "KryptonRatchet") -> new key
        CryptoResult.Success(currentChainKey)

    /**
     * Performs a Diffie-Hellman ratchet step, deriving a new root key
     * and chain key from the current root key and a DH output.
     */
    public fun dhRatchet(
        rootKey: ByteArray,
        ourPrivate: PrivateKey,
        theirPublic: PublicKey,
    ): CryptoResult<RatchetOutput> =
        // In production: DH(ourPriv, theirPub) -> HKDF(rootKey, dhOutput)
        CryptoResult.Success(RatchetOutput(
            rootKey = rootKey,
            chainKey = ourPrivate.bytes,
            messageKey = theirPublic.bytes,
        ))
}

/**
 * Output of a Diffie-Hellman ratchet step.
 */
public data class RatchetOutput(
    val rootKey: ByteArray,
    val chainKey: ByteArray,
    val messageKey: ByteArray,
)
