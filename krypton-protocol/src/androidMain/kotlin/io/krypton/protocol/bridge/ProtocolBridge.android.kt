package io.krypton.protocol.bridge

import io.krypton.core.types.*
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore
import org.signal.libsignal.protocol.ecc.ECKeyPair
import kotlin.random.Random

/**
 * Android platform bridge factory.
 *
 * Android uses the same Java JNI backend as JVM.
 * The libsignal-client Maven artifact bundles libsignal_jni.so
 * for arm64-v8a, armeabi-v7a, and x86_64.
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
 * Android: Generates real Curve25519 keys via libsignal's JNI.
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
 * Android: Generates a random valid registration ID.
 */
public actual fun createPlatformRegistrationId(): RegistrationId =
    RegistrationId(Random.nextInt(1, 0x3FFF))
