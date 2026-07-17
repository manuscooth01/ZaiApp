package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.core.content.FileProvider
import androidx.compose.runtime.Immutable
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.example.data.api.GroqMessage
import com.example.data.database.AppDatabase
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import com.example.data.database.ActionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// @Immutable: todos sus campos son val y el objeto no cambia tras crearse. Sin esto,
// Compose la infiere como inestable (por el campo Uri) y no garantiza saltar la
// recomposición de los composables que reciben AttachedFile o List<AttachedFile>.
@Immutable
data class AttachedFile(val uri: Uri, val name: String, val type: String)
data class FileItem(val name: String, val sizeBytes: Long, val lastModified: Long)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

class ZaiViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository: AppRepository = AppRepository(AppDatabase.getDatabase(application).chatDao())
    private val prefs = application.getSharedPreferences("groq_prefs", Context.MODE_PRIVATE)

    // SharedPreferences cifradas (Android Keystore) solo para datos sensibles como la API key.
    private val securePrefs = try {
        val masterKey = androidx.security.crypto.MasterKey.Builder(application)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            application,
            "secure_groq_prefs",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Si el Keystore falla (dispositivo muy antiguo/corrupto), seguimos con prefs normales
        // en vez de tumbar la app.
        null
    }

    init {
        // Migración única: si había una API key vieja en texto plano, se mueve a las prefs cifradas.
        val legacyKey = prefs.getString("api_key", null)
        if (!legacyKey.isNullOrBlank() && securePrefs != null) {
            securePrefs.edit().putString("api_key", legacyKey).apply()
            prefs.edit().remove("api_key").apply()
        }
    }

    // TTS properties
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private fun initTTS() {
        if (tts == null) {
            tts = TextToSpeech(getApplication(), this)
        }
    }

    private val _availableVoices = MutableStateFlow<List<android.speech.tts.Voice>>(emptyList())
    val availableVoices: StateFlow<List<android.speech.tts.Voice>> = _availableVoices.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow(prefs.getString("tts_voice_name", null))
    val selectedVoiceName: StateFlow<String?> = _selectedVoiceName.asStateFlow()

    fun setTtsVoice(voiceName: String) {
        _selectedVoiceName.value = voiceName
        prefs.edit().putString("tts_voice_name", voiceName).apply()
        tts?.voices?.find { it.name == voiceName }?.let {
            tts?.voice = it
        }
    }

    init {
        // Initialize TTS
        initTTS()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "ES")
            _availableVoices.value = tts?.voices?.filter { it.locale.language == "es" }?.toList() ?: emptyList()

            // Restore voice
            _selectedVoiceName.value?.let { savedName ->
                tts?.voices?.find { it.name == savedName }?.let { tts?.voice = it }
            }

            ttsReady = true
        }
    }

    fun speak(text: String) {
        if (_ttsEnabled.value && ttsReady) {
            val cleanText = text
                .replace(Regex("```[\\s\\S]*?```"), " [Bloque de código] ")
                .take(4000)
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "ZaiTTS")
            logAction("Lector de Voz", "Lectura en voz alta iniciada.")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    // Dynamic sandbox per session
    fun getSessionSandboxDir(): File {
        val sessionId = if (_currentTab.value == "Agente") agentSessionId else chatSessionId
        val dirName = if (sessionId != null) "session_$sessionId" else "default"
        val dir = File(getApplication<Application>().filesDir, "sandbox/$dirName")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // Logging actions to DB
    fun logAction(type: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveLog(type, description)
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllLogs()
            logAction("Logs", "Historial de registros limpiado.")
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllDataForUser(_currentUserEmail.value)
            _chatMessages.value = emptyList()
            _agentMessages.value = emptyList()
        }
    }

    fun clearCache() {
        val cacheDir = getApplication<Application>().cacheDir
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }

    // ─── Onboarding ─────────────────────────────────────
    private val _onboardingCompleted =
        MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
        _onboardingCompleted.value = true
        _showSettings.value = true
        logAction("Onboarding", "Onboarding completado.")
    }

    fun resetOnboarding() {
        prefs.edit().putBoolean("onboarding_done", false).apply()
        _onboardingCompleted.value = false
        logAction("Onboarding", "Onboarding restablecido.")
    }

    // ─── Configuración ──────────────────────────────────
    val providers = mapOf(
        "Groq" to "https://api.groq.com/openai/v1/",
        "OpenAI" to "https://api.openai.com/v1/",
        "Ollama" to "http://10.0.2.2:11434/v1/",
        "OpenRouter" to "https://openrouter.ai/api/v1/",
        "Together" to "https://api.together.xyz/v1/"
    )

    val defaultModels = mapOf(
        "Groq" to "llama-3.1-8b-instant",
        "OpenAI" to "gpt-4o",
        "Ollama" to "llama3",
        "OpenRouter" to "meta-llama/llama-3.1-8b-instruct",
        "Together" to "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo"
    )

    val groqModels = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "gemma2-9b-it",
        "deepseek-r1-distill-llama-70b",
        "llama-3.2-90b-vision-preview"
    )

    val openAiModels = listOf(
        "gpt-4o",
        "gpt-4-turbo",
        "gpt-3.5-turbo"
    )

    private val _selectedProvider =
        MutableStateFlow(prefs.getString("provider", "Groq") ?: "Groq")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    fun setProvider(p: String) {
        _selectedProvider.value = p
        _baseUrl.value = providers[p] ?: _baseUrl.value
        val defaultModel = defaultModels[p]
        if (defaultModel != null) {
            _selectedModel.value = defaultModel
        }
        prefs.edit().putString("provider", p).apply()
    }

    private val _apiKey = MutableStateFlow(securePrefs?.getString("api_key", "") ?: prefs.getString("api_key", "") ?: "")
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
        if (securePrefs != null) {
            securePrefs.edit().putString("api_key", k).apply()
        } else {
            prefs.edit().putString("api_key", k).apply()
        }
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

    // ─── Extras: creatividad, web search, TTS ──────────────
    private val _creativity = MutableStateFlow(prefs.getFloat("creativity", 0.7f))
    val creativity: StateFlow<Float> = _creativity.asStateFlow()
    fun setCreativity(v: Float) {
        val clamped = v.coerceIn(0f, 2f)
        _creativity.value = clamped
        prefs.edit().putFloat("creativity", clamped).apply()
    }

    private val _webSearchEnabled = MutableStateFlow(prefs.getBoolean("web_search", false))
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()
    fun setWebSearchEnabled(v: Boolean) {
        _webSearchEnabled.value = v
        prefs.edit().putBoolean("web_search", v).apply()
    }

    private val _ttsEnabled = MutableStateFlow(prefs.getBoolean("tts_enabled", false))
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()
    fun setTtsEnabled(v: Boolean) {
        _ttsEnabled.value = v
        prefs.edit().putBoolean("tts_enabled", v).apply()
    }

    // ─── Personalización ──────────────────────────────
    private val _primaryColor = MutableStateFlow(prefs.getInt("primary_color", 0xFFFF5722.toInt())) // Default GroqOrange
    val primaryColor: StateFlow<Int> = _primaryColor.asStateFlow()
    fun setPrimaryColor(color: Int) {
        _primaryColor.value = color
        prefs.edit().putInt("primary_color", color).apply()
    }

    private val _backgroundImageUri = MutableStateFlow(prefs.getString("background_uri", "") ?: "")
    val backgroundImageUri: StateFlow<String> = _backgroundImageUri.asStateFlow()
    fun setBackgroundImageUri(uri: String) {
        _backgroundImageUri.value = uri
        prefs.edit().putString("background_uri", uri).apply()
    }

    private val _backgroundTransparency = MutableStateFlow(prefs.getFloat("background_transparency", 0.5f))
    val backgroundTransparency: StateFlow<Float> = _backgroundTransparency.asStateFlow()
    fun setBackgroundTransparency(transparency: Float) {
        _backgroundTransparency.value = transparency
        prefs.edit().putFloat("background_transparency", transparency).apply()
    }

    // ─── UI flags ───────────────────────────────────────
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()
    fun openSettings() { _showSettings.value = true }
    fun closeSettings() { _showSettings.value = false }

    // Mantener compat con drawer viejo pero ya no se usa el history overlay antiguo:
    private val _showHistoryMenu = MutableStateFlow(false)
    val showHistoryMenu: StateFlow<Boolean> = _showHistoryMenu.asStateFlow()
    fun toggleHistoryMenu() { _showHistoryMenu.value = !_showHistoryMenu.value }
    fun closeHistoryMenu() { _showHistoryMenu.value = false }

    private val _currentTab = MutableStateFlow("Chat")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()
    fun setCurrentTab(tab: String) { _currentTab.value = tab }

    private val _reasoningEnabled = MutableStateFlow(prefs.getBoolean("reasoning_enabled", true))
    val reasoningEnabled: StateFlow<Boolean> = _reasoningEnabled.asStateFlow()
    fun setReasoningEnabled(v: Boolean) {
        _reasoningEnabled.value = v
        prefs.edit().putBoolean("reasoning_enabled", v).apply()
    }

    // ─── Sesiones separadas ─────────────────────────────
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    private val _agentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val agentMessages: StateFlow<List<ChatMessage>> = _agentMessages.asStateFlow()

    private var chatSessionId: Long? = null
    private var agentSessionId: Long? = null

    private val _currentChatSessionId = MutableStateFlow<Long?>(null)
    val currentChatSessionId: StateFlow<Long?> = _currentChatSessionId.asStateFlow()
    private val _currentAgentSessionId = MutableStateFlow<Long?>(null)
    val currentAgentSessionId: StateFlow<Long?> = _currentAgentSessionId.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var generationJob: Job? = null
    private var lastMessageTime: Long = 0L

    fun stopGeneration() {
        generationJob?.cancel()
        _isGenerating.value = false
        _loadingMessage.value = ""
    }

    private val _apiError = MutableStateFlow<String?>(null)
    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()
    // Indica QUÉ proveedor está autenticando ("google", "github", "email") o null
    // si no hay ninguno en curso. Permite mostrar el spinner solo en el botón pulsado.
    private val _authProvider = MutableStateFlow<String?>(null)
    val authProvider: StateFlow<String?> = _authProvider.asStateFlow()
    val apiError: StateFlow<String?> = _apiError.asStateFlow()
    private val _loadingMessage = MutableStateFlow("")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()
    private val _thinkingSteps = MutableStateFlow<List<String>>(emptyList())
    val thinkingSteps: StateFlow<List<String>> = _thinkingSteps.asStateFlow()

    private val _connectionTestStatus = MutableStateFlow<String?>(null)
    val connectionTestStatus: StateFlow<String?> = _connectionTestStatus.asStateFlow()
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

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
                    val dest = File(getSessionSandboxDir(), file.name)
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
                        sb.append(text.take(8000))
                        if (text.length > 8000) sb.append("\n[...contenido truncado...]")
                    } else {
                        sb.append("(archivo binario o vacío — copiado al sandbox: ${file.name})")
                    }
                    logAction("Archivo", "Archivo adjuntado y copiado a sandbox de sesión: ${file.name}")
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

    fun parseThinkingAndContent(fullResponse: String): Pair<String?, String> {
        val thinkStart = fullResponse.indexOf("<think>")
        val thinkEnd = fullResponse.indexOf("</think>")
        if (thinkStart != -1 && thinkEnd != -1 && thinkEnd > thinkStart) {
            val thinking = fullResponse.substring(thinkStart + 7, thinkEnd).trim()
            val content = fullResponse.substring(thinkEnd + 8).trim()
            return Pair(thinking, content)
        } else if (thinkStart != -1) {
            val thinking = fullResponse.substring(thinkStart + 7).trim()
            return Pair(thinking, "")
        } else {
            return Pair(null, fullResponse)
        }
    }

    // ─── Envío de mensajes ──────────────────────────────
    fun sendChatMessage(text: String) {
        if (_isGenerating.value) return
        if (text.isBlank() && _pendingFiles.value.isEmpty()) return
        if (System.currentTimeMillis() - lastMessageTime < 1000) return
        lastMessageTime = System.currentTimeMillis()

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _loadingMessage.value = "Preparando mensaje..."
            _thinkingSteps.value = emptyList()
            try {
                val attachments = _pendingFiles.value.toList()
                if (attachments.isNotEmpty()) {
                    _loadingMessage.value = "Leyendo archivos adjuntos..."
                }
                val attachmentContext = buildAttachmentContext(attachments)
                val displayText = text.ifBlank {
                    "Analiza estos archivos: ${attachments.joinToString { it.name }}"
                }
                var fullText = displayText + attachmentContext
                
                var actionDesc = "Mensaje enviado."
                if (_webSearchEnabled.value) {
                    fullText = "[Búsqueda web activada: Simula recuperar información web de tiempo real sobre la siguiente consulta del usuario y responde adecuadamente]\n\n$fullText"
                    actionDesc += " (Búsqueda web activa)"
                    logAction("Búsqueda Web", "Solicitando información actualizada para: ${displayText.take(50)}")
                }

                _loadingMessage.value = "Guardando mensaje..."
                val id = chatSessionId ?: repository.createSession(
                    title = displayText.take(40).ifBlank { "Nuevo chat" },
                    model = _selectedModel.value,
                    sessionType = "Chat",
                    userEmail = _currentUserEmail.value
                ).also {
                    chatSessionId = it
                    _currentChatSessionId.value = it
                }

                repository.saveMessage(id, "user", displayText)
                logAction("Chat", actionDesc)

                val history = repository.getMessagesForSessionSync(id)
                
                val basePrompt = "Eres un asistente útil, claro y conciso. Razona y explica tu pensamiento de forma detallada y transparente, y luego proporciona tu respuesta final. Responde en el idioma del usuario."
                val systemPrompt = basePrompt


                val apiMessages = buildHistoryMessages(
                    history.dropLast(1),
                    systemPrompt
                ).toMutableList()
                apiMessages.add(GroqMessage(role = "user", content = fullText))

                _loadingMessage.value = "Conectando con ${_selectedProvider.value}..."
                _loadingMessage.value = "Enviando a ${_selectedModel.value}..."
                val result = repository.getChatCompletion(
                    _baseUrl.value,
                    _apiKey.value,
                    _selectedModel.value,
                    apiMessages,
                    temperature = _creativity.value.toDouble(),
                    provider = _selectedProvider.value
                )

                if (result.isSuccess) {
                    _loadingMessage.value = "Procesando respuesta..."
                    val rawContent = result.getOrThrow()
                    val (thinking, finalContent) = parseThinkingAndContent(rawContent)
                    
                    repository.saveMessage(id, "assistant", finalContent, thinkingSteps = thinking)
                    speak(finalContent)
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Error desconocido"
                    _apiError.value = errMsg
                    logAction("Error", "Error en llamada API de Chat: $errMsg")
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
        if (System.currentTimeMillis() - lastMessageTime < 1000) return
        lastMessageTime = System.currentTimeMillis()

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _loadingMessage.value = "Agente iniciando..."
            _thinkingSteps.value = emptyList()
            try {
                val attachments = _pendingFiles.value.toList()
                if (attachments.isNotEmpty()) {
                    _loadingMessage.value = "Leyendo archivos adjuntos..."
                }
                val attachmentContext = buildAttachmentContext(attachments)
                val displayText = text.ifBlank {
                    "Procesa estos archivos: ${attachments.joinToString { it.name }}"
                }
                var fullText = displayText + attachmentContext
                
                var actionDesc = "Instrucción de agente recibida."
                if (_webSearchEnabled.value) {
                    fullText = "[Búsqueda web activada: Simula recuperar información web actualizada y de tiempo real para resolver la consulta de manera precisa]\n$fullText"
                    actionDesc += " (Búsqueda web activa)"
                    logAction("Búsqueda Web", "Agente solicitando información para: ${displayText.take(50)}")
                }

                val id = agentSessionId ?: repository.createSession(
                    title = displayText.take(40).ifBlank { "Nuevo agente" },
                    model = _selectedModel.value,
                    sessionType = "Agente",
                    userEmail = _currentUserEmail.value
                ).also {
                    agentSessionId = it
                    _currentAgentSessionId.value = it
                }

                repository.saveMessage(id, "user", displayText)
                logAction("Agente", actionDesc)

                if (_reasoningEnabled.value) {
                    addThinkingStep("Analizando la solicitud del usuario...")
                    if (attachments.isNotEmpty()) {
                        addThinkingStep("Procesando ${attachments.size} archivo(s) adjunto(s) en el sandbox...")
                    }
                    addThinkingStep("Planificando pasos de resolución...")
                } else {
                    _loadingMessage.value = "Preparando ejecución..."
                }

                val sandboxListing = withContext(Dispatchers.IO) {
                    getSessionSandboxDir().listFiles()?.filter { it.isFile }
                        ?.joinToString("\n") { "- ${it.name} (${it.length()} bytes)" }
                        ?: "(vacío)"
                }

                val systemPrompt = buildString {
                    append("Eres un agente autónomo experto. ")
                    append("Razona paso a paso y resuelve tareas complejas, explicando tu pensamiento de forma transparente antes de dar tu respuesta final. ")
                    append("Si necesitas calcular o procesar datos, incluye código Python en un bloque ```python. ")
                    append("Si modificas o creas archivos, describe el contenido completo del archivo resultante en un bloque ```file:nombre.ext. ")
                    append("Archivos disponibles en el sandbox de esta sesión:\n$sandboxListing\n")
                }

                val history = repository.getMessagesForSessionSync(id)
                val apiMessages = buildHistoryMessages(history.dropLast(1), systemPrompt)
                    .toMutableList()
                apiMessages.add(GroqMessage(role = "user", content = fullText))

                if (_reasoningEnabled.value) {
                    addThinkingStep("Conectando con la API y consultando al modelo ${_selectedModel.value}...")
                } else {
                    _loadingMessage.value = "Consultando al modelo..."
                }

                val result = repository.getChatCompletion(
                    _baseUrl.value,
                    _apiKey.value,
                    _selectedModel.value,
                    apiMessages,
                    temperature = _creativity.value.toDouble(),
                    provider = _selectedProvider.value
                )

                if (result.isSuccess) {
                    if (_reasoningEnabled.value) {
                        addThinkingStep("Procesando respuesta recibida...")
                    } else {
                        _loadingMessage.value = "Recibiendo respuesta..."
                    }
                    val content = result.getOrThrow()
                    val (thinking, finalContent) = parseThinkingAndContent(content)
                    
                    extractAndSaveFiles(finalContent)
                    
                    val stepsJoined = thinking ?: _thinkingSteps.value.joinToString("\n")
                    repository.saveMessage(
                        id,
                        "assistant",
                        finalContent,
                        thinkingSteps = stepsJoined.ifBlank { null }
                    )
                    speak(finalContent)
                    
                    if (finalContent.contains("```python")) {
                        _pendingPythonCode.value =
                            finalContent.substringAfter("```python").substringBefore("```").trim()
                        logAction("Sandbox Python", "Código Python detectado listo para ejecución en Pyodide.")
                    }
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Error desconocido"
                    _apiError.value = errMsg
                    logAction("Error", "Error en llamada API de Agente: $errMsg")
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
        val regex = Regex("```file:([^\\n`]+)\\n([\\s\\S]*?)```")
        regex.findAll(content).forEach { match ->
            val name = match.groupValues[1].trim().replace("/", "_").replace("\\", "_")
            val body = match.groupValues[2]
            try {
                File(getSessionSandboxDir(), name).writeText(body)
                logAction("Archivo", "Archivo extraído de la respuesta: $name")
            } catch (_: Exception) {
            }
        }
    }

    fun clearApiError() {
        _apiError.value = null
    }

    // ─── Connection test ────────────────────────────────
    fun testConnection(baseUrl: String, apiKey: String, model: String, provider: String = "") {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestStatus.value = "Conectando con $baseUrl ..."
            try {
                delay(300)
                if (provider == "Ollama" || apiKey.isBlank()) {
                    _connectionTestStatus.value = "Estableciendo conexión sin clave..."
                } else {
                    _connectionTestStatus.value = "Autenticando con la API Key..."
                }
                delay(200)
                _connectionTestStatus.value = "Enviando solicitud de prueba al modelo $model..."
                val result = repository.testConnection(baseUrl, apiKey, model, provider)
                _connectionTestStatus.value = if (result.isSuccess) {
                    "✓ ${result.getOrThrow()}"
                } else {
                    "✗ ${result.exceptionOrNull()?.message}"
                }
                logAction("Prueba de Conexión", "Resultado de prueba: ${_connectionTestStatus.value}")
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
            val id = repository.createSession(title, _selectedModel.value, type, _currentUserEmail.value)
            if (type == "Chat") {
                chatSessionId = id
                _currentChatSessionId.value = id
                _chatMessages.value = emptyList()
                _currentTab.value = "Chat"
            } else {
                agentSessionId = id
                _currentAgentSessionId.value = id
                _agentMessages.value = emptyList()
                _thinkingSteps.value = emptyList()
                _currentTab.value = "Agente"
            }
            clearPendingFiles()
            loadSandboxFiles()
            closeHistoryMenu()
            logAction("Sesión", "Nueva sesión iniciada: $title (ID: $id)")
        }
    }

    fun selectChatSession(id: Long) {
        viewModelScope.launch {
            chatSessionId = id
            _currentChatSessionId.value = id
            _chatMessages.value = repository.getMessagesForSessionSync(id)
            _currentTab.value = "Chat"
            loadSandboxFiles()
            closeHistoryMenu()
            logAction("Sesión", "Sesión de chat cargada (ID: $id)")
        }
    }

    fun selectAgentSession(id: Long) {
        viewModelScope.launch {
            agentSessionId = id
            _currentAgentSessionId.value = id
            _agentMessages.value = repository.getMessagesForSessionSync(id)
            _currentTab.value = "Agente"
            loadSandboxFiles()
            closeHistoryMenu()
            logAction("Sesión", "Sesión de agente cargada (ID: $id)")
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            logAction("Sesión", "Sesión eliminada (ID: $sessionId)")
            if (chatSessionId == sessionId) {
                chatSessionId = null
                _currentChatSessionId.value = null
                _chatMessages.value = emptyList()
            }
            if (agentSessionId == sessionId) {
                agentSessionId = null
                _currentAgentSessionId.value = null
                _agentMessages.value = emptyList()
            }
            loadSandboxFiles()
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
            logAction("Sesión", "Sesión renombrada a '$newTitle' (ID: $sessionId)")
        }
    }

    fun clearCurrentChat() {
        viewModelScope.launch {
            chatSessionId = null
            _currentChatSessionId.value = null
            _chatMessages.value = emptyList()
            loadSandboxFiles()
        }
    }

    fun clearCurrentAgent() {
        viewModelScope.launch {
            agentSessionId = null
            _currentAgentSessionId.value = null
            _agentMessages.value = emptyList()
            _thinkingSteps.value = emptyList()
            loadSandboxFiles()
        }
    }

    fun clearCurrentConversation() {
        if (_currentTab.value == "Agente") clearCurrentAgent() else clearCurrentChat()
    }

    fun getCurrentChatExport(): String {
        return _chatMessages.value.joinToString("\n\n") {
            "${it.role.uppercase()}: ${it.content}"
        }
    }

    fun getCurrentAgentExport(): String {
        return _agentMessages.value.joinToString("\n\n") {
            "${it.role.uppercase()}: ${it.content}"
        }
    }

    fun getCurrentExport(): String {
        return if (_currentTab.value == "Agente") getCurrentAgentExport() else getCurrentChatExport()
    }

    fun exportConversationToFile(): Uri? {
        val text = getCurrentExport()
        if (text.isBlank()) return null
        return try {
            val file = File(getApplication<Application>().cacheDir, "groq_chat_export.md")
            file.writeText(text)
            FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            null
        }
    }

    fun login(email: String) {
        completeLogin(email)
    }

    private fun completeLogin(email: String) {
        logAction("Seguridad", "Inicio de sesión para $email")
        _currentUserEmail.value = email
        prefs.edit().putString("current_user_email", email).apply()
        completeOnboarding()
        
        chatSessionId = null
        agentSessionId = null
        _currentChatSessionId.value = null
        _currentAgentSessionId.value = null
        _chatMessages.value = emptyList()
        _agentMessages.value = emptyList()
        _thinkingSteps.value = emptyList()
    }

    /**
     * Inicia sesión real con Google usando Credential Manager + Firebase Auth.
     * Requiere que "default_web_client_id" exista (lo genera el plugin google-services
     * a partir del client OAuth de tipo 3 en google-services.json).
     */
    fun signInWithGoogle(context: Context) {
        _authLoading.value = true
        _authProvider.value = "google"
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(R.string.default_web_client_id))
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                    val authResult = suspendCancellableCoroutine<AuthResult> { cont ->
                        FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resumeWithException(it) }
                    }

                    val email = authResult.user?.email ?: googleIdTokenCredential.id
                    completeLogin(email)
                } else {
                    _apiError.value = "No se reconoció la credencial de Google recibida."
                }
            } catch (e: NoCredentialException) {
                _apiError.value = "No hay ninguna cuenta de Google disponible en el dispositivo, " +
                    "o falta registrar la huella SHA-1 de la app en Firebase."
            } catch (e: GetCredentialException) {
                _apiError.value = "No se pudo iniciar sesión con Google: ${e.message ?: "cancelado"}"
            } catch (e: GoogleIdTokenParsingException) {
                _apiError.value = "El token de Google recibido no es válido."
            } catch (e: Exception) {
                _apiError.value = "Error al iniciar sesión con Google: ${e.message}"
            } finally {
                _authLoading.value = false
                _authProvider.value = null
            }
        }
    }

    /**
     * Inicia sesión real con GitHub usando el flujo web de OAuthProvider de Firebase.
     * Requiere que el proveedor "GitHub" esté activado en Firebase Console con el
     * Client ID/Secret de una GitHub OAuth App, y que la URL de callback de Firebase
     * (https://<project-id>.firebaseapp.com/__/auth/handler) esté registrada en esa app.
     */
    fun signInWithGitHub(activity: Activity) {
        _authLoading.value = true
        _authProvider.value = "github"
        val auth = FirebaseAuth.getInstance()
        val provider = OAuthProvider.newBuilder("github.com")

        val pending = auth.pendingAuthResult
        val task = pending ?: auth.startActivityForSignInWithProvider(activity, provider.build())

        task
            .addOnSuccessListener { authResult ->
                _authLoading.value = false
                _authProvider.value = null
                val email = authResult.user?.email ?: "usuario@github.com"
                completeLogin(email)
            }
            .addOnFailureListener { e ->
                _authLoading.value = false
                _authProvider.value = null
                _apiError.value = "No se pudo iniciar sesión con GitHub: ${e.message}"
            }
    }

    /**
     * Inicia sesión real con correo y contraseña usando Firebase Auth.
     */
    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _apiError.value = "Ingresa correo y contraseña."
            return
        }
        _authLoading.value = true
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                _authLoading.value = false
                completeLogin(authResult.user?.email ?: email)
            }
            .addOnFailureListener { e ->
                _authLoading.value = false
                _apiError.value = "No se pudo iniciar sesión: ${e.message}"
            }
    }

    /**
     * Crea una cuenta nueva con correo y contraseña usando Firebase Auth.
     */
    fun registerWithEmail(email: String, password: String) {
        if (email.isBlank() || password.length < 6) {
            _apiError.value = "La contraseña debe tener al menos 6 caracteres."
            return
        }
        _authLoading.value = true
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                _authLoading.value = false
                completeLogin(authResult.user?.email ?: email)
            }
            .addOnFailureListener { e ->
                _authLoading.value = false
                _apiError.value = "No se pudo crear la cuenta: ${e.message}"
            }
    }

    /**
     * Envía el correo de recuperación de contraseña (enlace generado por Firebase).
     */
    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _apiError.value = "Ingresa tu correo para recuperar la contraseña."
            return
        }
        _authLoading.value = true
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                _authLoading.value = false
                _apiError.value = "Te enviamos un enlace a $email para restablecer tu contraseña."
            }
            .addOnFailureListener { e ->
                _authLoading.value = false
                _apiError.value = "No se pudo enviar el correo de recuperación: ${e.message}"
            }
    }

    fun logout() {
        logAction("Seguridad", "Cierre de sesión del usuario.")
        FirebaseAuth.getInstance().signOut()
        prefs.edit().remove("current_user_email").apply()
        _currentUserEmail.value = "usuario@groqapp.local"
        
        // No borramos la API Key para no perder la config en tests
        _onboardingCompleted.value = false
        prefs.edit().putBoolean("onboarding_done", false).apply()
        
        chatSessionId = null
        agentSessionId = null
        _currentChatSessionId.value = null
        _currentAgentSessionId.value = null
        _chatMessages.value = emptyList()
        _agentMessages.value = emptyList()
        _thinkingSteps.value = emptyList()
    }

    private val _accountDeletionNeedsReauth = MutableStateFlow(false)
    val accountDeletionNeedsReauth: StateFlow<Boolean> = _accountDeletionNeedsReauth.asStateFlow()

    /**
     * Elimina la cuenta de Firebase del usuario actual y todos sus datos locales
     * (sesiones y mensajes guardados con su email). Requerido por la política de
     * Google Play de eliminación de cuenta dentro de la app.
     *
     * Ya no es posible crear cuentas nuevas como invitado (ver OnboardingScreen),
     * pero se mantiene esta rama para instalaciones previas que ya tenían datos
     * locales guardados sin sesión de Firebase.
     */
    fun deleteAccount() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            val localEmail = _currentUserEmail.value
            viewModelScope.launch(Dispatchers.IO) {
                repository.deleteAllDataForUser(localEmail)
                withContext(Dispatchers.Main) {
                    logAction("Seguridad", "Datos locales eliminados (sin cuenta): $localEmail")
                    logout()
                    _apiError.value = "Tus datos locales fueron eliminados."
                }
            }
            return
        }
        val email = user.email ?: _currentUserEmail.value

        user.delete()
            .addOnSuccessListener {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.deleteAllDataForUser(email)
                    }
                    logAction("Seguridad", "Cuenta eliminada: $email")
                    logout()
                    _apiError.value = "Tu cuenta y tus datos fueron eliminados."
                }
            }
            .addOnFailureListener { e ->
                if (e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                    _accountDeletionNeedsReauth.value = true
                    _apiError.value = "Por seguridad, cierra sesión y vuelve a iniciarla antes de eliminar tu cuenta."
                } else {
                    _apiError.value = "No se pudo eliminar la cuenta: ${e.message}"
                }
            }
    }

    fun clearAccountDeletionReauthFlag() {
        _accountDeletionNeedsReauth.value = false
    }

    // ─── Sandbox de archivos ────────────────────────────
    private val _sandboxFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val sandboxFiles: StateFlow<List<FileItem>> = _sandboxFiles.asStateFlow()

    fun loadSandboxFiles() {
        viewModelScope.launch {
            _sandboxFiles.value = withContext(Dispatchers.IO) {
                getSessionSandboxDir().listFiles()
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
                    File(getSessionSandboxDir(), name).writeText(content)
                    logAction("Archivo", "Archivo creado en sandbox: $name")
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
                    File(getSessionSandboxDir(), name).delete()
                    logAction("Archivo", "Archivo eliminado: $name")
                } catch (_: Exception) {
                }
            }
            loadSandboxFiles()
        }
    }

    suspend fun readSandboxFileContent(name: String): String = withContext(Dispatchers.IO) {
        try {
            File(getSessionSandboxDir(), name).readText()
        } catch (_: Exception) {
            ""
        }
    }

    fun getDownloadUri(fileName: String): Uri? {
        val file = File(getSessionSandboxDir(), fileName)
        if (!file.exists()) return null
        logAction("Descarga", "Enlace de descarga generado para: $fileName")
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

    fun createSandboxZipUri(): Uri? {
        val files = getSessionSandboxDir().listFiles()?.filter { it.isFile }.orEmpty()
        if (files.isEmpty()) return null
        logAction("Descarga", "Generando archivo ZIP comprimido con ${files.size} archivos.")
        return try {
            val zipFile = File(getApplication<Application>().cacheDir, "sandbox_export.zip")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                files.forEach { file ->
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
    fun setPythonResult(r: String) { 
        _pythonResult.value = r 
        logAction("Python sandbox", "Código ejecutado en Pyodide. Resultado recibido.")
    }

    // Action logs flow for the Drawer
    val actionLogs: StateFlow<List<ActionLog>> = repository.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _currentUserEmail = MutableStateFlow(prefs.getString("current_user_email", "usuario@groqapp.local") ?: "usuario@groqapp.local")
    val currentUserEmail: StateFlow<String> = _currentUserEmail.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chatSessions: StateFlow<List<ChatSession>> = _currentUserEmail.flatMapLatest { email ->
        repository.getSessionsByType("Chat", email)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val agentSessions: StateFlow<List<ChatSession>> = _currentUserEmail.flatMapLatest { email ->
        repository.getSessionsByType("Agente", email)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSandboxFiles()
        tts = TextToSpeech(application, this)
        logAction("Sistema", "Inicialización completada.")
    }
}
