# Building end-to-end encrypted chat with Krypton

This is the same flow Signal uses (via libsignal) — Krypton just gives you one
clean Kotlin API for it. By the end you'll know exactly which pieces **Krypton
handles** and which pieces **your app must provide**.

> **The golden rule of E2E:** your server never sees plaintext and never sees a
> private key. It only relays **ciphertext** and **public** key bundles. If your
> server could read messages, it isn't end-to-end encrypted.

---

## 1. The mental model — who does what

```
┌─────────────┐         your server          ┌─────────────┐
│   Alice     │   (a dumb relay + key store)  │    Bob      │
│  (device)   │                               │  (device)   │
│             │  1. publish PUBLIC pre-key ──▶ │             │
│             │     bundle                     │             │
│             │ ◀── 2. fetch Bob's bundle ──── │             │
│  Krypton    │                               │  Krypton    │
│  encrypts ──┼──── 3. send CIPHERTEXT ──────▶ │ ── decrypts │
│             │                               │             │
└─────────────┘                               └─────────────┘
        the server sees ❌ plaintext, ❌ private keys
        the server sees ✅ public bundles, ✅ ciphertext
```

**Krypton (this library) handles:** identity keys, pre-keys, the X3DH handshake,
the Double Ratchet, encrypt/decrypt, sealed sender, safety numbers.

**Your app must provide three things Krypton can't:**
1. **Transport** — any way to move bytes between devices (WebSocket, HTTPS, MQTT,
   Firebase, even email). Krypton gives you a Base64 string; you deliver it.
2. **A key directory** — a server endpoint where a user uploads their *public*
   pre-key bundle and others fetch it. Public data only.
3. **Persistent storage** — somewhere on-device to keep identity/sessions/pre-keys
   between app launches (see §6). Krypton ships in-memory stores for prototyping.

---

## 2. The setup flow (five steps, mirroring Signal)

### Step 0 — add Krypton

```kotlin
dependencies {
    implementation("io.krypton:krypton:0.1.0")
}
```

### Step 1 — create the client (one per user/device)

`Krypton.protocol { }` generates a long-term **identity key pair** and a
**registration id** automatically. In production you generate this **once per
install** and persist it (see §6); never regenerate it, or you'll break every
existing session and change your safety number.

```kotlin
import io.krypton.Krypton

val krypton = Krypton.protocol { }   // zero-config: auto identity + in-memory stores
```

### Step 2 — generate and publish a pre-key bundle

A **pre-key bundle** is a packet of *public* keys that lets someone start a
session with you while you're offline. You generate these once at registration
(and replenish them — see §6), store the private halves locally, and upload the
public bundle to your server.

```kotlin
import io.krypton.core.types.ProtocolAddress
import io.krypton.core.types.DeviceId

val me = ProtocolAddress("alice", DeviceId.PRIMARY)

// Signed pre-key (rotated periodically) — store it, Krypton signs it with your identity.
val signedPreKey = krypton.generateSignedPreKey(signedKeyId = 1).getOrThrow()
// One-time pre-keys (consumed one per new session) — generate a batch.
val oneTimePreKeys = krypton.generatePreKeys(startKeyId = 1, count = 100).getOrThrow()

// In the zero-config setup these are already in the in-memory store; with your own
// stores, persist them here. Then build the PUBLIC bundle to upload:
val myBundle = krypton.getPreKeyBundle(me).getOrThrow()

// Send myBundle's PUBLIC fields to your server (serialize as JSON/protobuf — it
// contains only public keys + ids + signatures, never a private key).
myServerApi.uploadBundle("alice", myBundle)
```

### Step 3 — start a session with someone (X3DH handshake)

To message Bob the first time, fetch **his** public bundle from your server and
hand it to Krypton. This runs X3DH and establishes a shared session — no round
trip with Bob required; he can be offline.

```kotlin
val bobBundle = myServerApi.fetchBundle("bob")   // public bundle from your server
krypton.processPreKeyBundle(bobBundle).getOrThrow()
// You now have a session with Bob. Do this once per peer.
```

### Step 4 — send and receive messages

After a session exists, every message is one call. Strings in, a wire-ready
Base64 string out — the Double Ratchet advances automatically on each message, so
every message uses a fresh key (forward secrecy).

```kotlin
// Alice sends:
val wire = krypton.encrypt("bob", "Hey Bob 👋").getOrThrow()
myServerApi.relay(to = "bob", payload = wire)     // server forwards ciphertext only

// Bob receives (on his own Krypton client):
val text = kryptonOnBobsDevice.decrypt("alice", wire).getOrThrow()  // "Hey Bob 👋"
```

That's it. The first message Alice sends is a `PRE_KEY` message that also carries
the handshake, so **Bob doesn't need to do step 3** — decrypting Alice's first
message establishes his side of the session automatically.

### Step 5 — verify the conversation is secure (safety numbers)

To defend against a malicious server swapping keys (a MITM), the two users compare
a **safety number** out-of-band (read it aloud, scan a QR). Identical numbers =
no one is in the middle.

```kotlin
val sn = krypton.safetyNumber("alice", "bob", bobIdentityKeyBytes).getOrThrow()
println(sn.displayText)   // e.g. "12345 67890 ..." — compare with Bob's screen
// sn.scannable holds the QR-code bytes for scan-to-verify.
```

---

## 3. Full minimal example (two users, one process)

This is the whole protocol with no server — useful as a test or to see every step
in one place. (It's essentially what `VerifiedProductionTest` does with real
libsignal.)

```kotlin
import io.krypton.Krypton
import io.krypton.core.types.ProtocolAddress
import io.krypton.core.types.DeviceId

suspend fun demo() {
    // Two independent clients (in a real app, on two devices).
    val alice = Krypton.protocol { }
    val bob   = Krypton.protocol { }

    val aliceAddr = ProtocolAddress("alice", DeviceId.PRIMARY)
    val bobAddr   = ProtocolAddress("bob", DeviceId.PRIMARY)

    // Bob registers pre-keys and publishes a bundle.
    bob.generateSignedPreKey(1).getOrThrow()
    bob.generatePreKeys(1, 10).getOrThrow()
    val bobBundle = bob.getPreKeyBundle(bobAddr).getOrThrow()

    // Alice starts a session with Bob from his public bundle, then sends.
    alice.processPreKeyBundle(bobBundle).getOrThrow()
    val wire = alice.encrypt("bob", "Hello, Bob!").getOrThrow()

    // Bob decrypts — his session is set up automatically by this first message.
    val text = bob.decrypt("alice", wire).getOrThrow()
    println(text)  // Hello, Bob!
}
```

> Note: for Bob's `generateSignedPreKey`/`generatePreKeys` to feed
> `getPreKeyBundle`, use a setup that persists them into Bob's `PreKeyStore`. The
> zero-config client keeps them in memory for the process, which is enough for this
> demo; for a real app see §6.

---

## 4. What goes over the wire (and what your server stores)

| Item | Public or secret? | Lives where |
|---|---|---|
| Identity key **pair** | secret (private half) | on-device only, persisted forever |
| Identity **public** key | public | in the pre-key bundle, on your server |
| Signed pre-key (public + signature) | public | bundle, on your server |
| One-time pre-keys (public) | public | bundle, on your server (consumed on use) |
| Registration id | public | bundle |
| **Messages** | **ciphertext** | relayed by your server, never readable |
| Session state, ratchet keys | secret | on-device only |

Your server is a **key directory + relay**. It needs endpoints roughly like:
`POST /bundle` (upload mine), `GET /bundle/{user}` (fetch theirs),
`POST /messages/{user}` (relay ciphertext), `GET /messages` (fetch my inbox).
None of them ever touch plaintext or private keys.

---

## 5. Hiding *who* is talking — sealed sender (optional)

By default your server sees ciphertext but still sees the `from`/`to` routing.
**Sealed sender** encrypts the sender's identity inside the envelope too, so the
server can't tell who sent a message. It needs a server-issued sender certificate
(see Signal's sealed-sender design). Krypton exposes it directly:

```kotlin
val sealed = SealedSender(krypton, myUuid, myDeviceId, mySenderCertificate, serverTrustRoot)
val envelope = sealed.send(bobAddr, "anon hi".encodeToByteArray()).getOrThrow()
val opened   = sealed.receive(envelope, nowMillis).getOrThrow()  // opened.senderUuid, opened.message
```

---

## 6. Production checklist (don't skip these)

- [ ] **Persist the identity key pair** at first launch and reuse it forever.
      Regenerating it resets every session and changes your safety number.
- [ ] **Implement real stores.** Replace the in-memory stores with disk-backed
      implementations of `IdentityKeyStore` / `SessionStore` / `PreKeyStore` so
      sessions survive app restarts. (Krypton ships the interfaces +
      `InMemory*` references to copy.)
- [ ] **Replenish one-time pre-keys.** They're consumed one per new incoming
      session. Have clients top up the server when the count runs low (Signal
      keeps ~100 available).
- [ ] **Rotate the signed pre-key** periodically (e.g. every few days).
- [ ] **Verify safety numbers** in your UI for high-trust conversations.
- [ ] **Handle decrypt failures gracefully** — a `CryptoResult.Failure` can mean a
      duplicate/out-of-order message or a genuine tamper; surface, don't crash.
- [ ] **Pick a platform with real crypto** — JVM, Android, iOS, or macOS. Linux/
      Windows-native and wasm compile but fail loud (no libsignal binary).

---

## 7. Where to look in libsignal / Krypton

| Concept | libsignal | Krypton |
|---|---|---|
| Identity + registration | `IdentityKeyPair`, `KeyHelper` | `Krypton.protocol { }` |
| Pre-key bundle | `PreKeyBundle`, `SignedPreKeyRecord` | `generateSignedPreKey`, `generatePreKeys`, `getPreKeyBundle` |
| Session setup (X3DH) | `SessionBuilder.process(bundle)` | `processPreKeyBundle` |
| Encrypt/decrypt (Double Ratchet) | `SessionCipher` | `encrypt` / `decrypt` |
| Safety number | `NumericFingerprintGenerator` | `safetyNumber` |
| Sealed sender | `SealedSessionCipher` | `SealedSender` |

For the protocol theory, read Signal's specs: the
[X3DH](https://signal.org/docs/specifications/x3dh/) key agreement and the
[Double Ratchet](https://signal.org/docs/specifications/doubleratchet/). Krypton
implements them by binding to libsignal, so those docs apply directly.
