package com.example.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GroqWhisperSstManager {
  private const val TAG = "GroqWhisperSstManager"
  private const val PREFS_NAME = "groq_voice_prefs"
  private const val KEY_GROQ_API_KEY = "groq_api_key"
  private const val KEY_WHISPER_MODEL = "whisper_model"

  private const val DEFAULT_WHISPER_MODEL = "whisper-large-v3"
  // Hardcoded English language for maximum transcription accuracy
  const val HARDCODED_LANGUAGE = "en"

  private var mediaRecorder: MediaRecorder? = null
  private var currentAudioFile: File? = null

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _isTranscribing = MutableStateFlow(false)
  val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  fun getGroqApiKey(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_GROQ_API_KEY, "") ?: ""
  }

  fun setGroqApiKey(context: Context, key: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_GROQ_API_KEY, key.trim())
      .apply()
  }

  fun getWhisperModel(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_WHISPER_MODEL, DEFAULT_WHISPER_MODEL) ?: DEFAULT_WHISPER_MODEL
  }

  fun setWhisperModel(context: Context, model: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_WHISPER_MODEL, model)
      .apply()
  }

  fun startRecording(context: Context): Boolean {
    if (_isRecording.value) return true
    _lastError.value = null
    try {
      val audioDir = File(context.cacheDir, "audio_recordings")
      if (!audioDir.exists()) audioDir.mkdirs()
      currentAudioFile = File(audioDir, "groq_sst_${System.currentTimeMillis()}.m4a")

      mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
      } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
      }.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(128000)
        setAudioSamplingRate(44100)
        setOutputFile(currentAudioFile!!.absolutePath)
        prepare()
        start()
      }

      _isRecording.value = true
      Log.i(TAG, "Started recording to: ${currentAudioFile?.absolutePath}")
      return true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start audio recording: ${e.message}", e)
      _lastError.value = "Recording error: ${e.message}"
      _isRecording.value = false
      releaseRecorder()
      return false
    }
  }

  suspend fun stopRecordingAndTranscribe(context: Context): String? {
    if (!_isRecording.value) return null
    try {
      mediaRecorder?.apply {
        stop()
        release()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error stopping recorder: ${e.message}")
    } finally {
      mediaRecorder = null
      _isRecording.value = false
    }

    val audioFile = currentAudioFile
    if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
      _lastError.value = "Audio recording is empty or not found."
      return null
    }

    val apiKey = getGroqApiKey(context)
    if (apiKey.isBlank()) {
      _lastError.value = "Groq API key is required for Whisper v3 SST. Please configure it in Voice/Chat Settings."
      return null
    }

    _isTranscribing.value = true
    _lastError.value = null

    return withContext(Dispatchers.IO) {
      try {
        val model = getWhisperModel(context)
        val fileBody = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())

        // Multipart body with Groq Whisper v3 specification & Hardcoded English language for high accuracy
        val requestBody = MultipartBody.Builder()
          .setType(MultipartBody.FORM)
          .addFormDataPart("file", audioFile.name, fileBody)
          .addFormDataPart("model", model)
          .addFormDataPart("language", HARDCODED_LANGUAGE) // Hardcoded English
          .addFormDataPart("response_format", "json")
          .addFormDataPart("temperature", "0.0")
          .build()

        val request = Request.Builder()
          .url("https://api.groq.com/openai/v1/audio/transcriptions")
          .header("Authorization", "Bearer $apiKey")
          .post(requestBody)
          .build()

        val response = httpClient.newCall(request).execute()
        val responseText = response.body?.string() ?: ""

        if (!response.isSuccessful) {
          val errorMsg = try {
            JSONObject(responseText).optJSONObject("error")?.optString("message") ?: responseText
          } catch (_: Exception) {
            responseText
          }
          _lastError.value = "Groq Whisper Error ($response.code): $errorMsg"
          return@withContext null
        }

        val json = JSONObject(responseText)
        val transcribedText = json.optString("text", "").trim()
        Log.i(TAG, "Groq Whisper v3 SST Transcription: $transcribedText")
        transcribedText
      } catch (e: Exception) {
        Log.e(TAG, "Transcription failed: ${e.message}", e)
        _lastError.value = "Transcription failure: ${e.message}"
        null
      } finally {
        _isTranscribing.value = false
        // Cleanup local audio recording file
        try {
          audioFile.delete()
        } catch (_: Exception) {}
      }
    }
  }

  fun cancelRecording() {
    if (!_isRecording.value) return
    try {
      mediaRecorder?.apply {
        stop()
        release()
      }
    } catch (_: Exception) {}
    releaseRecorder()
    _isRecording.value = false
    try {
      currentAudioFile?.delete()
    } catch (_: Exception) {}
  }

  private fun releaseRecorder() {
    try {
      mediaRecorder?.release()
    } catch (_: Exception) {}
    mediaRecorder = null
  }
}
