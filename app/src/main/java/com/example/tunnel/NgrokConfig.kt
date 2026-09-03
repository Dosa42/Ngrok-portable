package com.example.tunnel

import android.content.Context
import android.content.SharedPreferences

object NgrokConfig {
  const val DEFAULT_AUTH_TOKEN: String = "3InS37Gf43NQLUhtvAfaVJJvFhc_2v2xsPryuj6R5UNLcFoYT"
  const val DEFAULT_LOCAL_PORT: Int = 8085
  private const val PREFS_NAME = "ngrok_prefs"
  private const val KEY_AUTH_TOKEN = "key_auth_token"
  private const val KEY_PORT = "key_local_port"
  private const val KEY_AUTO_START = "key_auto_start"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun getAuthToken(context: Context): String {
    val token = getPrefs(context).getString(KEY_AUTH_TOKEN, null)
    return if (token.isNullOrBlank()) DEFAULT_AUTH_TOKEN else token
  }

  fun setAuthToken(context: Context, token: String) {
    getPrefs(context).edit().putString(KEY_AUTH_TOKEN, token.trim()).apply()
  }

  fun getLocalPort(context: Context): Int {
    return getPrefs(context).getInt(KEY_PORT, DEFAULT_LOCAL_PORT)
  }

  fun setLocalPort(context: Context, port: Int) {
    getPrefs(context).edit().putInt(KEY_PORT, port).apply()
  }

  fun resetToDefaults(context: Context) {
    getPrefs(context).edit().clear().apply()
  }
}
