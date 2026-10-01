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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.ui.components.BridgeSyncGateScreen
import com.example.ui.components.DebugConfigEngineSection
import com.example.ui.components.EndpointsTesterSection
import com.example.ui.components.ReverseProxySection
import com.example.ui.components.TrafficLogsSection
import com.example.ui.components.TunnelControlCard
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
  val context = LocalContext.current
  val tunnelState by NgrokTunnelManager.state.collectAsState()
  val isBridgeSynced by BridgeSyncManager.isSynced.collectAsState()
  val devBypass by BridgeSyncManager.devBypass.collectAsState()
  val clientInfo by BridgeSyncManager.clientInfo.collectAsState()

  var selectedTab by remember { mutableIntStateOf(0) }
  val localPort = NgrokConfig.getLocalPort(context)

  // Start local companion server so it is ready to receive userscript handshakes
  LaunchedEffect(localPort) {
    NgrokTunnelManager.ensureLocalServerRunning(localPort)
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
    is TunnelState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
  }

  val isUnlocked = isBridgeSynced || devBypass

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Hub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Ngrok Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = when (tunnelState) {
                    is TunnelState.Connected -> "Tunnel Online"
                    is TunnelState.Connecting -> "Connecting..."
                    is TunnelState.Stopping -> "Stopping..."
                    is TunnelState.Error -> "Tunnel Error"
                    is TunnelState.Disconnected -> "Tunnel Idle"
                  },
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        },
        actions = {
          // Userscript Bridge Status Chip in Header
          val bridgeColor = when {
            isBridgeSynced -> StatusGreen
            devBypass -> StatusAmber
            else -> MaterialTheme.colorScheme.error
          }
          val bridgeText = when {
            isBridgeSynced -> "Bridge Synced"
            devBypass -> "Dev Bypass"
            else -> "Bridge Required"
          }

          Box(
            modifier = Modifier
              .padding(end = 12.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(bridgeColor.copy(alpha = 0.15f))
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (isBridgeSynced) Icons.Default.CheckCircle else Icons.Default.Sync,
                contentDescription = null,
                tint = bridgeColor,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = bridgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = bridgeColor
              )
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
      if (!isUnlocked) {
        // Gated Screen: Requires Userscript Bridge Synchronized connection
        BridgeSyncGateScreen(modifier = Modifier.fillMaxSize())
      } else {
        // Unlocked Full Dashboard
        TunnelControlCard(
          state = tunnelState,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Navigation Tabs
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.primary,
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
                Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reverse Proxy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_reverse_proxy")
          )

          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_endpoints_test")
          )

          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Traffic", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_traffic_logs")
          )

          Tab(
            selected = selectedTab == 3,
            onClick = { selectedTab = 3 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Engine", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              }
            },
            modifier = Modifier.testTag("tab_debug_config_engine")
          )
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          when (selectedTab) {
            0 -> ReverseProxySection()
            1 -> EndpointsTesterSection()
            2 -> TrafficLogsSection()
            3 -> DebugConfigEngineSection()
          }
        }
      }
    }
  }
}
