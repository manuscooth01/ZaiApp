package com.example.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface GroqService {
    @POST
    suspend fun getChatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: GroqChatRequest
    ): Response<GroqChatResponse>

    @GET
    suspend fun getModelsList(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Response<ModelsListResponse>
}
