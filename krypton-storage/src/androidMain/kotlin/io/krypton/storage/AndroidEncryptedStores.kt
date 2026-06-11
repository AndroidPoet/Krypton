package io.krypton.storage

import android.content.Context
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore

/**
 * Android production stores using EncryptedSharedPreferences + Android Keystore.
 *
 * NOTE: scaffold only — the store wiring is not implemented yet. Use
 * [io.krypton.storage.memory.InMemoryStores] for working storage today.
 * Wiring this up requires `androidx.security:security-crypto`.
 *
 * ```
 * val stores = AndroidEncryptedStores(context, identityKeyPair, regId)
 * ```
 */
public class AndroidEncryptedStores(
    context: Context,
    identityKeyPair: io.krypton.core.types.IdentityKeyPair,
    registrationId: Int,
) {
    public val identityKeyStore: IdentityKeyStore
        get() = TODO("Wire AndroidKeystore + EncryptedSharedPreferences IdentityStore")
    public val preKeyStore: PreKeyStore
        get() = TODO("Wire EncryptedSharedPrefs PreKeyStore")
    public val sessionStore: SessionStore
        get() = TODO("Wire EncryptedSharedPrefs SessionStore")
    public val senderKeyStore: SenderKeyStore
        get() = TODO("Wire SenderKeyStore")
}
