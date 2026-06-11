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
 * Encrypts [plaintext] and returns the serialized message as a Base64 string,
 * ready to send over the wire.
 */
public suspend fun KryptonProtocol.encryptToBase64(
    recipient: ProtocolAddress,
    plaintext: ByteArray,
): CryptoResult<String> =
    encrypt(recipient, plaintext).map { Base64.encode(it.serialized) }

/**
 * Decrypts a Base64-encoded [ciphertext] received from [sender].
 * Infers the message type from the first byte of the decoded data.
 */
public suspend fun KryptonProtocol.decryptFromBase64(
    sender: ProtocolAddress,
    ciphertext: String,
): CryptoResult<ByteArray> =
    CryptoResult.catching { Base64.decode(ciphertext) }
        .flatMap { raw ->
            val type = inferMessageType(raw)
            decrypt(sender, CiphertextMessage(type, raw))
        }

/**
 * Infers the [CiphertextMessageType] from the first byte of serialized data.
 *
 * Signal Protocol convention:
 *   1 (0x01) → PRE_KEY (PreKeySignalMessage)
 *   2 (0x02) → MESSAGE (SignalMessage)
 *   3 (0x03) → SENDER_KEY (SenderKeyMessage)
 *   else     → PLAINTEXT (fallback)
 */
private fun inferMessageType(data: ByteArray): CiphertextMessageType =
    when (data.firstOrNull()?.toInt()?.and(0xFF)) {
        1 -> CiphertextMessageType.PRE_KEY
        2 -> CiphertextMessageType.MESSAGE
        3 -> CiphertextMessageType.SENDER_KEY
        else -> CiphertextMessageType.PLAINTEXT
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
    encrypt(ProtocolAddress(name, DeviceId(deviceId)), text.encodeToByteArray())
        .map { Base64.encode(it.serialized) }

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
    CryptoResult.catching { Base64.decode(wire) }
        .flatMap { raw ->
            decrypt(ProtocolAddress(name, DeviceId(deviceId)), CiphertextMessage(inferMessageType(raw), raw))
        }
        .map { it.decodeToString() }
