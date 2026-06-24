#!/usr/bin/env bash
#
# Build libsignal_ffi static lib(s) for Krypton's Apple/native cinterop and copy
# them into krypton-protocol/libs/apple/. Run after bumping the libsignal version
# so the native targets stay on the SAME version as the JVM/Android Maven deps.
#
# Usage:
#   scripts/build-libsignal-ffi.sh <version>
#   e.g. scripts/build-libsignal-ffi.sh 0.96.0
#
# Requirements: git, rust/cargo (rustup), and the relevant `rustup target add`s
# for whatever Apple/native targets you build.
#
# NOTE: the exact crate name and header path follow libsignal's build layout
# (rust/bridge/ffi). If a future libsignal release moves things, adjust below.

set -euo pipefail

VERSION="${1:?usage: build-libsignal-ffi.sh <libsignal-version>   e.g. 0.96.0}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$REPO_ROOT/krypton-protocol/libs/apple"
WORK="${TMPDIR:-/tmp}/libsignal-$VERSION"

echo "==> libsignal v$VERSION -> $DEST"

if [ ! -d "$WORK/.git" ]; then
  git clone --depth 1 --branch "v$VERSION" https://github.com/signalapp/libsignal "$WORK"
fi
cd "$WORK"

# Apple per-arch builds. Each Kotlin/Native Apple target links its OWN-arch .a, so
# we build one archive per triple into libs/apple/<arch>/. The Krypton cinterop
# (krypton-protocol/build.gradle.kts → configureCInterop) maps target → subdir.
#
#   triple                  -> subdir          Kotlin target
#   aarch64-apple-darwin    -> macos-arm64     macosArm64   (host; runnable tests)
#   aarch64-apple-ios       -> ios-arm64       iosArm64     (device)
#   aarch64-apple-ios-sim   -> ios-sim-arm64   iosSimulatorArm64 (runnable on arm64 sim)
#
# libsignal pins its own toolchain via rust-toolchain(.toml); cross stds must be
# added for THAT toolchain, not just the default — so resolve it and add targets.
TOOLCHAIN="$(rustup show active-toolchain 2>/dev/null | awk '{print $1}')"
echo "==> libsignal toolchain: ${TOOLCHAIN:-<default>}"

build_arch() {
  local triple="$1" subdir="$2"
  echo "==> building libsignal-ffi for $triple -> $subdir"
  rustup target add ${TOOLCHAIN:+--toolchain "$TOOLCHAIN"} "$triple" >/dev/null 2>&1 || true
  cargo build -p libsignal-ffi --release --target "$triple"
  mkdir -p "$DEST/$subdir"
  cp "target/$triple/release/libsignal_ffi.a" "$DEST/$subdir/libsignal_ffi.a"
}

mkdir -p "$DEST"
build_arch aarch64-apple-darwin  macos-arm64    # macosArm64        (Apple Silicon desktop)
build_arch x86_64-apple-darwin   macos-x64      # macosX64          (Intel desktop)
build_arch aarch64-apple-ios     ios-arm64      # iosArm64          (device)
build_arch aarch64-apple-ios-sim ios-sim-arm64  # iosSimulatorArm64 (Apple Silicon sim)
build_arch x86_64-apple-ios      ios-sim-x64    # iosX64            (Intel sim)

# Regenerate / copy the FFI header if present (arch-independent; path varies by version).
H_FILE="$(find . -name 'signal_ffi.h' | head -1 || true)"
[ -n "$H_FILE" ] && cp "$H_FILE" "$DEST/signal_ffi.h" || echo "WARN: signal_ffi.h not found — keep existing header"

# Record the built version so :krypton-protocol:verifyLibsignalVersion can assert
# the native .a matches the JVM/Android Maven version (no silent skew).
echo "$VERSION" > "$DEST/VERSION"

echo "==> Updated per-arch libsignal_ffi.a (macos-arm64, macos-x64, ios-arm64, ios-sim-arm64, ios-sim-x64) to libsignal $VERSION"
echo "    NOTE: bump 'libsignal' in gradle/libs.versions.toml to '$VERSION' too, or"
echo "    ./gradlew check will fail on version skew."
echo "    Remember: the .a files are gitignored (>100MB). Distribute via CI artifact / release / git-lfs."
