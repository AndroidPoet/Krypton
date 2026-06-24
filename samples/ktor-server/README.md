# Krypton companion server (Ktor + libsignal-server)

A minimal backend for a Krypton-based messaging app, built on **Ktor** and
**`org.signal:libsignal-server`** — the JVM-only server half of libsignal.

The client (your app) uses **Krypton** for the actual end-to-end encryption. This server only does
the things a client *can't*:

| Endpoint | What it does | libsignal-server API |
| --- | --- | --- |
| `GET  /v1/trust-root` | EC public key clients pin to validate certificates | `ECKeyPair` |
| `POST /v1/keys/{user}` | store a client's pre-key bundle (base64 body) | — (transport) |
| `GET  /v1/keys/{user}` | another client fetches it to start an X3DH session | — (transport) |
| `POST /v1/certificate/{uuid}/{device}` | issue a sealed-sender `SenderCertificate` (body = client's identity public key, base64) | `ServerCertificate.issue(...)` |
| `GET  /v1/zkgroup/public-params` | publish zkgroup server public params | `ServerSecretParams.getPublicParams()` |
| `POST /v1/zkgroup/auth/{aci}/{pni}` | issue a zkgroup auth credential (UUIDs) | `ServerZkAuthOperations.issueAuthCredentialWithPniZkc(...)` |

**This server never sees plaintext** — only ciphertext bundles and public credential material.

## Run

```sh
./gradlew :samples:ktor-server:run     # http://localhost:8080
```

```sh
curl localhost:8080/v1/trust-root
curl -X POST localhost:8080/v1/keys/alice -d "<base64 pre-key bundle from Krypton>"
curl localhost:8080/v1/keys/alice
curl localhost:8080/v1/zkgroup/public-params
curl -X POST localhost:8080/v1/zkgroup/auth/$(uuidgen)/$(uuidgen)
```

## Storage: in-memory here, your database in production

This sample keeps pre-key bundles in a `ConcurrentHashMap` so it runs with zero setup. In production
you replace that map with a real store — the rest of the server is unchanged.

### Using Supabase / Postgres

`libsignal-server` is a **JVM** library; **Supabase Edge Functions run on Deno (TypeScript)**, so the
crypto **cannot** run inside Supabase. Instead, run *this* Ktor service on any JVM host (Fly.io,
Railway, Cloud Run, a VM) and use Supabase as its **data layer**:

```
client (Krypton + Ktor client)
      │ HTTPS
      ▼
this Ktor server  ──(JDBC or supabase-kmp)──►  Supabase (Postgres / Auth / Storage)
  org.signal:libsignal-server                  just stores bundles & credentials; no libsignal
```

Two ways to reach Supabase from this JVM server:

- **JDBC** — Supabase is Postgres; point any JDBC client at the connection string and swap the
  `ConcurrentHashMap` for SQL `INSERT`/`SELECT`.
- **supabase-kmp** — the [supabase-kmp](https://github.com/AndroidPoet/supabase-kmp) client runs on
  the JVM, so a small adapter implementing "store/fetch pre-key bundle" against Supabase tables drops
  straight in. That adapter belongs with your Supabase code, not in Krypton.
