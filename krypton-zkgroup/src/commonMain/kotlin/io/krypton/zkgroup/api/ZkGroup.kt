package io.krypton.zkgroup.api

import io.krypton.core.result.CryptoResult
import io.krypton.protocol.api.KryptonProtocol
import io.krypton.protocol.models.GroupSecretParamsResult

/**
 * Zero-knowledge group operations (libsignal's zkgroup), exposed as a thin
 * wrapper over [KryptonProtocol].
 *
 * ## What's implemented (client-side, deterministic — no server, real libsignal)
 * - **Group params**: derive a group's secret/public params and its stable
 *   identifier from the 32-byte group master key.
 * - **Profile access key**: derive the access key from a profile key (used to
 *   sealed-send to people who aren't your contacts).
 * - **Profile key version / commitment**: the opaque handles the server uses to
 *   serve and verify a profile without ever seeing the profile key.
 *
 * These are real on JVM/Android and **fail loud** on platforms where the bridge
 * hasn't wired zkgroup yet — they never return a fake/placeholder result.
 *
 * ## What's NOT here yet
 * The full credential dance (auth credentials, profile-key credentials, group
 * membership **proofs/presentations**) requires server-issued credentials and a
 * server params exchange. Those are intentionally absent rather than stubbed —
 * call into libsignal directly if you need them, or open an issue.
 */
public class ZkGroup(
    private val protocol: KryptonProtocol,
) {
    /**
     * Derive a group's deterministic secret/public params and stable identifier
     * from its 32-byte [masterKey].
     */
    public fun deriveGroupSecretParams(masterKey: ByteArray): CryptoResult<GroupSecretParamsResult> =
        protocol.deriveGroupSecretParams(masterKey)

    /**
     * Derive the 16-byte access key from a 32-byte [profileKey].
     */
    public fun deriveProfileKeyAccessKey(profileKey: ByteArray): CryptoResult<ByteArray> =
        protocol.deriveProfileKeyAccessKey(profileKey)

    /**
     * Compute the profile-key version string for the account [aciUuid].
     */
    public fun profileKeyVersion(profileKey: ByteArray, aciUuid: String): CryptoResult<String> =
        protocol.profileKeyVersion(profileKey, aciUuid)

    /**
     * Compute the profile-key commitment for the account [aciUuid].
     */
    public fun profileKeyCommitment(profileKey: ByteArray, aciUuid: String): CryptoResult<ByteArray> =
        protocol.profileKeyCommitment(profileKey, aciUuid)
}
