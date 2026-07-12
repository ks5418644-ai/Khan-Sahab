package com.example.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*

// ==========================================
// 🌟 GEMINI REST API MODEL DEF
// ==========================================

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class ResponseFormat(
    val text: ResponseFormatText? = null
)

@Serializable
data class ResponseFormatText(
    val mimeType: String,
    val schema: JsonObject? = null
)

@Serializable
data class GenerationConfig(
    val responseFormat: ResponseFormat? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val thinkingConfig: ThinkingConfig? = null,
    val imageConfig: ImageConfig? = null,
    val responseModalities: List<String>? = null,
    val speechConfig: SpeechConfig? = null
)

@Serializable
data class ThinkingConfig(
    val thinkingLevel: String
)

@Serializable
data class ImageConfig(
    val aspectRatio: String,
    val imageSize: String
)

@Serializable
data class SpeechConfig(
    val voiceConfig: VoiceConfig
)

@Serializable
data class VoiceConfig(
    val prebuiltVoiceConfig: PrebuiltVoiceConfig
)

@Serializable
data class PrebuiltVoiceConfig(
    val voiceName: String
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content
)

// ==========================================
// 🌟 OPENROUTER / CHATGPT CHAT MODEL DEF
// ==========================================

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenRouterChatRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val temperature: Float? = null
)

@Serializable
data class OpenRouterChoice(
    val message: OpenRouterMessage
)

@Serializable
data class OpenRouterChatResponse(
    val choices: List<OpenRouterChoice>? = null
)

// ==========================================
// 🌟 RETROFIT SERVICES
// ==========================================

interface GeminiApiService {
    
    // Direct Beta Gemini endpoint
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    // Dynamic absolute URL post for OpenRouter, OpenAI-compatibles, or Nov
    @POST
    suspend fun generateAbsoluteChatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Header("HTTP-Referer") referer: String = "https://ai.studio/build",
        @Header("X-Title") title: String = "Rabiya Companion",
        @Body request: OpenRouterChatRequest
    ): OpenRouterChatResponse

    // Get free unauthenticated text completions from Pollinations
    @GET
    suspend fun getFreeTextCompletion(
        @Url url: String
    ): ResponseBody
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(48, 5, java.util.concurrent.TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .build()

    val service: GeminiApiService by lazy {
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val json = Json { 
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}
