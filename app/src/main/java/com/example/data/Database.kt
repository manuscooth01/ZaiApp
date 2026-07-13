package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelUsed: String
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val conversationId: Int,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String
)

@Entity(tableName = "sandbox_files")
data class SandboxFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filename: String,
    val content: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val apiBaseUrl: String,
    val apiKey: String,
    val defaultModel: String,
    val isDarkMode: Boolean
)

@Entity(tableName = "support_tickets")
data class SupportTicket(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val description: String,
    val status: String, // "Enviado", "En revision"
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Int): Conversation?
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Int)
}

@Dao
interface FileDao {
    @Query("SELECT * FROM sandbox_files ORDER BY updatedAt DESC")
    fun getAllFiles(): Flow<List<SandboxFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: SandboxFile): Long

    @Update
    suspend fun updateFile(file: SandboxFile)

    @Delete
    suspend fun deleteFile(file: SandboxFile)

    @Query("SELECT COUNT(*) FROM sandbox_files")
    suspend fun getFileCount(): Int
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: AppSettings)
}

@Dao
interface SupportTicketDao {
    @Query("SELECT * FROM support_tickets ORDER BY createdAt DESC")
    fun getAllTickets(): Flow<List<SupportTicket>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: SupportTicket)
}

@Database(
    entities = [
        Conversation::class,
        ChatMessage::class,
        SandboxFile::class,
        AppSettings::class,
        SupportTicket::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun chatDao(): ChatDao
    abstract fun fileDao(): FileDao
    abstract fun settingsDao(): SettingsDao
    abstract fun supportTicketDao(): SupportTicketDao
}
