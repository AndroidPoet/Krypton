package io.krypton.protocol.api

import io.krypton.core.encoding.Base64
import io.krypton.core.result.*
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
