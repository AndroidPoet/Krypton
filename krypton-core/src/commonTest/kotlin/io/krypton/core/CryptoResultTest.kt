package io.krypton.core

import io.krypton.core.result.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoResultTest {

    @Test
    fun `success contains value`() {
        val r: CryptoResult<Int> = CryptoResult.Success(42)
        assertTrue(r.isSuccess)
        assertEquals(42, r.getOrNull())
        assertEquals(42, r.getOrThrow())
    }

    @Test
    fun `failure contains error`() {
        val r: CryptoResult<Int> = CryptoResult.Failure(CryptoError("fail"))
        assertTrue(r.isFailure)
        assertNull(r.getOrNull())
        assertNotNull(r.errorOrNull())
    }

    @Test
    fun `map transforms success`() = runTest {
        val r = CryptoResult.Success(21).map { it * 2 }
        assertEquals(42, r.getOrNull())
    }

    @Test
    fun `map skips failure`() = runTest {
        val err = CryptoError("boom")
        val r: CryptoResult<Int> = CryptoResult.Failure(err)
        val mapped = r.map { it * 2 }
        assertEquals(err, mapped.errorOrNull())
    }

    @Test
    fun `flatMap chains results`() = runTest {
        val r = CryptoResult.Success(10).flatMap { CryptoResult.Success(it * 3) }
        assertEquals(30, r.getOrNull())
    }

    @Test
    fun `getOrElse provides fallback`() {
        val r: CryptoResult<Int> = CryptoResult.Failure(CryptoError("fail"))
        val v = r.getOrElse { -1 }
        assertEquals(-1, v)
    }

    @Test
    fun `recover converts failure to success`() {
        val r: CryptoResult<Int> = CryptoResult.Failure(CryptoError("fail"))
        val recovered = r.recover { 99 }
        assertEquals(99, recovered.getOrNull())
    }

    @Test
    fun `catching catches exceptions`() {
        val r = CryptoResult.catching { throw RuntimeException("oops") }
        assertTrue(r.isFailure)
        assertEquals("oops", r.errorOrNull()!!.message)
    }

    @Test
    fun `onSuccess fires for success`() = runTest {
        var captured = false
        CryptoResult.Success(1).onSuccess { captured = true }
        assertTrue(captured)
    }

    @Test
    fun `onFailure fires for failure`() = runTest {
        var captured = false
        val r: CryptoResult<Int> = CryptoResult.Failure(CryptoError("err"))
        r.onFailure { captured = true }
        assertTrue(captured)
    }

    @Test
    fun `mapError transforms error`() {
        val r: CryptoResult<Int> = CryptoResult.Failure(CryptoError("original"))
        val mapped = r.mapError { CryptoError("transformed") }
        assertEquals("transformed", mapped.errorOrNull()!!.message)
    }

    @Test
    fun `flatMap on failure short-circuits`() = runTest {
        var sideEffect = false
        val r: CryptoResult<Int> = CryptoResult.Failure(CryptoError("stop"))
        val result = r.flatMap { CryptoResult.Success(1).also { sideEffect = true } }
        assertTrue(result.isFailure)
        assertFalse(sideEffect)
    }

    @Test
    fun `toKotlinResult converts correctly`() {
        val success: CryptoResult<Int> = CryptoResult.Success(42)
        assertTrue(success.toKotlinResult().isSuccess)

        val failure: CryptoResult<Int> = CryptoResult.Failure(CryptoError("err"))
        assertTrue(failure.toKotlinResult().isFailure)
    }

    @Test
    fun `catching propagates CancellationException`() {
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            CryptoResult.catching<Int> { throw kotlinx.coroutines.CancellationException() }
        }
    }

    @Test
    fun `onFailureCategory filters correctly`() = runTest {
        var captured = false
        val r1: CryptoResult<Int> = CryptoResult.Failure(CryptoError("err", category = CryptoErrorCategory.Authentication))
        r1.onFailureCategory(CryptoErrorCategory.Authentication) { captured = true }
        assertTrue(captured)

        val r2: CryptoResult<Int> = CryptoResult.Failure(CryptoError("err2", category = CryptoErrorCategory.Storage))
        r2.onFailureCategory(CryptoErrorCategory.Authentication) { captured = false }
        assertTrue(captured) // should still be true from first
    }
}
