package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chat_sessions ADD COLUMN userEmail TEXT NOT NULL DEFAULT 'usuario@groqapp.local'")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId ON chat_messages(sessionId)")
    }
}

// Añade cloudId a sesiones y mensajes para mapear 1:1 con Firestore (users/{uid}/sessions/{cloudId}).
// No destructiva: ALTER ADD COLUMN con default vacío, preserva el historial existente.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chat_sessions ADD COLUMN cloudId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN cloudId TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [ChatSession::class, ChatMessage::class, ActionLog::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "groq_chat_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
