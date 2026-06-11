package io.krypton.storage

import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore

/**
 * Linux production stores using the Secret Service API (dbus secret-storage)
 * or encrypted files at `~/.config/krypton/`.
 *
 * ```
 * val stores = LinuxSecretServiceStores(identityKeyPair, regId)
 * ```
 */
public class LinuxSecretServiceStores(
    identityKeyPair: io.krypton.core.types.IdentityKeyPair,
    registrationId: Int,
    private val storagePath: String = ".config/krypton",
) {
    public val identityKeyStore: IdentityKeyStore
        get() = TODO("Wire SecretService IdentityStore or FileBased")
    public val preKeyStore: PreKeyStore
        get() = TODO("Wire PreKeyStore")
    public val sessionStore: SessionStore
        get() = TODO("Wire SessionStore")
    public val senderKeyStore: SenderKeyStore
        get() = TODO("Wire SenderKeyStore")
}
