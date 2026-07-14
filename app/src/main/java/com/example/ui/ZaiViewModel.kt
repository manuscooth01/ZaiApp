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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ZaiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val prefs = application.getSharedPreferences("groq_prefs", Context.MODE_PRIVATE)

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()
    fun completeOnboarding() { prefs.edit().putBoolean("onboarding_done", true).apply(); _onboardingCompleted.value = true }

    val providers = mapOf("Groq" to "https://api.groq.com/openai/v1/", "OpenAI" to "https://api.openai.com/v1/")
    private val _selectedProvider = MutableStateFlow("Groq")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()
    fun setProvider(p: String) { _selectedProvider.value = p; _baseUrl.value = providers[p] ?: _baseUrl.value }

    private val _apiKey = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    private val _baseUrl = MutableStateFlow(prefs.getString("base_url", providers["Groq"]!!) ?: providers["Groq"]!!)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()
    private val _selectedModel = MutableStateFlow(prefs.getString("selected_model", "llama-3.1-8b-instant") ?: "llama-3.1-8b-instant")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    fun saveApiKey(k: String) { prefs.edit().putString("api_key", k).apply(); _apiKey.value = k }
    fun saveBaseUrl(u: String) { prefs.edit().putString("base_url", u).apply(); _baseUrl.value = u }
    fun saveSelectedModel(m: String) { prefs.edit().putString("selected_model", m).apply(); _selectedModel.value = m }

    // Sesiones separadas (mínimo)
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    private val _agentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val agentMessages: StateFlow<List<ChatMessage>> = _agentMessages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    val sessions: StateFlow<List<ChatSession>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AppRepository(db.chatDao())
        sessions = repository.getAllSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // Chat mínimo: sin archivos, sin sandbox
    fun sendChatMessage(text: String) {
        viewModelScope.launch {
            val id = repository.createSession(text.take(20), _selectedModel.value)
            repository.saveMessage(id, "user", text)
            _isGenerating.value = true
            val apiMessages = mutableListOf(GroqMessage(role = "system", content = "Eres un asistente útil."))
            apiMessages.add(GroqMessage(role = "user", content = text))
            val result = repository.getChatCompletion(_baseUrl.value, _apiKey.value, _selectedModel.value, apiMessages)
            result.fold(
                onSuccess = { repository.saveMessage(id, "assistant", it) },
                onFailure = { _apiError.value = it.message }
            )
            _isGenerating.value = false
            _chatMessages.value = repository.getMessagesForSessionSync(id)
        }
    }

    fun sendAgentMessage(text: String) {
        viewModelScope.launch {
            val id = repository.createSession(text.take(20), _selectedModel.value)
            repository.saveMessage(id, "user", text)
            _isGenerating.value = true
            val apiMessages = mutableListOf(GroqMessage(role = "system", content = "Eres un agente autónomo."))
            apiMessages.add(GroqMessage(role = "user", content = text))
            val result = repository.getChatCompletion(_baseUrl.value, _apiKey.value, _selectedModel.value, apiMessages)
            result.fold(
                onSuccess = { repository.saveMessage(id, "assistant", it) },
                onFailure = { _apiError.value = it.message }
            )
            _isGenerating.value = false
            _agentMessages.value = repository.getMessagesForSessionSync(id)
        }
    }

    fun clearApiError() { _apiError.value = null }
}
