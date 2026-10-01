package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Http
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunnel.NgrokTunnelManager
import com.example.tunnel.TrafficLogEntry
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrafficLogsSection(
  modifier: Modifier = Modifier
) {
  val logs by NgrokTunnelManager.trafficLogs.collectAsState()
  val totalRequests by NgrokTunnelManager.totalRequests.collectAsState()
  var filterMethod by remember { mutableStateOf("ALL") }

  val filteredLogs = remember(logs, filterMethod) {
    if (filterMethod == "ALL") logs
    else logs.filter { it.method.equals(filterMethod, ignoreCase = true) }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp)
      .testTag("traffic_logs_section")
  ) {
    // Header Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Traffic Inspector",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Total Requests Handled: $totalRequests",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      IconButton(
        onClick = { NgrokTunnelManager.clearLogs() },
        modifier = Modifier.testTag("clear_logs_button")
      ) {
        Icon(
          imageVector = Icons.Default.DeleteSweep,
          contentDescription = "Clear logs",
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Method Filter Chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      listOf("ALL", "GET", "POST", "SYSTEM").forEach { method ->
        FilterChip(
          selected = filterMethod == method,
          onClick = { filterMethod = method },
          label = { Text(method, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (filteredLogs.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.Http,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(44.dp)
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (logs.isEmpty()) "No traffic recorded yet" else "No matching requests for $filterMethod",
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Incoming requests forwarded through Ngrok or tested locally will appear here in real time.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(filteredLogs, key = { it.id }) { log ->
          TrafficLogItem(log = log)
        }
      }
    }
  }
}

@Composable
fun TrafficLogItem(log: TrafficLogEntry) {
  val context = LocalContext.current
  var isExpanded by remember { mutableStateOf(false) }

  val methodColor = when (log.method) {
    "GET" -> MaterialTheme.colorScheme.primary
    "POST" -> StatusGreen
    "SYSTEM" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
  }

  val statusColor = if (log.statusCode in 200..299) StatusGreen else StatusRed
  val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { isExpanded = !isExpanded },
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Method Tag
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(methodColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = log.method,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = methodColor
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Path & Client info
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = log.path,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (log.message != null) {
            Text(
              text = log.message,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            Text(
              text = "${log.clientIp} • ${log.responseDurationMs}ms",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Status Code & Timestamp & Expand Chevron
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "${log.statusCode}",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = statusColor
            )
            Text(
              text = timeStr,
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }

          Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).padding(start = 4.dp)
          )
        }
      }

      // Expandable Details (Headers, Body)
      AnimatedVisibility(visible = isExpanded) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(10.dp)
        ) {
          if (log.headers.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Request Headers:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              IconButton(
                onClick = {
                  val headerText = log.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cb.setPrimaryClip(ClipData.newPlainText("Headers", headerText))
                  Toast.makeText(context, "Headers copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(20.dp)
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy headers", modifier = Modifier.size(12.dp))
              }
            }

            log.headers.forEach { (k, v) ->
              Text(
                text = "$k: $v",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          if (!log.requestBody.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Request Body:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
              text = log.requestBody,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }
  }
}
