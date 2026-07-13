package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.io.File

class AppRepository(private val database: AppDatabase, private val context: Context) {
    private val conversationDao = database.conversationDao()
    private val chatDao = database.chatDao()
    private val fileDao = database.fileDao()
    private val settingsDao = database.settingsDao()
    private val supportTicketDao = database.supportTicketDao()

    // Conversiones & Chats
    val allConversations: Flow<List<Conversation>> = conversationDao.getAllConversations()
    
    fun getMessagesForConversation(conversationId: Int): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForConversation(conversationId)
    }

    suspend fun insertConversation(conversation: Conversation): Long {
        return conversationDao.insertConversation(conversation)
    }

    suspend fun updateConversation(conversation: Conversation) {
        conversationDao.updateConversation(conversation)
    }

    suspend fun deleteConversation(conversation: Conversation) {
        conversationDao.deleteConversation(conversation)
        chatDao.deleteMessagesForConversation(conversation.id)
    }

    suspend fun getConversationById(id: Int): Conversation? {
        return conversationDao.getConversationById(id)
    }

    suspend fun insertMessage(message: ChatMessage) {
        chatDao.insertMessage(message)
    }

    suspend fun clearAllMessages() {
        chatDao.clearAllMessages()
    }

    // Sandbox Files
    val allFiles: Flow<List<SandboxFile>> = fileDao.getAllFiles()

    suspend fun insertFile(filename: String, content: String): Long {
        // Save to physical file system
        val file = File(context.filesDir, filename)
        file.writeText(content)
        val sizeBytes = file.length()

        val sandboxFile = SandboxFile(
            filename = filename,
            content = content,
            sizeBytes = sizeBytes,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return fileDao.insertFile(sandboxFile)
    }

    suspend fun updateFile(sandboxFile: SandboxFile, newFilename: String, newContent: String) {
        // Delete old physical file if name changed
        if (sandboxFile.filename != newFilename) {
            val oldFile = File(context.filesDir, sandboxFile.filename)
            if (oldFile.exists()) {
                oldFile.delete()
            }
        }
        
        // Save to physical file system
        val file = File(context.filesDir, newFilename)
        file.writeText(newContent)
        val sizeBytes = file.length()

        val updated = sandboxFile.copy(
            filename = newFilename,
            content = newContent,
            sizeBytes = sizeBytes,
            updatedAt = System.currentTimeMillis()
        )
        fileDao.updateFile(updated)
    }

    suspend fun deleteFile(sandboxFile: SandboxFile) {
        // Delete from physical file system
        val file = File(context.filesDir, sandboxFile.filename)
        if (file.exists()) {
            file.delete()
        }
        fileDao.deleteFile(sandboxFile)
    }

    suspend fun prepopulateFilesIfEmpty() {
        if (fileDao.getFileCount() == 0) {
            insertFile(
                "reporte_resumen.md",
                "# Reporte Resumen de IA\n\nEste es un archivo de informe generado automáticamente en el entorno sandbox de ZaiApp.\n\n- **Modelo:** GLM-5.2\n- **Estado:** Completado\n- **Fecha:** 12 de julio de 2026\n\nSe han analizado las tendencias clave de agentes autónomos y se observó un crecimiento exponencial en la adopción de arquitecturas multi-agente para la automatización empresarial."
            )
            insertFile(
                "analizador.py",
                "import os\n\ndef analizar_datos():\n    print(\"Analizando archivos en el sandbox...\")\n    for f in os.listdir('.'):\n        print(f\"Encontrado: {f}\")\n\nif __name__ == '__main__':\n    analizar_datos()"
            )
            insertFile(
                "resultados_2026.csv",
                "Mes,Consultas,Precision,TiempoRespuesta(ms)\nEnero,1200,94.2,250\nFebrero,1500,94.8,240\nMarzo,1800,95.1,230\nAbril,2100,95.6,220\nMayo,2500,96.0,210\nJunio,3100,96.3,205"
            )
        }
    }

    // App Settings
    val appSettingsFlow: Flow<AppSettings?> = settingsDao.getSettingsFlow()

    suspend fun getAppSettings(): AppSettings? {
        return settingsDao.getSettings()
    }

    suspend fun saveAppSettings(settings: AppSettings) {
        settingsDao.insertOrUpdateSettings(settings)
    }

    // Support Tickets
    val allTickets: Flow<List<SupportTicket>> = supportTicketDao.getAllTickets()

    suspend fun insertTicket(ticket: SupportTicket) {
        supportTicketDao.insertTicket(ticket)
    }
}
