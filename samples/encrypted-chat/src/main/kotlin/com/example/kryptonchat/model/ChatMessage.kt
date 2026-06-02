package com.example.kryptonchat.model

data class ChatMessage(
    val id: Long,
    val text: String,
    val sender: String,
    val timestamp: String,
    val isOutgoing: Boolean,
    val isEncrypted: Boolean = true,
)
