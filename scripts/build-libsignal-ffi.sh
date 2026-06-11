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

# Host build by default. Add `--target <triple>` loops here for cross builds
# (e.g. aarch64-apple-ios, x86_64-unknown-linux-gnu, x86_64-pc-windows-gnu).
cargo build -p libsignal-ffi --release

mkdir -p "$DEST"
A_FILE="$(find target -name 'libsignal_ffi.a' | head -1)"
[ -n "$A_FILE" ] || { echo "ERROR: libsignal_ffi.a not found under target/" >&2; exit 1; }
cp "$A_FILE" "$DEST/libsignal_ffi.a"

# Regenerate / copy the FFI header if present (path may vary by version).
H_FILE="$(find . -name 'signal_ffi.h' | head -1 || true)"
[ -n "$H_FILE" ] && cp "$H_FILE" "$DEST/signal_ffi.h" || echo "WARN: signal_ffi.h not found — keep existing header"

# Record the built version so :krypton-protocol:verifyLibsignalVersion can assert
# the native .a matches the JVM/Android Maven version (no silent skew).
echo "$VERSION" > "$DEST/VERSION"

echo "==> Updated $DEST/libsignal_ffi.a to libsignal $VERSION"
echo "    NOTE: bump 'libsignal' in gradle/libs.versions.toml to '$VERSION' too, or"
echo "    ./gradlew check will fail on version skew."
echo "    Remember: the .a is gitignored (>100MB). Distribute via CI artifact / release / git-lfs."
