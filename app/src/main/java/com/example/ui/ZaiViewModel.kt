package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
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
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class AttachedFile(val uri: Uri, val name: String, val type: String)
data class FileItem(val name: String, val sizeBytes: Long, val lastModified: Long)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

class ZaiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val prefs = application.getSharedPreferences("groq_prefs", Context.MODE_PRIVATE)
    private val sandboxDir: File by lazy {
        File(application.filesDir, "sandbox").apply { if (!exists()) mkdirs() }
    }

    // ─── Onboarding ─────────────────────────────────────
    private val _onboardingCompleted =
        MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
        _onboardingCompleted.value = true
        // Open settings after finishing onboarding
        _showSettings.value = true
    }

    // ─── Configuración ──────────────────────────────────
    val providers = mapOf(
        "Groq" to "https://api.groq.com/openai/v1/",
        "OpenAI" to "https://api.openai.com/v1/",
        "OpenRouter" to "https://openrouter.ai/api/v1/",
        "Together" to "https://api.together.xyz/v1/"
    )

    val defaultModels = mapOf(
        "Groq" to "llama-3.1-8b-instant",
        "OpenAI" to "gpt-4o-mini",
        "OpenRouter" to "meta-llama/llama-3.1-8b-instruct",
        "Together" to "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo"
    )

    private val _selectedProvider =
        MutableStateFlow(prefs.getString("provider", "Groq") ?: "Groq")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    fun setProvider(p: String) {
        _selectedProvider.value = p
        _baseUrl.value = providers[p] ?: _baseUrl.value
        val defaultModel = defaultModels[p]
        if (defaultModel != null && _selectedModel.value.isBlank()) {
            _selectedModel.value = defaultModel
        } else if (defaultModel != null) {
            // Auto-fill model when switching provider if current looks empty/default
            _selectedModel.value = defaultModel
        }
        prefs.edit().putString("provider", p).apply()
    }

    private val _apiKey = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _baseUrl = MutableStateFlow(
        prefs.getString("base_url", providers["Groq"]!!) ?: providers["Groq"]!!
    )
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _selectedModel = MutableStateFlow(
        prefs.getString("selected_model", "llama-3.1-8b-instant") ?: "llama-3.1-8b-instant"
    )
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _themeMode = MutableStateFlow(
        when (prefs.getString("theme_mode", "DARK")) {
            "LIGHT" -> ThemeMode.LIGHT
            "SYSTEM" -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun saveApiKey(k: String) {
        prefs.edit().putString("api_key", k).apply()
        _apiKey.value = k
    }

    fun saveBaseUrl(u: String) {
        prefs.edit().putString("base_url", u).apply()
        _baseUrl.value = u
    }

    fun saveSelectedModel(m: String) {
        prefs.edit().putString("selected_model", m).apply()
        _selectedModel.value = m
    }

    fun saveAllSettings(provider: String, baseUrl: String, model: String, key: String) {
        setProvider(provider)
        saveBaseUrl(baseUrl)
        saveSelectedModel(model)
        saveApiKey(key)
    }

    // ─── UI flags ───────────────────────────────────────
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()
    fun openSettings() { _showSettings.value = true }
    fun closeSettings() { _showSettings.value = false }

    private val _showHistoryMenu = MutableStateFlow(false)
    val showHistoryMenu: StateFlow<Boolean> = _showHistoryMenu.asStateFlow()
    fun toggleHistoryMenu() { _showHistoryMenu.value = !_showHistoryMenu.value }
    fun closeHistoryMenu() { _showHistoryMenu.value = false }

    private val _currentTab = MutableStateFlow("Chat")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()
    fun setCurrentTab(tab: String) { _currentTab.value = tab }

    /** Agent reasoning switch (shown in agent header). */
    private val _reasoningEnabled = MutableStateFlow(true)
    val reasoningEnabled: StateFlow<Boolean> = _reasoningEnabled.asStateFlow()
    fun setReasoningEnabled(v: Boolean) { _reasoningEnabled.value = v }

    // ─── Sesiones separadas ─────────────────────────────
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    private val _agentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val agentMessages: StateFlow<List<ChatMessage>> = _agentMessages.asStateFlow()

    private var chatSessionId: Long? = null
    private var agentSessionId: Long? = null

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()
    private val _loadingMessage = MutableStateFlow("")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()
    private val _thinkingSteps = MutableStateFlow<List<String>>(emptyList())
    val thinkingSteps: StateFlow<List<String>> = _thinkingSteps.asStateFlow()

    /** Live connection test status for Settings dialog. */
    private val _connectionTestStatus = MutableStateFlow<String?>(null)
    val connectionTestStatus: StateFlow<String?> = _connectionTestStatus.asStateFlow()
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    val sessions: StateFlow<List<ChatSession>>
    val chatSessions: StateFlow<List<ChatSession>>
    val agentSessions: StateFlow<List<ChatSession>>

    // ─── Archivos adjuntos ──────────────────────────────
    private val _pendingFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val pendingFiles: StateFlow<List<AttachedFile>> = _pendingFiles.asStateFlow()

    fun addPendingFile(file: AttachedFile) {
        _pendingFiles.value = _pendingFiles.value + file
    }

    fun removePendingFile(index: Int) {
        _pendingFiles.value = _pendingFiles.value.toMutableList().also {
            if (index in it.indices) it.removeAt(index)
        }
    }

    fun clearPendingFiles() {
        _pendingFiles.value = emptyList()
    }

    private suspend fun buildAttachmentContext(files: List<AttachedFile>): String {
        if (files.isEmpty()) return ""
        return withContext(Dispatchers.IO) {
            val sb = StringBuilder("\n\n--- Archivos adjuntos ---\n")
            files.forEach { file ->
                sb.append("\n[${file.name}] (${file.type})\n")
                try {
                    // Also copy into sandbox so agent can "modify" them
                    val dest = File(sandboxDir, file.name)
                    getApplication<Application>().contentResolver
                        .openInputStream(file.uri)?.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                    val text = try {
                        dest.readText()
                    } catch (_: Exception) {
                        null
                    }
                    if (!text.isNullOrBlank() && text.length < 200_000) {
                        sb.append(text.take(6000))
                        if (text.length > 6000) sb.append("\n[...contenido truncado...]")
                    } else {
                        sb.append("(archivo binario o vacío — copiado al sandbox: ${file.name})")
                    }
                } catch (e: Exception) {
                    sb.append("(error al leer: ${e.message})")
                }
                sb.append("\n")
            }
            sb.toString()
        }
    }

    private fun buildHistoryMessages(
        history: List<ChatMessage>,
        systemPrompt: String
    ): List<GroqMessage> {
        val apiMessages = mutableListOf(GroqMessage(role = "system", content = systemPrompt))
        history.forEach { msg ->
            if (msg.role == "user" || msg.role == "assistant") {
                apiMessages.add(GroqMessage(role = msg.role, content = msg.content))
            }
        }
        return apiMessages
    }

    // ─── Envío de mensajes ──────────────────────────────
    fun sendChatMessage(text: String) {
        if (_isGenerating.value) return
        if (text.isBlank() && _pendingFiles.value.isEmpty()) return

        viewModelScope.launch {
            _isGenerating.value = true
            _loadingMessage.value = "Preparando mensaje..."
            try {
                val attachments = _pendingFiles.value.toList()
                val attachmentContext = buildAttachmentContext(attachments)
                val displayText = text.ifBlank {
                    "Analiza estos archivos: ${attachments.joinToString { it.name }}"
                }
                val fullText = displayText + attachmentContext

                _loadingMessage.value = "Guardando mensaje..."
                val id = chatSessionId ?: repository.createSession(
                    title = displayText.take(40).ifBlank { "Nuevo chat" },
                    model = _selectedModel.value,
                    sessionType = "Chat"
                ).also { chatSessionId = it }

                repository.saveMessage(id, "user", displayText)
                val history = repository.getMessagesForSessionSync(id)
                // Replace last user content with full (incl. attachments) for the API
                val apiMessages = buildHistoryMessages(
                    history.dropLast(1),
                    "Eres un asistente útil, claro y conciso. Responde en el idioma del usuario."
                ).toMutableList()
                apiMessages.add(GroqMessage(role = "user", content = fullText))

                _loadingMessage.value = "Enviando a ${_selectedProvider.value}..."
                val result = repository.getChatCompletion(
                    _baseUrl.value,
                    _apiKey.value,
                    _selectedModel.value,
                    apiMessages
                )

                if (result.isSuccess) {
                    _loadingMessage.value = "Recibiendo respuesta..."
                    repository.saveMessage(id, "assistant", result.getOrThrow())
                } else {
                    _apiError.value = result.exceptionOrNull()?.message
                }

                _chatMessages.value = repository.getMessagesForSessionSync(id)
                clearPendingFiles()
                loadSandboxFiles()
            } finally {
                _isGenerating.value = false
                _loadingMessage.value = ""
            }
        }
    }

    fun sendAgentMessage(text: String) {
        if (_isGenerating.value) return
        if (text.isBlank() && _pendingFiles.value.isEmpty()) return

        viewModelScope.launch {
            _isGenerating.value = true
            _loadingMessage.value = "Agente iniciando..."
            _thinkingSteps.value = emptyList()
            try {
                val attachments = _pendingFiles.value.toList()
                val attachmentContext = buildAttachmentContext(attachments)
                val displayText = text.ifBlank {
                    "Procesa estos archivos: ${attachments.joinToString { it.name }}"
                }
                val fullText = displayText + attachmentContext

                val id = agentSessionId ?: repository.createSession(
                    title = displayText.take(40).ifBlank { "Nuevo agente" },
                    model = _selectedModel.value,
                    sessionType = "Agente"
                ).also { agentSessionId = it }

                repository.saveMessage(id, "user", displayText)

                if (_reasoningEnabled.value) {
                    addThinkingStep("Analizando la solicitud del usuario...")
                    delay(250)
                    if (attachments.isNotEmpty()) {
                        addThinkingStep("Leyendo ${attachments.size} archivo(s) adjunto(s)...")
                        delay(200)
                    }
                    addThinkingStep("Planificando pasos de resolución...")
                    delay(200)
                    addThinkingStep("Consultando al modelo ${_selectedModel.value}...")
                } else {
                    _loadingMessage.value = "Consultando al modelo..."
                }

                val sandboxListing = withContext(Dispatchers.IO) {
                    sandboxDir.listFiles()?.filter { it.isFile }
                        ?.joinToString("\n") { "- ${it.name} (${it.length()} bytes)" }
                        ?: "(vacío)"
                }

                val systemPrompt = buildString {
                    append("Eres un agente autónomo experto. ")
                    append("Razona paso a paso y resuelve tareas complejas. ")
                    append("Si necesitas calcular o procesar datos, incluye código Python en un bloque ```python. ")
                    append("Si modificas archivos, describe el contenido completo del archivo resultante en un bloque ```file:nombre.ext. ")
                    append("Archivos disponibles en el sandbox:\n$sandboxListing")
                    if (_reasoningEnabled.value) {
                        append("\nMuestra tu razonamiento de forma clara.")
                    }
                }

                val history = repository.getMessagesForSessionSync(id)
                val apiMessages = buildHistoryMessages(history.dropLast(1), systemPrompt)
                    .toMutableList()
                apiMessages.add(GroqMessage(role = "user", content = fullText))

                val result = repository.getChatCompletion(
                    _baseUrl.value,
                    _apiKey.value,
                    _selectedModel.value,
                    apiMessages
                )

                if (result.isSuccess) {
                    if (_reasoningEnabled.value) {
                        addThinkingStep("Generando respuesta final...")
                        delay(150)
                    }
                    val content = result.getOrThrow()
                    // Persist any ```file:name blocks into sandbox
                    extractAndSaveFiles(content)
                    val stepsJoined = _thinkingSteps.value.joinToString("\n")
                    repository.saveMessage(
                        id,
                        "assistant",
                        content,
                        thinkingSteps = stepsJoined.ifBlank { null }
                    )
                    // Auto-detect python for UI
                    if (content.contains("```python")) {
                        _pendingPythonCode.value =
                            content.substringAfter("```python").substringBefore("```").trim()
                    }
                } else {
                    _apiError.value = result.exceptionOrNull()?.message
                }

                _agentMessages.value = repository.getMessagesForSessionSync(id)
                clearPendingFiles()
                loadSandboxFiles()
            } finally {
                _isGenerating.value = false
                _loadingMessage.value = ""
            }
        }
    }

    private fun addThinkingStep(step: String) {
        _thinkingSteps.value = _thinkingSteps.value + step
    }

    private suspend fun extractAndSaveFiles(content: String) = withContext(Dispatchers.IO) {
        // Pattern: ```file:name.ext\n...\n```
        val regex = Regex("```file:([^\\n`]+)\\n([\\s\\S]*?)```")
        regex.findAll(content).forEach { match ->
            val name = match.groupValues[1].trim().replace("/", "_").replace("\\", "_")
            val body = match.groupValues[2]
            try {
                File(sandboxDir, name).writeText(body)
            } catch (_: Exception) {
            }
        }
    }

    fun clearApiError() {
        _apiError.value = null
    }

    // ─── Connection test ────────────────────────────────
    fun testConnection(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestStatus.value = "Conectando con $baseUrl ..."
            try {
                delay(300)
                _connectionTestStatus.value = "Autenticando con la API Key..."
                delay(200)
                _connectionTestStatus.value = "Enviando solicitud de prueba al modelo $model..."
                val result = repository.testConnection(baseUrl, apiKey, model)
                _connectionTestStatus.value = if (result.isSuccess) {
                    "✓ ${result.getOrThrow()}"
                } else {
                    "✗ ${result.exceptionOrNull()?.message}"
                }
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun clearConnectionTest() {
        _connectionTestStatus.value = null
    }

    // ─── Gestión de sesiones ────────────────────────────
    fun startNewSession(type: String) {
        viewModelScope.launch {
            val title = if (type == "Chat") "Nuevo Chat" else "Nuevo Agente"
            val id = repository.createSession(title, _selectedModel.value, type)
            if (type == "Chat") {
                chatSessionId = id
                _chatMessages.value = emptyList()
                _currentTab.value = "Chat"
            } else {
                agentSessionId = id
                _agentMessages.value = emptyList()
                _thinkingSteps.value = emptyList()
                _currentTab.value = "Agente"
            }
            clearPendingFiles()
            closeHistoryMenu()
        }
    }

    fun selectChatSession(id: Long) {
        viewModelScope.launch {
            chatSessionId = id
            _chatMessages.value = repository.getMessagesForSessionSync(id)
            _currentTab.value = "Chat"
            closeHistoryMenu()
        }
    }

    fun selectAgentSession(id: Long) {
        viewModelScope.launch {
            agentSessionId = id
            _agentMessages.value = repository.getMessagesForSessionSync(id)
            _currentTab.value = "Agente"
            closeHistoryMenu()
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (chatSessionId == sessionId) {
                chatSessionId = null
                _chatMessages.value = emptyList()
            }
            if (agentSessionId == sessionId) {
                agentSessionId = null
                _agentMessages.value = emptyList()
            }
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    // ─── Sandbox de archivos ────────────────────────────
    private val _sandboxFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val sandboxFiles: StateFlow<List<FileItem>> = _sandboxFiles.asStateFlow()

    fun loadSandboxFiles() {
        viewModelScope.launch {
            _sandboxFiles.value = withContext(Dispatchers.IO) {
                sandboxDir.listFiles()
                    ?.filter { it.isFile }
                    ?.map { f ->
                        FileItem(
                            name = f.name,
                            sizeBytes = f.length(),
                            lastModified = f.lastModified()
                        )
                    }
                    ?.sortedByDescending { it.lastModified }
                    ?: emptyList()
            }
        }
    }

    fun createSandboxFile(name: String, content: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    File(sandboxDir, name).writeText(content)
                } catch (_: Exception) {
                }
            }
            loadSandboxFiles()
        }
    }

    fun deleteSandboxFile(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    File(sandboxDir, name).delete()
                } catch (_: Exception) {
                }
            }
            loadSandboxFiles()
        }
    }

    suspend fun readSandboxFileContent(name: String): String = withContext(Dispatchers.IO) {
        try {
            File(sandboxDir, name).readText()
        } catch (_: Exception) {
            ""
        }
    }

    fun getDownloadUri(fileName: String): Uri? {
        val file = File(sandboxDir, fileName)
        if (!file.exists()) return null
        return try {
            FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Create a ZIP of all sandbox files and return a shareable URI. */
    fun createSandboxZipUri(): Uri? {
        return try {
            val zipFile = File(getApplication<Application>().cacheDir, "sandbox_export.zip")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                sandboxDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.fileprovider",
                zipFile
            )
        } catch (_: Exception) {
            null
        }
    }

    // ─── Python sandbox ─────────────────────────────────
    private val _pendingPythonCode = MutableStateFlow<String?>(null)
    val pendingPythonCode: StateFlow<String?> = _pendingPythonCode.asStateFlow()
    fun clearPendingPython() { _pendingPythonCode.value = null }

    private val _pythonResult = MutableStateFlow("")
    val pythonResult: StateFlow<String> = _pythonResult.asStateFlow()
    fun setPythonResult(r: String) { _pythonResult.value = r }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AppRepository(db.chatDao())
        sessions = repository.getAllSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        chatSessions = repository.getSessionsByType("Chat")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        agentSessions = repository.getSessionsByType("Agente")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        loadSandboxFiles()
    }
}
