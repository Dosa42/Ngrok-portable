package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.chat.BackendHealthReport
import com.example.chat.ChatManager
import com.example.chat.ChatMessage
import com.example.chat.ChatgptOAuthPkceManager
import com.example.chat.ParsedTokenInfo
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.voice.GroqEnglishTtsManager
import com.example.voice.GroqWhisperSstManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatboxSection(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  var isInitialized by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    ChatManager.init(context)
    ChatgptOAuthPkceManager.init(context)
    GroqEnglishTtsManager.init(context)
    isInitialized = true
  }

  DisposableEffect(Unit) {
    onDispose {
      GroqEnglishTtsManager.stop()
      GroqWhisperSstManager.cancelRecording()
    }
  }

  val messages by ChatManager.messages.collectAsState()
  val isGenerating by ChatManager.isGenerating.collectAsState()
  val selectedModel by ChatManager.selectedModel.collectAsState()
  val availableModels by ChatManager.availableModels.collectAsState()
  val isFetchingModels by ChatManager.isFetchingModels.collectAsState()
  val modelCatalogError by ChatManager.modelCatalogError.collectAsState()
  val customBaseUrl by ChatManager.customBaseUrl.collectAsState()
  val reasoningEffort by ChatManager.reasoningEffort.collectAsState()
  val isAuthenticated by ChatgptOAuthPkceManager.isAuthenticated.collectAsState()
  val authStatus by ChatgptOAuthPkceManager.authStatus.collectAsState()
  val activeTokenInfo by ChatgptOAuthPkceManager.activeTokenInfo.collectAsState()

  val isRecording by GroqWhisperSstManager.isRecording.collectAsState()
  val isTranscribing by GroqWhisperSstManager.isTranscribing.collectAsState()
  val whisperError by GroqWhisperSstManager.lastError.collectAsState()
  val isTtsSpeaking by GroqEnglishTtsManager.isSpeaking.collectAsState()
  val isTtsSynthesizing by GroqEnglishTtsManager.isSynthesizing.collectAsState()
  val activeUtteranceId by GroqEnglishTtsManager.currentUtteranceId.collectAsState()
  val ttsError by GroqEnglishTtsManager.lastError.collectAsState()

  var inputText by remember { mutableStateOf("") }
  var showModelDropdown by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var showUploadTokenDialog by remember { mutableStateOf(false) }

  val selectedModelLabel = availableModels.firstOrNull { it.first == selectedModel }?.second
  val canSend = inputText.isNotBlank() && !isGenerating && !isFetchingModels && selectedModelLabel != null

  LaunchedEffect(isInitialized, isAuthenticated, authStatus, customBaseUrl, showSettingsDialog) {
    if (isInitialized && !showSettingsDialog) {
      ChatManager.fetchLiveModelsFromApi(context)
    }
  }

  // Audio permission launcher for Whisper v3 SST
  val audioPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      GroqWhisperSstManager.startRecording(context)
    } else {
      Toast.makeText(context, "Microphone permission is required for Groq Whisper SST", Toast.LENGTH_SHORT).show()
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
  val micScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "micScale"
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag("chatbox_section")
  ) {
    // Top Bar: Model Selector, Auth Token Status Pill, Reasoning Effort & Settings
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Model Dropdown
          Box {
            OutlinedButton(
              onClick = {
                ChatManager.invalidateModelCatalog()
                showModelDropdown = true
                scope.launch { ChatManager.fetchLiveModelsFromApi(context) }
              },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.height(36.dp).testTag("model_selector_button")
            ) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isFetchingModels) "Fetching models..." else selectedModelLabel ?: "Choose a model",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
              )
              Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
              expanded = showModelDropdown,
              onDismissRequest = { showModelDropdown = false }
            ) {
              if (isFetchingModels || availableModels.isEmpty()) {
                DropdownMenuItem(
                  text = { Text(if (isFetchingModels) "Fetching models..." else modelCatalogError ?: "No models available", fontSize = 12.sp) },
                  onClick = {},
                  enabled = false
                )
              }
              availableModels.forEach { (modelId, label) ->
                DropdownMenuItem(
                  text = {
                    Column {
                      Text(
                        text = label,
                        fontWeight = if (modelId == selectedModel) FontWeight.Bold else FontWeight.Normal,
                        color = if (modelId == selectedModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                      )
                      Text(
                        text = modelId,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  },
                  onClick = {
                    ChatManager.setModel(context, modelId)
                    showModelDropdown = false
                  }
                )
              }
            }
          }

          // Reasoning Effort Pill & Actions
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = reasoningEffort.uppercase(),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer
                )
              }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
              onClick = { showSettingsDialog = true },
              modifier = Modifier.size(34.dp).testTag("chat_settings_button")
            ) {
              Icon(Icons.Default.Settings, contentDescription = "AI & Backend Settings", modifier = Modifier.size(18.dp))
            }

            IconButton(
              onClick = { ChatManager.clearMessages() },
              modifier = Modifier.size(34.dp).testTag("clear_chat_button")
            ) {
              Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", modifier = Modifier.size(18.dp))
            }
          }
        }

        // Secondary Row: Direct "Upload / Paste ChatGPT Auth Token" Quick Action Strip
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isAuthenticated) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else StatusAmber.copy(alpha = 0.15f))
            .clickable { showUploadTokenDialog = true }
            .padding(horizontal = 8.dp, vertical = 5.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              imageVector = if (isAuthenticated) Icons.Default.Verified else Icons.Default.Key,
              contentDescription = null,
              tint = if (isAuthenticated) StatusGreen else StatusAmber,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isAuthenticated) {
                "Auth: ${activeTokenInfo?.tokenType ?: "Connected"} • ${activeTokenInfo?.preview ?: "Active"}"
              } else {
                "No Auth Token • Tap to Upload or Direct Paste"
              },
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (isAuthenticated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              maxLines = 1
            )
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(if (isAuthenticated) MaterialTheme.colorScheme.primary else StatusAmber)
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = if (isAuthenticated) "Manage Token" else "Upload Token",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
        }
      }
    }

    // Whisper Error / Info Banner
    if (whisperError != null) {
      Spacer(modifier = Modifier.height(4.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(StatusRed.copy(alpha = 0.15f))
          .padding(8.dp)
      ) {
        Text(
          text = whisperError ?: "",
          color = StatusRed,
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    // Groq TTS Error Banner
    if (ttsError != null) {
      Spacer(modifier = Modifier.height(4.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(StatusRed.copy(alpha = 0.15f))
          .padding(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = ttsError ?: "",
            color = StatusRed,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
          )
          TextButton(onClick = { showSettingsDialog = true }) {
            Text("Settings", fontSize = 10.sp, color = StatusRed)
          }
        }
      }
    }

    // Active Groq TTS Audio Strip
    AnimatedVisibility(visible = isTtsSynthesizing || isTtsSpeaking) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
          .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isTtsSynthesizing) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isTtsSynthesizing) "Synthesizing with Groq English TTS (Cloud LPU)..." else "Playing Groq English TTS Voice...",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
          )
        }
        TextButton(onClick = { GroqEnglishTtsManager.stop() }) {
          Text("Stop", fontSize = 11.sp, color = StatusRed)
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Messages List
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .testTag("chat_messages_list"),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(messages, key = { it.id }) { message ->
        ChatMessageBubble(
          message = message,
          isSpeaking = isTtsSpeaking && activeUtteranceId == message.id,
          isSynthesizing = isTtsSynthesizing && activeUtteranceId == message.id,
          onSpeakClick = {
            if ((isTtsSpeaking || isTtsSynthesizing) && activeUtteranceId == message.id) {
              GroqEnglishTtsManager.stop()
            } else {
              GroqEnglishTtsManager.speak(message.content, utteranceId = message.id)
            }
          }
        )
      }

      if (isGenerating) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ChatGPT is thinking with $selectedModel (${reasoningEffort} effort)...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Audio Recording / Transcribing Indicator Strip
    AnimatedVisibility(visible = isRecording || isTranscribing) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(if (isRecording) StatusRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer)
          .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (isRecording) Icons.Default.GraphicEq else Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = if (isRecording) StatusRed else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isRecording) "Recording Audio in English (Groq Whisper v3)..." else "Transcribing with Groq Whisper-large-v3...",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isRecording) StatusRed else MaterialTheme.colorScheme.onPrimaryContainer
          )
        }

        if (isRecording) {
          TextButton(onClick = { GroqWhisperSstManager.cancelRecording() }) {
            Text("Cancel", fontSize = 11.sp, color = StatusRed)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Bottom Chat Input Bar with Groq Whisper Microphone & Send Button
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 3.dp
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Groq Whisper v3 SST Microphone Button
        IconButton(
          onClick = {
            if (isRecording) {
              scope.launch {
                val transcribed = GroqWhisperSstManager.stopRecordingAndTranscribe(context)
                if (!transcribed.isNullOrBlank()) {
                  inputText = if (inputText.isBlank()) transcribed else "$inputText $transcribed"
                  ChatManager.sendMessage(context, transcribed)
                  inputText = ""
                }
              }
            } else {
              val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
              if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                GroqWhisperSstManager.startRecording(context)
              } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            }
          },
          modifier = Modifier
            .size(44.dp)
            .scale(if (isRecording) micScale else 1f)
            .clip(CircleShape)
            .background(if (isRecording) StatusRed else MaterialTheme.colorScheme.primaryContainer)
            .testTag("groq_whisper_mic_btn")
        ) {
          Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = "Groq Whisper Voice Input",
            tint = if (isRecording) Color.White else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Input Text Field
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = { Text(if (selectedModelLabel != null) "Ask $selectedModelLabel or tap mic for English SST..." else "Choose a model to start chatting...", fontSize = 12.sp) },
          modifier = Modifier
            .weight(1f)
            .testTag("chat_message_input"),
          maxLines = 4,
          shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Send Button
        IconButton(
          onClick = {
            if (inputText.isNotBlank()) {
              val textToSend = inputText
              inputText = ""
              scope.launch {
                ChatManager.sendMessage(context, textToSend)
              }
            }
          },
          enabled = canSend,
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .testTag("chat_send_button")
        ) {
          Icon(
            imageVector = Icons.Default.Send,
            contentDescription = "Send",
            tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }

  // Upload / Direct Paste ChatGPT Auth Token Dialog
  if (showUploadTokenDialog) {
    UploadChatgptAuthTokenDialog(
      onDismiss = { showUploadTokenDialog = false }
    )
  }

  // Comprehensive AI & Backend Settings Dialog
  if (showSettingsDialog) {
    ComprehensiveAiBackendSettingsDialog(
      onDismiss = { showSettingsDialog = false },
      onOpenUploadToken = {
        showSettingsDialog = false
        showUploadTokenDialog = true
      }
    )
  }
}

/**
 * Dedicated Upload & Direct Paste ChatGPT Auth Token Dialog
 * Allows 1-Click File Upload (.json / .env / .txt) or Direct Paste without sign-in
 */
@Composable
fun UploadChatgptAuthTokenDialog(
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var rawTokenInput by remember { mutableStateOf("") }
  var isApplying by remember { mutableStateOf(false) }
  var validationMessage by remember { mutableStateOf<String?>(null) }
  var isSuccess by remember { mutableStateOf(false) }

  val activeTokenInfo by ChatgptOAuthPkceManager.activeTokenInfo.collectAsState()
  val isAuthenticated by ChatgptOAuthPkceManager.isAuthenticated.collectAsState()

  // File Picker Launcher for JSON / txt / env files
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      scope.launch {
        isApplying = true
        try {
          val inputStream = context.contentResolver.openInputStream(uri)
          if (inputStream != null) {
            val result = ChatgptOAuthPkceManager.importTokenFromInputStream(context, inputStream)
            if (result.isSuccess) {
              val info = result.getOrNull()
              isSuccess = true
              validationMessage = "Successfully connected via uploaded file! Token type: ${info?.tokenType ?: "Active"}"
              Toast.makeText(context, "Auth Token Uploaded & Connected!", Toast.LENGTH_SHORT).show()
            } else {
              isSuccess = false
              validationMessage = "Import failed: ${result.exceptionOrNull()?.message}"
            }
          } else {
            validationMessage = "Could not open selected file."
          }
        } catch (e: Exception) {
          isSuccess = false
          validationMessage = "File error: ${e.message}"
        } finally {
          isApplying = false
        }
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Upload ChatGPT Auth Token", fontSize = 16.sp, fontWeight = FontWeight.Bold)
      }
    },
    text = {
      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
          Text(
            text = "Upload a token file (.json, .env, .txt) or paste a valid ChatGPT OAuth access token, session dump, or API key to connect instantly without signing in.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // 1. Direct File Upload Button
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Option 1: Upload Auth / Session File", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
              Text("Supports auth.json, session.json, .env, or raw token text files.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

              Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !isApplying
              ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Select & Upload File (.json / .env / .txt)", fontSize = 12.sp)
              }
            }
          }
        }

        // 2. Direct Paste Input Area
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Option 2: Direct Paste Token", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                TextButton(
                  onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cb.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                      val text = clip.getItemAt(0).text?.toString() ?: ""
                      rawTokenInput = text
                      Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                      Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                  }
                ) {
                  Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Paste Clipboard", fontSize = 11.sp)
                }
              }

              OutlinedTextField(
                value = rawTokenInput,
                onValueChange = { rawTokenInput = it },
                label = { Text("Paste Access Token / JWT / JSON / sk-...") },
                placeholder = { Text("eyJhbGciOiJSUzI1NiIs... or {\"access_token\": \"...\"}") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                shape = RoundedCornerShape(8.dp)
              )

              Button(
                onClick = {
                  if (rawTokenInput.isNotBlank()) {
                    scope.launch {
                      isApplying = true
                      val result = ChatgptOAuthPkceManager.applyDirectAccessToken(context, rawTokenInput, validateNow = true)
                      if (result.isSuccess) {
                        val info = result.getOrNull()
                        isSuccess = true
                        validationMessage = "Connected successfully! Token Type: ${info?.tokenType ?: "Active"}"
                        rawTokenInput = ""
                        Toast.makeText(context, "Token Connected & Verified!", Toast.LENGTH_SHORT).show()
                      } else {
                        isSuccess = false
                        validationMessage = "Token error: ${result.exceptionOrNull()?.message}"
                      }
                      isApplying = false
                    }
                  }
                },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = rawTokenInput.isNotBlank() && !isApplying
              ) {
                if (isApplying) {
                  CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Connecting & Validating...", fontSize = 11.sp)
                } else {
                  Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Apply & Direct Connect", fontSize = 12.sp)
                }
              }
            }
          }
        }

        // Validation / Result Message Banner
        if (validationMessage != null) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSuccess) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.15f))
                .padding(10.dp)
            ) {
              Text(
                text = validationMessage ?: "",
                color = if (isSuccess) StatusGreen else StatusRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        // 3. Active Token Telemetry & Inspection Card
        if (activeTokenInfo != null && isAuthenticated) {
          val info = activeTokenInfo!!
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Active Token In-Use", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = StatusGreen)
                  }

                  TextButton(
                    onClick = {
                      ChatgptOAuthPkceManager.logout(context)
                      Toast.makeText(context, "Token disconnected", Toast.LENGTH_SHORT).show()
                    }
                  ) {
                    Icon(Icons.Default.KeyOff, contentDescription = null, modifier = Modifier.size(14.dp), tint = StatusRed)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Disconnect", fontSize = 11.sp, color = StatusRed)
                  }
                }

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Type:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(info.tokenType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Preview:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(info.preview, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                if (info.email != null) {
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Account Email:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(info.email, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                  }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Expiry:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(info.formattedExpiry, fontSize = 11.sp, color = if (info.isExpired) StatusRed else StatusGreen)
                }

                if (info.scopes.isNotEmpty()) {
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Scopes:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(info.scopes.joinToString(", "), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) {
        Text("Done")
      }
    }
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComprehensiveAiBackendSettingsDialog(
  onDismiss: () -> Unit,
  onOpenUploadToken: () -> Unit = {}
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var apiKeyInput by remember { mutableStateOf(ChatgptOAuthPkceManager.getDirectApiKey(context)) }
  var clientIdInput by remember { mutableStateOf(ChatgptOAuthPkceManager.getOAuthClientId(context)) }
  var authCodeInput by remember { mutableStateOf("") }
  var customBaseUrlInput by remember { mutableStateOf(ChatManager.customBaseUrl.value) }
  var reasoningEffortState by remember { mutableStateOf(ChatManager.reasoningEffort.value) }
  var groqApiKeyInput by remember { mutableStateOf(GroqWhisperSstManager.getGroqApiKey(context)) }
  var whisperModelInput by remember { mutableStateOf(GroqWhisperSstManager.getWhisperModel(context)) }
  var ttsVoiceState by remember { mutableStateOf(GroqEnglishTtsManager.getTtsVoice(context)) }
  var ttsModelState by remember { mutableStateOf(GroqEnglishTtsManager.getTtsModel(context)) }
  var systemPromptInput by remember { mutableStateOf(ChatManager.systemPrompt.value) }

  var hideApiKey by remember { mutableStateOf(true) }
  var hideGroqKey by remember { mutableStateOf(true) }

  val isFetchingModels by ChatManager.isFetchingModels.collectAsState()
  val isTestingHealth by ChatManager.isTestingHealth.collectAsState()
  val healthReport by ChatManager.healthReport.collectAsState()
  val isAuthenticated by ChatgptOAuthPkceManager.isAuthenticated.collectAsState()
  val activeTokenInfo by ChatgptOAuthPkceManager.activeTokenInfo.collectAsState()

  val reasoningOptions = listOf("low", "medium", "high")
  val promptPresets = listOf(
    "Expert Software Engineer" to "You are an expert full-stack software engineer and cloud systems architect. Write clean, optimal code and provide precise technical reasoning in English.",
    "Concise Voice Companion" to "You are a concise, ultra-fast voice assistant. Keep answers brief (1-3 sentences) in plain English.",
    "Security & Tunnel Auditor" to "You are a cybersecurity expert analyzing network tunnels, reverse proxies, and API security. Focus on security best practices."
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("AI & Backend Engine Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
      }
    },
    text = {
      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Section 1: Hot Reload & Live Health Diagnostics
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Backend Diagnostics & Hot Loading", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Fetch Live Models Button
                Button(
                  onClick = {
                    scope.launch {
                      val res = ChatManager.fetchLiveModelsFromApi(context)
                      if (res.isSuccess) {
                        Toast.makeText(context, "Discovered ${res.getOrNull()} live models from API!", Toast.LENGTH_SHORT).show()
                      } else {
                        Toast.makeText(context, "Model discovery error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                      }
                    }
                  },
                  modifier = Modifier.weight(1f).height(36.dp),
                  shape = RoundedCornerShape(8.dp),
                  enabled = !isFetchingModels
                ) {
                  if (isFetchingModels) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.White)
                  } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Fetch Models", fontSize = 11.sp)
                  }
                }

                // Hot Reload Button
                OutlinedButton(
                  onClick = {
                    ChatManager.hotReloadConfig(context)
                    Toast.makeText(context, "Hot reload applied to all configs!", Toast.LENGTH_SHORT).show()
                  },
                  modifier = Modifier.weight(1f).height(36.dp),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Hot Reload", fontSize = 11.sp)
                }
              }

              // Real Health Test Button
              Button(
                onClick = {
                  scope.launch {
                    ChatManager.runBackendHealthTest(context)
                  }
                },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                enabled = !isTestingHealth
              ) {
                if (isTestingHealth) {
                  CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Running Real Diagnostics...", fontSize = 11.sp)
                } else {
                  Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Run Real Health Test", fontSize = 11.sp)
                }
              }

              // Health Test Report View
              if (healthReport != null) {
                val rep = healthReport!!
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Auth Status:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rep.authStatusMessage, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (rep.isAuthValid) StatusGreen else StatusRed)
                  }
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("API Models Latency:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${rep.modelsEndpointLatencyMs ?: 0}ms (${rep.modelsCount} models)", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                  }
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mini Ping Completion:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (rep.completionTestSuccess) "Passed (${rep.completionTestLatencyMs}ms)" else "Skipped/Failed", fontSize = 10.sp, color = if (rep.completionTestSuccess) StatusGreen else StatusAmber)
                  }
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Groq Voice Endpoint:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (rep.groqWhisperSuccess) "Online (${rep.groqWhisperLatencyMs}ms)" else "Not Configured", fontSize = 10.sp, color = if (rep.groqWhisperSuccess) StatusGreen else StatusAmber)
                  }
                }
              }
            }
          }
        }

        // Section 2: Direct Upload & Connect Auth Token
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Upload & Direct Auth Token", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
              Text("Upload or paste any valid access token / session dump to direct connect without sign-in.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

              Button(
                onClick = onOpenUploadToken,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Upload / Paste Token Screen", fontSize = 11.sp)
              }
            }
          }
        }

        // Section 3: Provider Base URL
        item {
          Text("Backend Provider Endpoint", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = customBaseUrlInput,
            onValueChange = {
              customBaseUrlInput = it
              ChatManager.setCustomBaseUrl(context, it)
            },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
          )

          Spacer(modifier = Modifier.height(6.dp))

          // Presets
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(
              onClick = {
                customBaseUrlInput = ChatManager.OPENAI_BASE_URL
                ChatManager.setCustomBaseUrl(context, ChatManager.OPENAI_BASE_URL)
              },
              label = { Text("OpenAI", fontSize = 10.sp) }
            )
            AssistChip(
              onClick = {
                customBaseUrlInput = ChatManager.GROQ_BASE_URL
                ChatManager.setCustomBaseUrl(context, ChatManager.GROQ_BASE_URL)
              },
              label = { Text("Groq LPU", fontSize = 10.sp) }
            )
          }
        }

        item { HorizontalDivider() }

        // Section 4: ChatGPT OAuth 2.0 PKCE & Direct Key
        item {
          Text("ChatGPT OAuth 2.0 PKCE & API Key", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.height(4.dp))

          Button(
            onClick = {
              ChatgptOAuthPkceManager.setOAuthClientId(context, clientIdInput)
              ChatgptOAuthPkceManager.startOAuthFlow(context, clientIdInput)
            },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Login via ChatGPT OAuth 2.0 PKCE", fontSize = 11.sp)
          }

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = authCodeInput,
              onValueChange = { authCodeInput = it },
              label = { Text("OAuth Code / Callback URL", fontSize = 10.sp) },
              modifier = Modifier.weight(1f),
              singleLine = true
            )
            Button(
              onClick = {
                val code = if (authCodeInput.contains("code=")) {
                  authCodeInput.substringAfter("code=").substringBefore("&")
                } else authCodeInput
                scope.launch {
                  val s = ChatgptOAuthPkceManager.exchangeCodeForToken(context, code, null)
                  if (s) Toast.makeText(context, "Connected!", Toast.LENGTH_SHORT).show()
                }
              },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.padding(top = 6.dp)
            ) {
              Text("Exchange", fontSize = 10.sp)
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          OutlinedTextField(
            value = apiKeyInput,
            onValueChange = {
              apiKeyInput = it
              ChatgptOAuthPkceManager.setDirectApiKey(context, it)
              ChatManager.invalidateModelCatalog()
            },
            label = { Text("Bearer API Key (sk-...)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (hideApiKey) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
              TextButton(onClick = { hideApiKey = !hideApiKey }) {
                Text(if (hideApiKey) "Show" else "Hide", fontSize = 10.sp)
              }
            }
          )
        }

        item { HorizontalDivider() }

        // Section 5: Reasoning Effort & Model Parameters
        item {
          Text("Reasoning Effort Configuration", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.height(4.dp))
          Text("Controls thinking budget for reasoning models (o1, o3-mini, gpt-6-astra, gpt-5.4).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            reasoningOptions.forEach { effort ->
              FilterChip(
                selected = reasoningEffortState == effort,
                onClick = {
                  reasoningEffortState = effort
                  ChatManager.setReasoningEffort(context, effort)
                },
                label = { Text(effort.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.weight(1f)
              )
            }
          }

        }

        item { HorizontalDivider() }

        // Section 6: System Instructions Prompt
        item {
          Text("System Instructions Prompt", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.height(6.dp))

          // Quick Presets
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            promptPresets.forEach { (title, prompt) ->
              AssistChip(
                onClick = {
                  systemPromptInput = prompt
                  ChatManager.setSystemPrompt(context, prompt)
                },
                label = { Text(title, fontSize = 10.sp) }
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          OutlinedTextField(
            value = systemPromptInput,
            onValueChange = {
              systemPromptInput = it
              ChatManager.setSystemPrompt(context, it)
            },
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
          )
        }

        item { HorizontalDivider() }

        // Section 7: Pure Groq Voice Engine (Whisper v3 SST + English TTS)
        item {
          Text("Groq Voice Pipeline (Zero Native Phone TTS)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
          Text("Hardcoded English SST & cloud-native Groq English TTS. Native phone TTS is disabled.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(modifier = Modifier.height(4.dp))

          OutlinedTextField(
            value = groqApiKeyInput,
            onValueChange = {
              groqApiKeyInput = it
              GroqWhisperSstManager.setGroqApiKey(context, it)
            },
            label = { Text("Groq API Key (gsk_...)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (hideGroqKey) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
              TextButton(onClick = { hideGroqKey = !hideGroqKey }) {
                Text(if (hideGroqKey) "Show" else "Hide", fontSize = 10.sp)
              }
            }
          )

          Spacer(modifier = Modifier.height(6.dp))

          OutlinedTextField(
            value = whisperModelInput,
            onValueChange = {
              whisperModelInput = it
              GroqWhisperSstManager.setWhisperModel(context, it)
            },
            label = { Text("Groq SST Model (whisper-large-v3)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          Spacer(modifier = Modifier.height(6.dp))

          OutlinedTextField(
            value = ttsModelState,
            onValueChange = {
              ttsModelState = it
              GroqEnglishTtsManager.setTtsModel(context, it)
            },
            label = { Text("Groq TTS Model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text("Groq English TTS Voice Persona:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          Spacer(modifier = Modifier.height(4.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            GroqEnglishTtsManager.AVAILABLE_VOICES.forEach { (voiceId, label) ->
              FilterChip(
                selected = ttsVoiceState == voiceId,
                onClick = {
                  ttsVoiceState = voiceId
                  GroqEnglishTtsManager.setTtsVoice(context, voiceId)
                },
                label = { Text(label, fontSize = 10.sp) }
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) {
        Text("Done")
      }
    }
  )
}

@Composable
fun ChatMessageBubble(
  message: ChatMessage,
  isSpeaking: Boolean,
  isSynthesizing: Boolean,
  onSpeakClick: () -> Unit
) {
  val context = LocalContext.current
  val isUser = message.role == "user"

  val bubbleBg = when {
    message.isError -> StatusRed.copy(alpha = 0.15f)
    isUser -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surface
  }

  val textColor = when {
    message.isError -> StatusRed
    isUser -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurface
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth(if (isUser) 0.85f else 0.95f),
      shape = RoundedCornerShape(
        topStart = 14.dp,
        topEnd = 14.dp,
        bottomStart = if (isUser) 14.dp else 2.dp,
        bottomEnd = if (isUser) 2.dp else 14.dp
      ),
      colors = CardDefaults.cardColors(containerColor = bubbleBg),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        // Bubble Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (isUser) "You" else (message.modelUsed ?: "ChatGPT"),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            fontFamily = FontFamily.Monospace
          )

          Row(verticalAlignment = Alignment.CenterVertically) {
            // Groq English TTS Speak Aloud Action
            if (!isUser && !message.isError) {
              IconButton(
                onClick = onSpeakClick,
                modifier = Modifier.size(24.dp)
              ) {
                if (isSynthesizing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                  )
                } else {
                  Icon(
                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                    contentDescription = "Speak Aloud via Groq English TTS",
                    tint = if (isSpeaking) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }

            // Copy Action
            IconButton(
              onClick = {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Chat Message", message.content))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy message",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message Body
        Text(
          text = message.content,
          fontSize = 13.sp,
          color = textColor,
          lineHeight = 18.sp
        )
      }
    }
  }
}
