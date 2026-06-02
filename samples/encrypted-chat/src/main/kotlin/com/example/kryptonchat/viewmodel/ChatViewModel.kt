package com.example.kryptonchat.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import io.krypton.Krypton
import io.krypton.core.encoding.Base64
import io.krypton.core.types.*
import io.krypton.storage.memory.InMemoryStores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.kryptonchat.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ChatViewModel {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _connectionStatus = MutableStateFlow("🔐 Initialising…")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    // In a real app, you'd generate and persist these once
    private val myIdentity = IdentityKeyPair(
        IdentityKey(PublicKey(ByteArray(32) { 1 }), 0),
        PrivateKey(ByteArray(32) { 2 }),
    )

    private val aliceIdentity = IdentityKeyPair(
        IdentityKey(PublicKey(ByteArray(32) { 3 }), 0),
        PrivateKey(ByteArray(32) { 4 }),
    )

    private val protocol = Krypton.protocol {
        identityKeyPair = myIdentity
        registrationId = RegistrationId(5678)
    }

    private val aliceAddress = ProtocolAddress("alice", DeviceId.PRIMARY)
    private var messageId = 0L

    init {
        scope.launch {
            _connectionStatus.value = "🔐 Session establishing…"
            // In production: fetch Alice's pre-key bundle from server
            // and call protocol.processPreKeyBundle(bundle)
            _connectionStatus.value = "🔒 Ready — end-to-end encrypted"
        }
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value
        if (text.isBlank()) return
        _inputText.value = ""

        scope.launch {
            val encrypted = protocol.encryptString(aliceAddress, text)

            encrypted.onSuccess { ciphertext ->
                val encoded = Base64.encode(ciphertext.serialized)
                addMessage(text, isOutgoing = true)
                // In production: send `encoded` to server
                // Simulate receiving it back for demo
                receiveMessage(encoded)
            }.onFailure { error ->
                addMessage("❌ Send failed: ${error.message}", isOutgoing = true)
            }
        }
    }

    private fun receiveMessage(encodedCiphertext: String) {
        scope.launch {
            protocol.decryptFromBase64(aliceAddress, encodedCiphertext)
                .onSuccess { plaintext ->
                    addMessage(plaintext.decodeToString(), isOutgoing = false)
                }
                .onFailure { error ->
                    addMessage("❌ Decrypt failed: ${error.message}", isOutgoing = false)
                }
        }
    }

    private fun addMessage(text: String, isOutgoing: Boolean) {
        val now = Instant.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val msg = ChatMessage(
            id = messageId++,
            text = text,
            sender = if (isOutgoing) "You" else "Alice",
            timestamp = formatter.format(now),
            isOutgoing = isOutgoing,
            isEncrypted = true,
        )
        _messages.value = _messages.value + msg
    }

}
