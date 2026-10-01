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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunnel.NgrokConfig
import com.example.tunnel.NgrokTunnelManager
import com.example.tunnel.TunnelState
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Composable
fun EndpointsTesterSection(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val tunnelState by NgrokTunnelManager.state.collectAsState()

  var selectedMethod by remember { mutableStateOf("GET") }
  var endpointPath by remember { mutableStateOf("/ping") }
  var requestBody by remember { mutableStateOf("{\n  \"message\": \"test payload from android\"\n}") }
  var usePublicUrl by remember { mutableStateOf(false) }

  var isSending by remember { mutableStateOf(false) }
  var responseCode by remember { mutableStateOf<Int?>(null) }
  var responseDurationMs by remember { mutableStateOf<Long?>(null) }
  var responseBody by remember { mutableStateOf<String?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val publicUrl = (tunnelState as? TunnelState.Connected)?.publicUrl
  val localPort = NgrokConfig.getLocalPort(context)

  val activeBaseUrl = if (usePublicUrl && !publicUrl.isNullOrBlank()) {
    publicUrl
  } else {
    "http://127.0.0.1:$localPort"
  }

  val quickEndpoints = listOf("/api/phone/status", "/api/proxy/config", "/ping", "/status", "/echo", "/headers", "/Proxy-Redirect.user.js")

  val curlCommand = buildString {
    append("curl -i")
    if (selectedMethod != "GET") {
      append(" -X $selectedMethod")
    }
    append(" \"$activeBaseUrl$endpointPath\"")
    if (selectedMethod in listOf("POST", "PUT") && requestBody.isNotBlank()) {
      append(" -H \"Content-Type: application/json\"")
      append(" -d '${requestBody.replace("\n", " ").trim()}'")
    }
  }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(16.dp)
      .testTag("endpoints_tester_section"),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "HTTP Request Tester",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Test real HTTP requests against the local server or public tunnel",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // Target Base URL Selection Chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(
        selected = !usePublicUrl || publicUrl.isNullOrBlank(),
        onClick = { usePublicUrl = false },
        label = { Text("Local (127.0.0.1:$localPort)", fontSize = 12.sp) }
      )

      FilterChip(
        selected = usePublicUrl && !publicUrl.isNullOrBlank(),
        onClick = {
          if (!publicUrl.isNullOrBlank()) {
            usePublicUrl = true
          } else {
            Toast.makeText(context, "Start the tunnel first to use public URL", Toast.LENGTH_SHORT).show()
          }
        },
        enabled = !publicUrl.isNullOrBlank(),
        label = { Text("Public Ngrok URL", fontSize = 12.sp) }
      )
    }

    // Method and Path
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      listOf("GET", "POST", "PUT").forEach { method ->
        FilterChip(
          selected = selectedMethod == method,
          onClick = { selectedMethod = method },
          label = { Text(method, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        )
      }
    }

    // Quick Endpoint Shortcuts
    val chipScroll = rememberScrollState()
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(chipScroll),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      quickEndpoints.forEach { ep ->
        AssistChip(
          onClick = { endpointPath = ep },
          label = { Text(ep, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
          colors = AssistChipDefaults.assistChipColors(
            containerColor = if (endpointPath == ep) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          )
        )
      }
    }

    // Path Input
    OutlinedTextField(
      value = endpointPath,
      onValueChange = { endpointPath = it },
      label = { Text("Endpoint Path") },
      modifier = Modifier.fillMaxWidth().testTag("endpoint_path_input"),
      singleLine = true,
      textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    )

    // Request Body if POST/PUT
    if (selectedMethod in listOf("POST", "PUT")) {
      OutlinedTextField(
        value = requestBody,
        onValueChange = { requestBody = it },
        label = { Text("JSON Payload") },
        modifier = Modifier.fillMaxWidth().testTag("request_payload_input"),
        maxLines = 6,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
      )
    }

    // Send Button
    Button(
      onClick = {
        isSending = true
        errorMessage = null
        responseCode = null
        responseBody = null
        responseDurationMs = null

        scope.launch {
          val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

          val fullUrl = "$activeBaseUrl$endpointPath"
          val startTime = System.currentTimeMillis()

          try {
            val reqBuilder = Request.Builder().url(fullUrl)
            if (selectedMethod == "POST") {
              val mediaType = "application/json; charset=utf-8".toMediaType()
              reqBuilder.post(requestBody.toRequestBody(mediaType))
            } else if (selectedMethod == "PUT") {
              val mediaType = "application/json; charset=utf-8".toMediaType()
              reqBuilder.put(requestBody.toRequestBody(mediaType))
            } else {
              reqBuilder.get()
            }

            val response = withContext(Dispatchers.IO) {
              client.newCall(reqBuilder.build()).execute()
            }

            val elapsed = System.currentTimeMillis() - startTime
            val bodyText = withContext(Dispatchers.IO) {
              response.body?.string() ?: ""
            }

            responseCode = response.code
            responseDurationMs = elapsed
            responseBody = bodyText
          } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: e.message ?: "Network error"
            responseDurationMs = System.currentTimeMillis() - startTime
          } finally {
            isSending = false
          }
        }
      },
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("send_request_button"),
      shape = RoundedCornerShape(10.dp),
      enabled = !isSending
    ) {
      if (isSending) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Sending Request...")
      } else {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Execute $selectedMethod Request")
      }
    }

    // Response Result Card
    AnimatedVisibility(visible = responseCode != null || errorMessage != null) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              val code = responseCode
              val isSuccess = code != null && code in 200..299
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (isSuccess) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.15f))
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = if (code != null) "HTTP $code" else "FAILED",
                  color = if (isSuccess) StatusGreen else StatusRed,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
              }
              if (responseDurationMs != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "${responseDurationMs}ms",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (!responseBody.isNullOrBlank()) {
              IconButton(
                onClick = {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cb.setPrimaryClip(ClipData.newPlainText("Response", responseBody))
                  Toast.makeText(context, "Response copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          if (errorMessage != null) {
            Text(
              text = "Error: $errorMessage",
              color = StatusRed,
              fontSize = 13.sp,
              fontFamily = FontFamily.Monospace
            )
          }

          if (responseBody != null) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(10.dp)
            ) {
              Text(
                text = responseBody ?: "",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }
    }

    // Copyable cURL Snippet Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
      )
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("cURL Command", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          }

          IconButton(
            onClick = {
              val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cb.setPrimaryClip(ClipData.newPlainText("cURL", curlCommand))
              Toast.makeText(context, "Copied cURL command!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(24.dp)
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = curlCommand,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
