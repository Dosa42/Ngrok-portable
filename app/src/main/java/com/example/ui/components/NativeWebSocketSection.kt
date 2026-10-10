package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.websocket.WebSocketClientSession
import com.example.websocket.WebSocketLogMessage
import com.example.websocket.WebSocketManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NativeWebSocketSection(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val isRunning by WebSocketManager.isRunning.collectAsState()
  val port by WebSocketManager.currentPort.collectAsState()
  val activeClients by WebSocketManager.activeClients.collectAsState()
  val logs by WebSocketManager.recentLogs.collectAsState()
  val totalReceived by WebSocketManager.totalReceivedCount.collectAsState()
  val totalSent by WebSocketManager.totalSentCount.collectAsState()

  var messageInput by remember { mutableStateOf("{\"action\": \"ping\", \"timestamp\": ${System.currentTimeMillis()}}") }
  var portInput by remember { mutableStateOf(port.toString()) }
  var showSnippet by remember { mutableStateOf(true) }

  val portPresets = listOf(8086, 8087, 8088, 9001, 8085)
  val messagePresets = listOf(
    "Ping" to "{\"action\": \"ping\"}",
    "Phone Telemetry" to "{\"action\": \"get_phone_status\"}",
    "Proxy Rules" to "{\"action\": \"get_proxy_rules\"}",
    "Browser Notification" to "{\"action\": \"alert\", \"message\": \"Message from Android Local WebSocket!\"}"
  )

  val browserJsSnippet = """
// Connect to Native Android Local WebSocket (No CORS Blockers)
const ws = new WebSocket('ws://127.0.0.1:$port/ws');

ws.onopen = () => {
  console.log('✅ Connected to Android WebSocket on port $port');
  ws.send(JSON.stringify({ action: 'ping', timestamp: Date.now() }));
};

ws.onmessage = (event) => {
  console.log('📩 Message from Android:', JSON.parse(event.data));
};

ws.onerror = (err) => console.error('WS Error:', err);
ws.onclose = () => console.log('WS Connection closed');
""".trimIndent()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag("native_websocket_section"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. Server Master Status & Port Control Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(if (isRunning) StatusGreen else StatusRed)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Native Local WebSocket", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Zero CORS Blocker Pill
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(StatusGreen.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CORS Free (Origin: *)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
              }
            }
          }

          // Active Endpoint Box
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              .padding(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("Endpoint URL:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  text = "ws://127.0.0.1:$port/ws",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.primary
                )
              }

              IconButton(
                onClick = {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cb.setPrimaryClip(ClipData.newPlainText("WebSocket URL", "ws://127.0.0.1:$port/ws"))
                  Toast.makeText(context, "Copied WebSocket URL", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(16.dp))
              }
            }
          }

          // Telemetry Stats Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            StatPill(label = "Active Browsers", value = "${activeClients.size}", color = if (activeClients.isNotEmpty()) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            StatPill(label = "Messages Recv", value = "$totalReceived", color = MaterialTheme.colorScheme.primary)
            StatPill(label = "Messages Sent", value = "$totalSent", color = MaterialTheme.colorScheme.secondary)
          }

          HorizontalDivider()

          // Port Switcher & Actions
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = portInput,
              onValueChange = { portInput = it },
              label = { Text("Port", fontSize = 11.sp) },
              modifier = Modifier.weight(1f),
              singleLine = true,
              textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )

            Button(
              onClick = {
                val newPort = portInput.toIntOrNull() ?: 8086
                WebSocketManager.setWebSocketPort(context, newPort)
                Toast.makeText(context, "WebSocket server updated to port $newPort", Toast.LENGTH_SHORT).show()
              },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.padding(top = 6.dp)
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Set Port", fontSize = 11.sp)
            }

            if (isRunning) {
              OutlinedButton(
                onClick = { WebSocketManager.stopServer() },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed)
              ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop", fontSize = 11.sp)
              }
            } else {
              Button(
                onClick = { WebSocketManager.startServer(context, portInput.toIntOrNull() ?: 8086) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start", fontSize = 11.sp)
              }
            }
          }

          // Port Presets
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            portPresets.forEach { p ->
              AssistChip(
                onClick = {
                  portInput = p.toString()
                  WebSocketManager.setWebSocketPort(context, p)
                },
                label = { Text("Port $p", fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
              )
            }
          }
        }
      }
    }

    // 2. Browser JavaScript Integration Code Snippet (Collapsible)
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth().clickable { showSnippet = !showSnippet },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Browser JavaScript Connection Snippet", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cb.setPrimaryClip(ClipData.newPlainText("Browser JS Snippet", browserJsSnippet))
                  Toast.makeText(context, "Snippet copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy snippet", modifier = Modifier.size(14.dp))
              }
              Icon(
                imageVector = if (showSnippet) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
            }
          }

          AnimatedVisibility(visible = showSnippet) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
                .padding(10.dp)
            ) {
              Text(
                text = browserJsSnippet,
                color = Color(0xFFD4D4D4),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
              )
            }
          }
        }
      }
    }

    // 3. Active Browser Sessions
    if (activeClients.isNotEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connected Browser Clients (${activeClients.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

            activeClients.forEach { client ->
              ClientItem(client = client)
            }
          }
        }
      }
    }

    // 4. Interactive Message Dispatcher (Send to Browser)
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Send Message to Connected Browsers", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

          // Presets
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            messagePresets.forEach { (label, payload) ->
              AssistChip(
                onClick = { messageInput = payload },
                label = { Text(label, fontSize = 10.sp) }
              )
            }
          }

          OutlinedTextField(
            value = messageInput,
            onValueChange = { messageInput = it },
            label = { Text("Payload (JSON or Text)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            shape = RoundedCornerShape(8.dp)
          )

          Button(
            onClick = {
              if (messageInput.isNotBlank()) {
                WebSocketManager.broadcastMessage(messageInput)
                Toast.makeText(context, "Broadcast message sent!", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier.fillMaxWidth().height(38.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = messageInput.isNotBlank() && isRunning
          ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Broadcast to All Connected WebSockets", fontSize = 11.sp)
          }
        }
      }
    }

    // 5. Live WebSocket Message Log & Frame Stream
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Live Frame Inspector (${logs.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

            IconButton(
              onClick = { WebSocketManager.clearLogs() },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", modifier = Modifier.size(16.dp))
            }
          }

          if (logs.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              contentAlignment = Alignment.Center
            ) {
              Text("No WebSocket traffic yet. Open browser to ws://127.0.0.1:$port/ws", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              logs.takeLast(25).reversed().forEach { log ->
                WebSocketLogItem(log = log)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
    Text(text = label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun ClientItem(client: WebSocketClientSession) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
      .padding(8.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Client: ${client.clientIp}", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(client.connectedDuration, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Origin: ${client.origin}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("In: ${client.messagesReceived} / Out: ${client.messagesSent}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
      }
    }
  }
}

@Composable
private fun WebSocketLogItem(log: WebSocketLogMessage) {
  val (badgeBg, badgeColor, prefix) = when (log.direction) {
    "INBOUND" -> Triple(StatusGreen.copy(alpha = 0.15f), StatusGreen, "⬇ IN")
    "OUTBOUND" -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, "⬆ OUT")
    "ERROR" -> Triple(StatusRed.copy(alpha = 0.15f), StatusRed, "✖ ERR")
    else -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "⚙ SYS")
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
      .padding(6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .background(badgeBg)
          .padding(horizontal = 4.dp, vertical = 2.dp)
      ) {
        Text(prefix, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeColor)
      }

      Text(
        text = log.formattedTime,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Text(
        text = log.payload,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f)
      )
    }
  }
}
