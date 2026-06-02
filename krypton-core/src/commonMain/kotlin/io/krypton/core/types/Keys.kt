package io.krypton.core.types

// ── Core key types ─────────────────────────────────────────────────────────

public class PublicKey(public val bytes: ByteArray) {
    init { require(bytes.size == 32) { "Public key must be exactly 32 bytes" } }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKey) return false
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "PublicKey(${bytes.take(4).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}…)"
}

public class PrivateKey(public val bytes: ByteArray) {
    init { require(bytes.size == 32) { "Private key must be exactly 32 bytes" } }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrivateKey) return false
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "PrivateKey(…)"
}

public data class KeyPair(
    val publicKey: PublicKey,
    val privateKey: PrivateKey,
)

public data class IdentityKey(
    val publicKey: PublicKey,
    val keyId: Int = 0,
)

public data class IdentityKeyPair(
    val identityKey: IdentityKey,
    val privateKey: PrivateKey,
)

public data class SignedPreKey(
    val keyId: Int,
    val keyPair: KeyPair,
    val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedPreKey) return false
        return keyId == other.keyId && keyPair == other.keyPair && signature.contentEquals(other.signature)
    }
    override fun hashCode(): Int {
        var r = keyId; r = 31 * r + keyPair.hashCode(); r = 31 * r + signature.contentHashCode(); return r
    }
}

public data class PreKey(
    val keyId: Int,
    val keyPair: KeyPair,
)

@JvmInline
public value class RegistrationId(public val value: Int) {
    init { require(value in 1..0x3FFF) { "Registration ID must be between 1 and 16383" } }
}

@JvmInline
public value class DeviceId(public val value: Int) {
    init { require(value >= 0) { "Device ID must be non-negative" } }
    public companion object {
        public val PRIMARY: DeviceId = DeviceId(1)
    }
}

public data class ProtocolAddress(
    val name: String,
    val deviceId: DeviceId = DeviceId.PRIMARY,
) {
    override fun toString(): String = "$name.${deviceId.value}"
}
