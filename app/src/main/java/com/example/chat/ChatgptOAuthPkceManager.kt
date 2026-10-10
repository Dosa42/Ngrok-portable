package com.example.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ParsedTokenInfo(
  val token: String,
  val tokenType: String,
  val expiresAt: Long? = null,
  val issuedAt: Long? = null,
  val email: String? = null,
  val userId: String? = null,
  val issuer: String? = null,
  val scopes: List<String> = emptyList(),
  val preview: String,
  val rawLength: Int = token.length
) {
  val isExpired: Boolean
    get() = expiresAt != null && expiresAt < System.currentTimeMillis()

  val formattedExpiry: String
    get() {
      if (expiresAt == null) return "No Expiry / Static Key"
      val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
      val expDate = Date(expiresAt)
      return if (isExpired) "Expired on ${sdf.format(expDate)}" else "Expires on ${sdf.format(expDate)}"
    }
}

object ChatgptOAuthPkceManager {
  private const val TAG = "ChatgptOAuthPkce"
  private const val PREFS_NAME = "chatgpt_oauth_prefs"
  private const val KEY_ACCESS_TOKEN = "access_token"
  private const val KEY_REFRESH_TOKEN = "refresh_token"
  private const val KEY_EXPIRES_AT = "expires_at"
  private const val KEY_CLIENT_ID = "oauth_client_id"
  private const val KEY_CODE_VERIFIER = "pkce_code_verifier"
  private const val KEY_AUTH_STATE = "oauth_state"
  private const val KEY_DIRECT_API_KEY = "direct_api_key"

  const val REDIRECT_URI = "ngrokagent://oauth-callback"
  const val DEFAULT_AUTH_ENDPOINT = "https://auth.openai.com/authorize"
  const val DEFAULT_TOKEN_ENDPOINT = "https://auth.openai.com/oauth/token"

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  private val _authStatus = MutableStateFlow("Not Authenticated")
  val authStatus: StateFlow<String> = _authStatus.asStateFlow()

  private val _isAuthenticated = MutableStateFlow(false)
  val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  private val _activeTokenInfo = MutableStateFlow<ParsedTokenInfo?>(null)
  val activeTokenInfo: StateFlow<ParsedTokenInfo?> = _activeTokenInfo.asStateFlow()

  private val _isValidatingToken = MutableStateFlow(false)
  val isValidatingToken: StateFlow<Boolean> = _isValidatingToken.asStateFlow()

  fun init(context: Context) {
    val token = getEffectiveToken(context)
    val hasToken = token.isNotBlank()
    _isAuthenticated.value = hasToken
    if (hasToken) {
      val parsed = parseRawTokenInput(token)
      _activeTokenInfo.value = parsed
      val typeLabel = parsed?.tokenType ?: "Active"
      _authStatus.value = if (parsed?.isExpired == true) "Token Expired" else "Connected & Active ($typeLabel)"
    } else {
      _activeTokenInfo.value = null
      _authStatus.value = "Not Authenticated"
    }
  }

  fun getDirectApiKey(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_DIRECT_API_KEY, "") ?: ""
  }

  fun setDirectApiKey(context: Context, key: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_DIRECT_API_KEY, key.trim())
      .apply()
    val effective = getEffectiveToken(context)
    _isAuthenticated.value = effective.isNotBlank()
    if (effective.isNotBlank()) {
      val parsed = parseRawTokenInput(effective)
      _activeTokenInfo.value = parsed
      _authStatus.value = "Direct Key Active (${parsed?.tokenType ?: "Key"})"
    } else {
      _authStatus.value = "Not Authenticated"
    }
  }

  fun getOAuthClientId(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_CLIENT_ID, "openai-chatgpt-android") ?: "openai-chatgpt-android"
  }

  fun setOAuthClientId(context: Context, clientId: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_CLIENT_ID, clientId.trim())
      .apply()
  }

  fun getAccessToken(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_ACCESS_TOKEN, "") ?: ""
  }

  fun getEffectiveToken(context: Context): String {
    val oauthToken = getAccessToken(context)
    if (oauthToken.isNotBlank()) return oauthToken
    return getDirectApiKey(context)
  }

  /**
   * Generates PKCE Code Verifier (RFC 7636)
   */
  fun generateCodeVerifier(): String {
    val secureRandom = SecureRandom()
    val code = ByteArray(64)
    secureRandom.nextBytes(code)
    return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }

  /**
   * Generates PKCE Code Challenge (S256)
   */
  fun generateCodeChallenge(verifier: String): String {
    val bytes = verifier.toByteArray(StandardCharsets.US_ASCII)
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bytes, 0, bytes.size)
    val digest = md.digest()
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }

  /**
   * Builds the OAuth 2.0 PKCE Authorization URL and opens it in Lemur / default browser
   */
  fun startOAuthFlow(context: Context, clientIdOverride: String? = null): String {
    val clientId = clientIdOverride ?: getOAuthClientId(context)
    val verifier = generateCodeVerifier()
    val challenge = generateCodeChallenge(verifier)
    val state = UUIDRandom()

    // Save PKCE verifier and state for verification
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_CODE_VERIFIER, verifier)
      .putString(KEY_AUTH_STATE, state)
      .apply()

    val authUrl = Uri.parse(DEFAULT_AUTH_ENDPOINT).buildUpon()
      .appendQueryParameter("response_type", "code")
      .appendQueryParameter("client_id", clientId)
      .appendQueryParameter("redirect_uri", REDIRECT_URI)
      .appendQueryParameter("scope", "openid profile email model.request")
      .appendQueryParameter("code_challenge", challenge)
      .appendQueryParameter("code_challenge_method", "S256")
      .appendQueryParameter("state", state)
      .build()
      .toString()

    Log.i(TAG, "Starting ChatGPT OAuth 2.0 PKCE flow with URL: $authUrl")

    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      Log.w(TAG, "Could not open browser for OAuth: ${e.message}")
    }

    return authUrl
  }

  /**
   * Exchange authorization code for access token via PKCE
   */
  suspend fun exchangeCodeForToken(context: Context, authorizationCode: String, returnedState: String?): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedVerifier = prefs.getString(KEY_CODE_VERIFIER, "") ?: ""
    val clientId = getOAuthClientId(context)

    if (authorizationCode.isBlank()) {
      _authStatus.value = "Auth Code was empty"
      return false
    }

    _authStatus.value = "Exchanging PKCE token..."

    return withContext(Dispatchers.IO) {
      try {
        val formBuilder = FormBody.Builder()
          .add("grant_type", "authorization_code")
          .add("client_id", clientId)
          .add("code", authorizationCode.trim())
          .add("redirect_uri", REDIRECT_URI)
          .add("code_verifier", savedVerifier)

        val request = Request.Builder()
          .url(DEFAULT_TOKEN_ENDPOINT)
          .post(formBuilder.build())
          .header("Content-Type", "application/x-www-form-urlencoded")
          .build()

        val response = httpClient.newCall(request).execute()
        val bodyText = response.body?.string() ?: ""

        if (response.isSuccessful) {
          val json = JSONObject(bodyText)
          val accessToken = json.optString("access_token", "")
          val refreshToken = json.optString("refresh_token", "")
          val expiresIn = json.optLong("expires_in", 3600)
          val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

          prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()

          _isAuthenticated.value = true
          _authStatus.value = "OAuth 2.0 PKCE Connected"
          _activeTokenInfo.value = parseRawTokenInput(accessToken)
          Log.i(TAG, "OAuth PKCE token exchange succeeded")
          true
        } else {
          Log.w(TAG, "Token exchange returned ${response.code}: $bodyText")
          _authStatus.value = "Token error (${response.code}). If using direct token, paste/upload it."
          false
        }
      } catch (e: Exception) {
        Log.e(TAG, "OAuth PKCE token exchange error: ${e.message}", e)
        _authStatus.value = "Exchange failed: ${e.message}"
        false
      }
    }
  }

  /**
   * Parses raw token text (JSON dump, raw JWT, .env key-value, Bearer string, HTTP header)
   */
  fun parseRawTokenInput(rawInput: String): ParsedTokenInfo? {
    val trimmed = rawInput.trim()
    if (trimmed.isBlank()) return null

    var extractedToken = trimmed
    var extractedExpiresAt: Long? = null
    var extractedIssuedAt: Long? = null
    var extractedEmail: String? = null
    var extractedUserId: String? = null
    var extractedIssuer: String? = null
    val extractedScopes = mutableListOf<String>()

    // 1. Check if JSON payload (e.g. session dump, auth.json, OpenAI token response)
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      try {
        val json = JSONObject(trimmed)
        extractedToken = when {
          json.has("access_token") -> json.getString("access_token")
          json.has("accessToken") -> json.getString("accessToken")
          json.has("token") -> json.getString("token")
          json.has("apiKey") -> json.getString("apiKey")
          json.has("api_key") -> json.getString("api_key")
          json.has("key") -> json.getString("key")
          json.has("chatgpt_token") -> json.getString("chatgpt_token")
          json.has("session") -> {
            val sessionObj = json.getJSONObject("session")
            sessionObj.optString("accessToken", sessionObj.optString("access_token", trimmed))
          }
          json.has("auth") -> {
            val authObj = json.getJSONObject("auth")
            authObj.optString("accessToken", authObj.optString("access_token", trimmed))
          }
          else -> trimmed
        }
        if (json.has("expires_at")) extractedExpiresAt = json.optLong("expires_at")
        if (json.has("expiresAt")) extractedExpiresAt = json.optLong("expiresAt")
        if (json.has("email")) extractedEmail = json.optString("email")
        if (json.has("user")) extractedEmail = json.optJSONObject("user")?.optString("email")
      } catch (_: Exception) {}
    } else if (trimmed.contains("=") && (trimmed.contains("TOKEN") || trimmed.contains("KEY") || trimmed.contains("OPENAI"))) {
      // 2. Check if .env or key-value format: OPENAI_API_KEY=sk-... or ACCESS_TOKEN=eyJ...
      trimmed.lines().forEach { line ->
        val l = line.trim()
        if (l.startsWith("OPENAI_API_KEY=") ||
            l.startsWith("ACCESS_TOKEN=") ||
            l.startsWith("CHATGPT_TOKEN=") ||
            l.startsWith("AUTH_TOKEN=") ||
            l.startsWith("BEARER_TOKEN=") ||
            l.startsWith("OPENAI_TOKEN=")) {
          extractedToken = l.substringAfter("=").trim('"', '\'', ' ')
        }
      }
    }

    // 3. Remove optional "Authorization:" / "Bearer " prefix
    if (extractedToken.startsWith("Authorization:", ignoreCase = true)) {
      extractedToken = extractedToken.substringAfter("Authorization:").trim()
    }
    if (extractedToken.startsWith("Bearer ", ignoreCase = true)) {
      extractedToken = extractedToken.substring(7).trim()
    }

    // Determine type & inspect JWT payload claims if present
    var type = "API Key / Bearer"
    if (extractedToken.count { it == '.' } == 2) {
      type = "JWT OAuth Access Token"
      try {
        val parts = extractedToken.split(".")
        if (parts.size >= 2) {
          val payloadBase64 = parts[1]
          val payloadJsonStr = String(Base64.decode(payloadBase64, Base64.URL_SAFE or Base64.NO_PADDING), StandardCharsets.UTF_8)
          val payloadJson = JSONObject(payloadJsonStr)

          val exp = payloadJson.optLong("exp", 0)
          if (exp > 0) {
            extractedExpiresAt = exp * 1000
          }
          val iat = payloadJson.optLong("iat", 0)
          if (iat > 0) {
            extractedIssuedAt = iat * 1000
          }
          if (extractedEmail == null) {
            extractedEmail = payloadJson.optString("email",
              payloadJson.optString("https://api.openai.com/profile.email",
                payloadJson.optString("https://api.openai.com/profile.user", null)))
          }
          extractedUserId = payloadJson.optString("sub", payloadJson.optString("user_id", null))
          extractedIssuer = payloadJson.optString("iss", null)

          val scopeStr = payloadJson.optString("scope", "")
          if (scopeStr.isNotBlank()) {
            extractedScopes.addAll(scopeStr.split(" "))
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "JWT claim decoding notice: ${e.message}")
      }
    } else if (extractedToken.startsWith("sk-proj-") || extractedToken.startsWith("sk-")) {
      type = "OpenAI Secret Key"
    } else if (extractedToken.length > 40) {
      type = "Access / Session Token"
    }

    val preview = if (extractedToken.length > 20) {
      "${extractedToken.take(10)}...${extractedToken.takeLast(8)}"
    } else {
      extractedToken
    }

    return ParsedTokenInfo(
      token = extractedToken,
      tokenType = type,
      expiresAt = extractedExpiresAt,
      issuedAt = extractedIssuedAt,
      email = extractedEmail,
      userId = extractedUserId,
      issuer = extractedIssuer,
      scopes = extractedScopes,
      preview = preview,
      rawLength = extractedToken.length
    )
  }

  /**
   * Import token from an InputStream (e.g. from File Picker)
   */
  suspend fun importTokenFromInputStream(context: Context, inputStream: InputStream): Result<ParsedTokenInfo> {
    return withContext(Dispatchers.IO) {
      try {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val content = reader.readText()
        reader.close()
        applyDirectAccessToken(context, content, validateNow = true)
      } catch (e: Exception) {
        Log.e(TAG, "Failed reading token file stream: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * Direct Upload / Paste Access Token: Connects immediately without needing to sign in
   */
  suspend fun applyDirectAccessToken(
    context: Context,
    rawInput: String,
    validateNow: Boolean = true
  ): Result<ParsedTokenInfo> {
    val info = parseRawTokenInput(rawInput)
      ?: return Result.failure(Exception("Could not extract a valid access token or API key from input"))

    return withContext(Dispatchers.IO) {
      try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
          .putString(KEY_ACCESS_TOKEN, info.token)
          .apply()

        if (info.expiresAt != null) {
          prefs.edit().putLong(KEY_EXPIRES_AT, info.expiresAt).apply()
        }

        _isAuthenticated.value = true
        _activeTokenInfo.value = info
        _authStatus.value = "Direct Token Connected (${info.tokenType})"
        Log.i(TAG, "Applied direct uploaded auth token: ${info.preview}")

        if (validateNow) {
          validateTokenWithApi(context, info.token)
        }

        Result.success(info)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to apply auth token: ${e.message}", e)
        Result.failure(e)
      }
    }
  }

  /**
   * Validates the active token against /v1/models endpoint
   */
  suspend fun validateTokenWithApi(context: Context, token: String): Boolean {
    _isValidatingToken.value = true
    return withContext(Dispatchers.IO) {
      try {
        val baseUrl = ChatManager.customBaseUrl.value
        val request = Request.Builder()
          .url("$baseUrl/models")
          .header("Authorization", "Bearer $token")
          .get()
          .build()

        val response = httpClient.newCall(request).execute()
        val success = response.isSuccessful
        if (success) {
          _authStatus.value = "🟢 Active & Verified (${_activeTokenInfo.value?.tokenType ?: "Token"})"
        } else {
          _authStatus.value = "⚠️ Token Active (HTTP ${response.code})"
        }
        _isValidatingToken.value = false
        success
      } catch (e: Exception) {
        _authStatus.value = "🟡 Connected (Offline Check)"
        _isValidatingToken.value = false
        false
      }
    }
  }

  fun logout(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .remove(KEY_ACCESS_TOKEN)
      .remove(KEY_REFRESH_TOKEN)
      .remove(KEY_DIRECT_API_KEY)
      .remove(KEY_EXPIRES_AT)
      .apply()
    _isAuthenticated.value = false
    _activeTokenInfo.value = null
    _authStatus.value = "Not Authenticated"
  }

  private fun UUIDRandom(): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..24).map { chars.random() }.joinToString("")
  }
}
