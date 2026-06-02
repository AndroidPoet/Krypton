package io.krypton.protocol.bridge

import io.krypton.core.types.*
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore
import org.signal.libsignal.protocol.ecc.ECKeyPair
import kotlin.random.Random

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

/**
 * JVM: Generates a real Curve25519 identity key pair using libsignal.
 */
public actual fun createPlatformIdentityKeyPair(): IdentityKeyPair {
    val kp = ECKeyPair.generate()
    return IdentityKeyPair(
        identityKey = IdentityKey(
            publicKey = PublicKey(kp.publicKey.serialize()),
            keyId = 0,
        ),
        privateKey = PrivateKey(kp.privateKey.serialize()),
    )
}

/**
 * JVM: Generates a random valid registration ID.
 */
public actual fun createPlatformRegistrationId(): RegistrationId =
    RegistrationId(Random.nextInt(1, 0x3FFF))
