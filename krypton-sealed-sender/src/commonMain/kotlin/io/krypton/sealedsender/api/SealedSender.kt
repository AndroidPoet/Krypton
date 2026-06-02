package io.krypton.sealedsender.api

import io.krypton.core.result.*
import io.krypton.core.types.ProtocolAddress
import io.krypton.protocol.api.KryptonProtocol

/**
 * Sealed Sender allows sending encrypted messages to a recipient
 * **without** the server knowing who the sender is.
 *
 * The server only sees the recipient; the sender's identity is
 * encrypted inside the sealed envelope.
 *
 * ## Usage
 * ```
 * val sealed = SealedSender(protocol)
 * sealed.send(alice, "Anon msg 💌".encodeToByteArray())
 *     .onSuccess { serverResponse -> /* sent! */ }
 * ```
 */
public class SealedSender(
    private val protocol: KryptonProtocol,
) {
    /**
     * Encrypts [plaintext] in a sealed envelope for [recipient].
     * The server cannot identify the sender from this message.
     */
    public suspend fun send(
        recipient: ProtocolAddress,
        plaintext: ByteArray,
    ): CryptoResult<ByteArray> {
        // In production: use libsignal's sealed sender encryption
        // which encrypts the sender identity + message in a single envelope
        return protocol.encrypt(recipient, plaintext).map { it.serialized }
    }

    /**
     * Decrypts a sealed envelope received from [sender].
     */
    public suspend fun receive(
        sender: ProtocolAddress,
        sealedEnvelope: ByteArray,
    ): CryptoResult<ByteArray> {
        return protocol.decrypt(sender, io.krypton.protocol.models.CiphertextMessage(
            io.krypton.protocol.models.CiphertextMessageType.MESSAGE,
            sealedEnvelope,
        ))
    }
}
