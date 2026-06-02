package io.krypton.protocol.bridge

import io.krypton.core.types.*

/**
 * Windows x64 (MinGW) platform bridge factory.
 *
 * Uses the libsignal-client JAR which bundles native .dll for Windows amd64.
 * The JAR's Native.java static initializer loads the bundled library.
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
    identityKeyPair, registrationId, "Windows native DLL not loaded from JAR"
)
