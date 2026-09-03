package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.ChatMessageItem
import com.example.ui.chat.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatSection(
  viewModel: ChatViewModel,
  modifier: Modifier = Modifier
) {
  val messages by viewModel.messages.collectAsState()
  val inputText by viewModel.inputText.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val listState = rememberLazyListState()

  // Scroll to bottom when new messages arrive
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .testTag("chat_section")
  ) {
    // Header Bar with Model Badge and Clear Chat
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Bolt,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "gemini-3.1-flash-lite (Fast Copilot)",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      IconButton(
        onClick = { viewModel.clearChat() },
        modifier = Modifier.size(32.dp).testTag("clear_chat_button")
      ) {
        Icon(
          imageVector = Icons.Default.DeleteSweep,
          contentDescription = "Clear Chat",
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          modifier = Modifier.size(18.dp)
        )
      }
    }

    // Messages Thread (Scrollable)
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      items(messages, key = { it.id }) { message ->
        ChatMessageBubble(message = message)
      }

      if (isLoading) {
        item {
          TypingIndicator()
        }
      }
    }

    // Quick Suggestion Chips (Horizontal Scroll)
    val scrollState = rememberScrollState()
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(scrollState)
        .padding(horizontal = 16.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      viewModel.suggestionChips.forEach { chipText ->
        AssistChip(
          onClick = { viewModel.sendMessage(chipText) },
          label = { Text(chipText, fontSize = 12.sp) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(12.dp),
              tint = MaterialTheme.colorScheme.primary
            )
          },
          colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          ),
          border = null,
          shape = RoundedCornerShape(16.dp)
        )
      }
    }

    // Bottom Input Bar
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 4.dp
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = { viewModel.onInputChanged(it) },
          placeholder = { Text("Ask fast Gemini agent...", fontSize = 14.sp) },
          modifier = Modifier
            .weight(1f)
            .testTag("chat_input"),
          shape = RoundedCornerShape(24.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
          ),
          maxLines = 4,
          trailingIcon = {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
              )
            } else {
              IconButton(
                onClick = { viewModel.sendMessage() },
                enabled = inputText.isNotBlank(),
                modifier = Modifier.testTag("send_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = "Send",
                  tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
              }
            }
          }
        )
      }
    }
  }
}

@Composable
fun ChatMessageBubble(message: ChatMessageItem) {
  val context = LocalContext.current
  val isUser = message.isUser

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Top
  ) {
    if (!isUser) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Bolt,
          contentDescription = "Gemini",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
    }

    Column(
      horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
      modifier = Modifier.widthIn(max = 300.dp)
    ) {
      Box(
        modifier = Modifier
          .clip(
            RoundedCornerShape(
              topStart = 16.dp,
              topEnd = 16.dp,
              bottomStart = if (isUser) 16.dp else 4.dp,
              bottomEnd = if (isUser) 4.dp else 16.dp
            )
          )
          .background(
            if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
          )
          .padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Column {
          Text(
            text = message.text,
            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 20.sp
          )
        }
      }

      // Metadata Row: Timestamp, Latency Badge, Copy Action
      Row(
        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
        Text(
          text = timeStr,
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        if (message.latencyMs != null) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
              .padding(horizontal = 4.dp, vertical = 1.dp)
          ) {
            Text(
              text = "⚡ ${message.latencyMs}ms",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        IconButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Copied Message", message.text))
            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.size(16.dp)
        ) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy message",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(11.dp)
          )
        }
      }
    }

    if (isUser) {
      Spacer(modifier = Modifier.width(8.dp))
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Person,
          contentDescription = "User",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
fun TypingIndicator() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(vertical = 6.dp)
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(16.dp),
      strokeWidth = 2.dp,
      color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = "Gemini Flash-Lite generating...",
      fontSize = 12.sp,
      fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
