package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bridge.BridgeLogItem
import com.example.bridge.BridgeSyncManager
import com.example.bridge.UserscriptSource
import com.example.tunnel.NgrokConfig
import com.example.tunnel.NgrokTunnelManager
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BridgeSyncGateScreen(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isSynced by BridgeSyncManager.isSynced.collectAsState()
  val devBypass by BridgeSyncManager.devBypass.collectAsState()
  val clientInfo by BridgeSyncManager.clientInfo.collectAsState()
  val logs by BridgeSyncManager.bridgeLogs.collectAsState()

  val localPort = NgrokConfig.getLocalPort(context)
  var isTestingPing by remember { mutableStateOf(false) }
  var pingResult by remember { mutableStateOf<String?>(null) }

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  val statusColor by animateColorAsState(
    targetValue = if (isSynced) StatusGreen else StatusAmber,
    label = "statusColor"
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp)
      .testTag("bridge_sync_gate_screen"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top Radar / Status Hero Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Pulsing Radar Circle
        Box(
          modifier = Modifier
            .size(72.dp)
            .scale(if (isSynced) 1f else pulseScale)
            .clip(CircleShape)
            .background(statusColor.copy(alpha = 0.15f))
            .border(2.dp, statusColor.copy(alpha = 0.6f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isSynced) Icons.Default.CheckCircle else Icons.Default.Sync,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(36.dp)
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = if (isSynced) "Proxy Redirect Userscript Synced!" else "Tampermonkey Bridge Required",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = if (isSynced)
            "Active session paired with ${clientInfo?.browser ?: "Browser"} • Latency ${clientInfo?.latencyMs ?: 0}ms"
          else
            "This app requires an explicit green synchronization with the compiled Proxy Redirect userscript running in your browser.",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = 16.sp
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 1-Click Install Actions (GreasyFork Style direct trigger)
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
      )
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = "EMBEDDED USERSCRIPT (1-CLICK INSTALL)",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Main 1-Click Install Button
        Button(
          onClick = {
            try {
              val url = "http://127.0.0.1:$localPort/Proxy-Redirect.user.js"
              val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
              context.startActivity(browserIntent)
            } catch (e: Exception) {
              Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("one_click_install_button"),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = StatusGreen,
            contentColor = Color.White
          )
        ) {
          Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("⚡ 1-Click Install in Browser", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Open Built-in Web Installer Hub
          OutlinedButton(
            onClick = {
              try {
                val url = "http://127.0.0.1:$localPort/"
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(browserIntent)
              } catch (e: Exception) {
                Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier.weight(1f).height(40.dp).testTag("open_web_hub_button"),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Web Installer", fontSize = 11.sp)
          }

          // Copy Script Code
          OutlinedButton(
            onClick = {
              val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cb.setPrimaryClip(ClipData.newPlainText("Proxy-Redirect", UserscriptSource.SCRIPT_CONTENT))
              Toast.makeText(context, "Copied userscript to clipboard!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).height(40.dp).testTag("copy_userscript_button"),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy Script", fontSize = 11.sp)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Test Local Ping
        OutlinedButton(
          onClick = {
            isTestingPing = true
            pingResult = null
            scope.launch {
              val res = NgrokTunnelManager.testLocalPing(localPort)
              pingResult = res
              isTestingPing = false
            }
          },
          modifier = Modifier.fillMaxWidth().height(36.dp).testTag("check_local_ping_button"),
          shape = RoundedCornerShape(8.dp),
          enabled = !isTestingPing
        ) {
          if (isTestingPing) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Test Local Server (Port $localPort)", fontSize = 11.sp)
          }
        }

        if (pingResult != null) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = pingResult ?: "",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (pingResult?.contains("200") == true) StatusGreen else StatusRed
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Live Bridge Handshake Logs
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Bridge Handshake Telemetry",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
      )

      IconButton(
        onClick = { BridgeSyncManager.clearLogs() },
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Clear logs",
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Log list
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
      if (logs.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Listening on http://127.0.0.1:$localPort/api/bridge/handshake...",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          items(logs, key = { it.id }) { item ->
            BridgeLogItemRow(item = item)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Developer / Standalone Testing Bypass Option
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(10.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
      )
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.DeveloperMode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text("Developer Standalone Bypass", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Unlock tunnel dashboard for testing without browser", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        Switch(
          checked = devBypass,
          onCheckedChange = { BridgeSyncManager.setDevBypass(it) },
          modifier = Modifier.testTag("dev_bypass_switch")
        )
      }
    }
  }
}

@Composable
fun BridgeLogItemRow(item: BridgeLogItem) {
  val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
  val typeColor = when (item.type) {
    "HANDSHAKE" -> StatusGreen
    "HEARTBEAT" -> MaterialTheme.colorScheme.primary
    "DISCONNECT" -> StatusRed
    else -> MaterialTheme.colorScheme.secondary
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
      .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "[${item.type}]",
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = typeColor,
      fontFamily = FontFamily.Monospace
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = item.message,
      fontSize = 11.sp,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
      fontFamily = FontFamily.Monospace
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = timeStr,
      fontSize = 9.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
  }
}
