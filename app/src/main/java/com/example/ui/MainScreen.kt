package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bridge.BridgeSyncManager
import com.example.proxy.ReverseProxyManager
import com.example.tunnel.NgrokConfig
import com.example.tunnel.NgrokTunnelManager
import com.example.tunnel.TunnelState
import com.example.ui.components.ChatboxSection
import com.example.ui.components.DebugConfigEngineSection
import com.example.ui.components.EndpointsTesterSection
import com.example.ui.components.NativeWebSocketSection
import com.example.ui.components.ReverseProxySection
import com.example.ui.components.TrafficLogsSection
import com.example.ui.components.TunnelControlCard
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.websocket.WebSocketManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
  val context = LocalContext.current
  val tunnelState by NgrokTunnelManager.state.collectAsState()
  val isBridgeSynced by BridgeSyncManager.isSynced.collectAsState()
  val clientInfo by BridgeSyncManager.clientInfo.collectAsState()
  val isWsRunning by WebSocketManager.isRunning.collectAsState()
  val wsPort by WebSocketManager.currentPort.collectAsState()

  var selectedTab by remember { mutableIntStateOf(0) }
  val localPort = NgrokConfig.getLocalPort(context)

  // Start local companion server & native WebSocket server
  LaunchedEffect(localPort) {
    NgrokTunnelManager.ensureLocalServerRunning(localPort, context)
    WebSocketManager.init(context)
  }

  // Request notification permission for Foreground Service notification on Android 13+
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { /* Handled gracefully */ }

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  val statusDotColor = when (tunnelState) {
    is TunnelState.Connected -> StatusGreen
    is TunnelState.Connecting -> StatusAmber
    is TunnelState.Stopping -> StatusAmber
    is TunnelState.Error -> StatusRed
    is TunnelState.Disconnected -> MaterialTheme.colorScheme.outline
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusDotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Ngrok Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = when (tunnelState) {
                    is TunnelState.Connected -> "Active • Port $localPort"
                    is TunnelState.Connecting -> "Connecting..."
                    is TunnelState.Stopping -> "Stopping..."
                    is TunnelState.Error -> "Tunnel Error"
                    is TunnelState.Disconnected -> "Offline"
                  },
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isWsRunning) {
                  Text(
                    text = " • WS :$wsPort",
                    fontSize = 11.sp,
                    color = StatusGreen,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }
            }
          }
        },
        actions = {
          if (isBridgeSynced) {
            // Fully Synced HUD Badge
            Box(
              modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StatusGreen.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("bridge_synced_badge")
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = StatusGreen,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "SYNCED (${clientInfo?.latencyMs ?: 0}ms)",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = StatusGreen
                )
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Full Dashboard (Bridge Sync Gate removed)
      TunnelControlCard(
        state = tunnelState,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
      )

      // Navigation Tabs (6 Tabs with ScrollableTabRow)
        ScrollableTabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.primary,
          edgePadding = 8.dp,
          indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
              modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
              color = MaterialTheme.colorScheme.primary
            )
          }
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ChatGPT Voice", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_chatbox")
          )

          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("WebSocket", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_websocket")
          )

          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reverse Proxy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_reverse_proxy")
          )

          Tab(
            selected = selectedTab == 3,
            onClick = { selectedTab = 3 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_endpoints_test")
          )

          Tab(
            selected = selectedTab == 4,
            onClick = { selectedTab = 4 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Traffic", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_traffic_logs")
          )

          Tab(
            selected = selectedTab == 5,
            onClick = { selectedTab = 5 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Engine", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_debug_config_engine")
          )
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          when (selectedTab) {
            0 -> ChatboxSection()
            1 -> NativeWebSocketSection()
            2 -> ReverseProxySection()
            3 -> EndpointsTesterSection()
            4 -> TrafficLogsSection()
            5 -> DebugConfigEngineSection()
          }
        }
      }
    }
  }
