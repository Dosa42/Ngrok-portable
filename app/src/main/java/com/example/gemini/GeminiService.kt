package com.example.gemini

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessageItem(
  val id: String = java.util.UUID.randomUUID().toString(),
  val text: String,
  val isUser: Boolean,
  val timestamp: Long = System.currentTimeMillis(),
  val latencyMs: Long? = null,
  val isError: Boolean = false
)

object GeminiService {
  private const val TAG = "GeminiService"
  // Fast low-latency model mandated by user prompt: gemini-3.1-flash-lite
  const val MODEL_NAME = "gemini-3.1-flash-lite-preview"
  private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

  private const val SYSTEM_INSTRUCTION = """
You are an ultra-fast, intelligent Ngrok Tunnel & Android Systems Copilot embedded in this portable Android app.
Your goals:
1. Provide rapid, concise, low-latency technical answers about Ngrok reverse proxy tunnels, port forwarding, local HTTP servers, webhooks, and network security.
2. Help users configure, test, and troubleshoot their embedded Ngrok tunnel (default local port 8085).
3. Provide ready-to-run curl commands, webhook receiver examples, and architectural guidance.
Keep your answers direct, practical, and fast.
"""

  private val client = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

  suspend fun generateChatResponse(
    history: List<ChatMessageItem>,
    apiKeyOverride: String? = null
  ): Pair<String, Long> = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()
    val apiKey = (apiKeyOverride ?: BuildConfig.GEMINI_API_KEY).trim()

    if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
      return@withContext Pair("Gemini Error: API key is not configured.", 0L)
    }

    try {
      val contentsArray = JSONArray()
      // Include last 10 messages for fast multi-turn context
      val recentHistory = history.takeLast(10)
      for (msg in recentHistory) {
        val contentObj = JSONObject().apply {
          put("role", if (msg.isUser) "user" else "model")
          put("parts", JSONArray().apply {
            put(JSONObject().put("text", msg.text))
          })
        }
        contentsArray.put(contentObj)
      }

      val requestJson = JSONObject().apply {
        put("contents", contentsArray)
        put("systemInstruction", JSONObject().apply {
          put("parts", JSONArray().apply {
            put(JSONObject().put("text", SYSTEM_INSTRUCTION))
          })
        })
        put("generationConfig", JSONObject().apply {
          put("temperature", 0.3)
          put("topP", 0.9)
          put("topK", 20)
        })
      }

      val url = "$BASE_URL?key=$apiKey"
      val body = requestJson.toString().toRequestBody("application/json".toMediaType())
      val request = Request.Builder()
        .url(url)
        .post(body)
        .build()

      val response = client.newCall(request).execute()
      val responseBody = response.body?.string() ?: ""
      val latency = System.currentTimeMillis() - startTime

      if (!response.isSuccessful) {
        Log.e(TAG, "Gemini API error HTTP ${response.code}: $responseBody")
        val errorMsg = try {
          JSONObject(responseBody).getJSONObject("error").getString("message")
        } catch (_: Exception) {
          "HTTP ${response.code}: $responseBody"
        }
        return@withContext Pair("Gemini Error: $errorMsg", latency)
      }

      val json = JSONObject(responseBody)
      val candidates = json.optJSONArray("candidates")
      val text = candidates?.optJSONObject(0)
        ?.optJSONObject("content")
        ?.optJSONArray("parts")
        ?.optJSONObject(0)
        ?.optString("text")

      val reply = text ?: "No response from model."
      Pair(reply.trim(), latency)
    } catch (e: Exception) {
      Log.e(TAG, "Exception calling Gemini: ${e.message}", e)
      val duration = System.currentTimeMillis() - startTime
      Pair("Connection error: ${e.localizedMessage ?: e.message}. Check network connection or API key.", duration)
    }
  }

  suspend fun generateResponse(prompt: String): String {
    val messages = listOf(ChatMessageItem(text = prompt, isUser = true))
    val (reply, _) = generateChatResponse(messages)
    return reply
  }

}
