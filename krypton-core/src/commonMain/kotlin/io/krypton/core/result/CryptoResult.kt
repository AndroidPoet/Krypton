package io.krypton.core.result

import kotlinx.coroutines.CancellationException

/**
 * A discriminated union representing success ([CryptoResult.Success]) or
 * failure ([CryptoResult.Failure]) of a cryptographic operation.
 *
 * Designed for fluent, composable error handling without raw exceptions.
 *
 * ```
 * Krypton.protocol(clientConfig)
 *     .encrypt(alice, data)
 *     .onSuccess { send(it) }
 *     .onFailure { log.error("Encryption failed", it) }
 * ```
 */
public sealed interface CryptoResult<out T> {

    public data class Success<out T>(public val value: T) : CryptoResult<T>
    public data class Failure(public val error: CryptoError) : CryptoResult<Nothing>

    // ── Query ──────────────────────────────────────────────────────────────

    public val isSuccess: Boolean get() = this is Success
    public val isFailure: Boolean get() = this is Failure

    public fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    public fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error.toException()
    }

    public fun errorOrNull(): CryptoError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    public companion object {
        /**
         * Lifts a throwing block into a [CryptoResult].
         * [CancellationException]s propagate; all others are caught.
         */
        public inline fun <T> catching(block: () -> T): CryptoResult<T> =
            try {
                Success(block())
            } catch (e: CryptoException) {
                Failure(e.error)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Failure(CryptoError(message = e.message ?: "Unknown error"))
            }
    }
}

// ── Transform ──────────────────────────────────────────────────────────────

/**
 * Transforms a successful value using [transform].
 * Failures pass through unchanged.
 *
 * [transform] can be a suspend function, enabling fluent chaining
 * of async operations.
 */
public suspend fun <T, R> CryptoResult<T>.map(
    transform: suspend (T) -> R,
): CryptoResult<R> = when (this) {
    is CryptoResult.Success -> CryptoResult.Success(transform(value))
    is CryptoResult.Failure -> this
}

/**
 * Chains a [CryptoResult]-returning operation.
 * Failures short-circuit the chain.
 *
 * [transform] can be a suspend function, enabling fluent chaining
 * of suspend operations like store I/O and native crypto calls.
 *
 * ```
 * store.loadSession(addr)
 *     .flatMap { record -> bridge.encrypt(addr, record, data) }
 *     .onSuccess { send(it) }
 * ```
 */
public suspend fun <T, R> CryptoResult<T>.flatMap(
    transform: suspend (T) -> CryptoResult<R>,
): CryptoResult<R> = when (this) {
    is CryptoResult.Success -> transform(value)
    is CryptoResult.Failure -> this
}

// ── Side-effects ───────────────────────────────────────────────────────────

public suspend fun <T> CryptoResult<T>.onSuccess(
    action: suspend (T) -> Unit,
): CryptoResult<T> = apply {
    if (this is CryptoResult.Success) action(value)
}

public suspend fun <T> CryptoResult<T>.onFailure(
    action: suspend (CryptoError) -> Unit,
): CryptoResult<T> = apply {
    if (this is CryptoResult.Failure) action(error)
}

public suspend fun <T> CryptoResult<T>.onFailureCategory(
    category: CryptoErrorCategory,
    action: suspend (CryptoError) -> Unit,
): CryptoResult<T> = apply {
    if (this is CryptoResult.Failure && error.category == category) action(error)
}

// ── Recovery ───────────────────────────────────────────────────────────────

public inline fun <T> CryptoResult<T>.mapError(
    transform: (CryptoError) -> CryptoError,
): CryptoResult<T> = when (this) {
    is CryptoResult.Success -> this
    is CryptoResult.Failure -> CryptoResult.Failure(transform(error))
}

public inline fun <T> CryptoResult<T>.recover(
    transform: (CryptoError) -> T,
): CryptoResult<T> = when (this) {
    is CryptoResult.Success -> this
    is CryptoResult.Failure -> CryptoResult.Success(transform(error))
}

public inline fun <T> CryptoResult<T>.getOrElse(
    defaultValue: (CryptoError) -> T,
): T = when (this) {
    is CryptoResult.Success -> value
    is CryptoResult.Failure -> defaultValue(error)
}

// ── Interop ────────────────────────────────────────────────────────────────

public fun <T> CryptoResult<T>.toKotlinResult(): Result<T> = when (this) {
    is CryptoResult.Success -> Result.success(value)
    is CryptoResult.Failure -> Result.failure(error.toException())
}

public inline fun <T> Result<T>.toCryptoResult(
    crossinline mapThrowable: (Throwable) -> CryptoError = { throwable ->
        val ce = throwable as? CryptoException
        ce?.error ?: CryptoError(message = throwable.message ?: "Unknown error")
    },
): CryptoResult<T> = fold(
    onSuccess = { CryptoResult.Success(it) },
    onFailure = { t ->
        if (t is CancellationException) throw t
        CryptoResult.Failure(mapThrowable(t))
    },
)
