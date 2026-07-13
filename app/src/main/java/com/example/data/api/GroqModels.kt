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
    val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GroqMessageResponse(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class GroqChoice(
    val index: Int,
    val message: GroqMessageResponse,
    @Json(name = "finish_reason") val finishReason: String?
)

@JsonClass(generateAdapter = true)
data class GroqChatResponse(
    val id: String?,
    val model: String?,
    val choices: List<GroqChoice>?
)
