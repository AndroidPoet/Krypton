package io.krypton.storage

import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore

/**
 * Windows production stores using the Windows Credential Manager (DPAPI)
 * or encrypted files in `%APPDATA%/krypton/`.
 *
 * ```
 * val stores = WindowsCredentialStoreStores(identityKeyPair, regId)
 * ```
 */
public class WindowsCredentialStoreStores(
    identityKeyPair: io.krypton.core.types.IdentityKeyPair,
    registrationId: Int,
    private val storagePath: String = "${System.getenv("APPDATA")}/krypton",
) {
    public val identityKeyStore: IdentityKeyStore
        get() = TODO("Wire DPAPI-protected IdentityStore")
    public val preKeyStore: PreKeyStore
        get() = TODO("Wire PreKeyStore")
    public val sessionStore: SessionStore
        get() = TODO("Wire SessionStore")
    public val senderKeyStore: SenderKeyStore
        get() = TODO("Wire SenderKeyStore")
}
