// Minimal wrapper around libsignal_ffi.
// Only exposes the types and functions Krypton needs for X3DH + Double Ratchet.

#pragma once

#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

// ── Error ─────────────────────────────────────────────────────────────
typedef struct SignalFfiError SignalFfiError;
typedef const SignalFfiError *SignalUnwindSafeArgSignalFfiError;

void signal_free_string(const char *buf);
void signal_free_buffer(const unsigned char *buf, size_t buf_len);
void signal_error_free(SignalFfiError *err);
uint32_t signal_error_get_type(const SignalFfiError *err);
SignalFfiError *signal_error_get_message(const char **out, SignalUnwindSafeArgSignalFfiError err);

// ── Buffer types ──────────────────────────────────────────────────────
typedef struct {
  const unsigned char *base;
  size_t length;
} SignalBorrowedBuffer;

typedef struct {
  unsigned char *base;
  size_t length;
} SignalOwnedBuffer;

typedef struct {
  const SignalBorrowedBuffer *base;
  size_t length;
} SignalBorrowedSliceOfBuffers;

// ── Forward declarations (opaque types) ───────────────────────────────
typedef struct SignalProtocolAddress SignalProtocolAddress;
typedef struct SignalPublicKey SignalPublicKey;
typedef struct SignalPrivateKey SignalPrivateKey;
typedef struct SignalSessionRecord SignalSessionRecord;
typedef struct SignalPreKeyRecord SignalPreKeyRecord;
typedef struct SignalSignedPreKeyRecord SignalSignedPreKeyRecord;
typedef struct SignalKyberPreKeyRecord SignalKyberPreKeyRecord;
typedef struct SignalKyberKeyPair SignalKyberKeyPair;
typedef struct SignalKyberPublicKey SignalKyberPublicKey;
typedef struct SignalPreKeyBundle SignalPreKeyBundle;
typedef struct SignalPreKeySignalMessage SignalPreKeySignalMessage;
typedef struct SignalMessage SignalMessage;
typedef struct SignalCiphertextMessage SignalCiphertextMessage;

// ── Pointer wrappers for opaque types ─────────────────────────────────
typedef struct { SignalProtocolAddress *raw; } SignalMutPointerProtocolAddress;
typedef struct { const SignalProtocolAddress *raw; } SignalConstPointerProtocolAddress;
typedef struct { SignalPublicKey *raw; } SignalMutPointerPublicKey;
typedef struct { const SignalPublicKey *raw; } SignalConstPointerPublicKey;
typedef struct { SignalPrivateKey *raw; } SignalMutPointerPrivateKey;
typedef struct { const SignalPrivateKey *raw; } SignalConstPointerPrivateKey;
typedef struct { SignalSessionRecord *raw; } SignalMutPointerSessionRecord;
typedef struct { const SignalSessionRecord *raw; } SignalConstPointerSessionRecord;
typedef struct { SignalPreKeyRecord *raw; } SignalMutPointerPreKeyRecord;
typedef struct { const SignalPreKeyRecord *raw; } SignalConstPointerPreKeyRecord;
typedef struct { SignalSignedPreKeyRecord *raw; } SignalMutPointerSignedPreKeyRecord;
typedef struct { const SignalSignedPreKeyRecord *raw; } SignalConstPointerSignedPreKeyRecord;
typedef struct { SignalKyberPreKeyRecord *raw; } SignalMutPointerKyberPreKeyRecord;
typedef struct { const SignalKyberPreKeyRecord *raw; } SignalConstPointerKyberPreKeyRecord;
typedef struct { SignalKyberKeyPair *raw; } SignalMutPointerKyberKeyPair;
typedef struct { const SignalKyberKeyPair *raw; } SignalConstPointerKyberKeyPair;
typedef struct { SignalKyberPublicKey *raw; } SignalMutPointerKyberPublicKey;
typedef struct { const SignalKyberPublicKey *raw; } SignalConstPointerKyberPublicKey;
typedef struct { SignalPreKeyBundle *raw; } SignalMutPointerPreKeyBundle;
typedef struct { const SignalPreKeyBundle *raw; } SignalConstPointerPreKeyBundle;
typedef struct { const SignalPreKeySignalMessage *raw; } SignalConstPointerPreKeySignalMessage;
typedef struct { SignalPreKeySignalMessage *raw; } SignalMutPointerPreKeySignalMessage;
typedef struct { const SignalMessage *raw; } SignalConstPointerSignalMessage;
typedef struct { SignalMessage *raw; } SignalMutPointerSignalMessage;
typedef struct { SignalCiphertextMessage *raw; } SignalMutPointerCiphertextMessage;
typedef struct { const SignalCiphertextMessage *raw; } SignalConstPointerCiphertextMessage;
typedef struct { SignalMutPointerPublicKey public_key; SignalMutPointerPrivateKey private_key; } SignalPairOfMutPointerPublicKeyMutPointerPrivateKey;

// ── Address ───────────────────────────────────────────────────────────
SignalFfiError *signal_address_new(SignalMutPointerProtocolAddress *out, const char *name, uint32_t device_id);
SignalFfiError *signal_address_destroy(SignalMutPointerProtocolAddress p);
SignalFfiError *signal_address_clone(SignalMutPointerProtocolAddress *new_obj, SignalConstPointerProtocolAddress obj);
SignalFfiError *signal_address_get_device_id(uint32_t *out, SignalConstPointerProtocolAddress obj);
SignalFfiError *signal_address_get_name(const char **out, SignalConstPointerProtocolAddress obj);

// ── Public Key ────────────────────────────────────────────────────────
SignalFfiError *signal_publickey_deserialize(SignalMutPointerPublicKey *out, SignalBorrowedBuffer data);
SignalFfiError *signal_publickey_serialize(SignalOwnedBuffer *out, SignalConstPointerPublicKey obj);
SignalFfiError *signal_publickey_destroy(SignalMutPointerPublicKey p);
SignalFfiError *signal_publickey_clone(SignalMutPointerPublicKey *new_obj, SignalConstPointerPublicKey obj);
SignalFfiError *signal_publickey_compare(int32_t *out, SignalConstPointerPublicKey lhs, SignalConstPointerPublicKey rhs);
SignalFfiError *signal_publickey_verify_signature(SignalOwnedBuffer *out, SignalConstPointerPublicKey key, SignalBorrowedBuffer message, SignalBorrowedBuffer signature);
SignalFfiError *signal_publickey_verify_signature_for_multipart_message(SignalOwnedBuffer *out, SignalConstPointerPublicKey key, SignalBorrowedSliceOfBuffers message, SignalBorrowedBuffer signature);

// ── Private Key ───────────────────────────────────────────────────────
SignalFfiError *signal_privatekey_generate(SignalMutPointerPrivateKey *out);
SignalFfiError *signal_privatekey_deserialize(SignalMutPointerPrivateKey *out, SignalBorrowedBuffer data);
SignalFfiError *signal_privatekey_serialize(SignalOwnedBuffer *out, SignalConstPointerPrivateKey obj);
SignalFfiError *signal_privatekey_destroy(SignalMutPointerPrivateKey p);
SignalFfiError *signal_privatekey_sign(SignalOwnedBuffer *out, SignalConstPointerPrivateKey key, SignalBorrowedBuffer message);
SignalFfiError *signal_privatekey_agree(SignalOwnedBuffer *out, SignalConstPointerPrivateKey key, SignalConstPointerPublicKey other_key);
SignalFfiError *signal_privatekey_get_public_key(SignalMutPointerPublicKey *out, SignalConstPointerPrivateKey obj);

// ── Identity Key ──────────────────────────────────────────────────────
SignalFfiError *signal_identitykeypair_deserialize(SignalPairOfMutPointerPublicKeyMutPointerPrivateKey *out, SignalBorrowedBuffer input);

// ── Session Record ────────────────────────────────────────────────────
SignalFfiError *signal_session_record_deserialize(SignalMutPointerSessionRecord *out, SignalBorrowedBuffer data);
SignalFfiError *signal_session_record_serialize(SignalOwnedBuffer *out, SignalConstPointerSessionRecord obj);
SignalFfiError *signal_session_record_destroy(SignalMutPointerSessionRecord p);
SignalFfiError *signal_session_record_clone(SignalMutPointerSessionRecord *new_obj, SignalConstPointerSessionRecord obj);

// ── PreKey Record ─────────────────────────────────────────────────────
SignalFfiError *signal_pre_key_record_new(SignalMutPointerPreKeyRecord *out, uint32_t id, SignalConstPointerPublicKey pub_key, SignalConstPointerPrivateKey priv_key);
SignalFfiError *signal_pre_key_record_destroy(SignalMutPointerPreKeyRecord p);

// ── Signed PreKey Record ──────────────────────────────────────────────
SignalFfiError *signal_signed_pre_key_record_new(SignalMutPointerSignedPreKeyRecord *out, uint32_t id, uint64_t timestamp, SignalConstPointerPublicKey pub_key, SignalConstPointerPrivateKey priv_key, SignalBorrowedBuffer signature);
SignalFfiError *signal_signed_pre_key_record_destroy(SignalMutPointerSignedPreKeyRecord p);

// ── Kyber ─────────────────────────────────────────────────────────────
SignalFfiError *signal_kyber_key_pair_generate(SignalMutPointerKyberKeyPair *out);
SignalFfiError *signal_kyber_key_pair_destroy(SignalMutPointerKyberKeyPair p);
SignalFfiError *signal_kyber_pre_key_record_new(SignalMutPointerKyberPreKeyRecord *out, uint32_t id, uint64_t timestamp, SignalConstPointerKyberKeyPair key_pair, SignalBorrowedBuffer signature);
SignalFfiError *signal_kyber_pre_key_record_destroy(SignalMutPointerKyberPreKeyRecord p);

// ── PreKey Bundle ─────────────────────────────────────────────────────
SignalFfiError *signal_pre_key_bundle_new(SignalMutPointerPreKeyBundle *out, uint32_t registration_id, uint32_t device_id, uint32_t prekey_id, SignalConstPointerPublicKey prekey, uint32_t signed_prekey_id, SignalConstPointerPublicKey signed_prekey, SignalBorrowedBuffer signed_prekey_signature, SignalConstPointerPublicKey identity_key, uint32_t kyber_prekey_id, SignalConstPointerKyberPublicKey kyber_prekey, SignalBorrowedBuffer kyber_prekey_signature);
SignalFfiError *signal_pre_key_bundle_destroy(SignalMutPointerPreKeyBundle p);
SignalFfiError *signal_pre_key_bundle_get_device_id(uint32_t *out, SignalConstPointerPreKeyBundle obj);
SignalFfiError *signal_pre_key_bundle_get_registration_id(uint32_t *out, SignalConstPointerPreKeyBundle obj);
SignalFfiError *signal_pre_key_bundle_get_pre_key_id(uint32_t *out, SignalConstPointerPreKeyBundle obj);
SignalFfiError *signal_pre_key_bundle_get_pre_key_public(SignalMutPointerPublicKey *out, SignalConstPointerPreKeyBundle obj);
SignalFfiError *signal_pre_key_bundle_get_signed_pre_key_id(uint32_t *out, SignalConstPointerPreKeyBundle obj);
SignalFfiError *signal_pre_key_bundle_get_signed_pre_key_public(SignalMutPointerPublicKey *out, SignalConstPointerPreKeyBundle obj);
SignalFfiError *signal_pre_key_bundle_get_signed_pre_key_signature(SignalOwnedBuffer *out, SignalConstPointerPreKeyBundle obj);
SignalFfiError *signal_pre_key_bundle_get_identity_key(SignalMutPointerPublicKey *out, SignalConstPointerPreKeyBundle p);
SignalFfiError *signal_pre_key_bundle_get_kyber_pre_key_id(uint32_t *out, SignalConstPointerPreKeyBundle obj);
SignalFfiError *signal_pre_key_bundle_get_kyber_pre_key_public(SignalMutPointerKyberPublicKey *out, SignalConstPointerPreKeyBundle bundle);
SignalFfiError *signal_pre_key_bundle_get_kyber_pre_key_signature(SignalOwnedBuffer *out, SignalConstPointerPreKeyBundle obj);

// ── Ciphertext Message ────────────────────────────────────────────────
SignalFfiError *signal_ciphertext_message_destroy(SignalMutPointerCiphertextMessage p);
SignalFfiError *signal_ciphertext_message_serialize(SignalOwnedBuffer *out, SignalConstPointerCiphertextMessage obj);
SignalFfiError *signal_ciphertext_message_type(uint8_t *out, SignalConstPointerCiphertextMessage msg);

// ── Signal Message (regular, not pre-key) ──────────────────────────────
SignalFfiError *signal_message_deserialize(SignalMutPointerSignalMessage *out, SignalBorrowedBuffer data);
SignalFfiError *signal_message_destroy(SignalMutPointerSignalMessage p);

// ── PreKey Signal Message ──────────────────────────────────────────────
SignalFfiError *signal_pre_key_signal_message_deserialize(SignalMutPointerPreKeySignalMessage *out, SignalBorrowedBuffer data);
SignalFfiError *signal_pre_key_signal_message_destroy(SignalMutPointerPreKeySignalMessage p);

// ── Store Callback Structs ────────────────────────────────────────────
typedef struct {
  void *ctx;
  int (*load_session)(void *ctx, SignalMutPointerSessionRecord *out, SignalMutPointerProtocolAddress address);
  int (*store_session)(void *ctx, SignalMutPointerProtocolAddress address, SignalMutPointerSessionRecord record);
  void (*destroy)(void *ctx);
} SignalSessionStore;

typedef struct { const SignalSessionStore *raw; } SignalConstPointerFfiSessionStoreStruct;

typedef struct {
  void *ctx;
  int (*get_local_identity_key_pair)(void *ctx, SignalPairOfMutPointerPublicKeyMutPointerPrivateKey *out);
  int (*get_local_registration_id)(void *ctx, uint32_t *out);
  int (*get_identity_key)(void *ctx, SignalMutPointerPublicKey *out, SignalMutPointerProtocolAddress address);
  int (*save_identity)(void *ctx, uint8_t *out, SignalMutPointerProtocolAddress address, SignalMutPointerPublicKey public_key);
  int (*is_trusted_identity)(void *ctx, bool *out, SignalMutPointerProtocolAddress address, SignalMutPointerPublicKey public_key, uint32_t direction);
  void (*destroy)(void *ctx);
} SignalIdentityKeyStore;

typedef struct { const SignalIdentityKeyStore *raw; } SignalConstPointerFfiIdentityKeyStoreStruct;

typedef struct {
  void *ctx;
  int (*load_pre_key)(void *ctx, SignalMutPointerPreKeyRecord *out, uint32_t id);
  int (*store_pre_key)(void *ctx, uint32_t id, SignalMutPointerPreKeyRecord record);
  int (*remove_pre_key)(void *ctx, uint32_t id);
  void (*destroy)(void *ctx);
} SignalPreKeyStore;

typedef struct { const SignalPreKeyStore *raw; } SignalConstPointerFfiPreKeyStoreStruct;

typedef struct {
  void *ctx;
  int (*load_signed_pre_key)(void *ctx, SignalMutPointerSignedPreKeyRecord *out, uint32_t id);
  int (*store_signed_pre_key)(void *ctx, uint32_t id, SignalMutPointerSignedPreKeyRecord record);
  void (*destroy)(void *ctx);
} SignalSignedPreKeyStore;

typedef struct { const SignalSignedPreKeyStore *raw; } SignalConstPointerFfiSignedPreKeyStoreStruct;

typedef struct {
  void *ctx;
  int (*load_kyber_pre_key)(void *ctx, SignalMutPointerKyberPreKeyRecord *out, uint32_t id);
  int (*store_kyber_pre_key)(void *ctx, uint32_t id, SignalMutPointerKyberPreKeyRecord record);
  int (*mark_kyber_pre_key_used)(void *ctx, uint32_t id, uint32_t ec_prekey_id, SignalMutPointerPublicKey base_key);
  void (*destroy)(void *ctx);
} SignalKyberPreKeyStore;

typedef struct { const SignalKyberPreKeyStore *raw; } SignalConstPointerFfiKyberPreKeyStoreStruct;

// ── Krypton adapter functions ─────────────────────────────────────────
// These thin wrappers bridge the const/mut type gap that Kotlin/Native
// cinterop cannot handle directly. Declared here so cinterop picks them up.
// Implemented in krypton_adapter.c, compiled into libkrypton_adapter.a.

SignalFfiError *krypton_address_get_name(const char **out, SignalMutPointerProtocolAddress addr);
SignalFfiError *krypton_address_get_device_id(uint32_t *out, SignalMutPointerProtocolAddress addr);
SignalFfiError *krypton_session_record_serialize(SignalOwnedBuffer *out, SignalMutPointerSessionRecord record);
SignalFfiError *krypton_publickey_serialize(SignalOwnedBuffer *out, SignalMutPointerPublicKey key);
SignalFfiError *krypton_privatekey_serialize(SignalOwnedBuffer *out, SignalMutPointerPrivateKey key);
SignalFfiError *krypton_build_identity_key_pair(
    SignalPairOfMutPointerPublicKeyMutPointerPrivateKey *out,
    SignalBorrowedBuffer pub_key_data,
    SignalBorrowedBuffer priv_key_data
);
SignalFfiError *krypton_kyber_pre_key_record_new_from_raw(
    SignalMutPointerKyberPreKeyRecord *out,
    uint32_t id, uint64_t timestamp,
    void *kyber_key_pair_raw,
    SignalBorrowedBuffer signature
);
void krypton_error_free(SignalFfiError *err);

// ── Core Protocol Functions ───────────────────────────────────────────
SignalFfiError *signal_process_prekey_bundle(SignalConstPointerPreKeyBundle bundle, SignalConstPointerProtocolAddress protocol_address, SignalConstPointerProtocolAddress local_address, SignalConstPointerFfiSessionStoreStruct session_store, SignalConstPointerFfiIdentityKeyStoreStruct identity_key_store, uint64_t now);

SignalFfiError *signal_encrypt_message(SignalMutPointerCiphertextMessage *out, SignalBorrowedBuffer ptext, SignalConstPointerProtocolAddress protocol_address, SignalConstPointerProtocolAddress local_address, SignalConstPointerFfiSessionStoreStruct session_store, SignalConstPointerFfiIdentityKeyStoreStruct identity_key_store, uint64_t now);

SignalFfiError *signal_decrypt_message(SignalOwnedBuffer *out, SignalConstPointerSignalMessage message, SignalConstPointerProtocolAddress protocol_address, SignalConstPointerProtocolAddress local_address, SignalConstPointerFfiSessionStoreStruct session_store, SignalConstPointerFfiIdentityKeyStoreStruct identity_key_store);

SignalFfiError *signal_decrypt_pre_key_message(SignalOwnedBuffer *out, SignalConstPointerPreKeySignalMessage message, SignalConstPointerProtocolAddress protocol_address, SignalConstPointerProtocolAddress local_address, SignalConstPointerFfiSessionStoreStruct session_store, SignalConstPointerFfiIdentityKeyStoreStruct identity_key_store, SignalConstPointerFfiPreKeyStoreStruct prekey_store, SignalConstPointerFfiSignedPreKeyStoreStruct signed_prekey_store, SignalConstPointerFfiKyberPreKeyStoreStruct kyber_prekey_store);
