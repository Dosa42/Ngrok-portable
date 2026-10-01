package com.example.chat

import android.content.Context
import android.util.Log
import com.example.voice.GroqWhisperSstManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
  private const val KEY_REASONING_EFFORT = "reasoning_effort"
  private const val KEY_CUSTOM_BASE_URL = "custom_base_url"

  const val OPENAI_BASE_URL = "https://api.openai.com/v1"
  const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"

  private val _messages = MutableStateFlow<List<ChatMessage>>(
    listOf(
      ChatMessage(
        role = "assistant",
        content = "Hello! Choose a model from the live backend catalog to start chatting."
      )
    )
  )
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _isGenerating = MutableStateFlow(false)
  val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

  private val _selectedModel = MutableStateFlow("")
  val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

  private val _availableModels = MutableStateFlow<List<Pair<String, String>>>(emptyList())
  val availableModels: StateFlow<List<Pair<String, String>>> = _availableModels.asStateFlow()

  private val _isFetchingModels = MutableStateFlow(false)
  val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

  private val _modelCatalogError = MutableStateFlow<String?>(null)
  val modelCatalogError: StateFlow<String?> = _modelCatalogError.asStateFlow()
  private val modelFetchMutex = Mutex()
  @Volatile private var modelCatalogSource: Pair<String, String>? = null

  private val _systemPrompt = MutableStateFlow("You are an expert AI software architect and companion. Answer questions accurately and concisely in English.")
  val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

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
    _selectedModel.value = prefs.getString(KEY_SELECTED_MODEL, "") ?: ""
    _systemPrompt.value = prefs.getString(KEY_SYSTEM_PROMPT, _systemPrompt.value) ?: _systemPrompt.value
    _reasoningEffort.value = prefs.getString(KEY_REASONING_EFFORT, "medium") ?: "medium"
    _customBaseUrl.value = prefs.getString(KEY_CUSTOM_BASE_URL, OPENAI_BASE_URL) ?: OPENAI_BASE_URL

    // Discard legacy settings, including cached models merged with the old fixed catalog.
    prefs.edit().remove("dynamic_models_json").remove("temperature").apply()
    invalidateModelCatalog()
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

  fun setReasoningEffort(context: Context, effort: String) {
    _reasoningEffort.value = effort
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_REASONING_EFFORT, effort)
      .apply()
  }

  fun setCustomBaseUrl(context: Context, url: String) {
    val cleanUrl = url.trim().replace(Regex("/+$"), "")
    if (cleanUrl != _customBaseUrl.value) invalidateModelCatalog()
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

  fun invalidateModelCatalog() {
    modelCatalogSource = null
    _availableModels.value = emptyList()
    _modelCatalogError.value = null
  }

  private fun parseModelCatalog(json: JSONObject): List<Pair<String, String>> {
    val chatgptModels = json.optJSONArray("models")
    val models = chatgptModels ?: json.optJSONArray("data")
      ?: throw IllegalArgumentException("No model catalog returned in response")
    val idKey = if (chatgptModels != null) "slug" else "id"
    val result = mutableListOf<Pair<String, String>>()
    for (i in 0 until models.length()) {
      val model = models.getJSONObject(i)
      if (chatgptModels != null && model.optString("visibility") != "list") continue
      val id = model.optString(idKey)
      if (id.isNotBlank()) {
        result.add(id to model.optString("display_name").ifBlank { id })
      }
    }
    return result.distinctBy { it.first }
  }

  /**
   * Fetch the current backend catalog without cached or hardcoded fallback models.
   */
  suspend fun fetchLiveModelsFromApi(context: Context): Result<Int> = modelFetchMutex.withLock {
    _isFetchingModels.value = true
    invalidateModelCatalog()
    try {
      val token = ChatgptOAuthPkceManager.getEffectiveToken(context)
      val baseUrl = _customBaseUrl.value
      val liveModels = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
          .url("$baseUrl/models")
          .header("Cache-Control", "no-cache")
          .get()

        if (token.isNotBlank()) {
          requestBuilder.header("Authorization", "Bearer $token")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
          val bodyText = response.body?.string() ?: ""
          if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: $bodyText")
          }
          parseModelCatalog(JSONObject(bodyText))
        }
      }
      if (baseUrl != _customBaseUrl.value || token != ChatgptOAuthPkceManager.getEffectiveToken(context)) {
        throw IllegalStateException("Backend or account changed; refresh the model catalog")
      }
      modelCatalogSource = baseUrl to token
      if (liveModels.none { it.first == _selectedModel.value }) setModel(context, "")
      _availableModels.value = liveModels
      if (liveModels.isEmpty()) throw IllegalStateException("No models returned in response")
      Result.success(liveModels.size)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _modelCatalogError.value = e.message ?: "Model discovery failed"
      Log.e(TAG, "Error fetching models: ${e.message}", e)
      Result.failure(e)
    } finally {
      _isFetchingModels.value = false
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
      val activeModel = _selectedModel.value

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
          .header("Cache-Control", "no-cache")
          .header("Authorization", "Bearer $token")
          .get()
          .build()
        val res = httpClient.newCall(req).execute()
        modelsLatency = System.currentTimeMillis() - startM
        if (res.isSuccessful) {
          val json = JSONObject(res.body?.string() ?: "{}")
          modelsCount = parseModelCatalog(json).size
          authStatusMsg = "Active & Verified"
        } else {
          authStatusMsg = "API Auth Failed (${res.code})"
          isAuthValid = false
        }
      } catch (e: Exception) {
        authStatusMsg = "Connection Error: ${e.message}"
      }

      // 2. Mini Completion Ping Test
      if (isAuthValid && activeModel.isNotBlank()) {
        try {
          val startC = System.currentTimeMillis()
          val payload = JSONObject().apply {
            put("model", activeModel)
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

    val activeModel = _selectedModel.value
    val baseUrl = _customBaseUrl.value
    if (activeModel.isBlank() || _availableModels.value.none { it.first == activeModel } ||
      modelCatalogSource != (baseUrl to token)) {
      _messages.value = _messages.value + listOf(
        ChatMessage(role = "user", content = userText),
        ChatMessage(role = "assistant", content = "Fetch the current backend catalog and choose an available model first.", isError = true)
      )
      return null
    }

    val userMessage = ChatMessage(role = "user", content = userText)
    _messages.value = _messages.value + userMessage
    _isGenerating.value = true

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
          put("model", activeModel)
          put("messages", messagesJsonArray)

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
          modelUsed = jsonResp.optString("model").ifBlank { activeModel }
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
