package io.krypton.protocol.models

import io.krypton.core.types.*

/**
 * A bundle of pre-keys that a user publishes so others can establish
 * a session with them (X3DH handshake).
 *
 * @property sender       Who published this bundle (their ProtocolAddress).
 * @property identityKey  The sender's long-term identity key.
 * @property registrationId The sender's registration ID.
 * @property deviceId     Which device this bundle is for.
 * @property preKeyId     ID of the one-time pre-key (null if none available).
 * @property preKeyPublic The one-time pre-key public key.
 * @property signedPreKeyId ID of the signed pre-key.
 * @property signedPreKeyPublic The signed pre-key public key.
 * @property signedPreKeySignature Signature of the signed pre-key by the identity key.
 * @property kyberPreKeyId ID of the Kyber pre-key (post-quantum).
 * @property kyberPreKeyPublic The Kyber pre-key public key.
 * @property kyberPreKeySignature Signature of the Kyber pre-key by the identity key.
 */
public data class PreKeyBundle(
    val sender: ProtocolAddress,
    val identityKey: IdentityKey,
    val registrationId: RegistrationId,
    val deviceId: DeviceId,
    val preKeyId: Int?,
    val preKeyPublic: PublicKey?,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: PublicKey,
    val signedPreKeySignature: ByteArray,
    val kyberPreKeyId: Int = 0,
    val kyberPreKeyPublic: ByteArray = ByteArray(0),
    val kyberPreKeySignature: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreKeyBundle) return false
        return sender == other.sender &&
            identityKey == other.identityKey &&
            registrationId == other.registrationId &&
            deviceId == other.deviceId &&
            preKeyId == other.preKeyId &&
            preKeyPublic == other.preKeyPublic &&
            signedPreKeyId == other.signedPreKeyId &&
            signedPreKeyPublic == other.signedPreKeyPublic &&
            signedPreKeySignature.contentEquals(other.signedPreKeySignature) &&
            kyberPreKeyId == other.kyberPreKeyId &&
            kyberPreKeyPublic.contentEquals(other.kyberPreKeyPublic) &&
            kyberPreKeySignature.contentEquals(other.kyberPreKeySignature)
    }

    override fun hashCode(): Int {
        var r = sender.hashCode()
        r = 31 * r + identityKey.hashCode()
        r = 31 * r + registrationId.hashCode()
        r = 31 * r + deviceId.hashCode()
        r = 31 * r + (preKeyId ?: 0)
        r = 31 * r + (preKeyPublic?.hashCode() ?: 0)
        r = 31 * r + signedPreKeyId
        r = 31 * r + signedPreKeyPublic.hashCode()
        r = 31 * r + signedPreKeySignature.contentHashCode()
        r = 31 * r + kyberPreKeyId
        r = 31 * r + kyberPreKeyPublic.contentHashCode()
        r = 31 * r + kyberPreKeySignature.contentHashCode()
        return r
    }
}

/**
 * The type of a ciphertext message.
 */
public enum class CiphertextMessageType {
    /** A message that's part of a pre-key session establishment (X3DH). */
    PRE_KEY,
    /** A regular encrypted message (after session is established). */
    MESSAGE,
    /** A sender key message (group messaging). */
    SENDER_KEY,
    /** A plaintext message (no encryption). */
    PLAINTEXT,
}

/**
 * An encrypted message produced by the Signal Protocol.
 *
 * @property type       What kind of encryption was used.
 * @property serialized The serialised encrypted bytes (includes type byte + data).
 */
public data class CiphertextMessage(
    val type: CiphertextMessageType,
    val serialized: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CiphertextMessage) return false
        return type == other.type && serialized.contentEquals(other.serialized)
    }

    override fun hashCode(): Int {
        var r = type.hashCode()
        r = 31 * r + serialized.contentHashCode()
        return r
    }

    override fun toString(): String = "CiphertextMessage(type=$type, size=${serialized.size})"
}

/**
 * A safety number (fingerprint) between the local identity and a remote identity,
 * used to verify there is no man-in-the-middle. Mirrors libsignal's `Fingerprint`.
 *
 * @property displayText The human-readable safety number (compare out-of-band).
 * @property scannable   The serialized scannable fingerprint (for QR comparison).
 */
public data class SafetyNumber(
    val displayText: String,
    val scannable: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SafetyNumber) return false
        return displayText == other.displayText && scannable.contentEquals(other.scannable)
    }

    override fun hashCode(): Int = 31 * displayText.hashCode() + scannable.contentHashCode()

    override fun toString(): String = "SafetyNumber($displayText)"
}
