package com.example.data

import com.example.data.api.GroqChatRequest
import com.example.data.api.GroqMessage
import com.example.data.api.GroqService
import com.example.data.api.ModelsListResponse
import com.example.data.database.ChatDao
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import com.example.data.database.ActionLog
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppRepository(private val chatDao: ChatDao) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Interceptor que añade cabeceras de navegador para evitar bloqueos
    // de Cloudflare WAF. OkHttp sin estas cabeceras recibe 403 Forbidden
    // porque Cloudflare identifica el User-Agent como bot/servidor.
    private val browserHeadersInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .header("Origin", "https://console.groq.com")
            .header("Referer", "https://console.groq.com/")
            .build()
        chain.proceed(request)
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(browserHeadersInterceptor)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (com.example.BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
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

    fun getSessionsByType(type: String, userEmail: String): Flow<List<ChatSession>> =
        chatDao.getSessionsByType(type, userEmail)

    suspend fun getSessionById(sessionId: Long): ChatSession? =
        chatDao.getSessionById(sessionId)

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun getMessagesForSessionSync(sessionId: Long): List<ChatMessage> =
        chatDao.getMessagesForSessionSync(sessionId)

    suspend fun createSession(title: String, model: String, sessionType: String, userEmail: String): Long {
        return chatDao.insertSession(
            ChatSession(title = title, model = model, sessionType = sessionType, userEmail = userEmail)
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

    suspend fun deleteAllDataForUser(userEmail: String) {
        chatDao.deleteMessagesByUser(userEmail)
        chatDao.deleteSessionsByUser(userEmail)
    }

    suspend fun updateSessionTitle(sessionId: Long, newTitle: String) {
        chatDao.updateSessionTitle(sessionId, newTitle)
    }

    suspend fun getChatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<GroqMessage>,
        temperature: Double = 0.7,
        provider: String = ""
    ): Result<String> {
        return try {
            val isOllama = provider == "Ollama" || baseUrl.contains("11434") || baseUrl.contains("ollama", ignoreCase = true)
            if (apiKey.isBlank() && !isOllama) {
                return Result.failure(
                    Exception("Falta la API Key. Configúrala en Ajustes.")
                )
            }
            val bearerToken = if (apiKey.isNotBlank()) "Bearer $apiKey" else "Bearer none"
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
                    401 -> "Clave API inválida (401). Crea una nueva en console.groq.com → API Keys."
                    403 -> "Acceso bloqueado (403). Posibles causas: clave sin créditos, cuenta suspendida, o bloqueo de red. Prueba regenerar tu clave en console.groq.com o usa OpenRouter como alternativa."
                    429 -> "Límite de velocidad alcanzado (429). Espera unos segundos e inténtalo de nuevo."
                    400 -> "Solicitud incorrecta (400). Puede que el modelo '$model' no exista o esté retirado. Prueba con llama-3.1-8b-instant."
                    404 -> "URL o modelo no encontrado (404). Revisa la URL base en Ajustes."
                    500, 502, 503 -> "El servidor de la API está teniendo problemas ($errorCode). Inténtalo más tarde."
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
        model: String,
        provider: String = ""
    ): Result<String> {
        val messages = listOf(
            GroqMessage(role = "user", content = "Responde solo con la palabra: OK")
        )
        return getChatCompletion(baseUrl, apiKey, model, messages, provider = provider).map { raw ->
            "Conexión exitosa. Modelo respondió: ${raw.take(120)}"
        }
    }

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        provider: String = ""
    ): Result<List<String>> {
        return try {
            val isOllama = provider == "Ollama" || baseUrl.contains("11434") || baseUrl.contains("ollama", ignoreCase = true)
            if (apiKey.isBlank() && !isOllama) {
                return Result.failure(
                    Exception("Falta la API Key. Configúrala en Ajustes.")
                )
            }
            val bearerToken = if (apiKey.isNotBlank()) "Bearer $apiKey" else "Bearer none"
            val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val fullUrl = "${cleanBaseUrl}models"
            val response = groqService.getModelsList(fullUrl, bearerToken)

            if (response.isSuccessful) {
                val body = response.body()
                val list = body?.data?.map { it.id } ?: emptyList()
                if (list.isNotEmpty()) {
                    Result.success(list)
                } else {
                    Result.failure(Exception("La API no devolvió ningún modelo disponible."))
                }
            } else {
                val errorCode = response.code()
                val errorBody = try { response.errorBody()?.string()?.take(500) } catch (_: Exception) { null }
                val errorMessage = when (errorCode) {
                    401 -> "Clave API inválida (401). Verifica en Ajustes."
                    403 -> "Acceso denegado (403). La API o red bloquea la petición."
                    else -> "Error al consultar modelos ($errorCode): ${errorBody ?: response.message()}"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(
                Exception("Error de conexión: ${e.localizedMessage ?: "No se pudo conectar para obtener modelos"}")
            )
        }
    }

    fun getAllLogs(): Flow<List<ActionLog>> = chatDao.getAllLogs()

    suspend fun saveLog(actionType: String, description: String): Long {
        return chatDao.insertLog(ActionLog(actionType = actionType, description = description))
    }

    suspend fun clearAllLogs() {
        chatDao.clearAllLogs()
    }
}
