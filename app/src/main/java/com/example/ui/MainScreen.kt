package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayCircleOutline
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunnel.NgrokTunnelManager
import com.example.tunnel.TunnelState
import com.example.ui.components.EndpointsTesterSection
import com.example.ui.components.TrafficLogsSection
import com.example.ui.components.TunnelControlCard
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
  val tunnelState by NgrokTunnelManager.state.collectAsState()
  var selectedTab by remember { mutableIntStateOf(0) }

  val statusDotColor = when (tunnelState) {
    is TunnelState.Connected -> StatusGreen
    is TunnelState.Connecting -> StatusAmber
    is TunnelState.Stopping -> StatusAmber
    is TunnelState.Error -> StatusRed
    is TunnelState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
  }

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
      // Top Tunnel Lifecycle Control Hero Card
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
              Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Endpoints & Test")
            }
          },
          modifier = Modifier.testTag("tab_endpoints_test")
        )

        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Traffic Inspector")
            }
          },
          modifier = Modifier.testTag("tab_traffic_logs")
        )
      }

      // Tab Content
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (selectedTab) {
          0 -> EndpointsTesterSection()
          1 -> TrafficLogsSection()
        }
      }
    }
  }
}
