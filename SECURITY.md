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

## Recommended setup (the safest practical default)

| Your situation | Use | Why |
| --- | --- | --- |
| JVM / Android | nothing — just the dependency | the binary ships in Signal's own Maven jars |
| Apple, you have (or can install) Rust | **`mode = "build"`** (the default) | compiled from Signal's source on your machine — reproducible, covers macOS too, nothing prebuilt to trust |
| Apple, no Rust / want speed (iOS only) | `mode = "download"` **+ a pinned checksum** | Signal's official prebuilt, but only trustworthy once you pin its SHA-256 |

**Bottom line: prefer `build`.** It is the only option where you trust *nothing but Signal's source
and your own compiler*. Use `download` when Rust isn't available, but always pin the checksum (below)
— an unpinned download is unverified.

## Consumer safety checklist

1. **Pin the version, and keep it matched.** Set `kryptonLibsignal { version.set("<X>") }` to the
   exact libsignal version Krypton targets (see the README badge / `gradle/libs.versions.toml`).
   `./gradlew check` runs `verifyLibsignalVersion` and fails on a mismatch — leave that wired in.
2. **Prefer building from source** (`mode = "build"`, the default). The bytes are produced locally
   from `signalapp/libsignal` at the `v<version>` tag.
3. **If you download, pin the checksum.** Set `LIBSIGNAL_FFI_PREBUILD_CHECKSUM` so a tampered or
   swapped archive aborts the build. Never ship a build that downloaded unverified.
4. **Verify once, in CI, not just locally.** Run the fetch in CI so the binary your releases link
   against is produced by an auditable, repeatable job — not whatever happens to be on a laptop.
5. **Re-derive trust, don't take ours.** You can reproduce everything yourself (next section); you
   never have to trust Krypton for the cryptographic binary.

## Getting a checksum you can trust

Signal does **not** publish a stable, committed SHA-256 for the prebuilt archive (it's passed via an
env var). So establish your own ground truth:

```sh
# Build the binary from Signal's source ONCE (this is the authoritative artifact):
scripts/fetch-libsignal-ffi.sh 0.86.5            # build mode

# …or hash the official prebuilt you intend to pin:
curl -fsSL https://build-artifacts.signal.org/libraries/libsignal-client-ios-build-v0.86.5.tar.gz \
  | shasum -a 256
```

Record that SHA-256 in your own repo/CI and feed it to every subsequent `download` build via
`LIBSIGNAL_FFI_PREBUILD_CHECKSUM`. Now any future download that doesn't match *your* pinned value
fails — you're trusting a value you verified, not one handed to you at fetch time.

## Reproduce it yourself

Don't trust — verify. Anyone can confirm the binary is genuine libsignal:

```sh
# 1. Build from Signal's source and confirm it links Krypton's bindings:
scripts/fetch-libsignal-ffi.sh 0.86.5
nm ~/.krypton/libsignal/0.86.5/ios-arm64/libsignal_ffi.a | grep -c ' T _signal_'   # exported FFI symbols

# 2. Two independent builders should land on the same archive contents for the same tag.
```

The release tag is `signalapp/libsignal@v<version>` (an annotated tag by a Signal maintainer; not
git-GPG-signed, so the anchor is the org + commit, not a PGP signature). Krypton itself never enters
the trust path for the cryptographic bytes.

## Scope and limitations

Krypton is unaudited binding code over an audited core. The cryptographic guarantees are
libsignal's; Krypton's responsibility is to pass data through faithfully and to **fail loud**
(never fake a result) where a platform has no libsignal. Review the bindings and the FFI bridge
(`krypton-protocol/src/appleMain`) before relying on them in production.

## Reporting a vulnerability

Please report security issues privately via GitHub Security Advisories on this repository
(**Security → Report a vulnerability**) rather than a public issue. For vulnerabilities in the
cryptography itself, report to the [libsignal project](https://github.com/signalapp/libsignal).
