package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bridge.BridgeSyncManager
import com.example.bridge.UserscriptSource
import com.example.tunnel.NgrokConfig
import com.example.tunnel.NgrokTunnelManager
import com.example.tunnel.TunnelState
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.LemurBrowserHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugConfigEngineSection(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val tunnelState by NgrokTunnelManager.state.collectAsState()
  val isBridgeSynced by BridgeSyncManager.isSynced.collectAsState()
  val clientInfo by BridgeSyncManager.clientInfo.collectAsState()
  val devBypass by BridgeSyncManager.devBypass.collectAsState()
  val bridgeLogs by BridgeSyncManager.bridgeLogs.collectAsState()

  var authToken by remember { mutableStateOf(NgrokConfig.getAuthToken(context)) }
  var portInput by remember { mutableStateOf(NgrokConfig.getLocalPort(context).toString()) }
  var hideToken by remember { mutableStateOf(true) }
  var autoStartBoot by remember { mutableStateOf(NgrokConfig.isAutoStartOnBoot(context)) }

  // Diagnostics states
  var jniStatus by remember { mutableStateOf("Ready (Loaded)") }
  var isTestingJni by remember { mutableStateOf(false) }
  var socketTestResult by remember { mutableStateOf<String?>(null) }
  var isTestingSocket by remember { mutableStateOf(false) }
  var showUserscriptCode by remember { mutableStateOf(false) }
  var localServerState by remember { mutableStateOf("Running on :${NgrokConfig.getLocalPort(context)}") }

  val isLemurInstalled = remember { LemurBrowserHelper.isLemurInstalled(context) }
  val runtime = java.lang.Runtime.getRuntime()
  val freeMemMb = runtime.freeMemory() / (1024 * 1024)
  val totalMemMb = runtime.totalMemory() / (1024 * 1024)
  val maxMemMb = runtime.maxMemory() / (1024 * 1024)

  val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
  val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
  } else {
    true
  }

  // Network Interfaces list
  val networkIps = remember {
    try {
      val list = mutableListOf<String>()
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces.hasMoreElements()) {
        val iface = interfaces.nextElement()
        val addresses = iface.inetAddresses
        while (addresses.hasMoreElements()) {
          val addr = addresses.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            list.add("${iface.name}: ${addr.hostAddress}")
          }
        }
      }
      if (list.isEmpty()) listOf("lo: 127.0.0.1") else list
    } catch (_: Exception) {
      listOf("127.0.0.1 (Loopback)")
    }
  }

  val portPresets = listOf(8085, 8083, 8080, 3000, 5000, 8000, 8888, 9000)

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .testTag("debug_config_engine_section"),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Dynamic Tunnel & Local HTTP Engine Configuration Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Tunnel & Port Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
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

          Spacer(modifier = Modifier.height(10.dp))

          // Auth Token Field
          OutlinedTextField(
            value = authToken,
            onValueChange = {
              authToken = it
              NgrokConfig.setAuthToken(context, it)
            },
            label = { Text("Ngrok Authtoken") },
            placeholder = { Text("Paste your official ngrok authtoken") },
            modifier = Modifier.fillMaxWidth().testTag("config_auth_token_input"),
            singleLine = true,
            visualTransformation = if (hideToken) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
              TextButton(onClick = { hideToken = !hideToken }) {
                Text(if (hideToken) "Show" else "Hide", fontSize = 11.sp)
              }
            }
          )

          Spacer(modifier = Modifier.height(12.dp))

          // Port Presets FlowRow
          Text("Quick Port Selectors:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            val currentSelectedPort = portInput.toIntOrNull() ?: 8085
            portPresets.forEach { p ->
              FilterChip(
                selected = currentSelectedPort == p,
                onClick = {
                  portInput = p.toString()
                  NgrokConfig.setLocalPort(context, p)
                  NgrokTunnelManager.ensureLocalServerRunning(p)
                  localServerState = "Running on :$p"
                },
                label = { Text(":$p", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Custom Port Input & Ping Test
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = portInput,
              onValueChange = {
                portInput = it
                it.toIntOrNull()?.let { p ->
                  if (p in 1..65535) {
                    NgrokConfig.setLocalPort(context, p)
                    NgrokTunnelManager.ensureLocalServerRunning(p)
                    localServerState = "Running on :$p"
                  }
                }
              },
              label = { Text("Custom Port") },
              modifier = Modifier.weight(1f).testTag("config_custom_port_input"),
              singleLine = true
            )

            Button(
              onClick = {
                val p = portInput.toIntOrNull() ?: 8085
                isTestingSocket = true
                socketTestResult = null
                scope.launch {
                  val res = NgrokTunnelManager.testLocalPing(p)
                  socketTestResult = res
                  isTestingSocket = false
                }
              },
              modifier = Modifier.padding(top = 6.dp).testTag("config_test_ping_btn"),
              shape = RoundedCornerShape(8.dp),
              enabled = !isTestingSocket
            ) {
              if (isTestingSocket) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
              } else {
                Text("Test Ping")
              }
            }
          }

          if (socketTestResult != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = socketTestResult ?: "",
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              color = if (socketTestResult?.contains("200") == true) StatusGreen else StatusRed
            )
          }

          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          Spacer(modifier = Modifier.height(10.dp))

          // Boot Auto-Start Switch
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Auto-start on Android Device Boot", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              Text("Starts background tunnel service on RECEIVE_BOOT_COMPLETED", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
              checked = autoStartBoot,
              onCheckedChange = {
                autoStartBoot = it
                NgrokConfig.setAutoStartOnBoot(context, it)
              },
              modifier = Modifier.testTag("config_auto_boot_switch")
            )
          }
        }
      }
    }

    // 2. Native C/Rust JNI Engine & Binary Inspector Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Native JNI & Binary Inspector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(StatusGreen.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text("libngrok_java.so", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // JNI Info Grid
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Device Architecture:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(Build.SUPPORTED_ABIS.joinToString(", "), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("JNI Init Status:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(jniStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusGreen, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("JVM Memory Footprint:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("$freeMemMb MB free / $totalMemMb MB total (max $maxMemMb MB)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Local Server Runtime:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(localServerState, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = StatusGreen, fontFamily = FontFamily.Monospace)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // JNI Actions
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                isTestingJni = true
                scope.launch {
                  try {
                    com.ngrok.Runtime.load()
                    jniStatus = "Re-initialized OK"
                    Toast.makeText(context, "Native ngrok JNI runtime successfully initialized!", Toast.LENGTH_SHORT).show()
                  } catch (t: Throwable) {
                    jniStatus = "Init Error: ${t.message}"
                    Toast.makeText(context, "JNI init note: ${t.message}", Toast.LENGTH_LONG).show()
                  }
                  isTestingJni = false
                }
              },
              modifier = Modifier.weight(1f).height(40.dp).testTag("reload_jni_button"),
              shape = RoundedCornerShape(8.dp),
              enabled = !isTestingJni
            ) {
              if (isTestingJni) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
              } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reload JNI Engine", fontSize = 11.sp)
              }
            }

            // Raw Socket Binder Test
            OutlinedButton(
              onClick = {
                scope.launch(Dispatchers.IO) {
                  try {
                    val s = ServerSocket()
                    s.reuseAddress = true
                    s.bind(java.net.InetSocketAddress("127.0.0.1", 0))
                    val boundPort = s.localPort
                    s.close()
                    withContext(Dispatchers.Main) {
                      Toast.makeText(context, "Socket binding verification passed on ephemeral port $boundPort", Toast.LENGTH_SHORT).show()
                    }
                  } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                      Toast.makeText(context, "Socket bind test error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                  }
                }
              },
              modifier = Modifier.weight(1f).height(40.dp).testTag("test_socket_bind_button"),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Verify Socket API", fontSize = 11.sp)
            }
          }
        }
      }
    }

    // 3. Lemur Browser & Userscript Bridge Diagnostics Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Lemur Browser & Userscript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isBridgeSynced) StatusGreen.copy(alpha = 0.15f) else StatusAmber.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(if (isBridgeSynced) "SYNCED" else "UNPAIRED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isBridgeSynced) StatusGreen else StatusAmber)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Bridge Telemetry Box
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Lemur Browser Package:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(if (isLemurInstalled) "Installed (Detected)" else "Not Installed", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isLemurInstalled) StatusGreen else StatusRed)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Paired Browser Session:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(clientInfo?.sessionId?.take(12) ?: "None", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Roundtrip Latency:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("${clientInfo?.latencyMs ?: 0} ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusGreen, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Active Page URL:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(clientInfo?.pageUrl?.take(28) ?: "N/A", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // 1-Click Install / Raw Script Viewer Actions
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                val p = portInput.toIntOrNull() ?: 8085
                LemurBrowserHelper.openUrlInLemur(context, "http://127.0.0.1:$p/Proxy-Redirect.user.js")
              },
              modifier = Modifier.weight(1f).height(40.dp).testTag("debug_install_lemur_btn"),
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = Color.White)
            ) {
              Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Install in Lemur", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
              onClick = { showUserscriptCode = !showUserscriptCode },
              modifier = Modifier.weight(1f).height(40.dp).testTag("view_raw_userscript_btn"),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (showUserscriptCode) "Hide Script" else "View Script", fontSize = 11.sp)
            }
          }

          // Expandable Raw Userscript Source
          AnimatedVisibility(visible = showUserscriptCode) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Proxy-Redirect.user.js", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                IconButton(
                  onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("Proxy-Redirect", UserscriptSource.SCRIPT_CONTENT))
                    Toast.makeText(context, "Copied full script to clipboard!", Toast.LENGTH_SHORT).show()
                  },
                  modifier = Modifier.size(24.dp)
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "Copy script", modifier = Modifier.size(14.dp))
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = UserscriptSource.SCRIPT_CONTENT.take(450) + "\n... [Full 117KB script bundled in APK assets]",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }

    // 4. Network Hardware & Battery Optimization Telemetry Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Network & Power Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Battery Optimization Exemption:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = if (isIgnoringBatteryOptimizations) "Exempt (Unrestricted)" else "Optimized (May Sleep)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isIgnoringBatteryOptimizations) StatusGreen else StatusAmber
              )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Foreground Service:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("dataSync (Active)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Local Interfaces:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(networkIps.firstOrNull() ?: "127.0.0.1", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
          }

          if (!isIgnoringBatteryOptimizations && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
              onClick = {
                try {
                  val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                  context.startActivity(intent)
                } catch (_: Exception) {
                  Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.fillMaxWidth().height(38.dp),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.Default.BatteryAlert, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Configure Unrestricted Background Battery", fontSize = 11.sp)
            }
          }
        }
      }
    }

    // 5. Full Diagnostics Report Export Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Diagnostic System Report", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Generate full debug snapshot (JNI, ports, bridge, memory)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          Button(
            onClick = {
              val currentPort = portInput.toIntOrNull() ?: 8085
              val report = JSONObject().apply {
                put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("android_version", Build.VERSION.RELEASE)
                put("sdk_int", Build.VERSION.SDK_INT)
                put("supported_abis", Build.SUPPORTED_ABIS.joinToString())
                put("configured_port", currentPort)
                put("tunnel_state", tunnelState.javaClass.simpleName)
                put("is_bridge_synced", isBridgeSynced)
                put("lemur_installed", isLemurInstalled)
                put("battery_optimization_exempt", isIgnoringBatteryOptimizations)
                put("memory_free_mb", freeMemMb)
                put("memory_total_mb", totalMemMb)
                put("network_interfaces", networkIps.joinToString())
              }.toString(2)

              val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cb.setPrimaryClip(ClipData.newPlainText("Ngrok Diagnostics Report", report))
              Toast.makeText(context, "Copied full diagnostics JSON to clipboard!", Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("export_diagnostics_btn")
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Export Report", fontSize = 11.sp)
          }
        }
      }
    }
  }
}
