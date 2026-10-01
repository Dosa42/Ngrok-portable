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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

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

  private val _authStatus = MutableStateFlow<String>("Not Authenticated")
  val authStatus: StateFlow<String> = _authStatus.asStateFlow()

  private val _isAuthenticated = MutableStateFlow(false)
  val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  fun init(context: Context) {
    val token = getEffectiveToken(context)
    val hasToken = token.isNotBlank()
    _isAuthenticated.value = hasToken
    _authStatus.value = if (hasToken) "Authenticated & Ready" else "Not Authenticated"
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
    _authStatus.value = if (effective.isNotBlank()) "API Key Active" else "Not Authenticated"
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
          Log.i(TAG, "OAuth PKCE token exchange succeeded")
          true
        } else {
          // If public token endpoint failed (e.g. mock / test code), accept code as direct token if configured
          Log.w(TAG, "Token exchange returned ${response.code}: $bodyText")
          _authStatus.value = "Token error ($response.code). If using custom key, enter API Key."
          false
        }
      } catch (e: Exception) {
        Log.e(TAG, "OAuth PKCE token exchange error: ${e.message}", e)
        _authStatus.value = "Exchange failed: ${e.message}"
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
      .apply()
    _isAuthenticated.value = false
    _authStatus.value = "Logged Out"
  }

  private fun UUIDRandom(): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..24).map { chars.random() }.joinToString("")
  }
}
