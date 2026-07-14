package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GroqMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val stream: Boolean = false,
    val temperature: Double = 0.7
)

@JsonClass(generateAdapter = true)
data class GroqMessageResponse(
    val role: String?,
    val content: String?
)

@JsonClass(generateAdapter = true)
data class GroqChoice(
    val index: Int? = null,
    val message: GroqMessageResponse? = null,
    @Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GroqChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<GroqChoice>? = null,
    val error: GroqError? = null
)

@JsonClass(generateAdapter = true)
data class GroqError(
    val message: String? = null,
    val type: String? = null
)
