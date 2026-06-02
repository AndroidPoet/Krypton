# ⚛️ Krypton

**A noble-gas-grade, multiplatform encryption library** — bringing Signal's battle-tested end-to-end encryption protocol to Kotlin Multiplatform with a clean, modern API.

> Named after Krypton (Kr), a noble gas that's inert, stable, and doesn't react with anything — just like perfectly encrypted data.

## Why Krypton?

| Problem | Krypton Solution |
|---------|-----------------|
| ❌ Signal Protocol is complex | ✅ Clean KMP API with builder DSL |
| ❌ Only Java/Swift bindings exist | ✅ True KMP: Android, iOS, JVM, macOS, WASM |
| ❌ Hard to store sessions | ✅ Plug-in store interfaces with defaults |
| ❌ Error handling is messy | ✅ `CryptoResult<T>` — composable, no exceptions |
| ❌ Need full Rust build setup | ✅ Drop-in modules, native bridge handles it |

## Quick Start

```kotlin
// In your build.gradle.kts:
// dependencies { implementation("io.krypton:krypton-factory:0.1.0") }

import io.krypton.Krypton
import io.krypton.core.types.*

// 1. Create the protocol client
val client = Krypton.protocol {
    identityKeyPair = myIdentity
    registrationId = RegistrationId(5678)
    // stores = MyProductionStores() // optional custom stores
}

// 2. Encrypt with Alice's pre-key bundle
client.processPreKeyBundle(alicesBundle)
    .flatMap { client.encrypt(ProtocolAddress("alice"), "Hello!".encodeToByteArray()) }
    .onSuccess { msg -> server.send(msg.serialized) }
    .onFailure { err -> log("Failed: $err") }
```

## Architecture

```
┌────────────────────────────────────────┐
│           Your App                      │
├────────────────────────────────────────┤
│  Krypton.protocol { }  ← Entry point    │
├────────────────────────────────────────┤
│  krypton-protocol  │ krypton-zkgroup    │
│  krypton-double-   │ krypton-sealed-    │
│  ratchet           │ sender             │
├────────────────────────────────────────┤
│  krypton-core  (Result, types, encoding)│
│  krypton-storage (IdentityKeyStore, …)  │
├────────────────────────────────────────┤
│      Native Bridge (JNI / FFI) ──────── │
│           libsignal Rust Core           │
└────────────────────────────────────────┘
```

## Modules

| Module | Description |
|--------|-------------|
| `krypton-core` | Result types, key types, encoding (Base64, Hex) |
| `krypton-storage` | Store interfaces + in-memory + platform stores |
| `krypton-protocol` | Signal Protocol (X3DH, session, encrypt/decrypt) |
| `krypton-sealed-sender` | Anonymous sender encryption |
| `krypton-double-ratchet` | Forward secrecy ratcheting |
| `krypton-zkgroup` | Zero-knowledge group membership |
| `krypton-factory` | Entry point (`Krypton.protocol { }`) |

## License

AGPL-3.0-only (matching libsignal's license)
