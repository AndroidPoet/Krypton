package io.krypton.protocol.bridge

import io.krypton.core.types.*

/**
 * Apple platform bridge factory (iOS, macOS, tvOS, watchOS via Kotlin/Native).
 *
 * Creates a [NativeBridge] that calls libsignal_ffi via cinterop.
 * Requires libsignal_ffi.a to be linked (see build.gradle.kts cinterop config).
 *
 * Build libsignal_ffi for Apple platforms:
 *   cargo build -p libsignal-ffi --release --target aarch64-apple-ios
 *   cargo build -p libsignal-ffi --release --target aarch64-apple-darwin
 * etc.
 */
public actual fun createPlatformBridge(
    identityKeyStore: io.krypton.storage.api.IdentityKeyStore,
    sessionStore: io.krypton.storage.api.SessionStore,
    preKeyStore: io.krypton.storage.api.PreKeyStore,
    senderKeyStore: io.krypton.storage.api.SenderKeyStore,
    identityKeyPair: IdentityKeyPair,
    registrationId: RegistrationId,
): Bridge = NativeBridge(
    identityKeyStore, sessionStore, preKeyStore, senderKeyStore,
    identityKeyPair, registrationId,
)

/**
 * Apple platform: generates real Curve25519 keys via libsignal_ffi cinterop.
 */
public actual fun createPlatformIdentityKeyPair(): IdentityKeyPair =
    NativeBridge.generateIdentityKeyPair()

/**
 * Apple platform: generates a random valid registration ID.
 */
public actual fun createPlatformRegistrationId(): RegistrationId =
    RegistrationId(kotlin.random.Random.nextInt(1, 0x3FFF))
