package io.krypton.sealedsender.api

import io.krypton.core.result.CryptoResult
import io.krypton.core.types.ProtocolAddress
import io.krypton.protocol.api.KryptonProtocol
import io.krypton.protocol.models.SealedSenderMessage

/**
 * Sealed Sender lets you send encrypted messages to a recipient **without** the
 * server learning who the sender is — the sender's identity is encrypted inside
 * the sealed envelope, signed by a server-issued certificate.
 *
 * This is a thin convenience wrapper around [KryptonProtocol]'s sealed-sender
 * operations, holding your own sender identity (UUID + device + certificate) and
 * the server [trustRoot] so each call is a one-liner.
 *
 * ```
 * val sealed = SealedSender(protocol, myUuid, myDeviceId, mySenderCertificate, serverTrustRoot)
 * val envelope = sealed.send(bob, "Anon msg".encodeToByteArray()).getOrThrow()
 * val opened   = sealed.receive(envelope, now).getOrThrow()   // opened.senderUuid, opened.message
 * ```
 *
 * Backed by real libsignal sealed sender on JVM/Android; fails loud on platforms
 * where the bridge hasn't wired it yet.
 */
public class SealedSender(
    private val protocol: KryptonProtocol,
    private val localUuid: String,
    private val localDeviceId: Int,
    private val senderCertificate: ByteArray,
    private val trustRoot: ByteArray,
) {
    /**
     * Sealed-sender encrypts [paddedPlaintext] for [recipient]. The server cannot
     * identify the sender from the returned envelope.
     */
    public suspend fun send(
        recipient: ProtocolAddress,
        paddedPlaintext: ByteArray,
    ): CryptoResult<ByteArray> =
        protocol.sealedSenderEncrypt(localUuid, localDeviceId, recipient, senderCertificate, paddedPlaintext)

    /**
     * Opens a sealed envelope, validating the sender's certificate against the
     * server [trustRoot] and revealing the sender identity. [timestampMillis] is
     * used to reject expired certificates.
     */
    public suspend fun receive(
        sealedEnvelope: ByteArray,
        timestampMillis: Long,
    ): CryptoResult<SealedSenderMessage> =
        protocol.sealedSenderDecrypt(localUuid, localDeviceId, trustRoot, sealedEnvelope, timestampMillis)
}
