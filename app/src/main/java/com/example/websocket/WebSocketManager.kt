package com.example.websocket

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WebSocketManager {
  private const val TAG = "WebSocketManager"
  private const val PREFS_NAME = "websocket_prefs"
  private const val KEY_PORT = "websocket_port"
  private const val KEY_AUTO_START = "auto_start"
  const val DEFAULT_PORT = 8086

  private val scope = CoroutineScope(Dispatchers.IO)
  private val mutex = Mutex()
  private var serverInstance: NativeWebSocketServer? = null

  private val _isRunning = MutableStateFlow(false)
  val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

  private val _currentPort = MutableStateFlow(DEFAULT_PORT)
  val currentPort: StateFlow<Int> = _currentPort.asStateFlow()

  private val _activeClients = MutableStateFlow<List<WebSocketClientSession>>(emptyList())
  val activeClients: StateFlow<List<WebSocketClientSession>> = _activeClients.asStateFlow()

  private val _recentLogs = MutableStateFlow<List<WebSocketLogMessage>>(emptyList())
  val recentLogs: StateFlow<List<WebSocketLogMessage>> = _recentLogs.asStateFlow()

  private val _totalReceivedCount = MutableStateFlow(0L)
  val totalReceivedCount: StateFlow<Long> = _totalReceivedCount.asStateFlow()

  private val _totalSentCount = MutableStateFlow(0L)
  val totalSentCount: StateFlow<Long> = _totalSentCount.asStateFlow()

  fun init(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
    _currentPort.value = port
    startServer(context, port)
  }

  fun getWebSocketPort(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getInt(KEY_PORT, DEFAULT_PORT)
  }

  fun setWebSocketPort(context: Context, port: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putInt(KEY_PORT, port)
      .apply()
    _currentPort.value = port
    restartServer(context, port)
  }

  fun startServer(context: Context, port: Int = _currentPort.value) {
    scope.launch {
      mutex.withLock {
        if (serverInstance?.isRunning == true && _currentPort.value == port) return@withLock
        try {
          serverInstance?.stop()
          serverInstance = NativeWebSocketServer(port = port, context = context.applicationContext)
          serverInstance?.start()
          _isRunning.value = true
          _currentPort.value = port
          recordMessage("SYSTEM", null, "Native WebSocket Server listening on ws://127.0.0.1:$port/ws (Zero CORS blockers)")
        } catch (e: Exception) {
          Log.e(TAG, "Failed starting WebSocket server: ${e.message}", e)
          _isRunning.value = false
          recordMessage("ERROR", null, "WebSocket start error on port $port: ${e.message}")
        }
      }
    }
  }

  fun stopServer() {
    scope.launch {
      mutex.withLock {
        try {
          serverInstance?.stop()
          serverInstance = null
          _isRunning.value = false
          _activeClients.value = emptyList()
          recordMessage("SYSTEM", null, "Native WebSocket Server stopped")
        } catch (e: Exception) {
          Log.e(TAG, "Error stopping WebSocket server: ${e.message}", e)
        }
      }
    }
  }

  fun restartServer(context: Context, port: Int = _currentPort.value) {
    stopServer()
    startServer(context, port)
  }

  fun broadcastMessage(payload: String, targetPath: String? = null) {
    serverInstance?.broadcastTextMessage(payload, targetPath)
  }

  fun clearLogs() {
    _recentLogs.value = emptyList()
  }

  internal fun registerClientConnected(session: WebSocketClientSession) {
    val current = _activeClients.value.toMutableList()
    current.add(session)
    _activeClients.value = current
    recordMessage("SYSTEM", session.id, "🟢 Browser client connected from ${session.clientIp} (Origin: ${session.origin}, Path: ${session.path})")
  }

  internal fun registerClientDisconnected(session: WebSocketClientSession) {
    val current = _activeClients.value.toMutableList()
    current.removeAll { it.id == session.id }
    _activeClients.value = current
    recordMessage("SYSTEM", session.id, "🔴 Browser client disconnected: ${session.id}")
  }

  internal fun recordMessage(
    direction: String,
    clientId: String?,
    payload: String,
    isBinary: Boolean = false
  ) {
    if (direction == "INBOUND") _totalReceivedCount.value++
    if (direction == "OUTBOUND") _totalSentCount.value++

    val item = WebSocketLogMessage(
      direction = direction,
      clientId = clientId,
      payload = payload,
      isBinary = isBinary
    )
    val list = _recentLogs.value.toMutableList()
    if (list.size > 200) {
      list.removeAt(0)
    }
    list.add(item)
    _recentLogs.value = list
  }
}
