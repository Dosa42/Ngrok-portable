package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object GroqEnglishTtsManager : TextToSpeech.OnInitListener {
  private const val TAG = "GroqEnglishTtsManager"
  private const val PREFS_NAME = "groq_voice_prefs"
  private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
  private const val KEY_TTS_PITCH = "tts_pitch"

  private var ttsEngine: TextToSpeech? = null
  private var isInitialized = false
  private var pendingTextToSpeak: String? = null

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _currentUtteranceId = MutableStateFlow<String?>(null)
  val currentUtteranceId: StateFlow<String?> = _currentUtteranceId.asStateFlow()

  fun init(context: Context) {
    if (ttsEngine == null) {
      ttsEngine = TextToSpeech(context.applicationContext, this)
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      // Hardcoded English for maximum voice fidelity and pronunciation accuracy
      val result = ttsEngine?.setLanguage(Locale.US)
      if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        ttsEngine?.setLanguage(Locale.ENGLISH)
      }
      isInitialized = true
      setupProgressListener()
      Log.i(TAG, "English TTS Engine initialized successfully")

      pendingTextToSpeak?.let {
        speak(it)
        pendingTextToSpeak = null
      }
    } else {
      Log.e(TAG, "TTS Engine initialization failed with status: $status")
    }
  }

  private fun setupProgressListener() {
    ttsEngine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) {
        _isSpeaking.value = true
        _currentUtteranceId.value = utteranceId
      }

      override fun onDone(utteranceId: String?) {
        _isSpeaking.value = false
        _currentUtteranceId.value = null
      }

      override fun onError(utteranceId: String?) {
        _isSpeaking.value = false
        _currentUtteranceId.value = null
      }
    })
  }

  fun speak(text: String, utteranceId: String = "chat_${System.currentTimeMillis()}") {
    if (text.isBlank()) return

    if (!isInitialized || ttsEngine == null) {
      pendingTextToSpeak = text
      return
    }

    // Ensure English locale is enforced
    ttsEngine?.language = Locale.US
    ttsEngine?.setSpeechRate(1.0f)
    ttsEngine?.setPitch(1.0f)

    ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    _isSpeaking.value = true
    _currentUtteranceId.value = utteranceId
  }

  fun stop() {
    try {
      ttsEngine?.stop()
      _isSpeaking.value = false
      _currentUtteranceId.value = null
    } catch (e: Exception) {
      Log.w(TAG, "Error stopping TTS: ${e.message}")
    }
  }

  fun shutdown() {
    try {
      ttsEngine?.stop()
      ttsEngine?.shutdown()
      ttsEngine = null
      isInitialized = false
    } catch (e: Exception) {
      Log.w(TAG, "Error shutting down TTS: ${e.message}")
    }
  }
}
