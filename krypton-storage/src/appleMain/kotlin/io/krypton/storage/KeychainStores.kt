package io.krypton.storage

import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore
import platform.Security.*
import kotlinx.cinterop.*

/**
 * iOS/macOS production stores using the system Keychain.
 *
 * ```
 * val stores = KeychainStores(identityKeyPair, regId)
 * ```
 */
public class KeychainStores(
    identityKeyPair: io.krypton.core.types.IdentityKeyPair,
    registrationId: Int,
) {
    public val identityKeyStore: IdentityKeyStore = TODO("Wire KeychainIdentityStore")
    public val preKeyStore: PreKeyStore = TODO("Wire KeychainPreKeyStore")
    public val sessionStore: SessionStore = TODO("Wire KeychainSessionStore")
    public val senderKeyStore: SenderKeyStore = TODO("Wire KeychainSenderKeyStore")
}
