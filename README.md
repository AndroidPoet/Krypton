# ⚛️ Krypton

**Signal's end-to-end encryption protocol for Kotlin Multiplatform** — the audited [libsignal](https://github.com/signalapp/libsignal) core under the hood, behind one clean common API.

> Named after Krypton (Kr), a noble gas that's inert, stable, and doesn't react with anything — just like perfectly encrypted data.

Krypton does **not** reimplement cryptography. It binds to Signal's real, audited libsignal on every platform and exposes a single `commonMain` API, so you write encryption code once and it runs everywhere libsignal does.

## Install

One dependency. It transitively brings the whole real API (`core` + `storage` + `protocol`):

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.krypton:krypton:0.1.0")
}
```

> **Status:** not yet published to Maven Central — see [Publishing](#publishing). Until then, build locally with `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.

## Quick start (the stupid-simple API)

```kotlin
import io.krypton.Krypton
import io.krypton.protocol.api.encrypt
import io.krypton.protocol.api.decrypt

val krypton = Krypton.protocol { }                          // zero config (auto identity + stores)

val wire = krypton.encrypt("alice", "Hello!").getOrThrow()  // wire-ready Base64 string — send it
val text = krypton.decrypt("alice", wire).getOrThrow()      // back to "Hello!"
```

Strings in, Base64 out — no `ProtocolAddress`, `ByteArray`, or message-type bookkeeping. The full typed API (`encrypt(ProtocolAddress, ByteArray)`, pre-key bundles, sessions, groups) is still there when you need it.

## "Do my users ship a 113 MB binary?" — No.

This is the most common confusion. **Your users add one Gradle line and nothing else.** Here's how the native libsignal reaches each platform:

| Platform | How libsignal is delivered | You ship a binary? |
|---|---|---|
| JVM / Android | Bundled inside Signal's Maven jars (pulled by Gradle) | **No** |
| iOS / macOS / native | The static lib is **linked into Krypton's published per-target artifact** (`staticLibraries` in the cinterop def) | **No** |

- The `libsignal_ffi.a` (~113 MB) is **gitignored only to keep it out of Git** (GitHub's 100 MB limit; binaries don't belong in source control). It is a **build input for Krypton's CI**, produced by [`scripts/build-libsignal-ffi.sh`](scripts/build-libsignal-ffi.sh), then embedded into the published Maven artifacts.
- It is **not** the size of your app. That `.a` is an unstripped archive; the linker keeps only used symbols and release builds strip — real app-size impact is a few MB (the same core Signal's own apps ship).

## Platform support (honest)

| Target | Crypto | Notes |
|---|---|---|
| **JVM** (desktop) | ✅ Real | libsignal-client (bundled natives) |
| **Android** | ✅ Real | libsignal-android |
| **iOS / macOS** (+ tvOS) | ✅ Real | cinterop → `libsignal_ffi` |
| **Linux / Windows** (native) | ⚠️ Stub | fail-loud; use the **JVM** target for desktop |
| **wasmJs** | ⚠️ Stub | libsignal has no wasm build — unsupported |

Compiles on all of the above; **real E2EE runs on JVM, Android, iOS, and macOS**.

## API parity with libsignal

Krypton aims to mirror Signal's libraries so you can follow libsignal's docs. Current coverage:

| libsignal area | Krypton API | Status |
|---|---|---|
| Protocol (X3DH, Double Ratchet, sessions, pre-keys) | `krypton-protocol` | ✅ Implemented (JVM/Android/Apple) |
| Fingerprints / safety numbers | `KryptonProtocol.safetyNumber(...)` | ✅ Implemented (JVM/Android) |
| Sealed sender | `KryptonProtocol.sealedSender*` / `SealedSender` | ✅ Implemented (JVM/Android) |
| zkgroup — group params, profile access key / version / commitment | `krypton-zkgroup` / `KryptonProtocol` | ✅ Implemented (JVM/Android) |
| zkgroup — server-issued credentials & membership proofs | — | ⛔ Not provided (needs a credential server) |
| Standalone Double Ratchet | `krypton-double-ratchet` | ⛔ Fails loud — use `KryptonProtocol` (ratchet runs inside libsignal) |

> Safety numbers, sealed sender, and the client-side zkgroup primitives are backed by real libsignal (`NumericFingerprintGenerator`, `SealedSessionCipher`, `GroupSecretParams`/`ProfileKey`) and verified by real-crypto tests — including byte-for-byte cross-checks against libsignal for the zkgroup derivations. On platforms where the native bridge hasn't wired a feature yet, it **fails loud** rather than faking. The full zkgroup **credential dance** (auth/profile-key credentials, membership presentations) needs a credential-issuing server and is intentionally **not stubbed** — it's absent, not fake. The standalone `double-ratchet` also **fails loud** instead of returning fake key material.

## Architecture

```
┌──────────────────────────────────────────────┐
│  Your app  →  io.krypton:krypton (one dep)    │
├──────────────────────────────────────────────┤
│  commonMain:  Krypton.protocol { }            │  ← one common API
│               encrypt(...) / decrypt(...)     │
├──────────────────────────────────────────────┤
│  expect/actual Bridge (per platform):         │
│    JVM/Android → libsignal Java SDK (JNI)     │
│    iOS/macOS   → cinterop → libsignal_ffi     │
├──────────────────────────────────────────────┤
│        Signal's audited libsignal core        │
└──────────────────────────────────────────────┘
```

## Keeping up with Signal

libsignal is pinned to one version across both tracks (`gradle/libs.versions.toml` → `libsignal`), and `:krypton-protocol:verifyLibsignalVersion` (run by `./gradlew check`) fails the build if the native `libsignal_ffi.a` drifts from the JVM/Android Maven version. The [`check-libsignal-update`](.github/workflows/check-libsignal-update.yml) workflow opens a bump PR when Signal ships a release; [`build-libsignal-ffi`](.github/workflows/build-libsignal-ffi.yml) rebuilds the native libs for it.

## Publishing

Not yet published. To make the one-line install real, Krypton needs: CI that builds `libsignal_ffi.a` for each native target → links it into the per-target artifacts → publishes `io.krypton:krypton` (+ per-target variants) to Maven Central. See the workflows in `.github/workflows/`.

## Modules

| Module | Description |
|--------|-------------|
| `krypton` | **The single dependency** — entry point `Krypton.protocol { }`, re-exports core+storage+protocol |
| `krypton-core` | Result types, key types, encoding |
| `krypton-storage` | Store interfaces + in-memory (platform stores are scaffolds) |
| `krypton-protocol` | Signal Protocol (X3DH, sessions, encrypt/decrypt) + the platform bridges |
| `krypton-sealed-sender` | Sealed-sender convenience wrapper (real on JVM/Android) |
| `krypton-zkgroup` | Client-side zkgroup primitives — group params, profile access key/version/commitment (real on JVM/Android) |
| `krypton-double-ratchet` | ⛔ fails loud — the ratchet runs inside `KryptonProtocol`/libsignal |

## License

AGPL-3.0-only (matching libsignal's license)
