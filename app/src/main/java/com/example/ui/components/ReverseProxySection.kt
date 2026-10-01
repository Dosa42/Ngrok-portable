package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bridge.BridgeSyncManager
import com.example.proxy.CustomUpstreamRoute
import com.example.proxy.ProxyServiceRule
import com.example.proxy.ProxyTrafficLog
import com.example.proxy.ReverseProxyManager
import com.example.tunnel.NgrokConfig
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReverseProxySection(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    ReverseProxyManager.init(context)
  }

  val services by ReverseProxyManager.services.collectAsState()
  val customRoutes by ReverseProxyManager.customRoutes.collectAsState()
  val isGlobalEnabled by ReverseProxyManager.isGlobalEnabled.collectAsState()
  val stripTrackingGlobal by ReverseProxyManager.stripTrackingGlobal.collectAsState()
  val forceHttps by ReverseProxyManager.forceHttps.collectAsState()
  val injectCors by ReverseProxyManager.injectCors.collectAsState()
  val userAgentMode by ReverseProxyManager.userAgentMode.collectAsState()
  val trafficLogs by ReverseProxyManager.trafficLogs.collectAsState()
  val isBenchmarking by ReverseProxyManager.isBenchmarking.collectAsState()
  val isBridgeSynced by BridgeSyncManager.isSynced.collectAsState()

  var selectedSubTab by remember { mutableIntStateOf(0) }
  var showAddRouteDialog by remember { mutableStateOf(false) }
  val localPort = NgrokConfig.getLocalPort(context)

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .testTag("reverse_proxy_section")
  ) {
    // Top Hero Control Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
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
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isGlobalEnabled) StatusGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Transform,
                contentDescription = null,
                tint = if (isGlobalEnabled) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Reverse Proxy & Redirects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = if (isGlobalEnabled) "Active (${services.count { it.isEnabled }} frontends enabled)" else "Reverse Proxy Disabled",
                fontSize = 11.sp,
                color = if (isGlobalEnabled) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Switch(
            checked = isGlobalEnabled,
            onCheckedChange = { ReverseProxyManager.toggleGlobalEnabled(context, it) },
            modifier = Modifier.testTag("reverse_proxy_master_switch")
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Actions Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = { ReverseProxyManager.benchmarkAllInstances(context) },
            modifier = Modifier.weight(1f).height(38.dp).testTag("benchmark_all_instances_btn"),
            shape = RoundedCornerShape(8.dp),
            enabled = !isBenchmarking && isGlobalEnabled
          ) {
            if (isBenchmarking) {
              CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Testing...", fontSize = 11.sp)
            } else {
              Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Benchmark All", fontSize = 11.sp)
            }
          }

          OutlinedButton(
            onClick = { showAddRouteDialog = true },
            modifier = Modifier.weight(1f).height(38.dp).testTag("add_custom_route_btn"),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Route", fontSize = 11.sp)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Sub Navigation Tabs
    ScrollableTabRow(
      selectedTabIndex = selectedSubTab,
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.primary,
      edgePadding = 0.dp
    ) {
      Tab(
        selected = selectedSubTab == 0,
        onClick = { selectedSubTab = 0 },
        text = { Text("Privacy Frontends (${services.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        modifier = Modifier.testTag("tab_sub_frontends")
      )
      Tab(
        selected = selectedSubTab == 1,
        onClick = { selectedSubTab = 1 },
        text = { Text("Custom Routes (${customRoutes.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        modifier = Modifier.testTag("tab_sub_custom_routes")
      )
      Tab(
        selected = selectedSubTab == 2,
        onClick = { selectedSubTab = 2 },
        text = { Text("Engine Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        modifier = Modifier.testTag("tab_sub_engine_settings")
      )
      Tab(
        selected = selectedSubTab == 3,
        onClick = { selectedSubTab = 3 },
        text = { Text("Proxy Logs (${trafficLogs.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        modifier = Modifier.testTag("tab_sub_proxy_logs")
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Tab Contents
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (selectedSubTab) {
        0 -> PrivacyFrontendsList(services = services)
        1 -> CustomRoutesList(routes = customRoutes, onAddClick = { showAddRouteDialog = true })
        2 -> ProxyEngineSettings(
          stripTracking = stripTrackingGlobal,
          forceHttps = forceHttps,
          injectCors = injectCors,
          userAgentMode = userAgentMode
        )
        3 -> ProxyTrafficLogsList(logs = trafficLogs)
      }
    }
  }

  // Dialog: Add Custom Upstream Route
  if (showAddRouteDialog) {
    AddCustomRouteDialog(
      onDismiss = { showAddRouteDialog = false },
      onConfirm = { route ->
        ReverseProxyManager.addCustomRoute(context, route)
        showAddRouteDialog = false
        Toast.makeText(context, "Added upstream route: ${route.name}", Toast.LENGTH_SHORT).show()
      }
    )
  }
}

@Composable
fun PrivacyFrontendsList(
  services: List<ProxyServiceRule>
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("privacy_frontends_list"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(services, key = { it.id }) { service ->
      ServiceRuleCard(service = service)
    }
  }
}

@Composable
fun ServiceRuleCard(service: ProxyServiceRule) {
  val context = LocalContext.current
  var expandedDropdown by remember { mutableStateOf(false) }
  var isExpandedDetails by remember { mutableStateOf(false) }

  val latencyColor = when {
    service.latencyMs == null -> MaterialTheme.colorScheme.onSurfaceVariant
    service.latencyMs!! < 400 -> StatusGreen
    service.latencyMs!! < 1000 -> StatusAmber
    else -> StatusRed
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = service.name,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            if (service.latencyMs != null) {
              Spacer(modifier = Modifier.width(6.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(latencyColor.copy(alpha = 0.15f))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = "${service.latencyMs}ms",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = latencyColor,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          }
          Text(
            text = "From: ${service.sourceDomain}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Switch(
          checked = service.isEnabled,
          onCheckedChange = {
            ReverseProxyManager.updateServiceRule(context, service.id, isEnabled = it)
          },
          modifier = Modifier.testTag("toggle_service_${service.id}")
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Selected Target Instance Dropdown
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(modifier = Modifier.weight(1f)) {
          OutlinedButton(
            onClick = { expandedDropdown = true },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = service.selectedInstance,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
            }
          }

          DropdownMenu(
            expanded = expandedDropdown,
            onDismissRequest = { expandedDropdown = false }
          ) {
            service.availableInstances.forEach { instance ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = instance,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (instance == service.selectedInstance) FontWeight.Bold else FontWeight.Normal,
                    color = if (instance == service.selectedInstance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                },
                onClick = {
                  ReverseProxyManager.updateServiceRule(context, service.id, selectedInstance = instance)
                  expandedDropdown = false
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
          onClick = {
            try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(service.selectedInstance))
              context.startActivity(intent)
            } catch (_: Exception) {
              Toast.makeText(context, "Could not open instance", Toast.LENGTH_SHORT).show()
            }
          },
          modifier = Modifier.size(36.dp)
        ) {
          Icon(Icons.Default.OpenInBrowser, contentDescription = "Open instance", modifier = Modifier.size(18.dp))
        }
      }
    }
  }
}

@Composable
fun CustomRoutesList(
  routes: List<CustomUpstreamRoute>,
  onAddClick: () -> Unit
) {
  val context = LocalContext.current

  if (routes.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AltRoute, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text("No Custom Routes Defined", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Forward custom paths (e.g. /api/upstream) to remote targets.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(14.dp))
        Button(onClick = onAddClick, shape = RoundedCornerShape(8.dp)) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Custom Upstream Route")
        }
      }
    }
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(routes, key = { it.id }) { route ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(route.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                  text = "${route.pathPrefix}  ➔  ${route.targetBaseUrl}",
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.primary
                )
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                  checked = route.isEnabled,
                  onCheckedChange = { ReverseProxyManager.toggleCustomRoute(context, route.id, it) }
                )
                IconButton(
                  onClick = { ReverseProxyManager.removeCustomRoute(context, route.id) },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProxyEngineSettings(
  stripTracking: Boolean,
  forceHttps: Boolean,
  injectCors: Boolean,
  userAgentMode: String
) {
  val context = LocalContext.current
  val userAgentModes = listOf("Desktop Chrome (Spoofed)", "Mobile Safari", "Standard Default")

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("HTTP Transformation & Security", fontWeight = FontWeight.Bold, fontSize = 13.sp)

          // Strip Tracking Switch
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Strip Tracking Query Parameters", fontSize = 12.sp, fontWeight = FontWeight.Medium)
              Text("Removes utm_*, si, fbclid, gclid, ref from URLs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
              checked = stripTracking,
              onCheckedChange = { ReverseProxyManager.toggleStripTrackingGlobal(context, it) }
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

          // Force HTTPS Switch
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Force HTTPS on all Target Endpoints", fontSize = 12.sp, fontWeight = FontWeight.Medium)
              Text("Upgrades all upstream forwarded connections to TLS", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
              checked = forceHttps,
              onCheckedChange = { ReverseProxyManager.toggleForceHttps(context, it) }
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

          // Inject CORS Switch
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Inject Wildcard CORS Headers", fontSize = 12.sp, fontWeight = FontWeight.Medium)
              Text("Access-Control-Allow-Origin: * for browser userscripts", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
              checked = injectCors,
              onCheckedChange = { ReverseProxyManager.toggleInjectCors(context, it) }
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

          // User-Agent Spoofing
          Column(modifier = Modifier.fillMaxWidth()) {
            Text("User-Agent Header Spoofing", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              userAgentModes.forEach { mode ->
                FilterChip(
                  selected = userAgentMode == mode,
                  onClick = { ReverseProxyManager.setUserAgentMode(context, mode) },
                  label = { Text(mode, fontSize = 11.sp) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ProxyTrafficLogsList(
  logs: List<ProxyTrafficLog>
) {
  val context = LocalContext.current

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Reverse Proxy Traffic Inspector",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      IconButton(
        onClick = { ReverseProxyManager.clearTrafficLogs() },
        modifier = Modifier.size(28.dp)
      ) {
        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(16.dp))
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    if (logs.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
          .padding(20.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "No proxy traffic forwarded yet. Requests sent via /proxy?url=... will stream here.",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontFamily = FontFamily.Monospace,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(logs, key = { it.id }) { log ->
          ProxyTrafficLogCard(log = log)
        }
      }
    }
  }
}

@Composable
fun ProxyTrafficLogCard(log: ProxyTrafficLog) {
  val context = LocalContext.current
  val statusColor = if (log.statusCode in 200..399) StatusGreen else StatusRed
  val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .background(MaterialTheme.colorScheme.primaryContainer)
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(log.method, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
      }

      Spacer(modifier = Modifier.width(8.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = log.targetUrl,
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${log.durationMs}ms • ${log.bytesTransferred} bytes • $timeStr",
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      Text(
        text = "${log.statusCode}",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = statusColor,
        fontFamily = FontFamily.Monospace
      )
    }
  }
}

@Composable
fun AddCustomRouteDialog(
  onDismiss: () -> Unit,
  onConfirm: (CustomUpstreamRoute) -> Unit
) {
  var name by remember { mutableStateOf("") }
  var pathPrefix by remember { mutableStateOf("/api/") }
  var targetBaseUrl by remember { mutableStateOf("https://") }
  var injectCors by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Upstream Reverse Proxy Route") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Route Name (e.g. Weather API)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = pathPrefix,
          onValueChange = { pathPrefix = it },
          label = { Text("Path Prefix (e.g. /api/weather)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
        OutlinedTextField(
          value = targetBaseUrl,
          onValueChange = { targetBaseUrl = it },
          label = { Text("Target Upstream URL") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Inject CORS Headers", fontSize = 12.sp)
          Switch(checked = injectCors, onCheckedChange = { injectCors = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isNotBlank() && pathPrefix.isNotBlank() && targetBaseUrl.isNotBlank()) {
            onConfirm(
              CustomUpstreamRoute(
                name = name,
                pathPrefix = pathPrefix,
                targetBaseUrl = targetBaseUrl,
                injectCors = injectCors
              )
            )
          }
        },
        enabled = name.isNotBlank() && targetBaseUrl.isNotBlank()
      ) {
        Text("Save Route")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
