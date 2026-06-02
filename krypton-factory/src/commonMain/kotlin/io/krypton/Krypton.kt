package io.krypton

import io.krypton.core.result.KryptonDsl
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.protocol.api.KryptonProtocol

/**
 * The main entry point to the Krypton encryption library.
 *
 * This is the **only** import you need to get started:
 *
 * ```
 * import io.krypton.Krypton
 * import io.krypton.core.types.*
 * import io.krypton.storage.memory.InMemoryStores
 *
 * // 1. Generate your identity (once, persist it)
 * val myIdentity = IdentityKeyPair(
 *     IdentityKey(PublicKey(ByteArray(32) { 1 }), 0),
 *     PrivateKey(ByteArray(32) { 2 }),
 * )
 *
 * // 2. Create the protocol client
 * val client = Krypton.protocol {
 *     identityKeyPair = myIdentity
 *     registrationId = RegistrationId(5678)
 *     // All stores default to in-memory — swap for production later
 * }
 *
 * // 3. Encrypt a message
 * val result = client.encrypt(
 *     ProtocolAddress("alice", DeviceId.PRIMARY),
 *     "Hello, Alice! 👋".encodeToByteArray(),
 * )
 *
 * result
 *     .onSuccess { msg -> sendToServer(msg.serialized) }
 *     .onFailure { err -> log("Encryption failed: $err") }
 * ```
 */
public object Krypton {

    /**
     * Creates a new [KryptonProtocol] client with the given configuration.
     *
     * @param configure A builder block on [KryptonConfigurator].
     * @return A fully wired [KryptonProtocol] instance.
     */
    @JvmStatic
    public fun protocol(configure: KryptonConfigurator.() -> Unit): KryptonProtocol {
        val configurator = KryptonConfigurator()
        configurator.configure()
        return configurator.build()
    }

    /**
     * Creates a [KryptonProtocol] from a pre-built [KryptonConfigurator].
     */
    @JvmStatic
    public fun protocol(configurator: KryptonConfigurator): KryptonProtocol =
        configurator.build()

    // ── Quick-start convenience ────────────────────────────────────────────

    /**
     * Creates a [KryptonProtocol] with minimal required parameters.
     * All stores default to in-memory.
     */
    @JvmStatic
    public fun protocol(
        identityKeyPair: io.krypton.core.types.IdentityKeyPair,
        registrationId: io.krypton.core.types.RegistrationId,
    ): KryptonProtocol = protocol {
        this.identityKeyPair = identityKeyPair
        this.registrationId = registrationId
    }

    /**
     * Returns the version of the Krypton library.
     */
    public val version: String = "0.1.0"
}
