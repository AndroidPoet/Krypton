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
