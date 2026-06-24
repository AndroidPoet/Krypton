package io.krypton.sample

import io.krypton.core.types.DeviceId
import io.krypton.core.types.ProtocolAddress
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.protocol.api.KryptonProtocol
import io.krypton.protocol.api.decrypt
import io.krypton.protocol.api.encrypt
import io.krypton.storage.memory.InMemoryPreKeyStore
import kotlinx.coroutines.runBlocking

/**
 * Minimal JVM (desktop) demo of a real end-to-end-encrypted exchange between two
 * parties — Alice and Bob — running on the actual libsignal native crypto.
 *
 * Run it:  ./gradlew :samples:jvm-demo:run
 *
 * No server here: we just hand Alice's pre-key bundle directly to Bob (in a real
 * app that bundle travels via your server). Everything else — X3DH handshake,
 * Double Ratchet, encrypt/decrypt — is the same code a production app would run.
 */

/** Builds a party with a signed pre-key + one-time pre-keys so it can publish a bundle. */
private suspend fun newParty(): KryptonProtocol {
    val preKeys = InMemoryPreKeyStore()
    val protocol = KryptonConfigurator().apply { preKeyStore = preKeys }.build()
    preKeys.saveSignedPreKey(1, protocol.generateSignedPreKey(1).getOrThrow()).getOrThrow()
    protocol.generatePreKeys(1, 10).getOrThrow().forEach { preKeys.storePreKey(it.keyId, it).getOrThrow() }
    return protocol
}

fun main() = runBlocking {
    println("Krypton JVM demo — real libsignal on ${System.getProperty("os.name")} (${System.getProperty("os.arch")})")
    println("─".repeat(64))

    val alice = newParty()
    val bob = newParty()
    val aliceAddr = ProtocolAddress("alice", DeviceId(1))

    // Bob fetches Alice's pre-key bundle and runs the X3DH handshake.
    bob.processPreKeyBundle(alice.getPreKeyBundle(aliceAddr).getOrThrow()).getOrThrow()
    println("✓ Session established (X3DH handshake)")

    // Bob → Alice. The simple API returns a Base64 wire string you can send anywhere.
    val plaintext = "Hello Alice — this is Bob, fully encrypted. 🔐"
    val wire = bob.encrypt("alice", plaintext).getOrThrow()
    println("✓ Bob encrypted   : ${wire.take(56)}…  (${wire.length} B64 chars)")

    // Alice decrypts back to the original.
    val decrypted = alice.decrypt("bob", wire).getOrThrow()
    println("✓ Alice decrypted : $decrypted")

    // Alice → Bob, on the now-ratcheting session.
    val reply = "Got it, Bob. Ratchet is turning. 🔁"
    val wire2 = alice.encrypt("bob", reply).getOrThrow()
    println("✓ Bob decrypted   : ${bob.decrypt("alice", wire2).getOrThrow()}")

    println("─".repeat(64))
    check(decrypted == plaintext) { "round-trip mismatch!" }
    println("ROUND-TRIP OK ✅  real end-to-end encryption works on the JVM.")
}
