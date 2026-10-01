package com.example.bridge

import android.util.Log
import com.example.tunnel.TrafficLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class BridgeClientInfo(
  val sessionId: String = UUID.randomUUID().toString(),
  val scriptVersion: String = "1.0.0",
  val browser: String = "Unknown",
  val pageUrl: String = "",
  val pageTitle: String = "",
  val lastHeartbeatTimestamp: Long = System.currentTimeMillis(),
  val latencyMs: Long = 0L
)

data class BridgeLogItem(
  val id: String = UUID.randomUUID().toString(),
  val timestamp: Long = System.currentTimeMillis(),
  val type: String, // HANDSHAKE, HEARTBEAT, DISCONNECT, SYSTEM
  val message: String,
  val isSuccess: Boolean = true
)

object BridgeSyncManager {
  private const val TAG = "BridgeSyncManager"
  private const val HEARTBEAT_TIMEOUT_MS = 10000L

  private val scope = CoroutineScope(Dispatchers.Default)
  private var monitorJob: Job? = null

  private val _isSynced = MutableStateFlow(false)
  val isSynced: StateFlow<Boolean> = _isSynced.asStateFlow()

  private val _devBypass = MutableStateFlow(false)
  val devBypass: StateFlow<Boolean> = _devBypass.asStateFlow()

  private val _clientInfo = MutableStateFlow<BridgeClientInfo?>(null)
  val clientInfo: StateFlow<BridgeClientInfo?> = _clientInfo.asStateFlow()

  private val _bridgeLogs = MutableStateFlow<List<BridgeLogItem>>(emptyList())
  val bridgeLogs: StateFlow<List<BridgeLogItem>> = _bridgeLogs.asStateFlow()

  init {
    addLog("SYSTEM", "Ngrok Agent Bridge Manager initialized. Awaiting Tampermonkey Userscript...", true)
    startHeartbeatMonitor()
  }

  fun registerHandshake(
    scriptVersion: String,
    browser: String,
    pageUrl: String,
    pageTitle: String
  ): String {
    val sessionId = UUID.randomUUID().toString()
    val info = BridgeClientInfo(
      sessionId = sessionId,
      scriptVersion = scriptVersion,
      browser = browser,
      pageUrl = pageUrl,
      pageTitle = pageTitle,
      lastHeartbeatTimestamp = System.currentTimeMillis()
    )
    _clientInfo.value = info
    _isSynced.value = true
    addLog(
      "HANDSHAKE",
      "✅ Bridge Synced with Tampermonkey v$scriptVersion from $browser (${if (pageTitle.isNotBlank()) pageTitle else pageUrl})",
      true
    )
    return sessionId
  }

  fun registerHeartbeat(sessionId: String, currentUrl: String) {
    val current = _clientInfo.value
    val now = System.currentTimeMillis()
    if (current != null) {
      val latency = if (current.lastHeartbeatTimestamp > 0) now - current.lastHeartbeatTimestamp else 0
      _clientInfo.value = current.copy(
        lastHeartbeatTimestamp = now,
        pageUrl = currentUrl,
        latencyMs = latency
      )
    }
    _isSynced.value = true
  }

  private fun startHeartbeatMonitor() {
    monitorJob?.cancel()
    monitorJob = scope.launch {
      while (isActive) {
        delay(2000)
        val info = _clientInfo.value
        if (info != null && _isSynced.value) {
          val elapsed = System.currentTimeMillis() - info.lastHeartbeatTimestamp
          if (elapsed > HEARTBEAT_TIMEOUT_MS) {
            Log.w(TAG, "Bridge heartbeat timed out after ${elapsed}ms")
            _isSynced.value = false
            addLog("DISCONNECT", "⚠️ Userscript heartbeat lost (no response for ${elapsed / 1000}s)", false)
          }
        }
      }
    }
  }

  fun setDevBypass(bypass: Boolean) {
    _devBypass.value = bypass
    addLog("SYSTEM", if (bypass) "⚠️ Developer bypass enabled: Dashboard unlocked without userscript" else "🔒 Developer bypass disabled: Userscript sync required", true)
  }

  fun addLog(type: String, message: String, isSuccess: Boolean = true) {
    val item = BridgeLogItem(type = type, message = message, isSuccess = isSuccess)
    _bridgeLogs.update { current ->
      (listOf(item) + current).take(100)
    }
  }

  fun clearLogs() {
    _bridgeLogs.value = emptyList()
  }
}
