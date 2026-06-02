package io.krypton.protocol.bridge

import io.krypton.core.types.*

/**
 * WASM/JS platform bridge factory.
 *
 * Returns a bridge that fails until libsignal is compiled to WASM.
 * When ready, use `@JsModule("@signalapp/libsignal")` to import.
 */
public actual fun createPlatformBridge(
    identityKeyStore: io.krypton.storage.api.IdentityKeyStore,
    sessionStore: io.krypton.storage.api.SessionStore,
    preKeyStore: io.krypton.storage.api.PreKeyStore,
    senderKeyStore: io.krypton.storage.api.SenderKeyStore,
    identityKeyPair: IdentityKeyPair,
    registrationId: RegistrationId,
): Bridge = NotImplementedBridge(
    identityKeyStore, sessionStore, preKeyStore, senderKeyStore,
    identityKeyPair, registrationId, "WASM bridge not loaded — build libsignal for wasm32"
)
