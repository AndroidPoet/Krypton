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

/**
 * Unsupported platform: returns deterministic test keys.
 * Real keys require bundling the native libsignal library.
 */
public actual public actual fun createPlatformIdentityKeyPair(): IdentityKeyPair =
    IdentityKeyPair(
        IdentityKey(PublicKey(ByteArray(32) { 1 }), 0),
        PrivateKey(ByteArray(32) { 2 }),
    )

/**
 * Unsupported platform: generates a random valid registration ID.
 */
public actual public actual fun createPlatformRegistrationId(): RegistrationId =
    RegistrationId(kotlin.random.Random.nextInt(1, 0x3FFF))
