package io.krypton.protocol.bridge

import io.krypton.core.types.*

/**
 * Apple platform bridge factory (iOS, macOS, tvOS, watchOS via Kotlin/Native).
 *
 * Returns a bridge that fails until libsignal_ffi is linked.
 * Build libsignal for Apple platforms:
 *   cargo build -p libsignal-ffi --release --target aarch64-apple-ios
 * Then link via cinterop.
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
    identityKeyPair, registrationId, "Apple FFI not yet linked — build libsignal_ffi"
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
