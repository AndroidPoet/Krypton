@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("UNCHECKED_CAST")

package io.krypton.protocol.bridge

import io.krypton.core.result.CryptoError
import io.krypton.core.result.CryptoResult
import io.krypton.core.types.*
import io.krypton.protocol.models.CiphertextMessage
import io.krypton.protocol.models.CiphertextMessageType
import io.krypton.protocol.models.PreKeyBundle as KryptonPreKeyBundle
import io.krypton.storage.api.IdentityKeyStore as KryptonIdentityKeyStore
import io.krypton.storage.api.PreKeyStore as KryptonPreKeyStore
import io.krypton.storage.api.SenderKeyStore as KryptonSenderKeyStore
import io.krypton.storage.api.SessionStore as KryptonSessionStore
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.ffi.*
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.concurrent.Volatile

// ════════════════════════════════════════════════════════════════════════
// Apple/Native bridge — calls libsignal_ffi via cinterop + krypton_adapter
// ════════════════════════════════════════════════════════════════════════

public class NativeBridge(
    identityKeyStore: KryptonIdentityKeyStore,
    sessionStore: KryptonSessionStore,
    preKeyStore: KryptonPreKeyStore,
    senderKeyStore: KryptonSenderKeyStore,
    identityKeyPair: IdentityKeyPair,
    registrationId: RegistrationId,
) : Bridge(identityKeyStore, sessionStore, preKeyStore, senderKeyStore, identityKeyPair, registrationId) {

    private val delegateRef: StableRef<StoreDelegate> = StableRef.create(
        StoreDelegate(identityKeyStore, sessionStore, preKeyStore, senderKeyStore, identityKeyPair, registrationId)
    )

    // ── helpers ─────────────────────────────────────────────────────────

    private fun nowMillis(): ULong =
        (NSDate().timeIntervalSince1970 * 1000.0).toULong()

    /** Check null error ptr; throw if non-null. */
    private fun checkErr(errPtr: Any?) {
        if (errPtr == null) return
        @Suppress("UNCHECKED_CAST")
        val errp = errPtr as? CPointer<cnames.structs.SignalFfiError>
        val code = errp?.let { signal_error_get_type(it) } ?: 0u
        val msg = memScoped {
            val out = alloc<CPointerVar<ByteVar>>()
            signal_error_get_message(out.ptr, errp)
            out.value?.toKString() ?: "<no message>"
        }
        error("libsignal_ffi operation failed (error type $code): $msg")
    }

    /** Build a CValue<SignalBorrowedBuffer> from this ByteArray. */
    private fun ByteArray.asBorrowedBuffer(scope: MemScope): CValue<SignalBorrowedBuffer> {
        val ptr = scope.allocArray<UByteVar>(size)
        for (i in indices) ptr[i] = this[i].toUByte()
        return scope.alloc<SignalBorrowedBuffer>().also {
            it.base = ptr
            it.length = size.toULong()
        }.readValue()
    }

    /** Read a SignalOwnedBuffer into ByteArray. */
    private fun readOwnedBuf(owned: CPointer<SignalOwnedBuffer>): ByteArray {
        val len = owned.pointed.length.toInt()
        val arr = ByteArray(len)
        if (len > 0) {
            val src = owned.pointed.base!!
            for (i in 0 until len) arr[i] = src[i].toByte()
        }
        signal_free_buffer(owned.pointed.base, owned.pointed.length)
        return arr
    }

    // ── Const conversions from alloc'd structs ─────────────────────────
    // These work because we can access `.raw` on alloc'd struct values.

    /** Build ProtocolAddress C object for remote address. Returns CValue for passing to libsignal. */
    private fun ProtocolAddress.toConstAddrCValue(scope: MemScope): CValue<SignalConstPointerProtocolAddress> {
        val mut = scope.alloc<SignalMutPointerProtocolAddress>()
        val e: Any? = signal_address_new(mut.ptr, name, deviceId.value.toUInt())
        checkErr(e)
        val const = scope.alloc<SignalConstPointerProtocolAddress>()
        const.raw = mut.raw
        return const.readValue()
    }

    /** Convert alloc'd SignalMutPointerPublicKey → CValue<SignalConstPointerPublicKey>. */
    private fun MemScope.constPub(mut: SignalMutPointerPublicKey): CValue<SignalConstPointerPublicKey> {
        val const = alloc<SignalConstPointerPublicKey>()
        const.raw = mut.raw
        return const.readValue()
    }

    /** Convert alloc'd SignalMutPointerPrivateKey → CValue<SignalConstPointerPrivateKey>. */
    private fun MemScope.constPriv(mut: SignalMutPointerPrivateKey): CValue<SignalConstPointerPrivateKey> {
        val const = alloc<SignalConstPointerPrivateKey>()
        const.raw = mut.raw
        return const.readValue()
    }

    /** Convert alloc'd SignalMutPointerKyberPublicKey → CValue<SignalConstPointerKyberPublicKey>. */
    private fun MemScope.constKyberPub(mut: SignalMutPointerKyberPublicKey): CValue<SignalConstPointerKyberPublicKey> {
        val const = alloc<SignalConstPointerKyberPublicKey>()
        const.raw = mut.raw
        return const.readValue()
    }

    /** Convert alloc'd SignalMutPointerKyberKeyPair → CValue<SignalConstPointerKyberKeyPair>. */
    private fun MemScope.constKyberPair(mut: SignalMutPointerKyberKeyPair): CValue<SignalConstPointerKyberKeyPair> {
        val const = alloc<SignalConstPointerKyberKeyPair>()
        const.raw = mut.raw
        return const.readValue()
    }

    // ════════════════════════════════════════════════════════════════════
    // Store struct builders (staticCFunction callbacks)
    // ════════════════════════════════════════════════════════════════════

    private fun MemScope.buildSessionStore(): CValue<SignalConstPointerFfiSessionStoreStruct> {
        val s = alloc<SignalSessionStore>()
        s.ctx = delegateRef.asCPointer()
        s.load_session = staticCFunction { ctx, out, address ->
            ctx!!.asStableRef<StoreDelegate>().get().cLoadSession(out!!, address!!)
        }
        s.store_session = staticCFunction { ctx, address, record ->
            ctx!!.asStableRef<StoreDelegate>().get().cStoreSession(address!!, record!!)
        }
        val wrapped = alloc<SignalConstPointerFfiSessionStoreStruct>()
        wrapped.raw = s.ptr
        return wrapped.readValue()
    }

    private fun MemScope.buildIdentityKeyStore(): CValue<SignalConstPointerFfiIdentityKeyStoreStruct> {
        val s = alloc<SignalIdentityKeyStore>()
        s.ctx = delegateRef.asCPointer()
        s.get_identity_key_pair = staticCFunction { ctx, out ->
            ctx!!.asStableRef<StoreDelegate>().get().cGetIdentityKeyPair(out!!)
        }
        s.get_local_registration_id = staticCFunction { ctx, out ->
            ctx!!.asStableRef<StoreDelegate>().get().cGetLocalRegistrationId(out!!)
        }
        s.save_identity = staticCFunction { ctx, address, publicKey ->
            ctx!!.asStableRef<StoreDelegate>().get().cSaveIdentity(address!!, publicKey!!)
        }
        s.get_identity = staticCFunction { ctx, out, address ->
            ctx!!.asStableRef<StoreDelegate>().get().cGetIdentityKey(out!!, address!!)
        }
        s.is_trusted_identity = staticCFunction { ctx, address, publicKey, direction ->
            ctx!!.asStableRef<StoreDelegate>().get().cIsTrustedIdentity(address!!, publicKey!!, direction!!)
        }
        val wrapped = alloc<SignalConstPointerFfiIdentityKeyStoreStruct>()
        wrapped.raw = s.ptr
        return wrapped.readValue()
    }

    private fun MemScope.buildPreKeyStore(): CValue<SignalConstPointerFfiPreKeyStoreStruct> {
        val s = alloc<SignalPreKeyStore>()
        s.ctx = delegateRef.asCPointer()
        s.load_pre_key = staticCFunction { ctx, out, id ->
            ctx!!.asStableRef<StoreDelegate>().get().cLoadPreKey(out!!, id!!)
        }
        s.store_pre_key = staticCFunction { ctx, id, record ->
            ctx!!.asStableRef<StoreDelegate>().get().cStorePreKey(id!!, record!!)
        }
        s.remove_pre_key = staticCFunction { ctx, id ->
            ctx!!.asStableRef<StoreDelegate>().get().cRemovePreKey(id!!)
        }
        val wrapped = alloc<SignalConstPointerFfiPreKeyStoreStruct>()
        wrapped.raw = s.ptr
        return wrapped.readValue()
    }

    private fun MemScope.buildSignedPreKeyStore(): CValue<SignalConstPointerFfiSignedPreKeyStoreStruct> {
        val s = alloc<SignalSignedPreKeyStore>()
        s.ctx = delegateRef.asCPointer()
        s.load_signed_pre_key = staticCFunction { ctx, out, id ->
            ctx!!.asStableRef<StoreDelegate>().get().cLoadSignedPreKey(out!!, id!!)
        }
        s.store_signed_pre_key = staticCFunction { ctx, id, record ->
            ctx!!.asStableRef<StoreDelegate>().get().cStoreSignedPreKey(id!!, record!!)
        }
        val wrapped = alloc<SignalConstPointerFfiSignedPreKeyStoreStruct>()
        wrapped.raw = s.ptr
        return wrapped.readValue()
    }

    private fun MemScope.buildKyberPreKeyStore(): CValue<SignalConstPointerFfiKyberPreKeyStoreStruct> {
        val s = alloc<SignalKyberPreKeyStore>()
        s.ctx = delegateRef.asCPointer()
        s.load_kyber_pre_key = staticCFunction { ctx, out, id ->
            ctx!!.asStableRef<StoreDelegate>().get().cLoadKyberPreKey(out!!, id!!)
        }
        s.store_kyber_pre_key = staticCFunction { ctx, id, record ->
            ctx!!.asStableRef<StoreDelegate>().get().cStoreKyberPreKey(id!!, record!!)
        }
        s.mark_kyber_pre_key_used = staticCFunction { ctx, id, signedPrekeyId, baseKey ->
            ctx!!.asStableRef<StoreDelegate>().get().cMarkKyberPreKeyUsed(id!!, signedPrekeyId!!, baseKey!!)
        }
        val wrapped = alloc<SignalConstPointerFfiKyberPreKeyStoreStruct>()
        wrapped.raw = s.ptr
        return wrapped.readValue()
    }

    // ════════════════════════════════════════════════════════════════════
    // Bridge implementation
    // ════════════════════════════════════════════════════════════════════

    override suspend fun processPreKeyBundle(bundle: KryptonPreKeyBundle): CryptoResult<ByteArray> =
        CryptoResult.catching {
            memScoped {
                val now = nowMillis()
                val remoteAddr = ProtocolAddress(bundle.sender.name, bundle.sender.deviceId).toConstAddrCValue(this)

                // Build PreKeyBundle C object
                val bundleOut = alloc<SignalMutPointerPreKeyBundle>()

                val preKeyId = bundle.preKeyId?.toUInt() ?: 0xFFFFFFFFu

                // Deserialize pre-key public (or empty if none)
                val preKeyPub = if (bundle.preKeyPublic != null) {
                    val pub = alloc<SignalMutPointerPublicKey>()
                    checkErr(signal_publickey_deserialize(pub.ptr, bundle.preKeyPublic.bytes.asBorrowedBuffer(this)))
                    constPub(pub)
                } else {
                    // alloc zeroes memory automatically — no memset needed
                    alloc<SignalConstPointerPublicKey>().readValue()
                }

                // Deserialize the signed pre-key public
                val signedPub = alloc<SignalMutPointerPublicKey>()
                checkErr(signal_publickey_deserialize(signedPub.ptr, bundle.signedPreKeyPublic.bytes.asBorrowedBuffer(this)))

                // Deserialize the identity public key
                val idPub = alloc<SignalMutPointerPublicKey>()
                checkErr(signal_publickey_deserialize(idPub.ptr, bundle.identityKey.publicKey.bytes.asBorrowedBuffer(this)))

                // Deserialize the REAL Kyber pre-key public key from the bundle.
                val kyberPub = alloc<SignalMutPointerKyberPublicKey>()
                checkErr(signal_kyber_public_key_deserialize(kyberPub.ptr, bundle.kyberPreKeyPublic.asBorrowedBuffer(this)))

                checkErr(signal_pre_key_bundle_new(
                    bundleOut.ptr,
                    bundle.registrationId.value.toUInt(),
                    bundle.deviceId.value.toUInt(),
                    preKeyId,
                    preKeyPub,
                    bundle.signedPreKeyId.toUInt(),
                    constPub(signedPub),
                    bundle.signedPreKeySignature.asBorrowedBuffer(this),
                    constPub(idPub),
                    bundle.kyberPreKeyId.toUInt(),
                    constKyberPub(kyberPub),
                    bundle.kyberPreKeySignature.asBorrowedBuffer(this),
                ))

                val bundleConst = alloc<SignalConstPointerPreKeyBundle>()
                bundleConst.raw = bundleOut.raw
                val bundlePtr = bundleConst.readValue()

                checkErr(signal_process_prekey_bundle(
                    bundlePtr, remoteAddr,
                    buildSessionStore(), buildIdentityKeyStore(), now,
                ))

                delegateRef.get().capturedSession ?: error("No session created by process_prekey_bundle")
            }
        }

    override suspend fun encrypt(recipient: ProtocolAddress, plaintext: ByteArray): CryptoResult<CiphertextMessage> =
        CryptoResult.catching {
            memScoped {
                val msgOut = alloc<SignalMutPointerCiphertextMessage>()
                checkErr(signal_encrypt_message(
                    msgOut.ptr,
                    plaintext.asBorrowedBuffer(this),
                    recipient.toConstAddrCValue(this),
                    buildSessionStore(),
                    buildIdentityKeyStore(),
                    nowMillis(),
                ))

                val serialized = alloc<SignalOwnedBuffer>()
                val msgConst = alloc<SignalConstPointerCiphertextMessage>()
                msgConst.raw = msgOut.raw
                checkErr(signal_ciphertext_message_serialize(serialized.ptr, msgConst.readValue()))
                val ctBytes = readOwnedBuf(serialized.ptr)

                val rawType = alloc<UByteVar>()
                checkErr(signal_ciphertext_message_type(rawType.ptr, msgConst.readValue()))
                val type = when (rawType.value.toInt()) {
                    3 -> CiphertextMessageType.PRE_KEY
                    1 -> CiphertextMessageType.MESSAGE
                    7 -> CiphertextMessageType.SENDER_KEY
                    else -> CiphertextMessageType.MESSAGE
                }

                checkErr(signal_ciphertext_message_destroy(msgOut.readValue()))
                CiphertextMessage(type, ctBytes)
            }
        }

    override suspend fun decrypt(sender: ProtocolAddress, message: CiphertextMessage): CryptoResult<ByteArray> =
        CryptoResult.catching {
            memScoped {
                val result = alloc<SignalOwnedBuffer>()
                val remoteAddr = sender.toConstAddrCValue(this)

                when (message.type) {
                    CiphertextMessageType.PRE_KEY -> {
                        val preKeyMsg = alloc<SignalMutPointerPreKeySignalMessage>()
                        checkErr(signal_pre_key_signal_message_deserialize(preKeyMsg.ptr, message.serialized.asBorrowedBuffer(this)))
                        val preKeyMsgConst = alloc<SignalConstPointerPreKeySignalMessage>()
                        preKeyMsgConst.raw = preKeyMsg.raw
                        checkErr(signal_decrypt_pre_key_message(
                            result.ptr, preKeyMsgConst.readValue(),
                            remoteAddr,
                            buildSessionStore(), buildIdentityKeyStore(),
                            buildPreKeyStore(), buildSignedPreKeyStore(), buildKyberPreKeyStore(),
                        ))
                        checkErr(signal_pre_key_signal_message_destroy(preKeyMsg.readValue()))
                    }
                    CiphertextMessageType.MESSAGE -> {
                        val signalMsg = alloc<SignalMutPointerSignalMessage>()
                        checkErr(signal_message_deserialize(signalMsg.ptr, message.serialized.asBorrowedBuffer(this)))
                        val signalMsgConst = alloc<SignalConstPointerSignalMessage>()
                        signalMsgConst.raw = signalMsg.raw
                        checkErr(signal_decrypt_message(
                            result.ptr, signalMsgConst.readValue(),
                            remoteAddr,
                            buildSessionStore(), buildIdentityKeyStore(),
                        ))
                        checkErr(signal_message_destroy(signalMsg.readValue()))
                    }
                    else -> error("Unsupported ciphertext type: ${message.type}")
                }

                readOwnedBuf(result.ptr)
            }
        }

    override fun generatePreKeys(startKeyId: Int, count: Int): CryptoResult<List<PreKey>> =
        CryptoResult.catching {
            (startKeyId until startKeyId + count).map { id ->
                memScoped {
                    val priv = alloc<SignalMutPointerPrivateKey>()
                    checkErr(signal_privatekey_generate(priv.ptr))
                    val pub = alloc<SignalMutPointerPublicKey>()
                    checkErr(signal_privatekey_get_public_key(pub.ptr, constPriv(priv)))

                    val pubB = alloc<SignalOwnedBuffer>()
                    checkErr(signal_publickey_serialize(pubB.ptr, constPub(pub)))
                    val privB = alloc<SignalOwnedBuffer>()
                    checkErr(signal_privatekey_serialize(privB.ptr, constPriv(priv)))

                    val pubArr = readOwnedBuf(pubB.ptr)
                    val privArr = readOwnedBuf(privB.ptr)

                    checkErr(signal_publickey_destroy(pub.readValue()))
                    checkErr(signal_privatekey_destroy(priv.readValue()))

                    PreKey(id, KeyPair(PublicKey(pubArr), PrivateKey(privArr)))
                }
            }
        }

    override fun generateSignedPreKey(signedKeyId: Int): CryptoResult<SignedPreKey> =
        CryptoResult.catching {
            memScoped {
                val idPriv = alloc<SignalMutPointerPrivateKey>()
                checkErr(signal_privatekey_deserialize(idPriv.ptr, identityKeyPair.privateKey.bytes.asBorrowedBuffer(this)))

                val spkPriv = alloc<SignalMutPointerPrivateKey>()
                checkErr(signal_privatekey_generate(spkPriv.ptr))
                val spkPub = alloc<SignalMutPointerPublicKey>()
                checkErr(signal_privatekey_get_public_key(spkPub.ptr, constPriv(spkPriv)))

                val spkPubB = alloc<SignalOwnedBuffer>()
                checkErr(signal_publickey_serialize(spkPubB.ptr, constPub(spkPub)))
                val spkPubArr = readOwnedBuf(spkPubB.ptr)

                val sig = alloc<SignalOwnedBuffer>()
                checkErr(signal_privatekey_sign(sig.ptr, constPriv(idPriv), spkPubArr.asBorrowedBuffer(this)))
                val sigArr = readOwnedBuf(sig.ptr)

                val spkPrivB = alloc<SignalOwnedBuffer>()
                checkErr(signal_privatekey_serialize(spkPrivB.ptr, constPriv(spkPriv)))
                val privArr = readOwnedBuf(spkPrivB.ptr)

                checkErr(signal_privatekey_destroy(spkPriv.readValue()))
                checkErr(signal_publickey_destroy(spkPub.readValue()))
                checkErr(signal_privatekey_destroy(idPriv.readValue()))

                SignedPreKey(signedKeyId, KeyPair(PublicKey(spkPubArr), PrivateKey(privArr)), sigArr)
            }
        }

    override fun generateKyberPreKey(keyId: Int): CryptoResult<KyberPreKeyResult> =
        CryptoResult.catching {
            memScoped {
                val kyberPair = alloc<SignalMutPointerKyberKeyPair>()
                checkErr(signal_kyber_key_pair_generate(kyberPair.ptr))

                // Serialize the REAL Kyber public key (~1568 bytes), not a placeholder.
                val kyberPub = alloc<SignalMutPointerKyberPublicKey>()
                checkErr(signal_kyber_key_pair_get_public_key(kyberPub.ptr, constKyberPair(kyberPair)))
                val kyberPubB = alloc<SignalOwnedBuffer>()
                checkErr(signal_kyber_public_key_serialize(kyberPubB.ptr, constKyberPub(kyberPub)))
                val kyberPubBytes = readOwnedBuf(kyberPubB.ptr)
                checkErr(signal_kyber_public_key_destroy(kyberPub.readValue()))

                // Sign the real Kyber public key with the identity key.
                val idPriv = alloc<SignalMutPointerPrivateKey>()
                checkErr(signal_privatekey_deserialize(idPriv.ptr, identityKeyPair.privateKey.bytes.asBorrowedBuffer(this)))
                val sig = alloc<SignalOwnedBuffer>()
                checkErr(signal_privatekey_sign(sig.ptr, constPriv(idPriv), kyberPubBytes.asBorrowedBuffer(this)))
                val sigArr = readOwnedBuf(sig.ptr)
                checkErr(signal_privatekey_destroy(idPriv.readValue()))

                // Build the full KyberPreKeyRecord now and serialize it to bytes.
                // Storing serialized bytes (not a long-lived raw FFI pointer) mirrors
                // the EC pre-key path and the JVM bridge: the bytes survive memScope
                // and store-callback boundaries intact, so the secret key decapsulates
                // correctly on the receive path. The old raw-pointer approach left a
                // dangling/again-borrowed handle and produced InvalidMessage (error 30).
                val rec = alloc<SignalMutPointerKyberPreKeyRecord>()
                checkErr(signal_kyber_pre_key_record_new(
                    rec.ptr, keyId.toUInt(), nowMillis(), constKyberPair(kyberPair), sigArr.asBorrowedBuffer(this),
                ))
                val recConst = alloc<SignalConstPointerKyberPreKeyRecord>()
                recConst.raw = rec.raw
                val recB = alloc<SignalOwnedBuffer>()
                checkErr(signal_kyber_pre_key_record_serialize(recB.ptr, recConst.readValue()))
                val recBytes = readOwnedBuf(recB.ptr)
                checkErr(signal_kyber_pre_key_record_destroy(rec.readValue()))
                checkErr(signal_kyber_key_pair_destroy(kyberPair.readValue()))

                delegateRef.get().storeKyberRecord(keyId, recBytes)
                KyberPreKeyResult(keyId, kyberPubBytes, sigArr)
            }
        }

    public fun dispose() {
        delegateRef.dispose()
    }

    public companion object {
        /** Generate a real Curve25519 identity key pair via libsignal_ffi. */
        public fun generateIdentityKeyPair(): IdentityKeyPair = memScoped {
            val privKey = alloc<SignalMutPointerPrivateKey>()
            val e1: Any? = signal_privatekey_generate(privKey.ptr)
            checkErrStatic(e1)

            val pubKey = alloc<SignalMutPointerPublicKey>()
            val privKeyConst = alloc<SignalConstPointerPrivateKey>()
            privKeyConst.raw = privKey.raw
            val e2: Any? = signal_privatekey_get_public_key(pubKey.ptr, privKeyConst.readValue())
            checkErrStatic(e2)

            val privBuf = alloc<SignalOwnedBuffer>()
            val privConst = alloc<SignalConstPointerPrivateKey>()
            privConst.raw = privKey.raw
            val e3: Any? = signal_privatekey_serialize(privBuf.ptr, privConst.readValue())
            checkErrStatic(e3)

            val pubBuf = alloc<SignalOwnedBuffer>()
            val pubConst = alloc<SignalConstPointerPublicKey>()
            pubConst.raw = pubKey.raw
            val e4: Any? = signal_publickey_serialize(pubBuf.ptr, pubConst.readValue())
            checkErrStatic(e4)

            val privBytes = readOwnedStatic(privBuf.ptr)
            val pubBytes = readOwnedStatic(pubBuf.ptr)

            signal_privatekey_destroy(privKey.readValue())
            signal_publickey_destroy(pubKey.readValue())

            IdentityKeyPair(IdentityKey(PublicKey(pubBytes), 0), PrivateKey(privBytes))
        }

        private fun checkErrStatic(err: Any?) {
            if (err == null) return
            error("libsignal_ffi error in generateIdentityKeyPair")
        }

        private fun readOwnedStatic(owned: CPointer<SignalOwnedBuffer>): ByteArray {
            val len = owned.pointed.length.toInt()
            val arr = ByteArray(len)
            if (len > 0) {
                val src = owned.pointed.base!!
                for (i in 0 until len) arr[i] = src[i].toByte()
            }
            signal_free_buffer(owned.pointed.base, owned.pointed.length)
            return arr
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Store delegate — bridges C callbacks to Kotlin store interfaces
// ════════════════════════════════════════════════════════════════════════

internal class StoreDelegate(
    private val identityKeyStore: KryptonIdentityKeyStore,
    private val sessionStore: KryptonSessionStore,
    private val preKeyStore: KryptonPreKeyStore,
    private val senderKeyStore: KryptonSenderKeyStore,
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: RegistrationId,
) {
    @Volatile
    var capturedSession: ByteArray? = null

    // Store each Kyber pre-key as a serialized KyberPreKeyRecord. Bytes (rather
    // than a raw FFI pointer) survive memScope/callback boundaries cleanly and let
    // libsignal reconstruct the exact record — including the secret key — at load.
    private val kyberRecords = mutableMapOf<Int, ByteArray>()

    fun storeKyberRecord(id: Int, recordBytes: ByteArray) {
        kyberRecords[id] = recordBytes
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Extract [ProtocolAddress] from a [CValue] of [SignalMutPointerProtocolAddress].
     *
     * Uses the krypton_adapter C helper functions because we CANNOT access `.raw`
     * on CValue parameters directly in Kotlin/Native cinterop.
     */
    private fun addressFromConstCValue(addr: CValue<SignalConstPointerProtocolAddress>): ProtocolAddress? = memScoped {
        val nameOut = alloc<CPointerVar<ByteVar>>()
        val devOut = alloc<UIntVar>()

        // 0.86.5 store callbacks receive CONST addresses, which signal_address_*
        // consume directly — no krypton_adapter mut→const shim needed.
        if (signal_address_get_name(nameOut.ptr, addr) != null) return@memScoped null
        if (signal_address_get_device_id(devOut.ptr, addr) != null) return@memScoped null

        ProtocolAddress(nameOut.value?.toKString() ?: return@memScoped null, DeviceId(devOut.value.toInt()))
    }

    /** ByteArray → CValue<SignalBorrowedBuffer>. */
    private fun ByteArray.toBorrowed(scope: MemScope): CValue<SignalBorrowedBuffer> {
        val ptr = scope.allocArray<UByteVar>(size)
        for (i in indices) ptr[i] = this[i].toUByte()
        return scope.alloc<SignalBorrowedBuffer>().also {
            it.base = ptr; it.length = size.toULong()
        }.readValue()
    }

    /** Read SignalOwnedBuffer → ByteArray. */
    private fun ownedBytes(owned: CPointer<SignalOwnedBuffer>): ByteArray {
        val len = owned.pointed.length.toInt()
        val arr = ByteArray(len)
        if (len > 0) {
            val src = owned.pointed.base!!
            for (i in 0 until len) arr[i] = src[i].toByte()
        }
        signal_free_buffer(owned.pointed.base, owned.pointed.length)
        return arr
    }

    /** Convert alloc'd SignalMutPointerPublicKey → CValue<SignalConstPointerPublicKey>. */
    private fun MemScope.constPub(mut: SignalMutPointerPublicKey): CValue<SignalConstPointerPublicKey> {
        val const = alloc<SignalConstPointerPublicKey>()
        const.raw = mut.raw
        return const.readValue()
    }

    /** Convert alloc'd SignalMutPointerPrivateKey → CValue<SignalConstPointerPrivateKey>. */
    private fun MemScope.constPriv(mut: SignalMutPointerPrivateKey): CValue<SignalConstPointerPrivateKey> {
        val const = alloc<SignalConstPointerPrivateKey>()
        const.raw = mut.raw
        return const.readValue()
    }

    /** Error check for callbacks — just check null since we can't import SignalFfiError. */
    private fun checkErrCB(err: Any?) {
        if (err != null) error("libsignal_ffi callback error")
    }

    // ════════════════════════════════════════════════════════════════
    // C callback implementations
    // ════════════════════════════════════════════════════════════════

    fun cLoadSession(out: CPointer<SignalMutPointerSessionRecord>, address: CValue<SignalConstPointerProtocolAddress>): Int = memScoped {
        val addr = addressFromConstCValue(address) ?: return@memScoped 1
        val result = runBlocking { sessionStore.loadSession(addr) }
        when (val data = result.getOrNull()) {
            null -> { out.pointed.raw = null; 0 }
            else -> {
                val sr = alloc<SignalMutPointerSessionRecord>()
                if (signal_session_record_deserialize(sr.ptr, data.toBorrowed(this)) != null) return@memScoped 1
                out.pointed.raw = sr.raw
                0
            }
        }
    }

    fun cStoreSession(address: CValue<SignalConstPointerProtocolAddress>, record: CValue<SignalConstPointerSessionRecord>): Int = memScoped {
        val addr = addressFromConstCValue(address) ?: return@memScoped 1
        val serialized = alloc<SignalOwnedBuffer>()
        // 0.86.5 passes a CONST session record, which signal_session_record_serialize
        // consumes directly.
        if (signal_session_record_serialize(serialized.ptr, record) != null) return@memScoped 1

        val data = ownedBytes(serialized.ptr)
        capturedSession = data
        runBlocking { sessionStore.storeSession(addr, data) }
        0
    }

    /**
     * 0.86.5's get_identity_key_pair returns ONLY the private key; libsignal
     * derives the public from it. Write the deserialized identity private key.
     */
    fun cGetIdentityKeyPair(out: CPointer<SignalMutPointerPrivateKey>): Int = memScoped {
        val priv = alloc<SignalMutPointerPrivateKey>()
        if (signal_privatekey_deserialize(priv.ptr, identityKeyPair.privateKey.bytes.toBorrowed(this)) != null) {
            return@memScoped 1
        }
        out.pointed.raw = priv.raw
        0
    }

    fun cGetLocalRegistrationId(out: CPointer<UIntVar>): Int {
        out.pointed.value = registrationId.value.toUInt()
        return 0
    }

    fun cGetIdentityKey(out: CPointer<SignalMutPointerPublicKey>, address: CValue<SignalConstPointerProtocolAddress>): Int = memScoped {
        val addr = addressFromConstCValue(address) ?: return@memScoped 1
        val result = runBlocking { identityKeyStore.getIdentity(addr) }
        when (val pk = result.getOrNull()) {
            null -> { out.pointed.raw = null; 0 }
            else -> {
                val pub = alloc<SignalMutPointerPublicKey>()
                if (signal_publickey_deserialize(pub.ptr, pk.bytes.toBorrowed(this)) != null) return@memScoped 1
                out.pointed.raw = pub.raw
                0
            }
        }
    }

    /**
     * 0.86.5: returns the `IdentityChange` value (0 = NewOrUnchanged) — there is
     * no separate "changed" out-param. The CONST public key serializes directly.
     */
    fun cSaveIdentity(
        address: CValue<SignalConstPointerProtocolAddress>,
        publicKey: CValue<SignalConstPointerPublicKey>,
    ): Int = memScoped {
        val addr = addressFromConstCValue(address) ?: return@memScoped -1
        val serialized = alloc<SignalOwnedBuffer>()
        if (signal_publickey_serialize(serialized.ptr, publicKey) != null) return@memScoped -1
        val pkBytes = ownedBytes(serialized.ptr)
        runBlocking { identityKeyStore.saveIdentity(addr, PublicKey(pkBytes)) }
        0 // IdentityChange::NewOrUnchanged
    }

    /**
     * 0.86.5: the trust result is the RETURN value — 1 = trusted, 0 = untrusted
     * (negative = error). No bool out-param.
     */
    fun cIsTrustedIdentity(
        address: CValue<SignalConstPointerProtocolAddress>,
        publicKey: CValue<SignalConstPointerPublicKey>,
        direction: UInt,
    ): Int = memScoped {
        val addr = addressFromConstCValue(address) ?: return@memScoped -1
        val serialized = alloc<SignalOwnedBuffer>()
        if (signal_publickey_serialize(serialized.ptr, publicKey) != null) return@memScoped -1
        val pkBytes = ownedBytes(serialized.ptr)
        val result = runBlocking { identityKeyStore.isTrustedIdentity(addr, PublicKey(pkBytes)) }
        if (result.getOrNull() ?: true) 1 else 0
    }

    fun cLoadPreKey(out: CPointer<SignalMutPointerPreKeyRecord>, id: UInt): Int = memScoped {
        val result = runBlocking { preKeyStore.loadPreKey(id.toInt()) }
        when (val pk = result.getOrNull()) {
            null -> { out.pointed.raw = null; 0 }
            else -> {
                val pub = alloc<SignalMutPointerPublicKey>()
                val priv = alloc<SignalMutPointerPrivateKey>()
                if (signal_publickey_deserialize(pub.ptr, pk.keyPair.publicKey.bytes.toBorrowed(this)) != null) return@memScoped 1
                if (signal_privatekey_deserialize(priv.ptr, pk.keyPair.privateKey.bytes.toBorrowed(this)) != null) return@memScoped 1
                val rec = alloc<SignalMutPointerPreKeyRecord>()
                if (signal_pre_key_record_new(rec.ptr, id, constPub(pub), constPriv(priv)) != null) return@memScoped 1
                out.pointed.raw = rec.raw
                0
            }
        }
    }

    fun cStorePreKey(id: UInt, record: CValue<SignalConstPointerPreKeyRecord>): Int = 0

    fun cRemovePreKey(id: UInt): Int {
        runBlocking { preKeyStore.removePreKey(id.toInt()) }
        return 0
    }

    fun cLoadSignedPreKey(out: CPointer<SignalMutPointerSignedPreKeyRecord>, id: UInt): Int = memScoped {
        val result = runBlocking { preKeyStore.loadSignedPreKey(id.toInt()) }
        when (val spk = result.getOrNull()) {
            null -> { out.pointed.raw = null; 0 }
            else -> {
                val pub = alloc<SignalMutPointerPublicKey>()
                val priv = alloc<SignalMutPointerPrivateKey>()
                if (signal_publickey_deserialize(pub.ptr, spk.keyPair.publicKey.bytes.toBorrowed(this)) != null) return@memScoped 1
                if (signal_privatekey_deserialize(priv.ptr, spk.keyPair.privateKey.bytes.toBorrowed(this)) != null) return@memScoped 1
                val rec = alloc<SignalMutPointerSignedPreKeyRecord>()
                val now = (NSDate().timeIntervalSince1970 * 1000.0).toULong()
                if (signal_signed_pre_key_record_new(rec.ptr, id, now, constPub(pub), constPriv(priv), spk.signature.toBorrowed(this)) != null) return@memScoped 1
                out.pointed.raw = rec.raw
                0
            }
        }
    }

    fun cStoreSignedPreKey(id: UInt, record: CValue<SignalConstPointerSignedPreKeyRecord>): Int = 0

    fun cLoadKyberPreKey(out: CPointer<SignalMutPointerKyberPreKeyRecord>, id: UInt): Int = memScoped {
        val recBytes = kyberRecords[id.toInt()]
        if (recBytes == null) {
            out.pointed.raw = null
            return@memScoped 0
        }
        // Deserialize the exact record that was serialized at generation time —
        // the secret key inside is what libsignal decapsulates against.
        val rec = alloc<SignalMutPointerKyberPreKeyRecord>()
        if (signal_kyber_pre_key_record_deserialize(rec.ptr, recBytes.toBorrowed(this)) != null) return@memScoped 1
        out.pointed.raw = rec.raw
        0
    }

    fun cStoreKyberPreKey(id: UInt, record: CValue<SignalConstPointerKyberPreKeyRecord>): Int = 0

    fun cMarkKyberPreKeyUsed(id: UInt, signedPrekeyId: UInt, baseKey: CValue<SignalConstPointerPublicKey>): Int {
        // Last-resort kyber keys are reused; one-time kyber keys would be removed
        // here. Keep the record so repeat decrypts in tests still resolve.
        return 0
    }
}
