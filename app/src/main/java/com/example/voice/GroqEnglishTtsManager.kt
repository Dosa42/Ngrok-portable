package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Pure Groq English Text-to-Speech Engine.
 * Direct cloud synthesis using Groq's high-speed LPU audio speech API
 * (model: canopylabs/orpheus-v1-english).
 * ZERO native phone Android TextToSpeech used.
 */
object GroqEnglishTtsManager {
  private const val TAG = "GroqEnglishTtsManager"
  private const val PREFS_NAME = "groq_voice_prefs"
  private const val KEY_TTS_VOICE = "groq_tts_voice"
  private const val KEY_TTS_MODEL = "groq_tts_model"

  const val GROQ_TTS_ENDPOINT = "https://api.groq.com/openai/v1/audio/speech"
  const val DEFAULT_MODEL = "canopylabs/orpheus-v1-english"
  const val DEFAULT_VOICE = "autumn"

  val AVAILABLE_VOICES = listOf(
    "autumn" to "Autumn (Female Expressive)",
    "diana" to "Diana (Female Warm)",
    "hannah" to "Hannah (Female Clear)",
    "austin" to "Austin (Male Confident)",
    "daniel" to "Daniel (Male Natural)",
    "troy" to "Troy (Male Resonant)"
  )

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var currentJob: Job? = null
  private var mediaPlayer: MediaPlayer? = null
  private var appContext: Context? = null

  private val _isSynthesizing = MutableStateFlow(false)
  val isSynthesizing: StateFlow<Boolean> = _isSynthesizing.asStateFlow()

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _currentUtteranceId = MutableStateFlow<String?>(null)
  val currentUtteranceId: StateFlow<String?> = _currentUtteranceId.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  fun init(context: Context) {
    appContext = context.applicationContext
  }

  fun getTtsVoice(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_TTS_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
  }

  fun setTtsVoice(context: Context, voice: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_TTS_VOICE, voice.trim())
      .apply()
  }

  fun getTtsModel(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_TTS_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
  }

  fun setTtsModel(context: Context, model: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_TTS_MODEL, model.trim())
      .apply()
  }

  /**
   * Synthesize text using Groq English TTS and play audio stream via MediaPlayer.
   * Completely bypasses phone native Android TextToSpeech.
   */
  fun speak(
    text: String,
    utteranceId: String = "chat_${System.currentTimeMillis()}",
    contextOverride: Context? = null
  ) {
    val cleanText = text.trim()
    if (cleanText.isBlank()) return

    val ctx = contextOverride ?: appContext
    if (ctx == null) {
      Log.e(TAG, "Context not initialized for GroqEnglishTtsManager")
      _lastError.value = "TTS Error: Context uninitialized"
      return
    }

    val groqApiKey = GroqWhisperSstManager.getGroqApiKey(ctx)
    if (groqApiKey.isBlank()) {
      val errorMsg = "Groq API Key missing. Please set your Groq API key in Settings to use Groq English TTS."
      Log.w(TAG, errorMsg)
      _lastError.value = errorMsg
      return
    }

    stop() // Stop any previous playback

    _currentUtteranceId.value = utteranceId
    _isSynthesizing.value = true
    _lastError.value = null

    val voice = getTtsVoice(ctx)
    val model = getTtsModel(ctx)

    currentJob = scope.launch {
      try {
        val payload = JSONObject().apply {
          put("model", model)
          put("input", cleanText)
          put("voice", voice)
          put("response_format", "wav")
        }

        val request = Request.Builder()
          .url(GROQ_TTS_ENDPOINT)
          .header("Authorization", "Bearer $groqApiKey")
          .header("Content-Type", "application/json")
          .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
          .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
          val errorBody = response.body?.string() ?: ""
          _isSynthesizing.value = false
          _currentUtteranceId.value = null
          _lastError.value = "Groq TTS Error (${response.code}): $errorBody"
          Log.e(TAG, "Groq TTS synthesis failed: ${response.code} $errorBody")
          return@launch
        }

        val audioBytes = response.body?.bytes()
        _isSynthesizing.value = false

        if (audioBytes == null || audioBytes.isEmpty()) {
          _lastError.value = "Groq TTS returned empty audio data"
          _currentUtteranceId.value = null
          return@launch
        }

        // Cache audio to local temp file for MediaPlayer
        val audioDir = File(ctx.cacheDir, "groq_tts_cache")
        if (!audioDir.exists()) audioDir.mkdirs()
        val tempAudioFile = File(audioDir, "groq_tts_${System.currentTimeMillis()}.wav")

        FileOutputStream(tempAudioFile).use { it.write(audioBytes) }

        withContext(Dispatchers.Main) {
          playAudioFile(tempAudioFile, utteranceId)
        }
      } catch (e: Exception) {
        _isSynthesizing.value = false
        _isSpeaking.value = false
        _currentUtteranceId.value = null
        _lastError.value = "Groq TTS exception: ${e.message}"
        Log.e(TAG, "Error synthesizing or playing Groq TTS", e)
      }
    }
  }

  private fun playAudioFile(audioFile: File, utteranceId: String) {
    try {
      mediaPlayer?.release()
      mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .build()
        )
        setDataSource(audioFile.absolutePath)
        prepare()
        setOnCompletionListener {
          _isSpeaking.value = false
          _currentUtteranceId.value = null
          releasePlayer()
          audioFile.delete()
        }
        setOnErrorListener { _, what, extra ->
          Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
          _isSpeaking.value = false
          _currentUtteranceId.value = null
          releasePlayer()
          audioFile.delete()
          true
        }
        start()
      }
      _isSpeaking.value = true
      _currentUtteranceId.value = utteranceId
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start MediaPlayer for Groq TTS: ${e.message}", e)
      _isSpeaking.value = false
      _currentUtteranceId.value = null
      _lastError.value = "Playback error: ${e.message}"
      audioFile.delete()
    }
  }

  fun stop() {
    currentJob?.cancel()
    currentJob = null
    _isSynthesizing.value = false
    _isSpeaking.value = false
    _currentUtteranceId.value = null

    try {
      mediaPlayer?.let {
        if (it.isPlaying) {
          it.stop()
        }
      }
      releasePlayer()
    } catch (e: Exception) {
      Log.w(TAG, "Error stopping MediaPlayer: ${e.message}")
    }
  }

  private fun releasePlayer() {
    try {
      mediaPlayer?.release()
      mediaPlayer = null
    } catch (e: Exception) {
      Log.w(TAG, "Error releasing MediaPlayer: ${e.message}")
    }
  }

  fun shutdown() {
    stop()
  }
}
