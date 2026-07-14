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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ZaiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val prefs = application.getSharedPreferences("groq_prefs", Context.MODE_PRIVATE)
    private val sandboxDir: File by lazy {
        File(application.filesDir, "sandbox").apply { if (!exists()) mkdirs() }
    }

    // ─── Onboarding ─────────────────────────────────────
    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()
    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
        _onboardingCompleted.value = true
    }

    // ─── Configuración ──────────────────────────────────
    val providers = mapOf("Groq" to "https://api.groq.com/openai/v1/", "OpenAI" to "https://api.openai.com/v1/")
    private val _selectedProvider = MutableStateFlow("Groq")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()
    fun setProvider(p: String) {
        _selectedProvider.value = p
        _baseUrl.value = providers[p] ?: _baseUrl.value
    }

    private val _apiKey = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    private val _baseUrl = MutableStateFlow(prefs.getString("base_url", providers["Groq"]!!) ?: providers["Groq"]!!)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()
    private val _selectedModel = MutableStateFlow(prefs.getString("selected_model", "llama-3.1-8b-instant") ?: "llama-3.1-8b-instant")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    fun saveApiKey(k: String) { prefs.edit().putString("api_key", k).apply(); _apiKey.value = k }
    fun saveBaseUrl(u: String) { prefs.edit().putString("base_url", u).apply(); _baseUrl.value = u }
    fun saveSelectedModel(m: String) { prefs.edit().putString("selected_model", m).apply(); _selectedModel.value = m }

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

    val sessions: StateFlow<List<ChatSession>>

    // ─── Archivos adjuntos ──────────────────────────────
    private val _pendingFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val pendingFiles: StateFlow<List<AttachedFile>> = _pendingFiles.asStateFlow()

    fun addPendingFile(file: AttachedFile) {
        _pendingFiles.value = _pendingFiles.value + file
    }
    fun removePendingFile(index: Int) {
        _pendingFiles.value = _pendingFiles.value.toMutableList().apply {
            if (index in indices) removeAt(index)
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
                    val text = getApplication<Application>().contentResolver
                        .openInputStream(file.uri)
                        ?.bufferedReader()
                        ?.readText()
                    if (!text.isNullOrBlank()) {
                        sb.append(text.take(4000))
                        if (text.length > 4000) sb.append("\n[...contenido truncado...]")
                    } else {
                        sb.append("(archivo binario o vacío)")
                    }
                } catch (e: Exception) {
                    sb.append("(error al leer: ${e.message})")
                }
                sb.append("\n")
            }
            sb.toString()
        }
    }

    // ─── Envío de mensajes ──────────────────────────────
    fun sendChatMessage(text: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            _loadingMessage.value = "Enviando a Groq..."
            try {
                val attachments = _pendingFiles.value
                val attachmentContext = buildAttachmentContext(attachments)
                val fullText = text + attachmentContext

                val id = chatSessionId ?: repository.createSession(text.take(20), _selectedModel.value).also {
                    chatSessionId = it
                }
                repository.saveMessage(id, "user", text)

                val apiMessages = mutableListOf(GroqMessage(role = "system", content = "Eres un asistente útil."))
                apiMessages.add(GroqMessage(role = "user", content = fullText))

                val result = repository.getChatCompletion(_baseUrl.value, _apiKey.value, _selectedModel.value, apiMessages)

                if (result.isSuccess) {
                    repository.saveMessage(id, "assistant", result.getOrThrow())
                } else {
                    _apiError.value = result.exceptionOrNull()?.message
                }

                _chatMessages.value = repository.getMessagesForSessionSync(id)
                clearPendingFiles()
            } finally {
                _isGenerating.value = false
                _loadingMessage.value = ""
            }
        }
    }

    fun sendAgentMessage(text: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            _loadingMessage.value = "Agente analizando..."
            _thinkingSteps.value = emptyList()
            try {
                val attachments = _pendingFiles.value
                val attachmentContext = buildAttachmentContext(attachments)
                val fullText = text + attachmentContext

                val id = agentSessionId ?: repository.createSession(text.take(20), _selectedModel.value).also {
                    agentSessionId = it
                }
                repository.saveMessage(id, "user", text)

                addThinkingStep("Analizando la solicitud...")
                if (attachments.isNotEmpty()) addThinkingStep("Leyendo archivos adjuntos...")
                addThinkingStep("Consultando al modelo...")

                val apiMessages = mutableListOf(GroqMessage(role = "system", content = "Eres un agente autónomo."))
                apiMessages.add(GroqMessage(role = "user", content = fullText))

                val result = repository.getChatCompletion(_baseUrl.value, _apiKey.value, _selectedModel.value, apiMessages)

                if (result.isSuccess) {
                    addThinkingStep("Generando respuesta...")
                    repository.saveMessage(id, "assistant", result.getOrThrow())
                } else {
                    _apiError.value = result.exceptionOrNull()?.message
                }

                _agentMessages.value = repository.getMessagesForSessionSync(id)
                clearPendingFiles()
            } finally {
                _isGenerating.value = false
                _loadingMessage.value = ""
            }
        }
    }

    private fun addThinkingStep(step: String) {
        _thinkingSteps.value = _thinkingSteps.value + step
    }

    fun clearApiError() { _apiError.value = null }

    // ─── Gestión de sesiones ────────────────────────────
    fun startNewSession(type: String) {
        viewModelScope.launch {
            val title = "Nueva sesión $type - ${System.currentTimeMillis() % 1000}"
            val id = repository.createSession(title, _selectedModel.value)
            if (type == "Chat") {
                chatSessionId = id
                _chatMessages.value = repository.getMessagesForSessionSync(id)
            } else {
                agentSessionId = id
                _agentMessages.value = repository.getMessagesForSessionSync(id)
                _thinkingSteps.value = emptyList()
            }
            clearPendingFiles()
        }
    }

    fun selectChatSession(id: Long) {
        viewModelScope.launch {
            chatSessionId = id
            _chatMessages.value = repository.getMessagesForSessionSync(id)
        }
    }

    fun selectAgentSession(id: Long) {
        viewModelScope.launch {
            agentSessionId = id
            _agentMessages.value = repository.getMessagesForSessionSync(id)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
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
                    ?.map { f -> FileItem(name = f.name, sizeBytes = f.length(), lastModified = f.lastModified()) }
                    ?.sortedByDescending { it.lastModified }
                    ?: emptyList()
            }
        }
    }

    fun createSandboxFile(name: String, content: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { try { File(sandboxDir, name).writeText(content) } catch (_: Exception) {} }
            loadSandboxFiles()
        }
    }

    fun deleteSandboxFile(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { try { File(sandboxDir, name).delete() } catch (_: Exception) {} }
            loadSandboxFiles()
        }
    }

    suspend fun readSandboxFileContent(name: String): String = withContext(Dispatchers.IO) {
        try { File(sandboxDir, name).readText() } catch (_: Exception) { "" }
    }

    fun getDownloadUri(fileName: String): Uri? {
        val file = File(sandboxDir, fileName)
        if (!file.exists()) return null
        return try {
            FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.fileprovider", file)
        } catch (e: Exception) { null }
    }

    // ─── Python sandbox ─────────────────────────────────
    private var pythonCallback: ((String) -> Unit)? = null

    fun executePython(code: String, callback: (String) -> Unit) {
        pythonCallback = callback
    }

    fun onPythonResult(result: String) {
        pythonCallback?.invoke(result)
        pythonCallback = null
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AppRepository(db.chatDao())
        sessions = repository.getAllSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        loadSandboxFiles()
    }
}
