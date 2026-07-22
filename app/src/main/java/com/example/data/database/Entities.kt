package com.example.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val model: String,
    /** "Chat" o "Agente" — las sesiones se mantienen separadas */
    val sessionType: String = "Chat",
    val userEmail: String = "usuario@groqapp.local",
    /** ID estable usado como documento en Firestore (users/{uid}/sessions/{cloudId}). */
    val cloudId: String = ""
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Newline-separated reasoning / thinking steps (agent) */
    val thinkingSteps: String? = null,
    /** ID estable usado como documento en Firestore (.../messages/{cloudId}). */
    val cloudId: String = ""
)

@Entity(tableName = "action_logs")
data class ActionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val description: String
)
