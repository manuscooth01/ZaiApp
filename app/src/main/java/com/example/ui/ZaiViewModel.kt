package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
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
import java.io.FileOutputStream
import java.util.zip.ZipOutputStream

class ZaiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val prefs = application.getSharedPreferences("groq_prefs", Context.MODE_PRIVATE)
    private val context = application

    val sandboxDir = File(application.filesDir, "sandbox").apply {
        if (!exists()) mkdirs()
    }
    val workspaceDir = File(application.filesDir, "workspace").apply {
        if (!exists()) mkdirs()
    }

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()
    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
        _onboardingCompleted.value = true
    }

    val providers = mapOf(
        "Groq" to "https://api.groq.com/openai/v1/",
        "OpenAI" to "https://api.openai.com/v1/",
        "Ollama (local)" to "http://localhost:11434/v1/"
    )
    private val _selectedProvider = MutableStateFlow("Groq")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()
    fun setProvider(provider: String) {
        _selectedProvider.value = provider
        _baseUrl.value = providers[provider] ?: _baseUrl.value
    }

    private val defaultApiKey = "YOUR_GROQ_API_KEY_HERE"
    private val defaultBaseUrl = providers["Groq"]!!
    private val _apiKey = MutableStateFlow(prefs.getString("api_key", defaultApiKey) ?: defaultApiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    private val _baseUrl = MutableStateFlow(prefs.getString("base_url", defaultBaseUrl) ?: defaultBaseUrl)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()
    private val _selectedModel = MutableStateFlow(prefs.getString("selected_model", "llama-3.1-8b-instant") ?: "llama-3.1-8b-instant")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    val availableModels = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "gemma2-9b-it",
        "deepseek-r1-distill-llama-70b",
        "llama-3.2-90b-vision-preview"
    )

    fun saveApiKey(key: String) { prefs.edit().putString("api_key", key).apply(); _apiKey.value = key }
    fun saveBaseUrl(url: String) { prefs.edit().putString("base_url", url).apply(); _baseUrl.value = url }
    fun saveSelectedModel(model: String) { prefs.edit().putString("selected_model", model).apply(); _selectedModel.value = model }

    private val _chatSessionId = MutableStateFlow<Long?>(null)
    val chatSessionId: StateFlow<Long?> = _chatSessionId.asStateFlow()
    private val _agentSessionId = MutableStateFlow<Long?>(null)
    val agentSessionId: StateFlow<Long?> = _agentSessionId.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    private val _agentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val agentMessages: StateFlow<List<ChatMessage>> = _agentMessages.asStateFlow()

    val sessions: StateFlow<List<ChatSession>>

    private val _sandboxFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val sandboxFiles: StateFlow<List<FileItem>> = _sandboxFiles.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    private val _loadingMessage = MutableStateFlow("")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()
    private val _thinkingSteps = MutableStateFlow<List<String>>(emptyList())
    val thinkingSteps: StateFlow<List<String>> = _thinkingSteps.asStateFlow()
    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("theme", "dark") ?: "dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    fun setTheme(mode: String) { prefs.edit().putString("theme", mode).apply(); _themeMode.value = mode }

    private val _pendingFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val pendingFiles: StateFlow<List<AttachedFile>> = _pendingFiles.asStateFlow()
    fun addPendingFile(file: AttachedFile) { _pendingFiles.value = _pendingFiles.value + file }
    fun removePendingFile(index: Int) { _pendingFiles.value = _pendingFiles.value.toMutableList().also { it.removeAt(index) } }
    fun clearPendingFiles() { _pendingFiles.value = emptyList() }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database.chatDao())

        sessions = repository.getAllSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            _chatSessionId.collect { sid ->
                if (sid != null) repository.getMessagesForSession(sid).collect { _chatMessages.value = it }
                else _chatMessages.value = emptyList()
            }
        }
        viewModelScope.launch {
            _agentSessionId.collect { sid ->
                if (sid != null) repository.getMessagesForSession(sid).collect { _agentMessages.value = it }
                else _agentMessages.value = emptyList()
            }
        }

        loadSandboxFiles()
    }

    fun selectChatSession(id: Long) { _chatSessionId.value = id; _apiError.value = null }
    fun selectAgentSession(id: Long) { _agentSessionId.value = id; _apiError.value = null }

    fun startNewSession(type: String) {
        viewModelScope.launch {
            val title = "Nueva sesión $type - ${System.currentTimeMillis() % 1000}"
            val id = repository.createSession(title, _selectedModel.value)
            if (type == "Chat") selectChatSession(id) else selectAgentSession(id)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }
    fun renameSession(sessionId: Long, title: String) {
        viewModelScope.launch { repository.updateSessionTitle(sessionId, title) }
    }

    // ─── Mensajes ───────────────────────────────────────
    fun sendChatMessage(text: String) {
        val sid = _chatSessionId.value
        viewModelScope.launch {
            val sessionId = sid ?: run {
                val id = repository.createSession(text.take(20), _selectedModel.value)
                _chatSessionId.value = id; id
            }
            repository.saveMessage(sessionId, "user", text)
            val fileContents = _pendingFiles.value.mapNotNull { readAttachedFile(it) }
            val fullPrompt = if (fileContents.isNotEmpty()) "$text\n\n[Archivos adjuntos]:\n${fileContents.joinToString("\n")}" else text
            _pendingFiles.value = emptyList()
            _isGenerating.value = true; _loadingMessage.value = "Enviando a Groq..."
            val messages = buildApiMessages(sessionId, fullPrompt, isAgent = false)
            val result = repository.getChatCompletion(_baseUrl.value, _apiKey.value, _selectedModel.value, messages)
            result.fold(
                onSuccess = { repository.saveMessage(sessionId, "assistant", it) },
                onFailure = { _apiError.value = it.message; repository.saveMessage(sessionId, "assistant", "Error: ${it.message}") }
            )
            _isGenerating.value = false
        }
    }

    fun sendAgentMessage(text: String) {
        val sid = _agentSessionId.value
        viewModelScope.launch {
            val sessionId = sid ?: run {
                val id = repository.createSession(text.take(20), _selectedModel.value)
                _agentSessionId.value = id; id
            }
            repository.saveMessage(sessionId, "user", text)
            val fileContents = _pendingFiles.value.mapNotNull { readAttachedFile(it) }
            val fullPrompt = if (fileContents.isNotEmpty()) "$text\n\n[Archivos adjuntos]:\n${fileContents.joinToString("\n")}" else text
            _pendingFiles.value = emptyList()
            _isGenerating.value = true; _loadingMessage.value = "Agente analizando..."
            val steps = mutableListOf("Analizando solicitud...", "Cargando archivos...")
            _thinkingSteps.value = steps.toList()
            val referenced = _sandboxFiles.value.filter { text.contains(it.name, true) }
            var ctx = ""
            for (f in referenced) {
                val c = readSandboxFileContent(f.name) ?: ""
                ctx += "\n--- ${f.name} ---\n$c"
            }
            steps.add("Construyendo contexto...")
            _thinkingSteps.value = steps.toList()
            val messages = buildApiMessages(sessionId, fullPrompt + ctx, isAgent = true)
            steps.add("Consultando Groq...")
            _thinkingSteps.value = steps.toList()
            val result = repository.getChatCompletion(_baseUrl.value, _apiKey.value, _selectedModel.value, messages)
            result.fold(
                onSuccess = {
                    repository.saveMessage(sessionId, "assistant", it, thinkingSteps = steps.joinToString("\n"))
                    if (it.contains("```python")) {
                        val code = extractPythonCode(it)
                        executePython(code) { output ->
                            repository.saveMessage(sessionId, "assistant", "\n[Resultado Python]:\n$output", thinkingSteps = null)
                        }
                    }
                },
                onFailure = { repository.saveMessage(sessionId, "assistant", "Error: ${it.message}") }
            )
            _isGenerating.value = false
        }
    }

    private suspend fun buildApiMessages(sessionId: Long, userText: String, isAgent: Boolean): List<GroqMessage> {
        val dbMessages = repository.getMessagesForSessionSync(sessionId)
        val apiMessages = mutableListOf<GroqMessage>()
        val sys = if (isAgent) "Eres un agente autónomo. Responde en español." else "Eres un asistente útil. Responde en español."
        apiMessages.add(GroqMessage(role = "system", content = sys))
        dbMessages.takeLast(10).forEach { apiMessages.add(GroqMessage(role = it.role, content = it.content)) }
        apiMessages.add(GroqMessage(role = "user", content = userText))
        return apiMessages
    }

    // Python sandbox
    fun executePython(code: String, callback: (String) -> Unit) {
        _pendingPythonCode = code
        _pythonCallback = callback
    }
    private var _pendingPythonCode: String? = null
    private var _pythonCallback: ((String) -> Unit)? = null
    fun consumePythonExecution(): Pair<String, (String) -> Unit>? {
        val code = _pendingPythonCode ?: return null
        val cb = _pythonCallback ?: return null
        _pendingPythonCode = null; _pythonCallback = null
        return code to cb
    }

    private fun extractPythonCode(text: String): String {
        val start = text.indexOf("```python")
        if (start == -1) return ""
        val end = text.indexOf("```", start + 9)
        if (end == -1) return text.substring(start + 9)
        return text.substring(start + 9, end).trim()
    }

    private fun readAttachedFile(file: AttachedFile): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(file.uri) ?: return null
            inputStream.bufferedReader().readText()
        } catch (e: Exception) { null }
    }

    // Workspace
    fun loadSandboxFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = sandboxDir.listFiles()?.map { FileItem(it.name, it.length(), it.lastModified()) }?.sortedByDescending { it.lastModified } ?: emptyList()
            _sandboxFiles.value = files
        }
    }

    fun createSandboxFile(name: String, content: String): Boolean {
        return try { File(sandboxDir, name).writeText(content); loadSandboxFiles(); true } catch (e: Exception) { false }
    }
    fun deleteSandboxFile(name: String): Boolean {
        return try { File(sandboxDir, name).delete().also { loadSandboxFiles() } } catch (e: Exception) { false }
    }
    fun readSandboxFileContent(name: String): String? {
        return try { File(sandboxDir, name).let { if (it.exists()) it.readText() else null } } catch (e: Exception) { null }
    }

    fun getDownloadUri(fileName: String): Uri? {
        val file = File(sandboxDir, fileName)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun createZipOfWorkspace(): File? {
        val zipFile = File(context.cacheDir, "workspace.zip")
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                sandboxDir.listFiles()?.forEach { file ->
                    zos.putNextEntry(java.util.zip.ZipEntry(file.name))
                    file.inputStream().copyTo(zos)
                    zos.closeEntry()
                }
            }
            return zipFile
        } catch (e: Exception) { return null }
    }

    fun clearApiError() { _apiError.value = null }
}

data class FileItem(val name: String, val sizeBytes: Long, val lastModified: Long)
data class AttachedFile(val uri: Uri, val name: String, val type: String)
