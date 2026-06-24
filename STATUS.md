# Krypton — Project State

_Last updated: 2026-06-14 · libsignal pinned to **0.86.5** · branch `master`_

A snapshot of what's real, what's verified, and what's pending. The rule for this
project: **no fake crypto.** Every feature is either backed by real libsignal and
covered by a test, or it **fails loud** — it never returns a plausible-looking
placeholder.

---

## TL;DR

- One consumable artifact: `io.krypton:krypton` (re-exports `core` + `storage` + `protocol`).
- libsignal is pinned to a single version across JVM/Android (Maven) and native
  (the `libsignal_ffi.a` recorded in `krypton-protocol/libs/apple/VERSION`); a
  build-time check (`:krypton-protocol:verifyLibsignalVersion`) fails on drift.
- Real end-to-end crypto runs on **JVM, Android, iOS, macOS**. Linux/Windows/wasm
  compile but fail loud (no libsignal binary for them).
- Every ✅ below has a real-libsignal test (most cross-check byte-for-byte).

---

## Feature parity with libsignal

| Area | Krypton API | State | Verified by |
|---|---|---|---|
| Protocol: X3DH, Double Ratchet, sessions, pre-keys | `KryptonProtocol.encrypt/decrypt`, `processPreKeyBundle`, `getPreKeyBundle` | ✅ Real (JVM/Android/Apple) | `VerifiedProductionTest` full X3DH+ratchet round-trip |
| Safety numbers / fingerprints | `KryptonProtocol.safetyNumber(...)` | ✅ Real (JVM/Android) | both parties compute the identical number |
| Sealed sender | `SealedSender` / `KryptonProtocol.sealedSender*` | ✅ Real (JVM/Android) | round-trip through a server-issued `SenderCertificate` |
| zkgroup — group params (master key → secret/public/identifier) | `ZkGroup.deriveGroupSecretParams` | ✅ Real (JVM/Android) | byte-for-byte vs libsignal + determinism |
| zkgroup — profile access key / version / commitment | `ZkGroup.deriveProfileKeyAccessKey` / `profileKeyVersion` / `profileKeyCommitment` | ✅ Real (JVM/Android) | byte-for-byte vs libsignal |
| zkgroup — group cipher (member IDs / profile keys / blobs) | `ZkGroup.encrypt*` / `decrypt*`, `KryptonProtocol.group*` | ✅ Real (JVM/Android) | encrypt→decrypt round-trips + cross-check vs libsignal |
| zkgroup — server-issued credentials & membership proofs | — | ⛔ Not provided | needs a credential-issuing server; **absent, not stubbed** |
| Standalone Double Ratchet | `krypton-double-ratchet` | ⛔ Fails loud | libsignal has **no** bare ratchet either; `encrypt/decrypt` **is** the ratchet |
| Group messaging (sender keys) | `KryptonProtocol.groupEncrypt/Decrypt` | 🚧 Not wired | currently returns a fail-loud error |

### Why the two ⛔ rows are correct, not gaps
- **Credential dance**: the issuing half requires `ServerSecretParams` held by a
  server. libsignal ships both halves, so it's fully implementable and locally
  testable later — but it's only *useful* if you run a Signal-compatible group
  server. Chosen scope: ship the group cipher (no server needed), leave issuance out.
- **Standalone double ratchet**: libsignal's jar contains zero ratchet/chainkey
  classes — the ratchet only runs inside a session. Krypton matches this 1:1.

---

## Platform support

| Target | Crypto | How libsignal is delivered |
|---|---|---|
| JVM (desktop) | ✅ Real, verified | Signal's Maven jars (bundled natives) |
| Android | ✅ Real | `libsignal-android` (same bridge as JVM) |
| iOS / macOS (+ tvOS) | ✅ Real, verified | cinterop → `libsignal_ffi`; **send + decrypt round-trip verified** (see below) |
| Linux / Windows (native) | ⚠️ Fail-loud | no libsignal binary; use JVM for desktop |
| wasmJs | ⚠️ Fail-loud | libsignal has no wasm build |

Full, test-verified E2EE runs on **JVM, Android, iOS, and macOS**. On **Apple**,
real libsignal_ffi 0.86.5 is wired and the **full round-trip** is proven by a
real-FFI test (`NativeRealCryptoTest`, run on every Apple target via `appleTest`):
genuine ~1568-byte Kyber pre-key, X3DH handshake, encrypt, **and PreKey decrypt
back to plaintext** all succeed — matching the JVM/Android tracks.

Per-arch native binaries are built and linked (each Kotlin target links its
own-arch `libsignal_ffi.a` from `libs/apple/<arch>/`):

| Arch dir | Triple | Kotlin target | Status |
|---|---|---|---|
| `macos-arm64` | `aarch64-apple-darwin` | `macosArm64` | ✅ round-trip **runs** (host test) |
| `ios-sim-arm64` | `aarch64-apple-ios-sim` | `iosSimulatorArm64` | ✅ round-trip **runs** on the iOS simulator |
| `ios-arm64` | `aarch64-apple-ios` | `iosArm64` (device) | ✅ test executable **links** as `platform IOS` (run needs a physical device) |

Other Apple arches (`*X64`, `tvos*`) compile against a fallback `.a` but aren't
link/run-verified here — build their own-arch `.a` (same script) to ship them.

> The earlier PreKey-decrypt failure (libsignal error 30 / `InvalidMessage`) was a
> binary/header version drift: the native bridge had been built against a newer
> mislabeled `libsignal_ffi.a` whose store-callback ABI (phantom `local_address`
> params, `destroy` struct fields, pair-returning identity callback) didn't match
> the 0.86.5 Rust core. Fixed by rebuilding the real 0.86.5 `.a` from source and
> converting the entire `appleMain` bridge to the 0.86.5 callback ABI.

---

## Architecture decisions (the "why")

- **expect/actual Bridge.** There's no official KMP binding for libsignal, so the
  platform split is unavoidable. Consumers never see it — they get one common API.
- **Fail-loud-default Bridge pattern.** The abstract `Bridge` declares each crypto
  op as `open` returning a `CryptoResult.Failure`. Only `RealBridge`
  (`jvmAndAndroidMain`) overrides with real libsignal. New features land without
  touching the Apple FFI / native source sets, and unsupported platforms fail
  loudly instead of faking.
- **Single version source of truth.** `gradle/libs.versions.toml` → `libsignal`,
  enforced against the native `VERSION` file by `verifyLibsignalVersion`.
- **`CryptoResult<T>`** everywhere — no thrown exceptions across the API surface.

---

## Pending / next up

1. **Maven publishing** — the per-arch native build + link is now proven locally
   (`scripts/build-libsignal-ffi.sh` produces `macos-arm64` / `ios-arm64` /
   `ios-sim-arm64`, each links into its Kotlin target). Remaining: run that in CI →
   publish `io.krypton:krypton` (+ per-target variants) to Maven Central so the
   one-line install becomes real. (Workflows scaffolded in `.github/workflows/`,
   not yet run — repo is private.) Building the remaining ship arches (`iosX64`
   sim-x64, `tvos*`) is the same script with more triples.
2. **Group messaging (sender keys)** — `groupEncrypt/groupDecrypt` are not wired yet.
3. _(Optional)_ zkgroup credential dance — implementable + locally testable if a
   compatible server is ever in scope.

> **Done:** Apple native E2EE (incl. the PreKey **decrypt** receive path) now
> works end-to-end on real `libsignal_ffi` 0.86.5 — see Platform support above.

---

## How to verify the current state yourself

```bash
# Real-libsignal crypto tests (X3DH, safety numbers, sealed sender, zkgroup)
./gradlew :krypton-protocol:jvmTest --tests "io.krypton.protocol.VerifiedProductionTest"

# Real native Apple E2EE — full encrypt→decrypt round-trip on libsignal_ffi
./gradlew :krypton-protocol:macosArm64Test          # runs on macOS host
./gradlew :krypton-protocol:iosSimulatorArm64Test   # runs on the iOS simulator
./gradlew :krypton-protocol:linkDebugTestIosArm64   # links the iOS-device arch

# (Re)build the per-arch native libs — macos-arm64, ios-arm64, ios-sim-arm64
scripts/build-libsignal-ffi.sh 0.86.5

# zkgroup wrapper tests
./gradlew :krypton-zkgroup:jvmTest

# Cross-platform compile (native + wasm fail-loud paths)
./gradlew :krypton:compileKotlinMacosArm64 :krypton:compileKotlinWasmJs :krypton:compileKotlinLinuxX64

# Version-skew guard
./gradlew :krypton-protocol:verifyLibsignalVersion
```
