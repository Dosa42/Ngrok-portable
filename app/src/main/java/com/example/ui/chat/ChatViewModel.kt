package com.example.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemini.ChatMessageItem
import com.example.gemini.GeminiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

  private val _messages = MutableStateFlow<List<ChatMessageItem>>(
    listOf(
      ChatMessageItem(
        text = "The optional Gemini assistant requires a configured API key. " +
          "The tunnel's live connection status is shown in Tunnel Control.",
        isUser = false
      )
    )
  )
  val messages: StateFlow<List<ChatMessageItem>> = _messages.asStateFlow()

  private val _inputText = MutableStateFlow("")
  val inputText: StateFlow<String> = _inputText.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  val suggestionChips = listOf(
    "Check tunnel status",
    "How to forward port 8085",
    "Curl test commands",
    "Webhook receiver guide",
    "Explain ngrok security"
  )

  fun onInputChanged(text: String) {
    _inputText.value = text
  }

  fun sendMessage(promptText: String? = null) {
    val textToSend = (promptText ?: _inputText.value).trim()
    if (textToSend.isBlank() || _isLoading.value) return

    _inputText.value = ""

    val userMessage = ChatMessageItem(text = textToSend, isUser = true)
    _messages.update { it + userMessage }
    _isLoading.value = true

    viewModelScope.launch {
      val (response, latencyMs) = GeminiService.generateChatResponse(_messages.value)
      val botMessage = ChatMessageItem(
        text = response,
        isUser = false,
        latencyMs = latencyMs
      )
      _messages.update { it + botMessage }
      _isLoading.value = false
    }
  }

  fun clearChat() {
    _messages.value = listOf(
      ChatMessageItem(
        text = "Conversation cleared. Ready for your next query!",
        isUser = false
      )
    )
  }
}
