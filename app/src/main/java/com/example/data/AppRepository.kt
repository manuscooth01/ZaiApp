package com.example.data

import com.example.data.api.GroqChatRequest
import com.example.data.api.GroqMessage
import com.example.data.api.GroqService
import com.example.data.database.ChatDao
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppRepository(private val chatDao: ChatDao) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/openai/v1/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val groqService = retrofit.create(GroqService::class.java)

    // Room DB features
    fun getAllSessions(): Flow<List<ChatSession>> = chatDao.getAllSessions()

    suspend fun getSessionById(sessionId: Long): ChatSession? = chatDao.getSessionById(sessionId)

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> = 
        chatDao.getMessagesForSession(sessionId)

    suspend fun getMessagesForSessionSync(sessionId: Long): List<ChatMessage> =
        chatDao.getMessagesForSessionSync(sessionId)

    suspend fun createSession(title: String, model: String): Long {
        return chatDao.insertSession(ChatSession(title = title, model = model))
    }

    suspend fun saveMessage(sessionId: Long, role: String, content: String, thinkingSteps: String? = null): Long {
        return chatDao.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = role,
                content = content,
                thinkingSteps = thinkingSteps
            )
        )
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteMessagesBySessionId(sessionId)
        chatDao.deleteSessionById(sessionId)
    }

    suspend fun updateSessionTitle(sessionId: Long, newTitle: String) {
        chatDao.updateSessionTitle(sessionId, newTitle)
    }

    // API feature
    suspend fun getChatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<GroqMessage>
    ): Result<String> {
        return try {
            val bearerToken = "Bearer $apiKey"
            val request = GroqChatRequest(model = model, messages = messages)
            val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val fullUrl = "${cleanBaseUrl}chat/completions"
            val response = groqService.getChatCompletion(fullUrl, bearerToken, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    Result.success(content)
                } else {
                    Result.failure(Exception("Respuesta vacía de Groq API"))
                }
            } else {
                val errorCode = response.code()
                val errorMessage = when (errorCode) {
                    401 -> "Clave API de Groq inválida. Verifica en console.groq.com"
                    429 -> "Límite de velocidad de Groq. Espera un momento."
                    400 -> "Error en la solicitud. Verifica el modelo seleccionado."
                    else -> "Error de Groq API (${errorCode}): ${response.message()}"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Error de conexión: ${e.localizedMessage ?: "No se pudo conectar con Groq API"}"))
        }
    }
}
