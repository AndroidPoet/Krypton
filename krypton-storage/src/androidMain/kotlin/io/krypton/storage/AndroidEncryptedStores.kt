package io.krypton.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore
import java.security.KeyStore
import javax.crypto.KeyGenerator

/**
 * Android production stores using EncryptedSharedPreferences + Android Keystore.
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
    private val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
        "krypton_storage",
        generateOrGetMasterKey(),
        context,
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    public val identityKeyStore: IdentityKeyStore = TODO("Wire AndroidKeystoreIdentityStore")
    public val preKeyStore: PreKeyStore = TODO("Wire EncryptedSharedPrefs PreKeyStore")
    public val sessionStore: SessionStore = TODO("Wire EncryptedSharedPrefs SessionStore")
    public val senderKeyStore: SenderKeyStore = TODO("Wire SenderKeyStore")

    private fun generateOrGetMasterKey(): android.security.keystore.KeyGenParameterSpec? {
        // In production, wire properly with AndroidKeyStore
        return null
    }
}
