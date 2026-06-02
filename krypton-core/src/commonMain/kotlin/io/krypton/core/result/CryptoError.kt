package io.krypton.core.result

/**
 * Structured error description for cryptographic operations.
 *
 * @property message  Human-readable description.
 * @property code     Machine-readable code (e.g. "INVALID_KEY", "SESSION_NOT_FOUND").
 * @property category High-level category for switch-style handling.
 * @property origin   Which subsystem produced the error.
 * @property cause    The underlying exception (not serialized).
 */
public data class CryptoError(
    val message: String,
    val code: String? = null,
    val category: CryptoErrorCategory = CryptoErrorCategory.Unknown,
    val origin: CryptoErrorOrigin = CryptoErrorOrigin.Internal,
    val cause: Throwable? = null,
) {
    public companion object {
        public fun notFound(
            message: String = "Entity not found",
            origin: CryptoErrorOrigin = CryptoErrorOrigin.Internal,
            cause: Throwable? = null,
        ): CryptoError = CryptoError(message, "NOT_FOUND", CryptoErrorCategory.NotFound, origin, cause)

        public fun invalidInput(
            message: String = "Invalid input",
            origin: CryptoErrorOrigin = CryptoErrorOrigin.Internal,
            cause: Throwable? = null,
        ): CryptoError = CryptoError(message, "INVALID_INPUT", CryptoErrorCategory.InvalidInput, origin, cause)

        public fun crypto(
            message: String = "Cryptographic operation failed",
            code: String? = "CRYPTO_ERROR",
            origin: CryptoErrorOrigin = CryptoErrorOrigin.Crypto,
            cause: Throwable? = null,
        ): CryptoError = CryptoError(message, code, CryptoErrorCategory.Crypto, origin, cause)

        public fun storage(
            message: String = "Storage operation failed",
            origin: CryptoErrorOrigin = CryptoErrorOrigin.Storage,
            cause: Throwable? = null,
        ): CryptoError = CryptoError(message, "STORAGE_ERROR", CryptoErrorCategory.Storage, origin, cause)

        public fun internal(
            message: String = "Internal error",
            cause: Throwable? = null,
        ): CryptoError = CryptoError(message, "INTERNAL_ERROR", CryptoErrorCategory.Internal, CryptoErrorOrigin.Internal, cause)
    }
}

public enum class CryptoErrorCategory {
    InvalidInput, Authentication, NotFound, Conflict,
    Crypto, Storage, Network, Internal, Unknown,
}

public enum class CryptoErrorOrigin {
    Protocol, DoubleRatchet, SealedSender, ZkGroup,
    Storage, Crypto, Network, Internal,
}

public class CryptoException(
    public val error: CryptoError,
) : Exception(error.message, error.cause)

public fun CryptoError.toException(): CryptoException = CryptoException(this)
