package io.krypton.protocol.api

import io.krypton.core.result.KryptonDsl
import io.krypton.core.types.IdentityKeyPair
import io.krypton.core.types.RegistrationId
import io.krypton.protocol.bridge.Bridge
import io.krypton.protocol.bridge.createPlatformBridge
import io.krypton.protocol.bridge.createPlatformIdentityKeyPair
import io.krypton.protocol.bridge.createPlatformRegistrationId
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore
import io.krypton.storage.memory.InMemoryIdentityKeyStore
import io.krypton.storage.memory.InMemoryPreKeyStore
import io.krypton.storage.memory.InMemorySenderKeyStore
import io.krypton.storage.memory.InMemorySessionStore

/**
 * Builder DSL for configuring a [KryptonProtocol] instance.
 *
 * ```
 * // Quick start — auto-generates keys, uses platform-native crypto
 * val protocol = Krypton.protocol { }
 *
 * // Advanced — customize everything
 * val protocol = Krypton.protocol {
 *     identityKeyPair = myKeyPair
 *     registrationId = RegistrationId(1234)
 *     sessionStore = myPersistentStore
 * }
 * ```
 */
@KryptonDsl
public class KryptonConfigurator {

    /** Your device's long-term identity key pair. Auto-generated if not set. */
    public var identityKeyPair: IdentityKeyPair? = null

    /** Your device's registration ID. Auto-generated if not set. */
    public var registrationId: RegistrationId? = null

    /** Identity key store. Defaults to [InMemoryIdentityKeyStore]. */
    public var identityKeyStore: IdentityKeyStore? = null

    /** Session store. Defaults to [InMemorySessionStore]. */
    public var sessionStore: SessionStore? = null

    /** Pre-key store. Defaults to [InMemoryPreKeyStore]. */
    public var preKeyStore: PreKeyStore? = null

    /** Sender key store. Defaults to [InMemorySenderKeyStore]. */
    public var senderKeyStore: SenderKeyStore? = null

    /** Native crypto bridge. Defaults to platform bridge (libsignal on JVM). */
    public var bridge: Bridge? = null

    public fun build(): KryptonProtocol {
        val ikp = identityKeyPair ?: createPlatformIdentityKeyPair()
        val rid = registrationId ?: createPlatformRegistrationId()

        val iks = identityKeyStore ?: InMemoryIdentityKeyStore(
            identityKeyPair = io.krypton.core.result.CryptoResult.Success(ikp),
            localRegistrationId = io.krypton.core.result.CryptoResult.Success(rid.value),
        )
        val ss = sessionStore ?: InMemorySessionStore()
        val pks = preKeyStore ?: InMemoryPreKeyStore()
        val sks = senderKeyStore ?: InMemorySenderKeyStore()
        val b = bridge ?: createPlatformBridge(iks, ss, pks, sks, ikp, rid)

        return KryptonProtocolImpl(ikp, rid, iks, ss, pks, sks, bridge = b)
    }
}
