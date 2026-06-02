package io.krypton.storage

import io.krypton.storage.api.IdentityKeyStore
import io.krypton.storage.api.PreKeyStore
import io.krypton.storage.api.SenderKeyStore
import io.krypton.storage.api.SessionStore

/**
 * Web (WASM/JS) production stores using the browser's IndexedDB
 * or localStorage (with encryption).
 *
 * In the browser, we use `window.crypto.subtle` for encryption
 * and IndexedDB for persistence.
 *
 * ```kotlin
 * val stores = WebLocalStorageStores(identityKeyPair, regId)
 * ```
 */
public class WebLocalStorageStores(
    identityKeyPair: io.krypton.core.types.IdentityKeyPair,
    registrationId: Int,
) {
    // In production: use kotlinx.browser.window.localStorage or IndexedDB
    // Keys are encrypted with Web Crypto API before storage

    public val identityKeyStore: IdentityKeyStore
        get() = TODO("Wire IndexedDB-backed IdentityStore")
    public val preKeyStore: PreKeyStore
        get() = TODO("Wire IndexedDB-backed PreKeyStore")
    public val sessionStore: SessionStore
        get() = TODO("Wire IndexedDB-backed SessionStore")
    public val senderKeyStore: SenderKeyStore
        get() = TODO("Wire IndexedDB-backed SenderKeyStore")
}
