package io.krypton.protocol.api

import io.krypton.core.encoding.Base64
import io.krypton.core.result.*
import io.krypton.core.types.DeviceId
import io.krypton.core.types.ProtocolAddress
import io.krypton.protocol.models.CiphertextMessage
import io.krypton.protocol.models.CiphertextMessageType
import io.krypton.protocol.models.PreKeyBundle

/**
 * Encrypts a [plaintext] string as UTF-8 bytes.
 */
public suspend fun KryptonProtocol.encryptString(
    recipient: ProtocolAddress,
    plaintext: String,
): CryptoResult<CiphertextMessage> =
    encrypt(recipient, plaintext.encodeToByteArray())

/**
 * Decrypts a [CiphertextMessage] back to a UTF-8 string.
 */
public suspend fun KryptonProtocol.decryptString(
    sender: ProtocolAddress,
    message: CiphertextMessage,
): CryptoResult<String> =
    decrypt(sender, message).map { it.decodeToString() }

/**
 * Encrypts [plaintext] and returns a Base64 string ready to send over the wire.
 *
 * The message **type** is prepended as a one-byte tag so the receiver can decrypt
 * deterministically. libsignal's serialized bytes are version-prefixed and do
 * **not** self-identify as PreKey vs. regular, so a self-describing wire is the
 * only robust scheme — never guess the type from libsignal's payload.
 */
public suspend fun KryptonProtocol.encryptToBase64(
    recipient: ProtocolAddress,
    plaintext: ByteArray,
): CryptoResult<String> =
    encrypt(recipient, plaintext).map { Base64.encode(byteArrayOf(it.type.wireTag) + it.serialized) }

/**
 * Decrypts a Base64-encoded [ciphertext] (produced by [encryptToBase64] or the
 * simple [encrypt]) received from [sender], reading the one-byte type tag.
 */
public suspend fun KryptonProtocol.decryptFromBase64(
    sender: ProtocolAddress,
    ciphertext: String,
): CryptoResult<ByteArray> =
    CryptoResult.catching { Base64.decode(ciphertext) }
        .flatMap { raw ->
            decodeWire(raw).flatMap { (type, payload) ->
                decrypt(sender, CiphertextMessage(type, payload))
            }
        }

/** One-byte wire tag for each message type (self-describing wire format). */
private val CiphertextMessageType.wireTag: Byte
    get() = when (this) {
        CiphertextMessageType.PRE_KEY -> 1
        CiphertextMessageType.MESSAGE -> 2
        CiphertextMessageType.SENDER_KEY -> 3
        CiphertextMessageType.PLAINTEXT -> 4
    }

/** Split a tagged wire payload back into its [CiphertextMessageType] + raw bytes. */
private fun decodeWire(raw: ByteArray): CryptoResult<Pair<CiphertextMessageType, ByteArray>> {
    val type = when (raw.firstOrNull()?.toInt()) {
        1 -> CiphertextMessageType.PRE_KEY
        2 -> CiphertextMessageType.MESSAGE
        3 -> CiphertextMessageType.SENDER_KEY
        4 -> CiphertextMessageType.PLAINTEXT
        else -> return CryptoResult.Failure(
            io.krypton.core.result.CryptoError.internal("Malformed wire: missing/unknown message-type tag"),
        )
    }
    return CryptoResult.Success(type to raw.copyOfRange(1, raw.size))
}

/**
 * Processes a [PreKeyBundle] and then immediately encrypts a message,
 * combining the session establishment and first message into one call.
 */
public suspend fun KryptonProtocol.processBundleAndEncrypt(
    bundle: PreKeyBundle,
    recipient: ProtocolAddress,
    plaintext: ByteArray,
): CryptoResult<CiphertextMessage> =
    processPreKeyBundle(bundle).flatMap { encrypt(recipient, plaintext) }

// ── Stupid-simple API ────────────────────────────────────────────────────────
// Strings in, wire-ready Base64 out. No ProtocolAddress / ByteArray /
// CiphertextMessage to build, no message-type bookkeeping. Recipient/sender are
// named (device id defaults to the primary device).
//
//   val krypton = Krypton.protocol { }                       // zero config
//   val wire    = krypton.encrypt("alice", "hello").getOrThrow()   // send this
//   val text    = krypton.decrypt("alice", wire).getOrThrow()      // read it

/**
 * Encrypt [text] for the recipient named [name] (defaulting to their primary
 * device) and return a wire-ready Base64 string you can send as-is.
 *
 * ```
 * val wire = krypton.encrypt("alice", "hello").getOrThrow()
 * ```
 */
public suspend fun KryptonProtocol.encrypt(
    name: String,
    text: String,
    deviceId: Int = DeviceId.PRIMARY.value,
): CryptoResult<String> =
    encryptToBase64(ProtocolAddress(name, DeviceId(deviceId)), text.encodeToByteArray())

/**
 * Decrypt a wire-ready Base64 string [wire] from the sender named [name]
 * (defaulting to their primary device) and return the original text. The Signal
 * message type is inferred from the payload, so you don't track it yourself.
 *
 * ```
 * val text = krypton.decrypt("alice", wire).getOrThrow()
 * ```
 */
public suspend fun KryptonProtocol.decrypt(
    name: String,
    wire: String,
    deviceId: Int = DeviceId.PRIMARY.value,
): CryptoResult<String> =
    decryptFromBase64(ProtocolAddress(name, DeviceId(deviceId)), wire)
        .map { it.decodeToString() }

/**
 * Compute the safety number between us ([localId]) and a remote user ([remoteId])
 * using string identifiers (UTF-8 encoded), given their identity public key.
 *
 * ```
 * val sn = krypton.safetyNumber("me", "alice", aliceIdentityKeyBytes).getOrThrow()
 * println(sn.displayText)   // compare this out-of-band with Alice
 * ```
 */
public fun KryptonProtocol.safetyNumber(
    localId: String,
    remoteId: String,
    remoteIdentityKey: ByteArray,
): CryptoResult<io.krypton.protocol.models.SafetyNumber> =
    safetyNumber(localId.encodeToByteArray(), remoteId.encodeToByteArray(), remoteIdentityKey)
