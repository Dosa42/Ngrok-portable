package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunnel.NgrokConfig
import com.example.tunnel.NgrokTunnelManager
import com.example.tunnel.TunnelState
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import kotlinx.coroutines.launch

@Composable
fun TunnelControlCard(
  state: TunnelState,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var showSettings by remember { mutableStateOf(false) }

  var tokenInput by remember { mutableStateOf(NgrokConfig.getAuthToken(context)) }
  var portInput by remember { mutableStateOf(NgrokConfig.getLocalPort(context).toString()) }
  var hideToken by remember { mutableStateOf(true) }
  var pingResult by remember { mutableStateOf<String?>(null) }
  var isTestingPing by remember { mutableStateOf(false) }

  val isRunning = state is TunnelState.Connected
  val isConnecting = state is TunnelState.Connecting
  val isStopping = state is TunnelState.Stopping

  val statusColor by animateColorAsState(
    targetValue = when (state) {
      is TunnelState.Connected -> StatusGreen
      is TunnelState.Connecting -> StatusAmber
      is TunnelState.Stopping -> StatusAmber
      is TunnelState.Error -> StatusRed
      is TunnelState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    },
    label = "statusColor"
  )

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("tunnel_control_card"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      // Header: Title & Status Chip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(12.dp)
              .clip(CircleShape)
              .background(statusColor)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Tunnel Lifecycle",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        // Status Badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(statusColor.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(
            text = when (state) {
              is TunnelState.Connected -> "CONNECTED"
              is TunnelState.Connecting -> "CONNECTING"
              is TunnelState.Stopping -> "STOPPING"
              is TunnelState.Error -> "ERROR"
              is TunnelState.Disconnected -> "DISCONNECTED"
            },
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Main Start/Stop Controller Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Button(
          onClick = {
            if (isRunning) {
              NgrokTunnelManager.stopTunnel(context)
            } else {
              val port = portInput.toIntOrNull() ?: 8085
              if (tokenInput.isBlank()) {
                showSettings = true
                Toast.makeText(context, "Please enter your Ngrok Authtoken", Toast.LENGTH_LONG).show()
              } else {
                NgrokConfig.setAuthToken(context, tokenInput)
                NgrokConfig.setLocalPort(context, port)
                NgrokTunnelManager.startTunnel(context, tokenInput, port)
              }
            }
          },
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("tunnel_toggle_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning) StatusRed else MaterialTheme.colorScheme.primary,
            contentColor = Color.White
          ),
          enabled = !isConnecting && !isStopping
        ) {
          if (isConnecting || isStopping) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (isConnecting) "Connecting..." else "Stopping...")
          } else {
            Icon(
              imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
              contentDescription = if (isRunning) "Stop Tunnel" else "Start Tunnel"
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isRunning) "Stop Ngrok Tunnel" else "Start Ngrok Tunnel",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp
            )
          }
        }

        IconButton(
          onClick = { showSettings = !showSettings },
          modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("tunnel_settings_button")
        ) {
          Icon(
            imageVector = if (showSettings) Icons.Default.ExpandLess else Icons.Default.Settings,
            contentDescription = "Toggle Settings",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Public URL Display Card (When Connected)
      if (state is TunnelState.Connected) {
        Spacer(modifier = Modifier.height(14.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
              shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "PUBLIC TUNNEL ENDPOINT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
              )
              Text(
                text = "Forwarding to :${state.localPort}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = state.publicUrl,
              fontFamily = FontFamily.Monospace,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              // Copy Button
              OutlinedButton(
                onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("Ngrok URL", state.publicUrl))
                  Toast.makeText(context, "Copied tunnel URL to clipboard!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("copy_url_button")
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy", fontSize = 12.sp)
              }

              // Open in Browser Button
              OutlinedButton(
                onClick = {
                  try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(state.publicUrl))
                    context.startActivity(browserIntent)
                  } catch (_: Exception) {
                    Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                  }
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("open_url_button")
              ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open", fontSize = 12.sp)
              }

              // Share Button
              OutlinedButton(
                onClick = {
                  val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, state.publicUrl)
                    type = "text/plain"
                  }
                  context.startActivity(Intent.createChooser(sendIntent, "Share Ngrok Tunnel URL"))
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("share_url_button")
              ) {
                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share", fontSize = 12.sp)
              }
            }
          }
        }
      }

      // Connecting / Stage description
      if (state is TunnelState.Connecting) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = state.stage,
          style = MaterialTheme.typography.bodySmall,
          color = StatusAmber,
          fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
      }

      // Error description
      if (state is TunnelState.Error) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Error: ${state.errorMessage}",
          style = MaterialTheme.typography.bodySmall,
          color = StatusRed
        )
      }

      // Collapsible Configuration & Diagnostics Accordion
      AnimatedVisibility(visible = showSettings) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "TUNNEL CONFIGURATION",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              letterSpacing = 1.sp
            )

            TextButton(
              onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dashboard.ngrok.com/get-started/your-authtoken"))
                context.startActivity(intent)
              }
            ) {
              Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Get Token", fontSize = 11.sp)
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          OutlinedTextField(
            value = tokenInput,
            onValueChange = {
              tokenInput = it
              NgrokConfig.setAuthToken(context, it)
            },
            label = { Text("Ngrok Auth Token") },
            placeholder = { Text("Paste your auth token here") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("auth_token_input"),
            singleLine = true,
            visualTransformation = if (hideToken) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
              TextButton(onClick = { hideToken = !hideToken }) {
                Text(if (hideToken) "Show" else "Hide", fontSize = 11.sp)
              }
            }
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = portInput,
              onValueChange = {
                portInput = it
                it.toIntOrNull()?.let { p -> NgrokConfig.setLocalPort(context, p) }
              },
              label = { Text("Local Port") },
              modifier = Modifier
                .weight(1f)
                .testTag("port_input"),
              singleLine = true
            )

            Button(
              onClick = {
                val port = portInput.toIntOrNull() ?: 8085
                isTestingPing = true
                pingResult = null
                scope.launch {
                  val res = NgrokTunnelManager.testLocalPing(port)
                  pingResult = res
                  isTestingPing = false
                }
              },
              modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(top = 6.dp)
                .testTag("test_ping_button"),
              shape = RoundedCornerShape(8.dp),
              enabled = !isTestingPing
            ) {
              if (isTestingPing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
              } else {
                Text("Test Ping")
              }
            }
          }

          if (pingResult != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = pingResult ?: "",
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              color = if (pingResult?.contains("200") == true) StatusGreen else StatusRed
            )
          }
        }
      }
    }
  }
}
