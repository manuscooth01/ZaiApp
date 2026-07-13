package com.example.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class Tab {
    Chat, Agente, Archivos, Historial, Mas
}

enum class StepStatus {
    IDLE, RUNNING, SUCCESS, ERROR
}

data class AgentStep(
    val number: Int,
    val title: String,
    val description: String,
    val status: StepStatus,
    val details: List<String> = emptyList()
)

enum class AgentMessageType {
    USER, TEXT, TOOL_CALL, TOOL_RESULT
}

data class AgentMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: AgentMessageType,
    val text: String,
    val toolName: String? = null,
    val toolDescription: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class ZaiViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "zai_database"
    ).fallbackToDestructiveMigration().build()

    private val repository = AppRepository(db, application)
    private val prefs = application.getSharedPreferences("zai_prefs", Context.MODE_PRIVATE)

    // UI Navigation & Onboarding States
    val currentTab = MutableStateFlow(Tab.Chat)
    val showOnboarding = MutableStateFlow(prefs.getBoolean("show_onboarding", true))

    // Anti-spam loading states
    val isTestingConnection = MutableStateFlow(false)

    // Active conversation state
    val activeConversationId = MutableStateFlow<Int?>(null)

    // Theme state (Dark Mode by default)
    val isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", true))

    // Settings States
    val apiBaseUrl = MutableStateFlow(prefs.getString("api_base_url", "https://api.z.ai/api/paas/v4/") ?: "https://api.z.ai/api/paas/v4/")
    val apiKey = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val defaultModel = MutableStateFlow(prefs.getString("default_model", "glm-4-flash") ?: "glm-4-flash")

    // Active streaming message content for real-time UI updates
    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    // Agent States
    val reasoningLevel = MutableStateFlow(prefs.getString("reasoning_level", "Maximo") ?: "Maximo")
    val reasoningModeEnabled: StateFlow<Boolean> = reasoningLevel
        .map { it != "No pensar" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    
    val agentMessages = MutableStateFlow<List<AgentMessage>>(listOf(
        AgentMessage(
            type = AgentMessageType.TEXT,
            text = "¡Hola! Soy tu Agente Autónomo. Escribe una tarea o pregunta en lenguaje natural y decidiré dinámicamente qué herramientas ejecutar en el sandbox."
        )
    ))

    private var agentJob: kotlinx.coroutines.Job? = null

    val isThinkingExpanded = MutableStateFlow(true)
    val isAgentRunning = MutableStateFlow(false)
    val agentChatInputText = MutableStateFlow("")

    private val _agentSteps = MutableStateFlow<List<AgentStep>>(emptyList())
    val agentSteps: StateFlow<List<AgentStep>> = _agentSteps.asStateFlow()

    // Support Ticket Form State
    val ticketsList: StateFlow<List<SupportTicket>> = repository.allTickets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Past Conversations Flow
    val conversationsList: StateFlow<List<Conversation>> = repository.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Room Database Observables
    val chatMessages: StateFlow<List<ChatMessage>> = activeConversationId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMessagesForConversation(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sandboxFiles: StateFlow<List<SandboxFile>> = repository.allFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat Input State
    val chatInputText = MutableStateFlow("")
    val isGeneratingReply = MutableStateFlow(false)

    // Error communication state
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.appSettingsFlow.first()?.let { settings ->
                apiBaseUrl.value = settings.apiBaseUrl
                apiKey.value = settings.apiKey
                defaultModel.value = settings.defaultModel
                isDarkMode.value = settings.isDarkMode
            } ?: run {
                repository.saveAppSettings(
                    AppSettings(
                        apiBaseUrl = apiBaseUrl.value,
                        apiKey = apiKey.value,
                        defaultModel = defaultModel.value,
                        isDarkMode = isDarkMode.value
                    )
                )
            }

            repository.prepopulateFilesIfEmpty()

            repository.allConversations.first().let { conversations ->
                if (conversations.isNotEmpty()) {
                    activeConversationId.value = conversations.first().id
                } else {
                    createNewConversation("Nueva Conversación")
                }
            }
        }
        resetAgentSteps()
    }

    private fun resetAgentSteps() {
        _agentSteps.value = listOf(
            AgentStep(1, "Analizando solicitud", "Listo para procesar comandos autónomos en el sandbox.", StepStatus.IDLE),
            AgentStep(2, "Ejecutando operaciones sandbox", "Esperando comando de usuario.", StepStatus.IDLE),
            AgentStep(3, "Sintetizando información", "Generando archivo de salida.", StepStatus.IDLE)
        )
    }

    fun completeOnboarding() {
        showOnboarding.value = false
        prefs.edit().putBoolean("show_onboarding", false).apply()
    }

    fun toggleDarkMode(enabled: Boolean) {
        isDarkMode.value = enabled
        prefs.edit().putBoolean("dark_mode", enabled).apply()
        updateSettingsInDb()
    }

    fun toggleReasoningMode(enabled: Boolean) {
        val level = if (enabled) "Maximo" else "No pensar"
        reasoningLevel.value = level
        prefs.edit().putString("reasoning_level", level).apply()
    }

    fun saveSettings(baseUrl: String, key: String, model: String) {
        apiBaseUrl.value = baseUrl
        apiKey.value = key
        defaultModel.value = model
        prefs.edit()
            .putString("api_base_url", baseUrl)
            .putString("api_key", key)
            .putString("default_model", model)
            .apply()
        updateSettingsInDb()
    }

    private fun updateSettingsInDb() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveAppSettings(
                AppSettings(
                    apiBaseUrl = apiBaseUrl.value,
                    apiKey = apiKey.value,
                    defaultModel = defaultModel.value,
                    isDarkMode = isDarkMode.value
                )
            )
        }
    }

    fun selectConversation(id: Int) {
        activeConversationId.value = id
        currentTab.value = Tab.Chat
    }

    fun createNewConversation(title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newConvId = repository.insertConversation(
                Conversation(
                    title = title,
                    modelUsed = defaultModel.value
                )
            )
            activeConversationId.value = newConvId.toInt()
            
            repository.insertMessage(
                ChatMessage(
                    conversationId = newConvId.toInt(),
                    role = "assistant",
                    content = "¡Hola! Soy ZaiApp AI. Estoy listo para ayudarte con tus tareas, analizar tus documentos o responder cualquier duda en español. ¿De qué te gustaría hablar hoy?",
                    modelUsed = defaultModel.value
                )
            )
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteConversation(conversation)
            if (activeConversationId.value == conversation.id) {
                repository.allConversations.first().let { list ->
                    if (list.isNotEmpty()) {
                        activeConversationId.value = list.first().id
                    } else {
                        createNewConversation("Nueva Conversación")
                    }
                }
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || isGeneratingReply.value) return
        val conversationId = activeConversationId.value ?: return

        chatInputText.value = ""

        viewModelScope.launch(Dispatchers.IO) {
            repository.getConversationById(conversationId)?.let { conv ->
                if (conv.title == "Nueva Conversación" || conv.title.length < 5) {
                    val newTitle = if (content.length > 30) content.substring(0, 30) + "..." else content
                    repository.updateConversation(conv.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
                } else {
                    repository.updateConversation(conv.copy(updatedAt = System.currentTimeMillis()))
                }
            }

            repository.insertMessage(
                ChatMessage(
                    conversationId = conversationId,
                    role = "user",
                    content = content,
                    modelUsed = defaultModel.value
                )
            )

            isGeneratingReply.value = true
            _streamingText.value = ""

            val activeKey = apiKey.value.trim()
            val baseUrl = apiBaseUrl.value.trim()
            val modelName = defaultModel.value.trim().lowercase()

            if (activeKey.isEmpty()) {
                isGeneratingReply.value = false
                _errorMessage.emit("Error de conexión. Clave API no configurada. Por favor, ve a la pestaña 'Más' -> 'Configuración' e ingresa tu clave de Z.ai.")
                return@launch
            }

            try {
                val previousMessages = chatMessages.value.takeLast(10)
                val requestMessages = JSONArray()

                var apiHistoryStarted = false
                previousMessages.forEach { msg ->
                    if (msg.role == "user" || msg.role == "system") {
                        apiHistoryStarted = true
                    }
                    if (apiHistoryStarted) {
                        val mObj = JSONObject()
                        mObj.put("role", msg.role)
                        mObj.put("content", msg.content)
                        requestMessages.put(mObj)
                    }
                }

                val currentObj = JSONObject().apply {
                    put("role", "user")
                    put("content", content)
                }
                requestMessages.put(currentObj)

                val requestBodyJson = JSONObject().apply {
                    put("model", modelName.lowercase())
                    put("messages", requestMessages)
                    put("stream", true)
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val endpoint = if (baseUrl.endsWith("/")) baseUrl + "chat/completions" else "$baseUrl/chat/completions"

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $activeKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) ZaiApp/1.0")
                    .post(RequestBody.create("application/json".toMediaTypeOrNull(), requestBodyJson.toString()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errText = response.body?.string() ?: ""
                        throw IOException("HTTP ${response.code}: $errText")
                    }

                    val source = response.body?.source() ?: throw IOException("Cuerpo de respuesta vacío")
                    var accumulatedText = ""

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val trimmed = line.trim()
                        if (trimmed.startsWith("data:")) {
                            val data = trimmed.substring(5).trim()
                            if (data == "[DONE]") break
                            try {
                                val dataJson = JSONObject(data)
                                val choices = dataJson.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    if (delta != null) {
                                        val deltaContent = delta.optString("content", "")
                                        if (deltaContent.isNotEmpty()) {
                                            accumulatedText += deltaContent
                                            _streamingText.value = accumulatedText
                                        }
                                    }
                                }
                            } catch (e: Exception) { }
                        }
                    }

                    if (accumulatedText.isNotEmpty()) {
                        repository.insertMessage(
                            ChatMessage(
                                conversationId = conversationId,
                                role = "assistant",
                                content = accumulatedText,
                                modelUsed = modelName
                            )
                        )
                    } else {
                        throw IOException("No se recibió contenido del servidor.")
                    }
                }
            } catch (e: Exception) {
                _errorMessage.emit("Error: ${e.localizedMessage}")
            } finally {
                _streamingText.value = null
                isGeneratingReply.value = false
            }
        }
    }

    fun testConnection(baseUrl: String, key: String, model: String, onResult: (Boolean, String) -> Unit) {
        if (isTestingConnection.value) return
        isTestingConnection.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build()

                // JSON minimalista limpio para evitar el Error 400
                val requestBodyJson = JSONObject().apply {
                    put("model", model.trim().lowercase())
                    put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
                }

                val endpoint = if (baseUrl.endsWith("/")) baseUrl + "chat/completions" else "$baseUrl/chat/completions"

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer ${key.trim()}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) ZaiApp/1.0")
                    .post(RequestBody.create("application/json".toMediaTypeOrNull(), requestBodyJson.toString()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onResult(true, "¡Conexión establecida con éxito!")
                    } else {
                        val code = response.code
                        val msg = if (code == 400) "Error 400: Estructura inválida. Comprueba el modelo." else "Error del Servidor ($code)"
                        onResult(false, msg)
                    }
                }
            } catch (e: Exception) {
                onResult(false, "Fallo de red: ${e.localizedMessage}")
            } finally {
                isTestingConnection.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllMessages()
            repository.allConversations.first().forEach { repository.deleteConversation(it) }
            createNewConversation("Nueva Conversación")
        }
    }

    fun addFile(name: String, content: String) = viewModelScope.launch(Dispatchers.IO) { repository.insertFile(name, content) }
    fun updateFileContent(file: SandboxFile, newName: String, newContent: String) = viewModelScope.launch(Dispatchers.IO) { repository.updateFile(file, newName, newContent) }
    fun deleteFile(file: SandboxFile) = viewModelScope.launch(Dispatchers.IO) { repository.deleteFile(file) }
    fun submitTicket(name: String, email: String, description: String) = viewModelScope.launch(Dispatchers.IO) { repository.insertTicket(SupportTicket(name, email, description, "Enviado")) }

    fun executeAgentTask(command: String) {
        if (command.isBlank() || isAgentRunning.value) return
        agentChatInputText.value = ""
        isAgentRunning.value = true
        isThinkingExpanded.value = true
        val prompt = command.trim()
        agentMessages.value = agentMessages.value + AgentMessage(type = AgentMessageType.USER, text = prompt)

        agentJob?.cancel()
        agentJob = viewModelScope.launch(Dispatchers.Default) {
            val isThinkingActive = reasoningLevel.value != "No pensar"
            val filenameRegex = "([\\w\\-]+\\.[a-zA-Z0-9]+)".toRegex()
            val matchedFile = filenameRegex.find(prompt)?.value
            val promptLower = prompt.lowercase()
            
            val toolName: String
            val toolDisplayName: String
            val toolDescription: String
            var toolResult = ""
            var finalResponse = ""

            if (promptLower.contains("busca") || promptLower.contains("web") || promptLower.contains("internet")) {
                toolName = "web_search"; toolDisplayName = "Búsqueda Web"
                val q = prompt.replace("(?i)(busca|en la web|noticias de)", "").trim()
                toolDescription = "Buscando en la web: \"$q\""
            } else if (promptLower.contains("lee") || promptLower.contains("cat ")) {
                toolName = "read_file"; toolDisplayName = "Leer Archivo"
                toolDescription = "Leyendo '${matchedFile ?: "reporte.md"}'..."
            } else {
                toolName = "web_search"; toolDisplayName = "Búsqueda Web"
                toolDescription = "Buscando contexto..."
            }

            if (isThinkingActive) {
                _agentSteps.value = listOf(AgentStep(1, "Analizando...", "Procesando comando natural.", StepStatus.RUNNING))
                delay(1000)
                _agentSteps.value = listOf(AgentStep(1, "Analizando...", "Completado.", StepStatus.SUCCESS))
            }

            agentMessages.value = agentMessages.value + AgentMessage(type = AgentMessageType.TOOL_CALL, text = toolDisplayName, toolName = toolName, toolDescription = toolDescription)
            toolResult = "Resultados obtenidos con éxito para la simulación de sandbox."
            finalResponse = "Acción completada de forma autónoma. Sandbox listo."
            delay(1000)

            agentMessages.value = agentMessages.value + AgentMessage(type = AgentMessageType.TOOL_RESULT, text = toolResult, toolName = toolName)
            delay(1000)
            agentMessages.value = agentMessages.value + AgentMessage(type = AgentMessageType.TEXT, text = finalResponse)
            isAgentRunning.value = false
        }
    }

    fun stopAgent() {
        agentJob?.cancel()
        isAgentRunning.value = false
        resetAgentSteps()
    }
    
    fun clearAgentChat() {
        agentJob?.cancel()
        isAgentRunning.value = false
        agentMessages.value = listOf(AgentMessage(type = AgentMessageType.TEXT, text = "¡Sesión reiniciada!"))
        resetAgentSteps()
    }
}
