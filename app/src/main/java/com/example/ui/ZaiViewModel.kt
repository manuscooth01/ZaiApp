package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.api.GroqMessage
import com.example.data.database.AppDatabase
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ZaiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val prefs = application.getSharedPreferences("groq_prefs", Context.MODE_PRIVATE)

    private val sandboxDir = File(application.filesDir, "sandbox").apply {
        if (!exists()) mkdirs()
    }

    private val defaultApiKey = "YOUR_GROQ_API_KEY_HERE"
    private val defaultBaseUrl = "https://api.groq.com/openai/v1/"

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _baseUrl = MutableStateFlow(defaultBaseUrl)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _selectedModel = MutableStateFlow("llama-3.1-8b-instant")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    val sessions: StateFlow<List<ChatSession>>

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val activeMessages: StateFlow<List<ChatMessage>> = _activeMessages.asStateFlow()

    private val _sandboxFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val sandboxFiles: StateFlow<List<FileItem>> = _sandboxFiles.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _currentThinkingSteps = MutableStateFlow<List<String>>(emptyList())
    val currentThinkingSteps: StateFlow<List<String>> = _currentThinkingSteps.asStateFlow()

    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database.chatDao())

        val savedKey = prefs.getString("api_key", defaultApiKey) ?: defaultApiKey
        _apiKey.value = savedKey

        val savedBaseUrl = prefs.getString("base_url", defaultBaseUrl) ?: defaultBaseUrl
        _baseUrl.value = savedBaseUrl

        val savedModel = prefs.getString("selected_model", "llama-3.1-8b-instant") ?: "llama-3.1-8b-instant"
        _selectedModel.value = savedModel

        sessions = repository.getAllSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            _activeSessionId.collect { sessionId ->
                if (sessionId != null) {
                    repository.getMessagesForSession(sessionId).collect { messages ->
                        _activeMessages.value = messages
                    }
                } else {
                    _activeMessages.value = emptyList()
                }
            }
        }

        loadSandboxFiles()
    }

    fun saveApiKey(newKey: String) {
        prefs.edit().putString("api_key", newKey).apply()
        _apiKey.value = newKey
    }

    fun saveBaseUrl(newUrl: String) {
        prefs.edit().putString("base_url", newUrl).apply()
        _baseUrl.value = newUrl
    }

    fun saveSelectedModel(newModel: String) {
        prefs.edit().putString("selected_model", newModel).apply()
        _selectedModel.value = newModel
    }

    fun testConnection(customBaseUrl: String, customApiKey: String, customModel: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val testMessages = listOf(GroqMessage(role = "user", content = "Ping"))
            val result = repository.getChatCompletion(
                baseUrl = customBaseUrl,
                apiKey = customApiKey,
                model = customModel,
                messages = testMessages
            )
            result.fold(
                onSuccess = { onResult(true, "¡Conexión exitosa con Groq API!") },
                onFailure = { throwable -> onResult(false, "Error de conexión: ${throwable.message}") }
            )
        }
    }

    fun selectSession(sessionId: Long) {
        _activeSessionId.value = sessionId
        _apiError.value = null
    }

    fun startNewSession() {
        viewModelScope.launch {
            val title = "Nueva sesión - ${System.currentTimeMillis() % 100000}"
            val model = _selectedModel.value
            val id = repository.createSession(title, model)
            selectSession(id)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = null
            }
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun loadSandboxFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = sandboxDir.listFiles() ?: emptyArray()
            val items = files.map { file ->
                FileItem(
                    name = file.name,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }.sortedByDescending { it.lastModified }
            _sandboxFiles.value = items
        }
    }

    fun createSandboxFile(name: String, content: String): Boolean {
        return try {
            val file = File(sandboxDir, name)
            file.writeText(content)
            loadSandboxFiles()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteSandboxFile(name: String): Boolean {
        return try {
            val file = File(sandboxDir, name)
            if (file.exists()) {
                val success = file.delete()
                loadSandboxFiles()
                success
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun readSandboxFileContent(name: String): String? {
        return try {
            val file = File(sandboxDir, name)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun clearApiError() {
        _apiError.value = null
    }

    // ─── Chat Directo (sin cambios) ────────────────────────
    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        val currentSessionId = _activeSessionId.value
        viewModelScope.launch {
            val sessionId = if (currentSessionId == null) {
                val title = if (userText.length > 20) userText.take(20) + "..." else userText
                val id = repository.createSession(title, _selectedModel.value)
                _activeSessionId.value = id
                id
            } else currentSessionId

            repository.saveMessage(sessionId, "user", userText)
            _isGenerating.value = true
            _apiError.value = null

            val dbMessages = repository.getMessagesForSessionSync(sessionId)
            val apiMessages = mutableListOf<GroqMessage>()
            val systemPrompt = "Eres GroqApp Chat, un asistente de IA de alta velocidad útil y conciso. Responde siempre en español de forma directa y clara."
            apiMessages.add(GroqMessage(role = "system", content = systemPrompt))
            dbMessages.takeLast(10).forEach { msg ->
                apiMessages.add(GroqMessage(role = msg.role, content = msg.content))
            }

            val result = repository.getChatCompletion(
                baseUrl = _baseUrl.value,
                apiKey = _apiKey.value,
                model = _selectedModel.value,
                messages = apiMessages
            )
            result.fold(
                onSuccess = { assistantResponse ->
                    repository.saveMessage(sessionId = sessionId, role = "assistant", content = assistantResponse, thinkingSteps = null)
                },
                onFailure = { throwable ->
                    _apiError.value = throwable.message
                    repository.saveMessage(
                        sessionId = sessionId, role = "assistant",
                        content = "Lo siento, ha ocurrido un error: ${throwable.message}.",
                        thinkingSteps = null
                    )
                }
            )
            _isGenerating.value = false
        }
    }

    // ─── Agente Autónomo (sin delays artificiales) ────────
    fun sendAgentMessage(userText: String) {
        if (userText.isBlank()) return
        val currentSessionId = _activeSessionId.value
        viewModelScope.launch {
            val sessionId = if (currentSessionId == null) {
                val title = if (userText.length > 20) userText.take(20) + "..." else userText
                val id = repository.createSession(title, _selectedModel.value)
                _activeSessionId.value = id
                id
            } else currentSessionId

            repository.saveMessage(sessionId, "user", userText)
            _isGenerating.value = true
            _apiError.value = null

            val stepsList = mutableListOf<String>()
            stepsList.add("Analizando la tarea...")
            _currentThinkingSteps.value = stepsList.toList()

            // Escanear sandbox
            val availableFiles = _sandboxFiles.value
            var fileContextString = ""
            val referencedFiles = availableFiles.filter { fileItem ->
                userText.contains(fileItem.name, ignoreCase = true)
            }
            if (referencedFiles.isNotEmpty()) {
                stepsList.add("Archivos referenciados: ${referencedFiles.joinToString { it.name }}")
                _currentThinkingSteps.value = stepsList.toList()
                referencedFiles.forEach { fileItem ->
                    val content = withContext(Dispatchers.IO) { readSandboxFileContent(fileItem.name) }
                    if (content != null) {
                        fileContextString += "\n\n--- ARCHIVO LOCAL: ${fileItem.name} ---\n$content\n"
                    }
                }
            } else {
                stepsList.add("No se detectaron referencias a archivos.")
                _currentThinkingSteps.value = stepsList.toList()
            }

            // Simulación de búsqueda web (se mantiene como contexto adicional)
            var webContextString = ""
            if (userText.contains("busca", ignoreCase = true) ||
                userText.contains("clima", ignoreCase = true) ||
                userText.contains("noticias", ignoreCase = true) ||
                userText.contains("precio", ignoreCase = true) ||
                userText.contains("último", ignoreCase = true) ||
                userText.contains("internet", ignoreCase = true)) {
                stepsList.add("Generando contexto web simulado...")
                _currentThinkingSteps.value = stepsList.toList()
                webContextString = "\n\n--- RESULTADOS DE BÚSQUEDA WEB ---\n" +
                        "1. Groq es un proveedor de aceleradores de inferencia LPU.\n" +
                        "2. Clima: simulación de 24°C despejado.\n" +
                        "3. Noticias: Mixtral-8x7b es eficiente.\n"
            }

            // Construir prompt del sistema
            val sandboxFileListText = if (availableFiles.isNotEmpty()) {
                "Archivos del usuario: " + availableFiles.joinToString { it.name }
            } else {
                "El sandbox de archivos está vacío."
            }
            val systemPrompt = "Eres un agente autónomo. Responde en español. Usa la información local y web cuando sea relevante.\n" +
                    "CONTEXTO:\n- $sandboxFileListText\n" +
                    (if (fileContextString.isNotEmpty()) "- Contenido de archivos:\n$fileContextString\n" else "") +
                    (if (webContextString.isNotEmpty()) "- Info web:\n$webContextString\n" else "")

            val dbMessages = repository.getMessagesForSessionSync(sessionId)
            val apiMessages = mutableListOf<GroqMessage>()
            apiMessages.add(GroqMessage(role = "system", content = systemPrompt))
            dbMessages.takeLast(10).forEach { msg ->
                apiMessages.add(GroqMessage(role = msg.role, content = msg.content))
            }

            stepsList.add("Consultando a Groq (${_selectedModel.value})...")
            _currentThinkingSteps.value = stepsList.toList()

            val result = repository.getChatCompletion(
                baseUrl = _baseUrl.value,
                apiKey = _apiKey.value,
                model = _selectedModel.value,
                messages = apiMessages
            )
            result.fold(
                onSuccess = { assistantResponse ->
                    repository.saveMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = assistantResponse,
                        thinkingSteps = stepsList.joinToString("\n")
                    )
                },
                onFailure = { throwable ->
                    _apiError.value = throwable.message
                    repository.saveMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = "Error: ${throwable.message}",
                        thinkingSteps = stepsList.joinToString("\n")
                    )
                }
            )
            _isGenerating.value = false
        }
    }
}

data class FileItem(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)
