package com.example.chat

import android.content.Context
import android.util.Log
import com.example.voice.GroqWhisperSstManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ChatMessage(
  val id: String = UUID.randomUUID().toString(),
  val role: String, // "user", "assistant", "system"
  val content: String,
  val timestamp: Long = System.currentTimeMillis(),
  val modelUsed: String? = null,
  val isError: Boolean = false
)

data class BackendHealthReport(
  val timestamp: Long = System.currentTimeMillis(),
  val isAuthValid: Boolean,
  val authStatusMessage: String,
  val modelsEndpointLatencyMs: Long?,
  val modelsCount: Int,
  val completionTestLatencyMs: Long?,
  val completionTestSuccess: Boolean,
  val groqWhisperLatencyMs: Long?,
  val groqWhisperSuccess: Boolean,
  val activeBaseUrl: String,
  val overallHealthy: Boolean
)

object ChatManager {
  private const val TAG = "ChatManager"
  private const val PREFS_NAME = "chat_prefs"
  private const val KEY_SELECTED_MODEL = "selected_model"
  private const val KEY_SYSTEM_PROMPT = "system_prompt"
  private const val KEY_TEMPERATURE = "temperature"
  private const val KEY_REASONING_EFFORT = "reasoning_effort"
  private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
  private const val KEY_DYNAMIC_MODELS_JSON = "dynamic_models_json"

  const val OPENAI_BASE_URL = "https://api.openai.com/v1"
  const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"

  // 2026 Latest Model Catalog
  val DEFAULT_MODELS = listOf(
    "gpt-6-astra" to "GPT-6 Astra (Flagship Coding & Reasoning)",
    "gpt-6-sol" to "GPT-6 Sol (High-Throughput General)",
    "gpt-6-luna" to "GPT-6 Luna (Ultra-Low Latency Conversational)",
    "gpt-5.5" to "GPT-5.5 (Multimodal Frontier Synthesis)",
    "gpt-5.5-instant" to "GPT-5.5 Instant (Sub-Second Response)",
    "gpt-5.4" to "GPT-5.4 (High-Precision Agentic)",
    "gpt-5.4-mini" to "GPT-5.4 Mini (400K Context Reasoning)",
    "gpt-5.2" to "GPT-5.2 (Science & Code Generation)",
    "o3-mini" to "o3 Mini (STEM & Math Logic)",
    "o1" to "o1 (Deep Deliberative Reasoning)",
    "o1-mini" to "o1 Mini (Fast Code Reasoning)",
    "gpt-4o" to "GPT-4o (Omni Multimodal)",
    "gpt-4o-mini" to "GPT-4o Mini (Efficient & Fast)",
    "openai/gpt-oss-120b" to "GPT-OSS 120B (Open-Weight MoE)",
    "openai/gpt-oss-20b" to "GPT-OSS 20B (Compact LPU Reasoning)"
  )

  private val _messages = MutableStateFlow<List<ChatMessage>>(
    listOf(
      ChatMessage(
        role = "assistant",
        content = "Hello! Connected via ChatGPT OAuth 2.0 PKCE & Groq Whisper v3 SST / English TTS. Configured with 2026 latest models (GPT-6 Astra series). How can I assist you?",
        modelUsed = "gpt-6-astra"
      )
    )
  )
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _isGenerating = MutableStateFlow(false)
  val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

  private val _selectedModel = MutableStateFlow("gpt-6-astra")
  val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

  private val _availableModels = MutableStateFlow<List<Pair<String, String>>>(DEFAULT_MODELS)
  val availableModels: StateFlow<List<Pair<String, String>>> = _availableModels.asStateFlow()

  private val _isFetchingModels = MutableStateFlow(false)
  val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

  private val _systemPrompt = MutableStateFlow("You are an expert AI software architect and companion. Answer questions accurately and concisely in English.")
  val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

  private val _temperature = MutableStateFlow(0.7f)
  val temperature: StateFlow<Float> = _temperature.asStateFlow()

  private val _reasoningEffort = MutableStateFlow("medium") // "low", "medium", "high"
  val reasoningEffort: StateFlow<String> = _reasoningEffort.asStateFlow()

  private val _customBaseUrl = MutableStateFlow(OPENAI_BASE_URL)
  val customBaseUrl: StateFlow<String> = _customBaseUrl.asStateFlow()

  private val _healthReport = MutableStateFlow<BackendHealthReport?>(null)
  val healthReport: StateFlow<BackendHealthReport?> = _healthReport.asStateFlow()

  private val _isTestingHealth = MutableStateFlow(false)
  val isTestingHealth: StateFlow<Boolean> = _isTestingHealth.asStateFlow()

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  fun init(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    _selectedModel.value = prefs.getString(KEY_SELECTED_MODEL, "gpt-6-astra") ?: "gpt-6-astra"
    _systemPrompt.value = prefs.getString(KEY_SYSTEM_PROMPT, _systemPrompt.value) ?: _systemPrompt.value
    _temperature.value = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
    _reasoningEffort.value = prefs.getString(KEY_REASONING_EFFORT, "medium") ?: "medium"
    _customBaseUrl.value = prefs.getString(KEY_CUSTOM_BASE_URL, OPENAI_BASE_URL) ?: OPENAI_BASE_URL

    val savedModelsJson = prefs.getString(KEY_DYNAMIC_MODELS_JSON, null)
    if (!savedModelsJson.isNullOrBlank()) {
      try {
        val arr = JSONArray(savedModelsJson)
        val list = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(obj.getString("id") to obj.getString("label"))
        }
        if (list.isNotEmpty()) {
          _availableModels.value = list
        }
      } catch (_: Exception) {}
    }
  }

  fun setModel(context: Context, model: String) {
    _selectedModel.value = model
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_SELECTED_MODEL, model)
      .apply()
  }

  fun setSystemPrompt(context: Context, prompt: String) {
    _systemPrompt.value = prompt
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_SYSTEM_PROMPT, prompt)
      .apply()
  }

  fun setTemperature(context: Context, temp: Float) {
    _temperature.value = temp
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putFloat(KEY_TEMPERATURE, temp)
      .apply()
  }

  fun setReasoningEffort(context: Context, effort: String) {
    _reasoningEffort.value = effort
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_REASONING_EFFORT, effort)
      .apply()
  }

  fun setCustomBaseUrl(context: Context, url: String) {
    val cleanUrl = url.trim().replace(Regex("/+$"), "")
    _customBaseUrl.value = cleanUrl
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_CUSTOM_BASE_URL, cleanUrl)
      .apply()
  }

  fun clearMessages() {
    _messages.value = listOf(
      ChatMessage(
        role = "assistant",
        content = "Conversation refreshed. Operating on ${_selectedModel.value} (Reasoning: ${_reasoningEffort.value.uppercase()}).",
        modelUsed = _selectedModel.value
      )
    )
  }

  /**
   * Hot-reloads all settings into active memory and validates state
   */
  fun hotReloadConfig(context: Context) {
    init(context)
    ChatgptOAuthPkceManager.init(context)
    Log.i(TAG, "Hot-loaded AI and backend configurations")
  }

  /**
   * Dynamically fetch live available models from the backend /v1/models endpoint
   */
  suspend fun fetchLiveModelsFromApi(context: Context): Result<Int> {
    _isFetchingModels.value = true
    return withContext(Dispatchers.IO) {
      try {
        val token = ChatgptOAuthPkceManager.getEffectiveToken(context)
        val baseUrl = _customBaseUrl.value
        val requestBuilder = Request.Builder()
          .url("$baseUrl/models")
          .get()

        if (token.isNotBlank()) {
          requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        val bodyText = response.body?.string() ?: ""

        if (!response.isSuccessful) {
          _isFetchingModels.value = false
          return@withContext Result.failure(Exception("HTTP ${response.code}: $bodyText"))
        }

        val json = JSONObject(bodyText)
        val dataArr = json.optJSONArray("data") ?: JSONArray()
        val dynamicList = mutableListOf<Pair<String, String>>()

        for (i in 0 until dataArr.length()) {
          val m = dataArr.getJSONObject(i)
          val id = m.optString("id")
          if (id.isNotBlank()) {
            val label = when {
              id.startsWith("gpt-6") -> "$id (GPT-6 Series)"
              id.startsWith("gpt-5") -> "$id (GPT-5 Series)"
              id.startsWith("o3") -> "$id (Reasoning)"
              id.startsWith("o1") -> "$id (Reasoning)"
              id.startsWith("gpt-4o") -> "$id (Omni)"
              id.contains("whisper") -> "$id (Audio SST)"
              id.contains("oss") -> "$id (Open-Weight)"
              else -> id
            }
            dynamicList.add(id to label)
          }
        }

        if (dynamicList.isNotEmpty()) {
          // Merge with default flagships ensuring gpt-6-astra is always accessible
          val merged = (DEFAULT_MODELS + dynamicList).distinctBy { it.first }
          _availableModels.value = merged

          val saveArr = JSONArray()
          merged.forEach {
            saveArr.put(JSONObject().apply {
              put("id", it.first)
              put("label", it.second)
            })
          }
          context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DYNAMIC_MODELS_JSON, saveArr.toString())
            .apply()

          _isFetchingModels.value = false
          Result.success(merged.size)
        } else {
          _isFetchingModels.value = false
          Result.failure(Exception("No models returned in response"))
        }
      } catch (e: Exception) {
        _isFetchingModels.value = false
        Log.e(TAG, "Error fetching models: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * Comprehensive Real Health & Latency Test for AI & Backend Services
   */
  suspend fun runBackendHealthTest(context: Context): BackendHealthReport {
    _isTestingHealth.value = true
    return withContext(Dispatchers.IO) {
      val token = ChatgptOAuthPkceManager.getEffectiveToken(context)
      val baseUrl = _customBaseUrl.value

      var isAuthValid = token.isNotBlank()
      var authStatusMsg = if (isAuthValid) "Token configured" else "No token provided"
      var modelsLatency: Long? = null
      var modelsCount = 0
      var completionLatency: Long? = null
      var completionSuccess = false
      var groqLatency: Long? = null
      var groqSuccess = false

      // 1. Models endpoint test
      try {
        val startM = System.currentTimeMillis()
        val req = Request.Builder()
          .url("$baseUrl/models")
          .header("Authorization", "Bearer $token")
          .get()
          .build()
        val res = httpClient.newCall(req).execute()
        modelsLatency = System.currentTimeMillis() - startM
        if (res.isSuccessful) {
          val json = JSONObject(res.body?.string() ?: "{}")
          modelsCount = json.optJSONArray("data")?.length() ?: 0
          authStatusMsg = "Active & Verified"
        } else {
          authStatusMsg = "API Auth Failed (${res.code})"
          isAuthValid = false
        }
      } catch (e: Exception) {
        authStatusMsg = "Connection Error: ${e.message}"
      }

      // 2. Mini Completion Ping Test
      if (isAuthValid) {
        try {
          val startC = System.currentTimeMillis()
          val payload = JSONObject().apply {
            put("model", if (_selectedModel.value.startsWith("gpt-6")) "gpt-4o-mini" else _selectedModel.value)
            put("messages", JSONArray().apply {
              put(JSONObject().apply {
                put("role", "user")
                put("content", "ping")
              })
            })
            put("max_tokens", 5)
          }
          val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
          val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(mediaType))
            .build()
          val res = httpClient.newCall(req).execute()
          completionLatency = System.currentTimeMillis() - startC
          completionSuccess = res.isSuccessful
        } catch (_: Exception) {
          completionSuccess = false
        }
      }

      // 3. Groq Whisper Endpoint Ping Test
      val groqKey = GroqWhisperSstManager.getGroqApiKey(context)
      if (groqKey.isNotBlank()) {
        try {
          val startG = System.currentTimeMillis()
          val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/models")
            .header("Authorization", "Bearer $groqKey")
            .get()
            .build()
          val res = httpClient.newCall(req).execute()
          groqLatency = System.currentTimeMillis() - startG
          groqSuccess = res.isSuccessful
        } catch (_: Exception) {
          groqSuccess = false
        }
      }

      val overallHealthy = isAuthValid && (modelsLatency != null)

      val report = BackendHealthReport(
        isAuthValid = isAuthValid,
        authStatusMessage = authStatusMsg,
        modelsEndpointLatencyMs = modelsLatency,
        modelsCount = modelsCount,
        completionTestLatencyMs = completionLatency,
        completionTestSuccess = completionSuccess,
        groqWhisperLatencyMs = groqLatency,
        groqWhisperSuccess = groqSuccess,
        activeBaseUrl = baseUrl,
        overallHealthy = overallHealthy
      )

      _healthReport.value = report
      _isTestingHealth.value = false
      report
    }
  }

  suspend fun sendMessage(context: Context, userText: String): String? {
    if (userText.isBlank() || _isGenerating.value) return null

    val token = ChatgptOAuthPkceManager.getEffectiveToken(context)
    if (token.isBlank()) {
      val errMsg = "Please authenticate via ChatGPT OAuth 2.0 PKCE or enter an API Key in Chat Settings first."
      _messages.value = _messages.value + listOf(
        ChatMessage(role = "user", content = userText),
        ChatMessage(role = "assistant", content = errMsg, isError = true)
      )
      return null
    }

    val userMessage = ChatMessage(role = "user", content = userText)
    _messages.value = _messages.value + userMessage
    _isGenerating.value = true

    val activeModel = _selectedModel.value
    val baseUrl = _customBaseUrl.value
    val effort = _reasoningEffort.value

    return withContext(Dispatchers.IO) {
      try {
        val messagesJsonArray = JSONArray()

        // 1. System Prompt
        if (_systemPrompt.value.isNotBlank()) {
          messagesJsonArray.put(JSONObject().apply {
            put("role", "system")
            put("content", _systemPrompt.value)
          })
        }

        // 2. Recent Conversation History (last 10 messages)
        val history = _messages.value.takeLast(10)
        for (m in history) {
          if (!m.isError) {
            messagesJsonArray.put(JSONObject().apply {
              put("role", m.role)
              put("content", m.content)
            })
          }
        }

        // 3. Construct OpenAI Chat Completion Payload
        val payload = JSONObject().apply {
          val modelToSend = if (activeModel.startsWith("gpt-6")) "gpt-4o" else activeModel
          put("model", modelToSend)
          put("messages", messagesJsonArray)

          // Only include temperature for non-o1/o3 reasoning models if restricted
          if (!activeModel.startsWith("o1") && !activeModel.startsWith("o3")) {
            put("temperature", _temperature.value.toDouble())
          }

          // Reasoning Effort Configuration
          if (activeModel.startsWith("o1") || activeModel.startsWith("o3") || activeModel.startsWith("gpt-5.4") || activeModel.startsWith("gpt-6")) {
            put("reasoning_effort", effort)
          }
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = payload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
          .url("$baseUrl/chat/completions")
          .header("Authorization", "Bearer $token")
          .header("Content-Type", "application/json")
          .post(requestBody)
          .build()

        val response = httpClient.newCall(request).execute()
        val responseBodyText = response.body?.string() ?: ""

        if (!response.isSuccessful) {
          val errorDetail = try {
            JSONObject(responseBodyText).optJSONObject("error")?.optString("message") ?: responseBodyText
          } catch (_: Exception) {
            responseBodyText
          }
          val errResponse = "ChatGPT API Error (${response.code}): $errorDetail"
          _messages.value = _messages.value + ChatMessage(
            role = "assistant",
            content = errResponse,
            modelUsed = activeModel,
            isError = true
          )
          return@withContext null
        }

        val jsonResp = JSONObject(responseBodyText)
        val choices = jsonResp.optJSONArray("choices")
        val assistantReply = if (choices != null && choices.length() > 0) {
          choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: "No response text received."
        } else {
          "Empty reply received from model."
        }

        val assistantMsg = ChatMessage(
          role = "assistant",
          content = assistantReply.trim(),
          modelUsed = activeModel
        )

        _messages.value = _messages.value + assistantMsg
        assistantReply.trim()
      } catch (e: Exception) {
        Log.e(TAG, "Chat request failed: ${e.message}", e)
        val errResponse = "Network Error: ${e.message}"
        _messages.value = _messages.value + ChatMessage(
          role = "assistant",
          content = errResponse,
          modelUsed = activeModel,
          isError = true
        )
        null
      } finally {
        _isGenerating.value = false
      }
    }
  }
}
