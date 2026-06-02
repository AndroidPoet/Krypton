package io.krypton.core.encoding

/**
 * Minimal, platform-independent Base64 and Hex encoder/decoder.
 */
public object Base64 {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val CHARS_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    public fun encode(data: ByteArray): String = encodeInternal(data, CHARS, withPadding = true)
    public fun encodeUrlSafe(data: ByteArray): String = encodeInternal(data, CHARS_URL, withPadding = false)
    public fun decode(encoded: String): ByteArray = decodeInternal(encoded, CHARS)
    public fun decodeUrlSafe(encoded: String): ByteArray = decodeInternal(encoded, CHARS_URL)

    private fun encodeInternal(data: ByteArray, alphabet: String, withPadding: Boolean): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = data.getOrElse(i + 1) { 0 }.toInt() and 0xFF
            val b2 = data.getOrElse(i + 2) { 0 }.toInt() and 0xFF
            sb.append(alphabet[b0 shr 2])
            sb.append(alphabet[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            sb.append(if (i + 1 < data.size) alphabet[((b1 shl 2) or (b2 shr 6)) and 0x3F] else if (withPadding) '=' else "")
            sb.append(if (i + 2 < data.size) alphabet[b2 and 0x3F] else if (withPadding) '=' else "")
            i += 3
        }
        return sb.toString()
    }

    private fun decodeInternal(encoded: String, alphabet: String): ByteArray {
        val s = encoded.filter { it != '=' && it != '\n' && it != '\r' }
        require(s.length % 4 != 1) { "Invalid Base64 input length" }
        val rev = alphabet.mapIndexed { i, c -> c to i }.toMap()
        val out = mutableListOf<Byte>()
        var i = 0
        while (i < s.length) {
            val c0 = rev[s[i]] ?: error("Invalid char at $i")
            val c1 = rev[s.getOrElse(i + 1) { '=' }] ?: error("Invalid char at ${i + 1}")
            val c2 = rev[s.getOrElse(i + 2) { '=' }] ?: -1
            val c3 = rev[s.getOrElse(i + 3) { '=' }] ?: -1
            out.add(((c0 shl 2) or (c1 shr 4)).toByte())
            if (c2 >= 0) out.add(((c1 shl 4) or (c2 shr 2)).toByte())
            if (c3 >= 0) out.add(((c2 shl 6) or c3).toByte())
            i += 4
        }
        return out.toByteArray()
    }
}

public object Hex {
    private const val HEX = "0123456789abcdef"

    public fun encode(data: ByteArray): String = buildString(data.size * 2) {
        for (b in data) { append(HEX[(b.toInt() shr 4) and 0xF]); append(HEX[b.toInt() and 0xF]) }
    }

    public fun decode(hex: String): ByteArray {
        val s = hex.lowercase()
        require(s.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(s.length / 2) { i ->
            val hi = HEX.indexOf(s[i * 2]).also { require(it >= 0) { "Invalid hex at ${i * 2}" } }
            val lo = HEX.indexOf(s[i * 2 + 1]).also { require(it >= 0) { "Invalid hex at ${i * 2 + 1}" } }
            ((hi shl 4) or lo).toByte()
        }
    }

    public fun ByteArray.toHex(): String = encode(this)
    public fun String.hexToBytes(): ByteArray = decode(this)
}
