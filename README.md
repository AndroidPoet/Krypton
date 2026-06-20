<h1 align="center">⚛️ Krypton</h1>

<p align="center"><b>Signal's end-to-end encryption for Kotlin Multiplatform.</b></p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.krypton/krypton"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/io.krypton/krypton?color=blue&label=Maven%20Central"/></a>
  <a href="https://kotlinlang.org"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white"/></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/License-AGPL--3.0-green.svg"/></a>
  <a href="https://github.com/signalapp/libsignal"><img alt="libsignal" src="https://img.shields.io/badge/libsignal-0.86.5-111111.svg"/></a>
</p>

<p align="center">
  <img alt="badge-android" src="http://img.shields.io/badge/-android-6EDB8D.svg?style=flat"/>
  <img alt="badge-ios" src="http://img.shields.io/badge/-ios-CDCDCD.svg?style=flat"/>
  <img alt="badge-macos" src="http://img.shields.io/badge/-macos-111111.svg?style=flat"/>
  <img alt="badge-jvm" src="http://img.shields.io/badge/-jvm-DB413D.svg?style=flat"/>
</p>

<p align="center">
One common API for the <b>Signal Protocol</b> — X3DH, Double Ratchet, sealed sender,
and zkgroup — backed by Signal's real, audited <a href="https://github.com/signalapp/libsignal">libsignal</a>
on Android, iOS, macOS, and JVM/Compose Desktop.
</p>

---

Krypton does **not** reimplement cryptography. It binds to Signal's audited libsignal
on every platform and exposes a single `commonMain` API, so you write your encryption
code once and it runs everywhere libsignal does. Where a platform has no libsignal
binary, Krypton **fails loud** — it never returns a plausible-looking fake.

> Named after Krypton (Kr) — a noble gas that's inert, stable, and reacts with nothing. Like properly encrypted data.

## Why Krypton

- **Real crypto, not a reimplementation.** Same library that ships inside Signal and WhatsApp.
- **Write once.** One `commonMain` API across Android, iOS, macOS, and JVM.
- **Tiny footprint.** ~3 MB added to a mobile app — the native lib is dead-stripped at link / stripped on release ([details](#how-big-is-it)).
- **No fakes.** Unsupported platforms fail loud; a build-time guard stops the native binary from ever drifting from the JVM/Android version.
- **One dependency.** It transitively brings the whole real API.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.krypton:krypton:0.1.0")
}
```

One line pulls the full API (`core` + `storage` + `protocol`) on every target. On JVM/Android,
libsignal is fetched from Maven; on Apple, the native `libsignal_ffi.a` is embedded inside the
published artifact — **your users add nothing else.**

> **Status — pre-first-release.** The Maven Central pipeline is wired and verified
> ([Releasing](#releasing)); until the first tag is pushed, build locally with
> `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.

## Quick start

A real two-party exchange — Alice and Bob each hold a `KryptonProtocol`; Bob runs the
X3DH handshake from Alice's published pre-key bundle, then messages flow encrypted both ways:

```kotlin
import io.krypton.core.types.DeviceId
import io.krypton.core.types.ProtocolAddress
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.protocol.api.decrypt
import io.krypton.protocol.api.encrypt
import io.krypton.storage.memory.InMemoryPreKeyStore

// Build a party with a signed pre-key + one-time pre-keys (so it can publish a bundle).
suspend fun newParty(): KryptonProtocol {
    val keys = InMemoryPreKeyStore()
    val p = KryptonConfigurator().apply { preKeyStore = keys }.build()
    keys.saveSignedPreKey(1, p.generateSignedPreKey(1).getOrThrow()).getOrThrow()
    p.generatePreKeys(1, 10).getOrThrow().forEach { keys.storePreKey(it.keyId, it).getOrThrow() }
    return p
}

val alice = newParty()
val bob = newParty()

// Bob fetches Alice's bundle (via your server) and runs X3DH.
bob.processPreKeyBundle(alice.getPreKeyBundle(ProtocolAddress("alice", DeviceId(1))).getOrThrow())

// Strings in, Base64 out — send the wire over any transport.
val wire = bob.encrypt("alice", "Hello Alice!").getOrThrow()
val text = alice.decrypt("bob", wire).getOrThrow()   // "Hello Alice!"
```

The full typed API (`encrypt(ProtocolAddress, ByteArray)`, sessions, safety numbers, sealed
sender, zkgroup) is there when you need it. For the end-to-end story — registration, publishing
bundles, what your server must and must not do — see **[GUIDE.md](GUIDE.md)**.

## Samples

| Sample | Run | What it shows |
| --- | --- | --- |
| `samples/jvm-demo` | `./gradlew :samples:jvm-demo:run` | Headless encrypt→decrypt round-trip on real libsignal |
| `samples/composeApp` | `./gradlew :samples:composeApp:run` | **One Compose UI shared across Desktop + iOS** — a live encrypted chat |

The iOS app is an Xcode project under `samples/composeApp/iosApp` (generated with
[XcodeGen](https://github.com/yonaskolb/XcodeGen)):

```sh
cd samples/composeApp/iosApp && xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO build
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted io.krypton.sample
```

## Platforms

| Target | Crypto | How libsignal is delivered |
| --- | --- | --- |
| **JVM** (desktop) | ✅ Real, verified | `libsignal-client` (bundled natives) from Maven |
| **Android** | ✅ Real, verified | `libsignal-android` from Maven |
| **iOS** (arm64 device + simulator) | ✅ Real, verified | cinterop → `libsignal_ffi`, embedded in the artifact |
| **macOS** (Apple Silicon + Intel) | ✅ Real, verified | cinterop → `libsignal_ffi`, embedded in the artifact |
| Linux / Windows (native) | ⛔ Fail-loud | no libsignal binary — use the JVM target for desktop |
| wasm / JS | ⛔ Fail-loud | libsignal has no wasm build |

The full Signal handshake (real ~1568-byte Kyber pre-key → X3DH → encrypt → decrypt) is proven
by `NativeRealCryptoTest`, which **runs on the iOS simulator and macOS** in addition to the
JVM/Android tracks.

## How big is it?

Krypton's own code is negligible (~0.2 MB). The size is libsignal — and far smaller than the
raw artifact suggests, because the linker keeps only what you call and release builds strip:

| Platform | Real app-size impact (release) |
| --- | --- |
| iOS / macOS | **~3 MB** (static lib, dead-stripped at link) |
| Android (per ABI) | **~3 MB download / ~6.4 MB installed** (AGP strips `.so` on release) |
| JVM desktop | ~20 MB per OS |

This is the same core that Signal and WhatsApp ship.

## API parity with libsignal

| libsignal area | Krypton API | Status |
| --- | --- | --- |
| Protocol — X3DH, Double Ratchet, sessions, pre-keys | `KryptonProtocol.encrypt`/`decrypt`, `processPreKeyBundle` | ✅ Real |
| Safety numbers / fingerprints | `KryptonProtocol.safetyNumber(...)` | ✅ Real |
| Sealed sender | `KryptonProtocol.sealedSender*` | ✅ Real |
| zkgroup — params, profile key derivations, group cipher | `ZkGroup.*` / `KryptonProtocol.group*` | ✅ Real |
| zkgroup — server-issued credentials & membership proofs | — | ⛔ Not provided (needs a credential server) |
| Standalone Double Ratchet | — | ⛔ Fails loud — *libsignal has no bare ratchet either*; the ratchet lives inside a session |

Every ✅ is covered by a real-libsignal test, most with byte-for-byte cross-checks. The two ⛔
rows are intentional, not gaps — the zkgroup credential dance needs a credential-issuing server,
and libsignal itself exposes no standalone ratchet (`encrypt`/`decrypt` **is** the ratchet).

## Architecture

```
┌──────────────────────────────────────────────┐
│  Your app  →  io.krypton:krypton  (one dep)   │
├──────────────────────────────────────────────┤
│  commonMain:  KryptonProtocol                 │  ← one common API
│               encrypt(...) / decrypt(...)     │
├──────────────────────────────────────────────┤
│  expect/actual bridge (per platform):         │
│    JVM / Android → libsignal Java SDK (JNI)   │
│    iOS / macOS   → cinterop → libsignal_ffi   │
├──────────────────────────────────────────────┤
│        Signal's audited libsignal core        │
└──────────────────────────────────────────────┘
```

## Staying in sync with Signal

libsignal is pinned to **one** version across both tracks (`gradle/libs.versions.toml` →
`libsignal`). The native `libsignal_ffi.a` is never committed — it's rebuilt from Signal's
source at the pinned tag and embedded into the published artifacts. Three workflows keep it honest:

- **`dependabot.yml`** + **`check-libsignal-update.yml`** — observe new libsignal releases and open a version-bump PR.
- **`ci.yml`** — on that PR, clones `signalapp/libsignal` at the new tag, rebuilds the `.a`, and runs the real-crypto tests on JVM + iOS, so an ABI change fails the PR instead of a release.
- **`release.yml`** — on a `v*` tag, rebuilds the `.a` fresh and publishes every target to Maven Central.

A build-time guard (`:krypton-protocol:verifyLibsignalVersion`, wired into `check`) fails the
build if the native and Maven versions ever drift, so an iOS user and an Android user can never
silently end up on incompatible wire formats.

## Building & testing

```sh
./gradlew :krypton-protocol:jvmTest                 # JVM real-crypto tests
./gradlew :krypton-protocol:iosSimulatorArm64Test   # iOS-simulator real-crypto tests
./gradlew :krypton-protocol:verifyLibsignalVersion  # native/Maven version guard
scripts/build-libsignal-ffi.sh 0.86.5               # (re)build the Apple native libs
```

## Releasing

A single `macos` job builds every target (Apple natively, the rest cross-compiled), so one
runner publishes the whole library. Push a tag and CI handles the rest:

```sh
git tag v0.1.0 && git push --tags    # triggers .github/workflows/release.yml
```

It needs four repo secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`
(ASCII-armored GPG key), `SIGNING_PASSWORD`.

## Modules

| Module | Description |
| --- | --- |
| **`krypton`** | The single dependency — re-exports `core` + `storage` + `protocol` |
| `krypton-core` | Result types, key types, encoding |
| `krypton-storage` | Store interfaces + in-memory implementations |
| `krypton-protocol` | The Signal Protocol + the per-platform libsignal bridges |
| `krypton-sealed-sender` | Sealed-sender convenience wrapper |
| `krypton-zkgroup` | Client-side zkgroup — params, profile derivations, group cipher |

## Contributing

Issues and PRs are welcome. Please make sure the build stays green:

```sh
./gradlew check
```

## Acknowledgements

Built on [signalapp/libsignal](https://github.com/signalapp/libsignal) — the real cryptography
is entirely Signal's work. Krypton only provides the Kotlin Multiplatform bindings.

## License

Krypton is licensed under **AGPL-3.0-only**, matching libsignal. See [LICENSE](LICENSE).

```
Copyright (C) 2026 Ranbir Singh

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.
```
