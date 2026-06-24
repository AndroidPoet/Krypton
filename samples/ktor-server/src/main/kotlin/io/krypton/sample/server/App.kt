package io.krypton.sample.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.signal.libsignal.metadata.certificate.ServerCertificate
import org.signal.libsignal.protocol.ServiceId
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.zkgroup.ServerSecretParams
import org.signal.libsignal.zkgroup.auth.ServerZkAuthOperations
import java.time.Instant
import java.util.Base64
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal Krypton companion server, built on Ktor + `org.signal:libsignal-server`
 * (the JVM-only server half of libsignal). It does the two things a client can't:
 *
 *  - hand out pre-key bundles so two clients can start an X3DH session, and
 *  - mint server-signed credentials (sealed-sender certificates, zkgroup auth).
 *
 * The actual end-to-end encryption stays on the client, in Krypton. This server
 * never sees plaintext.
 *
 * Run it:  ./gradlew :samples:ktor-server:run    (listens on http://localhost:8080)
 *
 * Storage here is an in-memory map so the sample runs with zero setup. In production
 * you'd persist to a database — see the README for swapping in Supabase/Postgres.
 */

// --- Server-held key material. In production: generate once, store in a vault/HSM,
//     and load on startup. Here we generate fresh each run. ---
private val trustRoot = ECKeyPair.generate()
private val serverKeyPair = ECKeyPair.generate()
private val serverCertificate = ServerCertificate(trustRoot.privateKey, /* keyId = */ 1, serverKeyPair.publicKey)
private val zkSecretParams = ServerSecretParams.generate()
private val zkAuthOps = ServerZkAuthOperations(zkSecretParams)

// --- In-memory store: user id -> base64 pre-key bundle. Swap for a real DB in prod. ---
private val preKeyBundles = ConcurrentHashMap<String, String>()

private val b64 = Base64.getEncoder()
private val b64d = Base64.getDecoder()

fun main() {
    embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
}

fun Application.module() {
    routing {
        // Clients pin this trust root to validate the sealed-sender certificates below.
        get("/v1/trust-root") {
            call.respondText(b64.encodeToString(trustRoot.publicKey.serialize()))
        }

        // --- Pre-key bundle exchange: the transport the X3DH handshake needs. ---
        // A client publishes the bundle Krypton produced (getPreKeyBundle), another fetches it.
        post("/v1/keys/{user}") {
            val user = call.parameters["user"]!!
            preKeyBundles[user] = call.receiveText().trim()
            call.respondText("stored", status = HttpStatusCode.Created)
        }
        get("/v1/keys/{user}") {
            val bundle = preKeyBundles[call.parameters["user"]!!]
            if (bundle == null) {
                call.respondText("no bundle for that user", status = HttpStatusCode.NotFound)
            } else {
                call.respondText(bundle)
            }
        }

        // --- Sealed sender: issue a SenderCertificate for a client (real libsignal-server crypto). ---
        // Body = base64 of the client's identity public key. Returns a base64 certificate the
        // client passes to Krypton's sealedSenderEncrypt(...).
        post("/v1/certificate/{uuid}/{device}") {
            val uuid = call.parameters["uuid"]!!
            val device = call.parameters["device"]!!.toInt()
            val identityKey = ECPublicKey(b64d.decode(call.receiveText().trim()))
            val expiresAt = Instant.now().plusSeconds(24 * 60 * 60).toEpochMilli()
            val cert = serverCertificate.issue(
                serverKeyPair.privateKey,
                uuid,
                Optional.empty<String>(), // sender E164 (phone) — omitted
                device,
                identityKey,
                expiresAt,
            )
            call.respondText(b64.encodeToString(cert.serialized))
        }

        // --- zkgroup: publish server public params + issue an auth credential. ---
        get("/v1/zkgroup/public-params") {
            call.respondText(b64.encodeToString(zkSecretParams.publicParams.serialize()))
        }
        // POST /v1/zkgroup/auth/{aci}/{pni} — both are UUIDs.
        post("/v1/zkgroup/auth/{aci}/{pni}") {
            val aci = ServiceId.Aci(UUID.fromString(call.parameters["aci"]!!))
            val pni = ServiceId.Pni(UUID.fromString(call.parameters["pni"]!!))
            val response = zkAuthOps.issueAuthCredentialWithPniZkc(aci, pni, Instant.now())
            call.respondText(b64.encodeToString(response.serialize()))
        }
    }
}
