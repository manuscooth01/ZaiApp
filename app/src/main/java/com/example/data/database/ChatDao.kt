package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE sessionType = :type AND userEmail = :userEmail ORDER BY timestamp DESC")
    fun getSessionsByType(type: String, userEmail: String): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): ChatSession?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: Long): List<ChatMessage>

    // ─── Sync con la nube (Firestore) ─────────────────────
    @Query("SELECT * FROM chat_sessions WHERE userEmail = :userEmail")
    suspend fun getSessionsByUserSync(userEmail: String): List<ChatSession>

    @Query("SELECT * FROM chat_sessions WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getSessionByCloudId(cloudId: String): ChatSession?

    @Query("SELECT * FROM chat_messages WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getMessageByCloudId(cloudId: String): ChatMessage?

    @Query("UPDATE chat_sessions SET cloudId = :cloudId WHERE id = :id")
    suspend fun updateSessionCloudId(id: Long, cloudId: String)

    @Query("UPDATE chat_messages SET cloudId = :cloudId WHERE id = :id")
    suspend fun updateMessageCloudId(id: Long, cloudId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: Long)

    @Query("UPDATE chat_sessions SET title = :newTitle WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, newTitle: String)

    @Query("UPDATE chat_sessions SET timestamp = :ts WHERE id = :sessionId")
    suspend fun touchSession(sessionId: Long, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_messages WHERE sessionId IN (SELECT id FROM chat_sessions WHERE userEmail = :userEmail)")
    suspend fun deleteMessagesByUser(userEmail: String)

    @Query("DELETE FROM chat_sessions WHERE userEmail = :userEmail")
    suspend fun deleteSessionsByUser(userEmail: String)

    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActionLog): Long

    @Query("DELETE FROM action_logs")
    suspend fun clearAllLogs()
}
