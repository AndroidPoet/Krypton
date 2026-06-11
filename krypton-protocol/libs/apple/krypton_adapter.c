// Thin C adapter: bridges pointer-wrapper type incompatibilities
// so that Kotlin/Native cinterop can call these functions without
// needing to access the opaque `.raw` field directly.
//
// Compile: clang -c krypton_adapter.c -o krypton_adapter.o
// Then link alongside libsignal_ffi.a

#include "krypton_ffi.h"

// ── Address helpers ─────────────────────────────────────────────────

SignalFfiError *krypton_address_get_name(const char **out, SignalMutPointerProtocolAddress addr) {
    SignalConstPointerProtocolAddress const_addr = { addr.raw };
    return signal_address_get_name(out, const_addr);
}

SignalFfiError *krypton_address_get_device_id(uint32_t *out, SignalMutPointerProtocolAddress addr) {
    SignalConstPointerProtocolAddress const_addr = { addr.raw };
    return signal_address_get_device_id(out, const_addr);
}

SignalFfiError *krypton_address_clone(SignalMutPointerProtocolAddress *out, SignalMutPointerProtocolAddress addr) {
    SignalConstPointerProtocolAddress const_addr = { addr.raw };
    return signal_address_clone(out, const_addr);
}

// ── Session helpers ─────────────────────────────────────────────────

SignalFfiError *krypton_session_record_serialize(SignalOwnedBuffer *out, SignalMutPointerSessionRecord record) {
    SignalConstPointerSessionRecord const_rec = { record.raw };
    return signal_session_record_serialize(out, const_rec);
}

// ── Public key helpers ──────────────────────────────────────────────

SignalFfiError *krypton_publickey_serialize(SignalOwnedBuffer *out, SignalMutPointerPublicKey key) {
    SignalConstPointerPublicKey const_key = { key.raw };
    return signal_publickey_serialize(out, const_key);
}

SignalFfiError *krypton_publickey_verify_signature(
    SignalOwnedBuffer *out, SignalMutPointerPublicKey key,
    SignalBorrowedBuffer message, SignalBorrowedBuffer signature
) {
    SignalConstPointerPublicKey const_key = { key.raw };
    return signal_publickey_verify_signature(out, const_key, message, signature);
}

// ── Private key helpers ─────────────────────────────────────────────

SignalFfiError *krypton_privatekey_sign(
    SignalOwnedBuffer *out, SignalMutPointerPrivateKey key,
    SignalBorrowedBuffer message
) {
    SignalConstPointerPrivateKey const_key = { key.raw };
    return signal_privatekey_sign(out, const_key, message);
}

SignalFfiError *krypton_privatekey_agree(
    SignalOwnedBuffer *out, SignalMutPointerPrivateKey key,
    SignalMutPointerPublicKey other_key
) {
    SignalConstPointerPrivateKey const_key = { key.raw };
    SignalConstPointerPublicKey const_other = { other_key.raw };
    return signal_privatekey_agree(out, const_key, const_other);
}

SignalFfiError *krypton_privatekey_serialize(SignalOwnedBuffer *out, SignalMutPointerPrivateKey key) {
    SignalConstPointerPrivateKey const_key = { key.raw };
    return signal_privatekey_serialize(out, const_key);
}

SignalFfiError *krypton_privatekey_get_public_key(SignalMutPointerPublicKey *out, SignalMutPointerPrivateKey key) {
    SignalConstPointerPrivateKey const_key = { key.raw };
    return signal_privatekey_get_public_key(out, const_key);
}

// ── Session record helpers ──────────────────────────────────────────

SignalFfiError *krypton_session_record_deserialize(SignalMutPointerSessionRecord *out, SignalBorrowedBuffer data) {
    return signal_session_record_deserialize(out, data);
}

// ── PreKey bundle helper ────────────────────────────────────────────

SignalFfiError *krypton_pre_key_bundle_new(
    SignalMutPointerPreKeyBundle *out,
    uint32_t registration_id, uint32_t device_id,
    uint32_t prekey_id, SignalMutPointerPublicKey prekey,
    uint32_t signed_prekey_id, SignalMutPointerPublicKey signed_prekey,
    SignalBorrowedBuffer signed_prekey_signature,
    SignalMutPointerPublicKey identity_key,
    uint32_t kyber_prekey_id, SignalMutPointerKyberPublicKey kyber_prekey,
    SignalBorrowedBuffer kyber_prekey_signature
) {
    SignalConstPointerPublicKey const_prekey = { prekey.raw };
    SignalConstPointerPublicKey const_signed = { signed_prekey.raw };
    SignalConstPointerPublicKey const_id = { identity_key.raw };
    SignalConstPointerKyberPublicKey const_kyber = { kyber_prekey.raw };
    return signal_pre_key_bundle_new(out, registration_id, device_id, prekey_id, const_prekey,
        signed_prekey_id, const_signed, signed_prekey_signature, const_id,
        kyber_prekey_id, const_kyber, kyber_prekey_signature);
}

// ── Process prekey bundle helper ────────────────────────────────────

SignalFfiError *krypton_process_prekey_bundle(
    SignalConstPointerPreKeyBundle bundle,
    SignalMutPointerProtocolAddress protocol_address,
    SignalMutPointerProtocolAddress local_address,
    SignalConstPointerFfiSessionStoreStruct session_store,
    SignalConstPointerFfiIdentityKeyStoreStruct identity_key_store,
    uint64_t now
) {
    // The store callbacks in SignalSessionStore take SignalMutPointerProtocolAddress,
    // but signal_process_prekey_bundle passes SignalConstPointerProtocolAddress.
    // Since the callback function pointer signature is part of the struct definition,
    // we need to ensure type compatibility. The actual libsignal_ffi function
    // takes SignalConstPointerProtocolAddress for both address parameters.
    SignalConstPointerProtocolAddress const_proto = { protocol_address.raw };
    SignalConstPointerProtocolAddress const_local = { local_address.raw };
    return signal_process_prekey_bundle(bundle, const_proto, const_local,
        session_store, identity_key_store, now);
}

// ── Encrypt message helper ──────────────────────────────────────────

SignalFfiError *krypton_encrypt_message(
    SignalMutPointerCiphertextMessage *out,
    SignalBorrowedBuffer ptext,
    SignalMutPointerProtocolAddress protocol_address,
    SignalMutPointerProtocolAddress local_address,
    SignalConstPointerFfiSessionStoreStruct session_store,
    SignalConstPointerFfiIdentityKeyStoreStruct identity_key_store,
    uint64_t now
) {
    SignalConstPointerProtocolAddress const_proto = { protocol_address.raw };
    SignalConstPointerProtocolAddress const_local = { local_address.raw };
    return signal_encrypt_message(out, ptext, const_proto, const_local,
        session_store, identity_key_store, now);
}

// ── Decrypt message helpers ─────────────────────────────────────────

SignalFfiError *krypton_decrypt_message(
    SignalOwnedBuffer *out,
    SignalMutPointerSignalMessage message,
    SignalMutPointerProtocolAddress protocol_address,
    SignalMutPointerProtocolAddress local_address,
    SignalConstPointerFfiSessionStoreStruct session_store,
    SignalConstPointerFfiIdentityKeyStoreStruct identity_key_store
) {
    SignalConstPointerSignalMessage const_msg = { message.raw };
    SignalConstPointerProtocolAddress const_proto = { protocol_address.raw };
    SignalConstPointerProtocolAddress const_local = { local_address.raw };
    return signal_decrypt_message(out, const_msg, const_proto, const_local,
        session_store, identity_key_store);
}

SignalFfiError *krypton_decrypt_pre_key_message(
    SignalOwnedBuffer *out,
    SignalMutPointerPreKeySignalMessage message,
    SignalMutPointerProtocolAddress protocol_address,
    SignalMutPointerProtocolAddress local_address,
    SignalConstPointerFfiSessionStoreStruct session_store,
    SignalConstPointerFfiIdentityKeyStoreStruct identity_key_store,
    SignalConstPointerFfiPreKeyStoreStruct prekey_store,
    SignalConstPointerFfiSignedPreKeyStoreStruct signed_prekey_store,
    SignalConstPointerFfiKyberPreKeyStoreStruct kyber_prekey_store
) {
    SignalConstPointerPreKeySignalMessage const_msg = { message.raw };
    SignalConstPointerProtocolAddress const_proto = { protocol_address.raw };
    SignalConstPointerProtocolAddress const_local = { local_address.raw };
    return signal_decrypt_pre_key_message(out, const_msg, const_proto, const_local,
        session_store, identity_key_store, prekey_store, signed_prekey_store, kyber_prekey_store);
}

// ── Ciphertext helpers ──────────────────────────────────────────────

SignalFfiError *krypton_ciphertext_message_serialize(SignalOwnedBuffer *out, SignalMutPointerCiphertextMessage msg) {
    SignalConstPointerCiphertextMessage const_msg = { msg.raw };
    return signal_ciphertext_message_serialize(out, const_msg);
}

SignalFfiError *krypton_ciphertext_message_type(uint8_t *out, SignalMutPointerCiphertextMessage msg) {
    SignalConstPointerCiphertextMessage const_msg = { msg.raw };
    return signal_ciphertext_message_type(out, const_msg);
}

// ── Identity key pair builder ───────────────────────────────────────

/** Build an identity key pair by deserializing pub key and priv key separately. */
SignalFfiError *krypton_build_identity_key_pair(
    SignalPairOfMutPointerPublicKeyMutPointerPrivateKey *out,
    SignalBorrowedBuffer pub_key_data,
    SignalBorrowedBuffer priv_key_data
) {
    SignalFfiError *err = signal_publickey_deserialize(&out->public_key, pub_key_data);
    if (err) return err;
    err = signal_privatekey_deserialize(&out->private_key, priv_key_data);
    if (err) {
        signal_publickey_destroy(out->public_key);
        return err;
    }
    return NULL;
}

// ── Kyber helpers ───────────────────────────────────────────────────

/** Create kyber pre-key record from a raw kyber key pair pointer (as void*). */
SignalFfiError *krypton_kyber_pre_key_record_new_from_raw(
    SignalMutPointerKyberPreKeyRecord *out,
    uint32_t id, uint64_t timestamp,
    void *kyber_key_pair_raw,
    SignalBorrowedBuffer signature
) {
    SignalConstPointerKyberKeyPair const_pair = { (SignalKyberKeyPair*)kyber_key_pair_raw };
    return signal_kyber_pre_key_record_new(out, id, timestamp, const_pair, signature);
}

// ── Error helper ────────────────────────────────────────────────────

void krypton_error_free(SignalFfiError *err) {
    signal_error_free(err);
}
