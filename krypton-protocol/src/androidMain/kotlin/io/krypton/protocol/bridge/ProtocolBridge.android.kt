package io.krypton.protocol.bridge

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.*

/**
 * Android platform bridge factory.
 *
 * Returns a bridge that fails until libsignal_jni.so is bundled.
 * See: https://github.com/signalapp/libsignal for Android build instructions.
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
    identityKeyPair, registrationId, "Android native lib not loaded"
)
