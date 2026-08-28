package com.focuslamp.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ChatMessageEntity — Stores AI Assistant chat history in local Room DB.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
