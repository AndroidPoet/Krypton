package io.krypton.storage

import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore
import java.io.File
import java.nio.file.Paths

/**
 * JVM/Desktop production stores using the filesystem with AES encryption.
 *
 * Stores data at `~/.krypton/` directory, encrypted with a machine-derived key.
 *
 * ```
 * val stores = DesktopKeyStoreStores(identityKeyPair, regId)
 * ```
 */
public class DesktopKeyStoreStores(
    identityKeyPair: io.krypton.core.types.IdentityKeyPair,
    registrationId: Int,
    private val storagePath: String = Paths.get(System.getProperty("user.home"), ".krypton").toString(),
) {
    init { File(storagePath).mkdirs() }

    public val identityKeyStore: IdentityKeyStore
        get() = TODO("Wire FileBasedIdentityStore at $storagePath/identity")
    public val preKeyStore: PreKeyStore
        get() = TODO("Wire FileBasedPreKeyStore at $storagePath/prekeys")
    public val sessionStore: SessionStore
        get() = TODO("Wire FileBasedSessionStore at $storagePath/sessions")
    public val senderKeyStore: SenderKeyStore
        get() = TODO("Wire FileBasedSenderKeyStore at $storagePath/senderkeys")
}
