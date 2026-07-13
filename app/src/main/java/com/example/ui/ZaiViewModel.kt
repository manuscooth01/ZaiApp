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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ZaiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val prefs = application.getSharedPreferences("groq_prefs", Context.MODE_PRIVATE)

    // Sandbox Directory
    private val sandboxDir = File(application.filesDir, "sandbox").apply {
        if (!exists()) mkdirs()
    }

    // Default API Key and Base URL
    private val defaultApiKey = "YOUR_GROQ_API_KEY_HERE"
    private val defaultBaseUrl = "https://api.groq.com/openai/v1/"

    // UI States
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _baseUrl = MutableStateFlow(defaultBaseUrl)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _selectedModel = MutableStateFlow("mixtral-8x7b-32768")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Sessions and active session
    val sessions: StateFlow<List<ChatSession>>

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    // Messages for the active session
    private val _activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val activeMessages: StateFlow<List<ChatMessage>> = _activeMessages.asStateFlow()

    // Sandbox files
    private val _sandboxFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val sandboxFiles: StateFlow<List<FileItem>> = _sandboxFiles.asStateFlow()

    // Active operations states
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _currentThinkingSteps = MutableStateFlow<List<String>>(emptyList())
    val currentThinkingSteps: StateFlow<List<String>> = _currentThinkingSteps.asStateFlow()

    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    init {
        // Initialize DB and Repository
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database.chatDao())

        // Load preferences
        val savedKey = prefs.getString("api_key", defaultApiKey) ?: defaultApiKey
        _apiKey.value = savedKey

        val savedBaseUrl = prefs.getString("base_url", defaultBaseUrl) ?: defaultBaseUrl
        _baseUrl.value = savedBaseUrl

        val savedModel = prefs.getString("selected_model", "mixtral-8x7b-32768") ?: "mixtral-8x7b-32768"
        _selectedModel.value = savedModel

        // Load sessions flow
        sessions = repository.getAllSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Sync messages whenever active session changes
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

        // Initialize sandbox files list
        loadSandboxFiles()
    }

    // Pref Actions
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
                onSuccess = {
                    onResult(true, "¡Conexión exitosa con Groq API!")
                },
                onFailure = { throwable ->
                    onResult(false, "Error de conexión: ${throwable.message}")
                }
            )
        }
    }

    // Session Actions
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

    // Sandbox File Actions
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

    // Clear API Error
    fun clearApiError() {
        _apiError.value = null
    }

    // Direct AI Chat Conversational Flow (No thinking steps shown)
    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        val currentSessionId = _activeSessionId.value

        viewModelScope.launch {
            val sessionId = if (currentSessionId == null) {
                val title = if (userText.length > 20) userText.take(20) + "..." else userText
                val id = repository.createSession(title, _selectedModel.value)
                _activeSessionId.value = id
                id
            } else {
                currentSessionId
            }

            // Save user message
            repository.saveMessage(sessionId, "user", userText)

            _isGenerating.value = true
            _apiError.value = null

            // Compile conversation history
            val dbMessages = repository.getMessagesForSessionSync(sessionId)
            val apiMessages = mutableListOf<GroqMessage>()

            // Simple friendly assistant prompt for direct Chat
            val systemPrompt = "Eres GroqApp Chat, un asistente de IA de alta velocidad útil y conciso. Responde siempre en español de forma directa y clara. Puedes usar formato Markdown y bloques de código si es necesario."
            apiMessages.add(GroqMessage(role = "system", content = systemPrompt))

            val historyWindow = dbMessages.takeLast(10)
            historyWindow.forEach { msg ->
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
                    repository.saveMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = assistantResponse,
                        thinkingSteps = null
                    )
                },
                onFailure = { throwable ->
                    _apiError.value = throwable.message
                    repository.saveMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = "Lo siento, ha ocurrido un error al comunicarme con Groq: ${throwable.message}. Por favor, verifica tu API Key y URL base en Ajustes.",
                        thinkingSteps = null
                    )
                }
            )

            _isGenerating.value = false
        }
    }

    // Agent Conversational Flow with Thinking Steps
    fun sendAgentMessage(userText: String) {
        if (userText.isBlank()) return
        val currentSessionId = _activeSessionId.value

        viewModelScope.launch {
            val sessionId = if (currentSessionId == null) {
                val title = if (userText.length > 20) userText.take(20) + "..." else userText
                val id = repository.createSession(title, _selectedModel.value)
                _activeSessionId.value = id
                id
            } else {
                currentSessionId
            }

            // Save user message
            repository.saveMessage(sessionId, "user", userText)

            // Start generation / thinking process
            _isGenerating.value = true
            _apiError.value = null
            _currentThinkingSteps.value = emptyList()

            val stepsList = mutableListOf<String>()

            // Step 1: Analizar la tarea
            addThinkingStep(stepsList, "Analizando la tarea del usuario en español...")
            delay(500)

            // Step 2: Escanear sandbox de archivos
            addThinkingStep(stepsList, "Buscando archivos locales relevantes en el sandbox...")
            delay(500)

            // Look for referenced files in the message or construct a sandbox listing
            val availableFiles = _sandboxFiles.value
            var fileContextString = ""
            var referencedFilesCount = 0

            val referencedFiles = availableFiles.filter { fileItem ->
                userText.contains(fileItem.name, ignoreCase = true)
            }

            if (referencedFiles.isNotEmpty()) {
                stepsList.add("Se han detectado referencias a archivos en tu mensaje:")
                referencedFiles.forEach { fileItem ->
                    val content = withContext(Dispatchers.IO) { readSandboxFileContent(fileItem.name) }
                    if (content != null) {
                        referencedFilesCount++
                        stepsList.add(" - Leyendo archivo '${fileItem.name}' (${fileItem.sizeBytes} bytes)...")
                        fileContextString += "\n\n--- ARCHIVO LOCAL: ${fileItem.name} ---\n$content\n"
                    }
                }
                delay(600)
            } else {
                addThinkingStep(stepsList, "No se encontraron referencias específicas a archivos locales.")
                delay(300)
            }

            // Step 3: Evaluar si requiere búsqueda web
            addThinkingStep(stepsList, "Evaluando si se requiere información adicional de la web...")
            delay(400)

            var webContextString = ""
            val requiresWeb = userText.contains("busca", ignoreCase = true) ||
                    userText.contains("clima", ignoreCase = true) ||
                    userText.contains("noticias", ignoreCase = true) ||
                    userText.contains("precio", ignoreCase = true) ||
                    userText.contains("último", ignoreCase = true) ||
                    userText.contains("internet", ignoreCase = true)

            if (requiresWeb) {
                addThinkingStep(stepsList, "Ejecutando simulación de búsqueda en la web...")
                delay(600)
                // Generate a mock search result to pass as web context
                val query = extractSearchQuery(userText)
                addThinkingStep(stepsList, "Resultados de búsqueda encontrados para: '$query'")
                webContextString = "\n\n--- RESULTADOS DE BÚSQUEDA WEB MOCK PARA: $query ---\n" +
                        "1. Groq es un proveedor de aceleradores de inferencia de modelos de lenguaje ultra rápidos basados en LPU (Language Processing Unit).\n" +
                        "2. Clima hoy: Simulación de clima despejado, 24°C con vientos leves.\n" +
                        "3. Noticias: Mixtral-8x7b es un modelo de mezcla de expertos (MoE) desarrollado por Mistral AI, de alto rendimiento y eficiente.\n"
                delay(500)
            } else {
                addThinkingStep(stepsList, "Decisión de agente: No se requiere búsqueda web activa.")
                delay(300)
            }

            // Step 4: Realizar la consulta a Groq API
            addThinkingStep(stepsList, "Construyendo el contexto integrado para Groq API...")
            delay(400)

            // Generate list of all available files in sandbox to inform the LLM
            val sandboxFileListText = if (availableFiles.isNotEmpty()) {
                "Archivos cargados en el sandbox actual del usuario: " + availableFiles.joinToString { it.name }
            } else {
                "El sandbox de archivos está vacío."
            }

            // Compile conversation history
            val dbMessages = repository.getMessagesForSessionSync(sessionId)
            val apiMessages = mutableListOf<GroqMessage>()

            // System prompt
            val systemPrompt = "Eres un agente autónomo. El usuario te dará tareas en español. " +
                    "Decide si necesitas buscar en web, leer archivos, o responder directamente. Responde siempre en español.\n" +
                    "INFORMACIÓN DEL CONTEXTO LOCAL:\n" +
                    "- $sandboxFileListText\n" +
                    (if (fileContextString.isNotEmpty()) "- Contenido de archivos referenciados: $fileContextString\n" else "") +
                    (if (webContextString.isNotEmpty()) "- Información simulada de la web: $webContextString\n" else "")

            apiMessages.add(GroqMessage(role = "system", content = systemPrompt))

            // Add last 10 messages for context window
            val historyWindow = dbMessages.takeLast(10)
            historyWindow.forEach { msg ->
                apiMessages.add(GroqMessage(role = msg.role, content = msg.content))
            }

            addThinkingStep(stepsList, "Enviando solicitud a Groq API usando ${_selectedModel.value}...")
            
            val result = repository.getChatCompletion(
                baseUrl = _baseUrl.value,
                apiKey = _apiKey.value,
                model = _selectedModel.value,
                messages = apiMessages
            )

            result.fold(
                onSuccess = { assistantResponse ->
                    addThinkingStep(stepsList, "Respuesta recibida correctamente.")
                    
                    // Format all thinking steps as a single newline-separated string
                    val formattedThinkingSteps = stepsList.joinToString("\n")
                    
                    // Save assistant message to DB
                    repository.saveMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = assistantResponse,
                        thinkingSteps = formattedThinkingSteps
                    )
                },
                onFailure = { throwable ->
                    _apiError.value = throwable.message
                    addThinkingStep(stepsList, "Error al llamar a la API de Groq: ${throwable.message}")
                    
                    // Save error message to DB so history displays it or user gets feedback
                    repository.saveMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = "Lo siento, ha ocurrido un error al comunicarme con Groq: ${throwable.message}. Por favor, verifica tu API Key.",
                        thinkingSteps = stepsList.joinToString("\n")
                    )
                }
            )

            _isGenerating.value = false
        }
    }

    private fun addThinkingStep(list: MutableList<String>, step: String) {
        list.add(step)
        _currentThinkingSteps.value = list.toList()
    }

    private fun extractSearchQuery(prompt: String): String {
        return prompt.replace("busca", "", ignoreCase = true)
            .replace("buscar", "", ignoreCase = true)
            .replace("en la web", "", ignoreCase = true)
            .replace("en internet", "", ignoreCase = true)
            .replace("sobre", "", ignoreCase = true)
            .trim()
    }
}

data class FileItem(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)
