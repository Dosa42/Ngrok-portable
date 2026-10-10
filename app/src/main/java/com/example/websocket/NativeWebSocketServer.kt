package com.example.websocket

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.bridge.BridgeSyncManager
import com.example.proxy.ReverseProxyManager
import com.example.util.PhoneDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class WebSocketClientSession(
  val id: String = UUID.randomUUID().toString(),
  val clientIp: String,
  val origin: String = "Any (*)",
  val path: String = "/ws",
  val userAgent: String = "Browser",
  val connectedAt: Long = System.currentTimeMillis(),
  var lastPingTimestamp: Long = System.currentTimeMillis(),
  var messagesReceived: Long = 0L,
  var messagesSent: Long = 0L
) {
  val connectedDuration: String
    get() {
      val diffSec = (System.currentTimeMillis() - connectedAt) / 1000
      val mins = diffSec / 60
      val secs = diffSec % 60
      return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }
}

data class WebSocketLogMessage(
  val id: String = UUID.randomUUID().toString(),
  val timestamp: Long = System.currentTimeMillis(),
  val direction: String, // "INBOUND", "OUTBOUND", "SYSTEM", "ERROR"
  val clientId: String? = null,
  val payload: String,
  val isBinary: Boolean = false
) {
  val formattedTime: String
    get() {
      val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
      return sdf.format(Date(timestamp))
    }
}

class NativeWebSocketServer(
  val port: Int = 8086,
  private val context: Context? = null,
  private val onMessageReceivedCallback: ((String, WebSocketClientSession) -> Unit)? = null
) {
  private val tag = "NativeWebSocketServer"
  private val scope = CoroutineScope(Dispatchers.IO)
  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null

  @Volatile
  var isRunning = false
    private set

  private val activeSockets = ConcurrentHashMap<String, Pair<WebSocketClientSession, Socket>>()

  fun start() {
    if (isRunning) return
    try {
      serverSocket = ServerSocket(port).apply {
        reuseAddress = true
      }
      isRunning = true
      Log.i(tag, "Native Local WebSocket Server listening on port $port (CORS Blockers: Disabled / Origin: *)")

      serverJob = scope.launch {
        while (isActive && isRunning) {
          try {
            val client = serverSocket?.accept() ?: break
            launch(Dispatchers.IO) {
              handleClientConnection(client)
            }
          } catch (e: Exception) {
            if (isRunning) {
              Log.w(tag, "WebSocket server accept error: ${e.message}")
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Failed to start native WebSocket server on port $port: ${e.message}", e)
      isRunning = false
      throw e
    }
  }

  fun stop() {
    isRunning = false
    try {
      activeSockets.values.forEach { (_, socket) ->
        try {
          socket.close()
        } catch (_: Exception) {}
      }
      activeSockets.clear()
      serverSocket?.close()
      serverSocket = null
      serverJob?.cancel()
      serverJob = null
      Log.i(tag, "Native WebSocket Server stopped")
    } catch (e: Exception) {
      Log.w(tag, "Error stopping WebSocket server: ${e.message}")
    }
  }

  /**
   * Performs RFC 6455 Handshake and enters WebSocket Frame Loop
   */
  private fun handleClientConnection(socket: Socket) {
    val clientIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"
    var sessionId = UUID.randomUUID().toString()
    var session: WebSocketClientSession? = null

    try {
      socket.tcpNoDelay = true
      socket.soTimeout = 0 // Keep alive for WebSocket streaming

      val inputStream = BufferedInputStream(socket.getInputStream())
      val outputStream = BufferedOutputStream(socket.getOutputStream())

      // 1. Read HTTP Upgrade Request Header
      val headers = mutableMapOf<String, String>()
      var path = "/ws"
      var method = "GET"
      val headerBuilder = StringBuilder()

      val lineReader = StringBuilder()
      var firstLine = true
      while (true) {
        val line = readHttpLine(inputStream) ?: break
        if (line.isEmpty()) break // End of HTTP headers
        headerBuilder.append(line).append("\r\n")

        if (firstLine) {
          val parts = line.split(" ")
          if (parts.size >= 2) {
            method = parts[0].uppercase(Locale.US)
            path = parts[1]
          }
          firstLine = false
        } else {
          val colon = line.indexOf(':')
          if (colon > 0) {
            val k = line.substring(0, colon).trim().lowercase(Locale.US)
            val v = line.substring(colon + 1).trim()
            headers[k] = v
          }
        }
      }

      val origin = headers["origin"] ?: "Any (*)"
      val userAgent = headers["user-agent"] ?: "Browser Client"
      val secWebSocketKey = headers["sec-websocket-key"]

      // Handle CORS Preflight / HTTP fallback if not WebSocket upgrade
      val isUpgrade = headers["upgrade"]?.equals("websocket", ignoreCase = true) == true
      if (!isUpgrade || secWebSocketKey == null) {
        // Send permissive CORS response
        sendHttpCorsResponse(outputStream, method)
        socket.close()
        return
      }

      // 2. Compute RFC 6455 Sec-WebSocket-Accept
      val acceptKey = computeSecWebSocketAccept(secWebSocketKey)

      // 3. Send 101 Switching Protocols with permissive headers (No CORS blockers!)
      val handshakeResponse = "HTTP/1.1 101 Switching Protocols\r\n" +
        "Upgrade: websocket\r\n" +
        "Connection: Upgrade\r\n" +
        "Sec-WebSocket-Accept: $acceptKey\r\n" +
        "Access-Control-Allow-Origin: *\r\n" +
        "Access-Control-Allow-Credentials: true\r\n" +
        "Access-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE\r\n" +
        "Access-Control-Allow-Headers: *\r\n" +
        "Server: Android-Native-WebSocket-Engine\r\n\r\n"

      outputStream.write(handshakeResponse.toByteArray(StandardCharsets.US_ASCII))
      outputStream.flush()

      session = WebSocketClientSession(
        id = sessionId,
        clientIp = clientIp,
        origin = origin,
        path = path,
        userAgent = userAgent
      )
      activeSockets[sessionId] = Pair(session, socket)
      WebSocketManager.registerClientConnected(session)

      // Send initial Welcome & Handshake payload to the connected browser
      val welcomePayload = JSONObject().apply {
        put("type", "WEBSOCKET_CONNECTED")
        put("sessionId", sessionId)
        put("server", "Android-Native-WebSocket-Engine")
        put("port", port)
        put("path", path)
        put("cors", "UNRESTRICTED")
        put("timestamp", System.currentTimeMillis())
        put("phone", JSONObject().apply {
          put("model", android.os.Build.MODEL)
          put("manufacturer", android.os.Build.MANUFACTURER)
          put("androidVersion", android.os.Build.VERSION.RELEASE)
        })
      }
      sendTextMessage(outputStream, welcomePayload.toString())
      WebSocketManager.recordMessage("OUTBOUND", sessionId, welcomePayload.toString())

      // 4. WebSocket Frame Loop (RFC 6455)
      while (isRunning && !socket.isClosed) {
        val frame = readWebSocketFrame(inputStream) ?: break

        when (frame.opcode) {
          0x1 -> { // Text Frame
            val text = String(frame.payload, StandardCharsets.UTF_8)
            session.messagesReceived++
            WebSocketManager.recordMessage("INBOUND", sessionId, text)

            // Process message and send reactive reply
            handleIncomingTextMessage(text, session, outputStream)
            onMessageReceivedCallback?.invoke(text, session)
          }
          0x2 -> { // Binary Frame
            session.messagesReceived++
            WebSocketManager.recordMessage("INBOUND", sessionId, "[Binary Frame ${frame.payload.size} bytes]", isBinary = true)
          }
          0x8 -> { // Close Frame
            Log.i(tag, "Client requested WebSocket Close: $sessionId")
            sendCloseFrame(outputStream)
            break
          }
          0x9 -> { // Ping -> send Pong
            session.lastPingTimestamp = System.currentTimeMillis()
            sendPongFrame(outputStream, frame.payload)
          }
          0xA -> { // Pong
            session.lastPingTimestamp = System.currentTimeMillis()
          }
        }
      }
    } catch (e: Exception) {
      if (isRunning) {
        Log.w(tag, "WebSocket client error ($sessionId): ${e.message}")
      }
    } finally {
      if (session != null) {
        activeSockets.remove(sessionId)
        WebSocketManager.registerClientDisconnected(session)
      }
      try {
        socket.close()
      } catch (_: Exception) {}
    }
  }

  private fun handleIncomingTextMessage(
    text: String,
    session: WebSocketClientSession,
    output: OutputStream
  ) {
    try {
      val trimmed = text.trim()
      if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        val json = JSONObject(trimmed)
        val action = json.optString("action", json.optString("type", "")).lowercase(Locale.US)

        when (action) {
          "ping" -> {
            val pong = JSONObject().apply {
              put("type", "pong")
              put("clientTimestamp", json.optLong("timestamp", System.currentTimeMillis()))
              put("serverTimestamp", System.currentTimeMillis())
              put("sessionId", session.id)
            }
            sendTextMessage(output, pong.toString())
            WebSocketManager.recordMessage("OUTBOUND", session.id, pong.toString())
          }

          "get_phone_status", "telemetry" -> {
            val phoneData = context?.let { PhoneDeviceManager.getTelemetryJson(it) }
              ?: JSONObject().apply { put("model", android.os.Build.MODEL) }
            val reply = JSONObject().apply {
              put("type", "PHONE_STATUS")
              put("data", phoneData)
              put("timestamp", System.currentTimeMillis())
            }
            sendTextMessage(output, reply.toString())
            WebSocketManager.recordMessage("OUTBOUND", session.id, reply.toString())
          }

          "get_proxy_rules", "proxy_status" -> {
            val proxyConfig = ReverseProxyManager.getFullConfigJson()
            val reply = JSONObject().apply {
              put("type", "PROXY_CONFIG")
              put("config", proxyConfig)
              put("timestamp", System.currentTimeMillis())
            }
            sendTextMessage(output, reply.toString())
            WebSocketManager.recordMessage("OUTBOUND", session.id, reply.toString())
          }

          "bridge_handshake", "bridge_sync" -> {
            val scriptVersion = json.optString("version", "1.0.0")
            val browser = json.optString("browser", session.userAgent)
            val pageUrl = json.optString("url", session.origin)
            val pageTitle = json.optString("title", "")
            BridgeSyncManager.registerHandshake(scriptVersion, browser, pageUrl, pageTitle)

            val reply = JSONObject().apply {
              put("type", "BRIDGE_SYNC_ACK")
              put("synced", true)
              put("sessionId", session.id)
              put("timestamp", System.currentTimeMillis())
            }
            sendTextMessage(output, reply.toString())
            WebSocketManager.recordMessage("OUTBOUND", session.id, reply.toString())
          }

          "echo" -> {
            val reply = JSONObject().apply {
              put("type", "ECHO_REPLY")
              put("payload", json.opt("payload"))
              put("timestamp", System.currentTimeMillis())
            }
            sendTextMessage(output, reply.toString())
            WebSocketManager.recordMessage("OUTBOUND", session.id, reply.toString())
          }

          else -> {
            // Default acknowledge
            val reply = JSONObject().apply {
              put("type", "MESSAGE_ACK")
              put("receivedAction", action)
              put("status", "SUCCESS")
              put("timestamp", System.currentTimeMillis())
            }
            sendTextMessage(output, reply.toString())
            WebSocketManager.recordMessage("OUTBOUND", session.id, reply.toString())
          }
        }
      } else {
        // Plain text message -> echo with ack
        val reply = JSONObject().apply {
          put("type", "TEXT_ACK")
          put("echo", text)
          put("timestamp", System.currentTimeMillis())
        }
        sendTextMessage(output, reply.toString())
        WebSocketManager.recordMessage("OUTBOUND", session.id, reply.toString())
      }
    } catch (e: Exception) {
      Log.e(tag, "Error dispatching incoming WebSocket message: ${e.message}", e)
    }
  }

  /**
   * Broadcast a message to all connected WebSocket clients
   */
  fun broadcastTextMessage(message: String, targetPath: String? = null) {
    scope.launch {
      activeSockets.values.forEach { (session, socket) ->
        if (targetPath == null || session.path.startsWith(targetPath)) {
          try {
            val out = BufferedOutputStream(socket.getOutputStream())
            sendTextMessage(out, message)
            session.messagesSent++
            WebSocketManager.recordMessage("OUTBOUND", session.id, message)
          } catch (e: Exception) {
            Log.w(tag, "Failed broadcasting to ${session.id}: ${e.message}")
          }
        }
      }
    }
  }

  /**
   * Computes RFC 6455 Sec-WebSocket-Accept token:
   * Base64(SHA-1(Key + 258EAFA5-E914-47DA-95CA-C5AB0DC85B11))
   */
  private fun computeSecWebSocketAccept(key: String): String {
    val magicGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    val digest = MessageDigest.getInstance("SHA-1")
    val hash = digest.digest((key + magicGuid).toByteArray(StandardCharsets.US_ASCII))
    return Base64.encodeToString(hash, Base64.NO_WRAP)
  }

  private fun readHttpLine(inputStream: InputStream): String? {
    val sb = StringBuilder()
    var prev = -1
    while (true) {
      val b = inputStream.read()
      if (b == -1) {
        return if (sb.isEmpty()) null else sb.toString()
      }
      if (prev == '\r'.code && b == '\n'.code) {
        sb.deleteCharAt(sb.length - 1)
        return sb.toString()
      }
      sb.append(b.toChar())
      prev = b
    }
  }

  private fun sendHttpCorsResponse(outputStream: OutputStream, method: String) {
    val body = if (method == "OPTIONS") "" else "{\"status\": \"ok\", \"websocket\": \"ws://127.0.0.1:$port/ws\"}"
    val resp = "HTTP/1.1 200 OK\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Access-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE\r\n" +
      "Access-Control-Allow-Headers: *\r\n" +
      "Content-Type: application/json\r\n" +
      "Content-Length: ${body.length}\r\n\r\n" +
      body
    outputStream.write(resp.toByteArray(StandardCharsets.UTF_8))
    outputStream.flush()
  }

  // --- RFC 6455 Frame Encoding & Decoding ---

  private data class WebSocketFrame(
    val fin: Boolean,
    val opcode: Int,
    val payload: ByteArray
  )

  private fun readWebSocketFrame(inputStream: InputStream): WebSocketFrame? {
    val b0 = inputStream.read()
    if (b0 == -1) return null
    val fin = (b0 and 0x80) != 0
    val opcode = b0 and 0x0F

    val b1 = inputStream.read()
    if (b1 == -1) return null
    val isMasked = (b1 and 0x80) != 0
    var payloadLen = (b1 and 0x7F).toLong()

    if (payloadLen == 126L) {
      val b2 = inputStream.read()
      val b3 = inputStream.read()
      if (b2 == -1 || b3 == -1) return null
      payloadLen = ((b2 and 0xFF) shl 8 or (b3 and 0xFF)).toLong()
    } else if (payloadLen == 127L) {
      var len = 0L
      for (i in 0 until 8) {
        val b = inputStream.read()
        if (b == -1) return null
        len = (len shl 8) or (b.toLong() and 0xFF)
      }
      payloadLen = len
    }

    val maskingKey = if (isMasked) {
      val key = ByteArray(4)
      var read = 0
      while (read < 4) {
        val r = inputStream.read(key, read, 4 - read)
        if (r == -1) return null
        read += r
      }
      key
    } else {
      null
    }

    if (payloadLen > 10 * 1024 * 1024) { // 10MB safety threshold
      throw IllegalStateException("WebSocket frame too large: $payloadLen")
    }

    val payload = ByteArray(payloadLen.toInt())
    var totalRead = 0
    while (totalRead < payload.size) {
      val r = inputStream.read(payload, totalRead, payload.size - totalRead)
      if (r == -1) return null
      totalRead += r
    }

    if (isMasked && maskingKey != null) {
      for (i in payload.indices) {
        payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
      }
    }

    return WebSocketFrame(fin, opcode, payload)
  }

  @Synchronized
  private fun sendTextMessage(output: OutputStream, text: String) {
    val bytes = text.toByteArray(StandardCharsets.UTF_8)
    val len = bytes.size

    output.write(0x81) // FIN=1, Opcode=1 (Text)
    if (len <= 125) {
      output.write(len)
    } else if (len <= 65535) {
      output.write(126)
      output.write((len shr 8) and 0xFF)
      output.write(len and 0xFF)
    } else {
      output.write(127)
      for (i in 7 downTo 0) {
        output.write(((len.toLong() shr (i * 8)) and 0xFF).toInt())
      }
    }
    output.write(bytes)
    output.flush()
  }

  @Synchronized
  private fun sendPongFrame(output: OutputStream, payload: ByteArray) {
    output.write(0x8A) // FIN=1, Opcode=0xA (Pong)
    output.write(payload.size and 0x7F)
    output.write(payload)
    output.flush()
  }

  @Synchronized
  private fun sendCloseFrame(output: OutputStream) {
    output.write(0x88) // FIN=1, Opcode=0x8 (Close)
    output.write(0)
    output.flush()
  }
}
