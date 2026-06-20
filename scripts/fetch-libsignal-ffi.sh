#!/usr/bin/env bash
#
# fetch-libsignal-ffi.sh — get the libsignal_ffi static lib FROM SIGNAL'S OFFICIAL
# SOURCE onto your own machine, for building a Krypton-based app on Apple targets.
#
# Krypton ships NO Signal binary (bring-your-own-libsignal). You run this; the
# binary comes straight from Signal — there is nothing of Krypton's to trust.
#
# Two ways to obtain it, both official:
#   build   (default) — git clone signalapp/libsignal @ the signed vX tag and
#                       compile libsignal_ffi.a yourself (needs Rust/cargo).
#   download          — fetch Signal's official prebuilt iOS archive from
#                       build-artifacts.signal.org and verify its SHA-256.
#
# Usage:
#   scripts/fetch-libsignal-ffi.sh <version> [outdir] [mode]
#     <version>  libsignal version, e.g. 0.86.5  (match Krypton's pinned version)
#     [outdir]   where to place <arch>/libsignal_ffi.a   (default: ~/.krypton/libsignal/<version>)
#     [mode]     build | download                        (default: build)
#
# Verify a download against Signal's published checksum:
#   LIBSIGNAL_FFI_PREBUILD_CHECKSUM=<sha256> scripts/fetch-libsignal-ffi.sh 0.86.5 "" download
#
# After it runs, add the printed -L path(s) to your app's Apple targets, e.g.:
#   kotlin { iosArm64 { binaries.all { linkerOpts("-L<outdir>/ios-arm64") } } }

set -euo pipefail

VERSION="${1:?usage: fetch-libsignal-ffi.sh <version> [outdir] [build|download]}"
OUTDIR="${2:-$HOME/.krypton/libsignal/$VERSION}"
MODE="${3:-build}"

# triple -> arch subdir (Krypton's cinterop expects these names)
APPLE_ARCHES=(
  "aarch64-apple-darwin:macos-arm64"
  "x86_64-apple-darwin:macos-x64"
  "aarch64-apple-ios:ios-arm64"
  "aarch64-apple-ios-sim:ios-sim-arm64"
  "x86_64-apple-ios:ios-sim-x64"
)

mkdir -p "$OUTDIR"
echo "==> libsignal v$VERSION  ->  $OUTDIR  (mode: $MODE)"

if [ "$MODE" = "download" ]; then
  ARCHIVE="libsignal-client-ios-build-v$VERSION.tar.gz"
  URL="https://build-artifacts.signal.org/libraries/$ARCHIVE"
  echo "==> downloading Signal's official prebuilt archive: $URL"
  curl -fSL "$URL" -o "$OUTDIR/$ARCHIVE"
  if [ -n "${LIBSIGNAL_FFI_PREBUILD_CHECKSUM:-}" ]; then
    echo "==> verifying SHA-256 against Signal's published checksum"
    echo "$LIBSIGNAL_FFI_PREBUILD_CHECKSUM  $OUTDIR/$ARCHIVE" | shasum -a 256 -c -
  else
    echo "WARN: no LIBSIGNAL_FFI_PREBUILD_CHECKSUM set — download is UNVERIFIED."
    echo "      Get the checksum from Signal's release and re-run to verify."
  fi
  tar -xzf "$OUTDIR/$ARCHIVE" -C "$OUTDIR"
  # Signal's archive lays the .a out as target/<triple>/release/; relocate into the <arch>/ layout
  # (same as build mode) so the printed -L lines work. The prebuilt is iOS-only.
  for entry in "${APPLE_ARCHES[@]}"; do
    triple="${entry%%:*}"; subdir="${entry##*:}"
    src="$OUTDIR/target/$triple/release/libsignal_ffi.a"
    if [ -f "$src" ]; then
      mkdir -p "$OUTDIR/$subdir"
      cp "$src" "$OUTDIR/$subdir/libsignal_ffi.a"
      echo "==> $subdir"; shasum -a 256 "$OUTDIR/$subdir/libsignal_ffi.a"
    fi
  done
  rm -rf "$OUTDIR/target" "$OUTDIR/$ARCHIVE"
  echo "==> Done (iOS-only). Add to your app's build.gradle.kts:"
  echo "    iosArm64          { binaries.all { linkerOpts(\"-L$OUTDIR/ios-arm64\") } }"
  echo "    iosSimulatorArm64 { binaries.all { linkerOpts(\"-L$OUTDIR/ios-sim-arm64\") } }"
  echo "    (macOS targets: re-run without 'download' to build them from source.)"
  exit 0
fi

# mode: build — compile from Signal's official source at the v<version> release tag.
command -v cargo >/dev/null || { echo "ERROR: Rust/cargo required for build mode. Install via https://rustup.rs"; exit 1; }
WORK="${TMPDIR:-/tmp}/libsignal-$VERSION"
[ -d "$WORK/.git" ] || git clone --depth 1 --branch "v$VERSION" https://github.com/signalapp/libsignal "$WORK"
cd "$WORK"
TOOLCHAIN="$(rustup show active-toolchain 2>/dev/null | awk '{print $1}')"

for entry in "${APPLE_ARCHES[@]}"; do
  triple="${entry%%:*}"; subdir="${entry##*:}"
  echo "==> building $triple -> $subdir"
  rustup target add ${TOOLCHAIN:+--toolchain "$TOOLCHAIN"} "$triple" >/dev/null 2>&1 || true
  cargo build -p libsignal-ffi --release --target "$triple"
  mkdir -p "$OUTDIR/$subdir"
  cp "target/$triple/release/libsignal_ffi.a" "$OUTDIR/$subdir/libsignal_ffi.a"
  shasum -a 256 "$OUTDIR/$subdir/libsignal_ffi.a"
done

echo
echo "==> Done. Add these to your app's build.gradle.kts so the linker finds it:"
echo "    iosArm64          { binaries.all { linkerOpts(\"-L$OUTDIR/ios-arm64\") } }"
echo "    iosSimulatorArm64 { binaries.all { linkerOpts(\"-L$OUTDIR/ios-sim-arm64\") } }"
echo "    macosArm64        { binaries.all { linkerOpts(\"-L$OUTDIR/macos-arm64\") } }"
