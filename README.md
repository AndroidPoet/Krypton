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

One line pulls the full API (`core` + `storage` + `protocol`) on every target.

- **JVM / Android — nothing else to do.** libsignal is fetched from Signal's official Maven jars.
- **Apple (iOS/macOS) — bring your own libsignal.** Krypton ships **no Signal binary**; you fetch
  `libsignal_ffi` from Signal's official source on your own machine (one command) and point the
  linker at it. See **[Bring your own libsignal](#bring-your-own-libsignal-apple)**. This is a
  deliberate trust choice — Krypton never redistributes a binary you'd have to trust ([SECURITY.md](SECURITY.md)).

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

## Bring your own libsignal (Apple)

Krypton's published Apple artifacts contain **only the cinterop bindings** (~88 KB) — **no Signal
binary**. For Apple targets you fetch `libsignal_ffi` from Signal's official source on your own
machine and point the linker at it. JVM/Android need none of this — libsignal comes from Maven
automatically.

### Option A — the Gradle plugin (recommended)

Apply the Krypton Gradle plugin and it does everything: fetches `libsignal_ffi` from Signal's
official source into a cache and wires the `-L` paths onto every Apple target automatically.

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.krypton.libsignal") version "0.1.0"
}

kryptonLibsignal {
    version.set("0.86.5")        // must match Krypton's pinned libsignal version
    mode.set("build")           // "build" = compile Signal's official source (needs Rust)
                                // "download" = Signal's official prebuilt (iOS-only) + checksum verify
}
```

That's it — `linkDebugTestMacosArm64`, your iOS framework link, etc. all `dependsOn` the generated
`fetchLibsignal` task, so a normal build just works. Nothing about libsignal is committed to your
repo; the `.a` lives in `~/.krypton/libsignal/<version>/` (override with `cacheDir`).

### Option B — the fetch script (no plugin)

If you'd rather not apply a plugin, run the script and wire the linker yourself. Two official ways,
both straight from Signal:

```sh
# Build from Signal's official source (needs Rust) — covers every arch, incl. macOS:
scripts/fetch-libsignal-ffi.sh 0.86.5

# …or download Signal's official prebuilt (iOS-only) and verify its checksum:
LIBSIGNAL_FFI_PREBUILD_CHECKSUM=<sha256> scripts/fetch-libsignal-ffi.sh 0.86.5 "" download
```

Then add the printed `-L` path(s) to your app's Apple targets:

```kotlin
kotlin {
    val ls = "${System.getProperty("user.home")}/.krypton/libsignal/0.86.5"
    iosArm64          { binaries.all { linkerOpts("-L$ls/ios-arm64") } }
    iosSimulatorArm64 { binaries.all { linkerOpts("-L$ls/ios-sim-arm64") } }
    macosArm64        { binaries.all { linkerOpts("-L$ls/macos-arm64") } }
}
```

> Either way, the libsignal version **must** match Krypton's pinned version (see the badge /
> `gradle/libs.versions.toml`).

The fetched `.a` is large (~77 MB) but that's a **build input, not your app** — a Release link keeps
only the few MB you actually use (~3 MB; see [How big is it?](#how-big-is-it)).

### What `build` mode runs under the hood (cargo)

Both the plugin's `build` mode and the script compile `libsignal_ffi` exactly the way Signal's own
docs describe — straight from the Rust source, with `cargo`:

```sh
git clone --branch v0.86.5 https://github.com/signalapp/libsignal
cd libsignal
rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios \
                  aarch64-apple-darwin x86_64-apple-darwin
cargo build -p libsignal-ffi --release --target aarch64-apple-ios   # …and one per target
# → target/<triple>/release/libsignal_ffi.a
```

You don't run these by hand — the plugin/script do it for you — but that's all that happens.
**Prerequisite:** a Rust toolchain (`build` mode only). Install once with
[rustup](https://rustup.rs); the plugin/script add the Apple targets for you. `download` mode needs
no Rust, but is iOS-only.

> **Why doesn't Krypton just run cargo and ship the result?** It can — that's exactly what `build`
> mode does on *your* machine. The one thing Krypton refuses to do is publish a pre-built Signal
> binary in its own artifacts, so no one ever has to trust bytes that came from us instead of from
> Signal. The crypto is always compiled (or downloaded) from Signal's official source, by you. See
> [SECURITY.md](SECURITY.md).

## How big is it?

Krypton's own published artifacts are tiny — the JVM jars are ~0.2 MB and the Apple klibs ~88 KB
(bindings only; the libsignal binary is fetched on your machine, never shipped by Krypton). The
size you ultimately link is libsignal itself — and far smaller than the raw archive suggests,
because the linker keeps only what you call and release builds strip:

| Platform | What's bundled | Real app-size impact (release) |
| --- | --- | --- |
| iOS / macOS | dead-stripped static code | **~3 MB** per arch (one arch per device via App Store thinning) |
| Android (per ABI) | one `.so`, AGP-stripped | **~3 MB download / ~6.4 MB installed** (down from ~70 MB in the AAR) |
| JVM desktop (macOS) | whole `.dylib` | **~20 MB** |
| JVM (Windows) | whole `.dll` | **~16 MB** |
| JVM server (Linux) | whole `.so`, **unstripped** | **~120 MB** (see note) |

This is the same core that Signal and WhatsApp ship.

**By default you get everything** — every platform and arch works out of the box, no exclusions
required. The notes below are *optional* size optimizations, not steps you have to take.

#### Optional: trim JVM size

The JVM jar carries Signal's native for **every** OS (macOS arm64+amd64, Linux, Windows), and the
Linux `.so` is shipped unstripped (~120 MB). A fat-jar that bundles all of them is large by default.
If size matters, two opt-in trims:

```kotlin
// 1. Drop libsignal's own test natives (never needed in production):
configurations.all { exclude(group = "org.signal", module = "libsignal-client-testing") }

// 2. In your packaging (shadowJar / jpackage), keep only the OS(es) you ship:
//    e.g. exclude **/libsignal_jni_amd64.so on a Mac-only desktop build.
```

For a Linux server you can also `strip` the extracted `.so` in your image build — most of the
120 MB is debug info.

### Why the 77 MB archive becomes ~3 MB

The `libsignal_ffi.a` you fetch is a fat, unstripped archive — but it's a **build input, not
something you ship**. Your Release link shrinks it automatically, in three layers:

1. **Archive member selection.** A static `.a` is a bag of `.o` object files; the linker pulls in
   *only* the ones that resolve a symbol you actually call. Unused modules are never included.
2. **Dead-strip.** Within those, `-dead_strip` removes any function/data unreachable from your
   entry points. Kotlin/Native Release and Xcode Release pass this automatically.
3. **Symbol strip.** Release builds drop DWARF debug info into a separate `.dSYM` that isn't shipped.

The one rule: **build Release.** Debug builds keep everything (that's expected — it's for dev);
Release builds apply all three steps and produce the ~3 MB above. No flags to add. On Android the
`.so` is dynamic so it can't dead-strip, but AGP strips its debug symbols on release and App Bundle
ships only the device's ABI.

> This is identical under [Bring your own libsignal](#bring-your-own-libsignal-apple) — stripping
> always happens at *your* final link, so the BYO trust model costs you nothing in app size.

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
| `krypton-gradle-plugin` | `id("io.krypton.libsignal")` — fetches libsignal from Signal's source & wires Apple targets |

## Contributing

Issues and PRs are welcome. Please make sure the build stays green:

```sh
./gradlew check
```

## Acknowledgements

Built on [signalapp/libsignal](https://github.com/signalapp/libsignal) — the real cryptography
is entirely Signal's work. Krypton only provides the Kotlin Multiplatform bindings.

## Cryptography / export notice

This distribution includes cryptographic software (it links [libsignal](https://github.com/signalapp/libsignal)).
The country in which you reside may restrict the import, possession, use, and/or re-export of
encryption software. Before using it, check your country's laws, regulations, and policies. See
<https://www.wassenaar.org/> for more information.

The U.S. Bureau of Industry and Security (BIS) classifies the underlying cryptographic functionality
under ECCN **5D002.C.1**. As **publicly available, open-source software**, the form and manner of this
distribution makes it eligible for export under the License Exception **ENC / TSU** (EAR §740.13).
Krypton itself ships **no** Signal binary — the cryptographic library is obtained directly from
Signal's official source; see [SECURITY.md](SECURITY.md). This notice is informational and is not
legal advice.

## License

Krypton is licensed under **AGPL-3.0-only**, matching libsignal. See [LICENSE](LICENSE).

```
Copyright (C) 2026 Ranbir Singh

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.
```
