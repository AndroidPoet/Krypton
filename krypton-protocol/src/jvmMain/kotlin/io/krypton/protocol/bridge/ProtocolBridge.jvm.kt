package io.krypton.protocol.bridge

import io.krypton.core.types.IdentityKeyPair
import io.krypton.core.types.RegistrationId
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore

/**
 * JVM/Desktop platform bridge factory.
 *
 * Creates a [RealBridge] that uses the libsignal-client Java SDK
 * with its JNI native backend.
 */
public actual fun createPlatformBridge(
    identityKeyStore: IdentityKeyStore,
    sessionStore: SessionStore,
    preKeyStore: PreKeyStore,
    senderKeyStore: SenderKeyStore,
    identityKeyPair: IdentityKeyPair,
    registrationId: RegistrationId,
): Bridge = RealBridge(
    identityKeyStore = identityKeyStore,
    sessionStore = sessionStore,
    preKeyStore = preKeyStore,
    senderKeyStore = senderKeyStore,
    identityKeyPair = identityKeyPair,
    registrationId = registrationId,
)
