# Security

## The native binary: Krypton ships none

Krypton is **bindings, not cryptography**. It does not implement, modify, vendor, or redistribute
any cryptographic binary. The actual crypto is [Signal's libsignal](https://github.com/signalapp/libsignal),
and it reaches your build **directly from Signal** — never through a Krypton-hosted blob:

| Track | Where the binary comes from | Who fetches it |
| --- | --- | --- |
| JVM / Android | Signal's official `org.signal:libsignal-*` jars on Maven Central | your Gradle build |
| Apple (iOS/macOS) | Signal's official source / prebuilt (`scripts/fetch-libsignal-ffi.sh`) | **you, on your machine** |

Krypton's published Apple artifacts contain only cinterop bindings (~88 KB) — **no `libsignal_ffi`
binary is embedded.** There is therefore nothing of ours for anyone to suspect: if you don't trust
a binary, you didn't get it from us.

## How to verify what you're running

- **JVM/Android:** the jars are published and signed by Signal under `org.signal` on Maven Central.
- **Apple, from source (default, strongest):** `scripts/fetch-libsignal-ffi.sh <version>` clones the
  official `signalapp/libsignal` repo at the `v<version>` release tag and compiles `libsignal_ffi.a`
  locally. The bytes are produced on *your* machine from Signal's own source — reproducible by anyone,
  and nothing has to be trusted but the GitHub org. (The release tag is an annotated tag by a Signal
  maintainer; it is not git-GPG-signed, so the trust anchor is the `signalapp` org + the commit it
  points at, not a PGP signature.)
- **Apple, prebuilt:** `… <version> "" download` fetches Signal's official archive from
  `build-artifacts.signal.org` (the same URL Signal's own CocoaPods integration uses) and, if you
  set `LIBSIGNAL_FFI_PREBUILD_CHECKSUM`, verifies the archive against that SHA-256 and aborts on a
  mismatch. Signal does **not** commit a checksum at the source tag (it's supplied via that env var),
  so an unset checksum means the download is unverified — which is why building from source is the
  default. Note the prebuilt archive is **iOS-only** (device + both simulators); macOS targets must
  use the from-source path.
- **Version lock-step:** `:krypton-protocol:verifyLibsignalVersion` (wired into `./gradlew check`)
  fails the build if the libsignal version used for the native side ever differs from the
  JVM/Android Maven version — so two peers can't silently land on incompatible wire formats.

## Scope and limitations

Krypton is unaudited binding code over an audited core. The cryptographic guarantees are
libsignal's; Krypton's responsibility is to pass data through faithfully and to **fail loud**
(never fake a result) where a platform has no libsignal. Review the bindings and the FFI bridge
(`krypton-protocol/src/appleMain`) before relying on them in production.

## Reporting a vulnerability

Please report security issues privately via GitHub Security Advisories on this repository
(**Security → Report a vulnerability**) rather than a public issue. For vulnerabilities in the
cryptography itself, report to the [libsignal project](https://github.com/signalapp/libsignal).
