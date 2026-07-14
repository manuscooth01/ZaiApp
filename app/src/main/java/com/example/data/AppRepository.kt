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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppRepository(private val chatDao: ChatDao) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/openai/v1/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val groqService = retrofit.create(GroqService::class.java)

    fun getAllSessions(): Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getSessionsByType(type: String): Flow<List<ChatSession>> =
        chatDao.getSessionsByType(type)

    suspend fun getSessionById(sessionId: Long): ChatSession? =
        chatDao.getSessionById(sessionId)

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun getMessagesForSessionSync(sessionId: Long): List<ChatMessage> =
        chatDao.getMessagesForSessionSync(sessionId)

    suspend fun createSession(title: String, model: String, sessionType: String): Long {
        return chatDao.insertSession(
            ChatSession(title = title, model = model, sessionType = sessionType)
        )
    }

    suspend fun saveMessage(
        sessionId: Long,
        role: String,
        content: String,
        thinkingSteps: String? = null
    ): Long {
        chatDao.touchSession(sessionId)
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

    suspend fun getChatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<GroqMessage>,
        temperature: Double = 0.7
    ): Result<String> {
        return try {
            if (apiKey.isBlank()) {
                return Result.failure(
                    Exception("Falta la API Key. Configúrala en Ajustes.")
                )
            }
            val bearerToken = "Bearer $apiKey"
            val request = GroqChatRequest(
                model = model,
                messages = messages,
                temperature = temperature.coerceIn(0.0, 2.0)
            )
            val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val fullUrl = "${cleanBaseUrl}chat/completions"
            val response = groqService.getChatCompletion(fullUrl, bearerToken, request)

            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
                if (!content.isNullOrBlank()) {
                    Result.success(content)
                } else {
                    val err = body?.error?.message
                    Result.failure(
                        Exception(err ?: "Respuesta vacía de la API")
                    )
                }
            } else {
                val errorCode = response.code()
                val errorBody = try {
                    response.errorBody()?.string()?.take(500)
                } catch (_: Exception) {
                    null
                }
                val errorMessage = when (errorCode) {
                    401 -> "Clave API inválida. Verifica en console.groq.com"
                    429 -> "Límite de velocidad alcanzado. Espera un momento."
                    400 -> "Error en la solicitud. Verifica el modelo: ${errorBody ?: ""}"
                    404 -> "URL o endpoint no encontrado. Revisa la URL base."
                    else -> "Error de API ($errorCode): ${errorBody ?: response.message()}"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    "Error de conexión: ${e.localizedMessage ?: "No se pudo conectar con la API"}"
                )
            )
        }
    }

    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> {
        val messages = listOf(
            GroqMessage(role = "user", content = "Responde solo con la palabra: OK")
        )
        return getChatCompletion(baseUrl, apiKey, model, messages).map { raw ->
            "Conexión exitosa. Modelo respondió: ${raw.take(120)}"
        }
    }
}
