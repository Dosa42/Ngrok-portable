package com.example.tunnel

import android.os.Build
import android.util.Log
import com.example.bridge.BridgeSyncManager
import com.example.bridge.UserscriptSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmbeddedHttpServer(
  private val port: Int = 8085,
  private val onRequestHandled: (TrafficLogEntry) -> Unit = {}
) {
  private val tag = "EmbeddedHttpServer"
  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO)
  private val startTime = System.currentTimeMillis()

  @Volatile
  var isRunning = false
    private set

  fun start() {
    if (isRunning) return
    try {
      serverSocket = ServerSocket(port).apply {
        reuseAddress = true
      }
      isRunning = true
      Log.i(tag, "Embedded HTTP Server listening on port $port")

      serverJob = scope.launch {
        while (isActive && isRunning) {
          try {
            val clientSocket = serverSocket?.accept() ?: break
            launch(Dispatchers.IO) {
              handleClient(clientSocket)
            }
          } catch (e: Exception) {
            if (isRunning) {
              Log.w(tag, "Server accept exception: ${e.message}")
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Failed to start server on port $port: ${e.message}", e)
      isRunning = false
      throw e
    }
  }

  private fun handleClient(socket: Socket) {
    val startMs = System.currentTimeMillis()
    val clientIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"
    var method = "GET"
    var path = "/"
    var statusCode = 200
    val headers = mutableMapOf<String, String>()
    var body = ""

    try {
      socket.use { s ->
        s.soTimeout = 15000
        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
        val output = s.getOutputStream()

        val requestLine = reader.readLine()
        if (requestLine.isNullOrBlank()) {
          return
        }

        val parts = requestLine.split(" ")
        if (parts.size >= 2) {
          method = parts[0].uppercase(Locale.US)
          path = parts[1]
        }

        // Read headers
        var contentLength = 0
        var line: String?
        while (reader.readLine().also { line = it } != null) {
          if (line.isNullOrBlank()) break
          val colonIdx = line!!.indexOf(':')
          if (colonIdx > 0) {
            val key = line!!.substring(0, colonIdx).trim()
            val value = line!!.substring(colonIdx + 1).trim()
            headers[key] = value
            if (key.equals("content-length", ignoreCase = true)) {
              contentLength = value.toIntOrNull() ?: 0
            }
          }
        }

        // Read body if payload is present
        if (contentLength > 0 && contentLength < 100000) {
          val charBuf = CharArray(contentLength)
          var readTotal = 0
          while (readTotal < contentLength) {
            val r = reader.read(charBuf, readTotal, contentLength - readTotal)
            if (r == -1) break
            readTotal += r
          }
          body = String(charBuf, 0, readTotal)
        }

        // Handle CORS Preflight
        if (method == "OPTIONS") {
          sendCorsPreflight(output)
          statusCode = 204
          return
        }

        // Route handling
        when {
          // 1. Tampermonkey Userscript Handshake
          path == "/api/bridge/handshake" && method == "POST" -> {
            var scriptVersion = "1.0.0"
            var browser = "Tampermonkey Browser"
            var pageUrl = ""
            var pageTitle = ""

            try {
              if (body.isNotBlank()) {
                val json = JSONObject(body)
                scriptVersion = json.optString("script_version", "1.0.0")
                browser = json.optString("browser", "Tampermonkey Browser")
                pageUrl = json.optString("page_url", "")
                pageTitle = json.optString("page_title", "")
              }
            } catch (_: Exception) {}

            val sessionId = BridgeSyncManager.registerHandshake(
              scriptVersion = scriptVersion,
              browser = browser,
              pageUrl = pageUrl,
              pageTitle = pageTitle
            )

            statusCode = 200
            val jsonResp = JSONObject().apply {
              put("status", "synchronized")
              put("app", "Ngrok Agent Android")
              put("version", "1.0.0")
              put("session_id", sessionId)
              put("server_timestamp", System.currentTimeMillis())
              put("heartbeat_interval_ms", 3000)
            }.toString()
            sendResponse(output, 200, "application/json", jsonResp)
          }

          // 2. Tampermonkey Userscript Heartbeat
          path == "/api/bridge/heartbeat" -> {
            var sessionId = ""
            var currentUrl = ""
            try {
              if (body.isNotBlank()) {
                val json = JSONObject(body)
                sessionId = json.optString("session_id", "")
                currentUrl = json.optString("current_url", "")
              }
            } catch (_: Exception) {}

            BridgeSyncManager.registerHeartbeat(sessionId, currentUrl)
            statusCode = 200
            val jsonResp = JSONObject().apply {
              put("status", "active")
              put("server_time", System.currentTimeMillis())
            }.toString()
            sendResponse(output, 200, "application/json", jsonResp)
          }

          // 3. Bridge Status Check
          path == "/api/bridge/status" -> {
            statusCode = 200
            val isSynced = BridgeSyncManager.isSynced.value
            val client = BridgeSyncManager.clientInfo.value
            val jsonResp = JSONObject().apply {
              put("is_synced", isSynced)
              put("port", port)
              if (client != null) {
                put("browser", client.browser)
                put("page_url", client.pageUrl)
                put("latency_ms", client.latencyMs)
                put("last_heartbeat", client.lastHeartbeatTimestamp)
              }
            }.toString()
            sendResponse(output, 200, "application/json", jsonResp)
          }

          // 4. Raw Userscript Distribution for Direct 1-Click Install in Browser
          path == "/userscript/ngrok-agent-bridge.user.js" -> {
            statusCode = 200
            sendResponse(output, 200, "text/javascript; charset=UTF-8", UserscriptSource.SCRIPT_CONTENT)
          }

          // 5. Ping
          path == "/ping" -> {
            statusCode = 200
            val json = JSONObject().apply {
              put("status", "pong")
              put("uptime_seconds", (System.currentTimeMillis() - startTime) / 1000)
              put("port", port)
              put("bridge_synced", BridgeSyncManager.isSynced.value)
              put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
              put("timestamp", System.currentTimeMillis())
            }.toString(2)
            sendResponse(output, 200, "application/json", json)
          }

          // 6. Diagnostics Status
          path == "/status" -> {
            statusCode = 200
            val runtime = java.lang.Runtime.getRuntime()
            val json = JSONObject().apply {
              put("service", "Android Ngrok Portable Server")
              put("status", "online")
              put("port", port)
              put("bridge_synced", BridgeSyncManager.isSynced.value)
              put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
              put("uptime_ms", System.currentTimeMillis() - startTime)
              put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
              put("android_version", Build.VERSION.RELEASE)
              put("sdk_int", Build.VERSION.SDK_INT)
              put("memory_free_mb", runtime.freeMemory() / (1024 * 1024))
              put("memory_total_mb", runtime.totalMemory() / (1024 * 1024))
            }.toString(2)
            sendResponse(output, 200, "application/json", json)
          }

          // 7. Echo
          path == "/echo" -> {
            statusCode = 200
            val json = JSONObject().apply {
              put("method", method)
              put("path", path)
              put("client_ip", clientIp)
              put("headers", JSONObject(headers as Map<*, *>))
              put("body", body)
              put("timestamp", System.currentTimeMillis())
            }.toString(2)
            sendResponse(output, 200, "application/json", json)
          }

          // 8. Headers
          path == "/headers" -> {
            statusCode = 200
            val json = JSONObject(headers as Map<*, *>).toString(2)
            sendResponse(output, 200, "application/json", json)
          }

          // 9. Root HTML Status & Userscript Bridge Landing Page
          else -> {
            statusCode = 200
            val isSynced = BridgeSyncManager.isSynced.value
            val bridgeStatusText = if (isSynced) "🟢 SYNCED" else "🟡 AWAITING USERSCRIPT"
            val html = """
              <!DOCTYPE html>
              <html lang="en">
              <head>
                <meta charset="utf-8">
                <title>Ngrok Agent & Userscript Bridge</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0B1120; color: #E2E8F0; margin: 0; padding: 24px; }
                  .card { background: #1E293B; border-radius: 12px; padding: 24px; max-width: 650px; margin: 0 auto; box-shadow: 0 10px 25px rgba(0,0,0,0.5); border: 1px solid #334155; }
                  h1 { color: #38BDF8; margin-top: 0; display: flex; align-items: center; gap: 8px; font-size: 22px; }
                  .badge { background: #0EA5E9; color: #0F172A; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: bold; }
                  .badge-green { background: #10B981; color: #064E3B; }
                  .badge-yellow { background: #F59E0B; color: #78350F; }
                  .btn { display: inline-block; background: #0284C7; color: white; padding: 10px 18px; border-radius: 8px; text-decoration: none; font-weight: bold; margin-top: 12px; font-size: 14px; }
                  .btn:hover { background: #0369A1; }
                  .code { background: #0F172A; padding: 12px; border-radius: 8px; font-family: monospace; font-size: 13px; color: #A7F3D0; overflow-x: auto; margin-top: 8px; }
                  .info-row { display: flex; justify-content: space-between; margin: 8px 0; border-bottom: 1px solid #334155; padding-bottom: 6px; font-size: 14px; }
                  .endpoints a { color: #38BDF8; text-decoration: none; font-weight: 500; }
                  .endpoints a:hover { text-decoration: underline; }
                </style>
              </head>
              <body>
                <div class="card">
                  <h1>⚡ Ngrok Agent Bridge <span class="badge ${if (isSynced) "badge-green" else "badge-yellow"}">$bridgeStatusText</span></h1>
                  <p>Android Native Server & Tampermonkey Userscript Companion Bridge.</p>
                  
                  <div class="info-row"><span>Local HTTP Port:</span><span>$port</span></div>
                  <div class="info-row"><span>Device:</span><span>${Build.MANUFACTURER} ${Build.MODEL}</span></div>
                  <div class="info-row"><span>Bridge Status:</span><span>$bridgeStatusText</span></div>
                  
                  <h3 style="margin-top: 20px; color: #94A3B8;">Tampermonkey Userscript</h3>
                  <p>Install the explicit synchronization userscript in Tampermonkey or Violentmonkey to pair this browser with your Ngrok Agent App:</p>
                  <a href="/userscript/ngrok-agent-bridge.user.js" class="btn">📥 Install Userscript in Tampermonkey</a>
                  
                  <h3 style="margin-top: 24px; color: #94A3B8;">Built-in Bridge Endpoints</h3>
                  <div class="endpoints">
                    <p>• <code>POST /api/bridge/handshake</code> &mdash; Initiates browser handshake</p>
                    <p>• <code>POST /api/bridge/heartbeat</code> &mdash; Synchronizes continuous connection</p>
                    <p>• <a href="/api/bridge/status">/api/bridge/status</a> &mdash; Bridge synchronization diagnostic JSON</p>
                    <p>• <a href="/ping">/ping</a> &mdash; Fast health check</p>
                  </div>
                </div>
              </body>
              </html>
            """.trimIndent()
            sendResponse(output, 200, "text/html; charset=UTF-8", html)
          }
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "Client request error: ${e.message}")
    } finally {
      val durationMs = System.currentTimeMillis() - startMs
      val entry = TrafficLogEntry(
        method = method,
        path = path,
        statusCode = statusCode,
        clientIp = clientIp,
        responseDurationMs = durationMs,
        headers = headers,
        requestBody = if (body.isNotBlank()) body else null
      )
      onRequestHandled(entry)
    }
  }

  private fun sendCorsPreflight(output: OutputStream) {
    val headers = "HTTP/1.1 204 No Content\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, HEAD\r\n" +
      "Access-Control-Allow-Headers: Content-Type, Authorization, X-Ngrok-Agent-Bridge, Accept, Origin, User-Agent, X-Requested-With, *\r\n" +
      "Access-Control-Expose-Headers: *\r\n" +
      "Access-Control-Max-Age: 86400\r\n" +
      "Content-Length: 0\r\n" +
      "Connection: close\r\n\r\n"
    output.write(headers.toByteArray(Charsets.UTF_8))
    output.flush()
  }

  private fun sendResponse(output: OutputStream, status: Int, contentType: String, content: String) {
    val bytes = content.toByteArray(Charsets.UTF_8)
    val statusText = when (status) {
      200 -> "200 OK"
      204 -> "204 No Content"
      404 -> "404 Not Found"
      else -> "$status OK"
    }
    val headers = "HTTP/1.1 $statusText\r\n" +
      "Content-Type: $contentType\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, HEAD\r\n" +
      "Access-Control-Allow-Headers: Content-Type, Authorization, X-Ngrok-Agent-Bridge, Accept, Origin, User-Agent, X-Requested-With, *\r\n" +
      "Access-Control-Expose-Headers: *\r\n" +
      "\r\n"
    output.write(headers.toByteArray(Charsets.UTF_8))
    output.write(bytes)
    output.flush()
  }

  fun stop() {
    isRunning = false
    try {
      serverSocket?.close()
    } catch (_: Exception) {}
    serverSocket = null
    serverJob?.cancel()
    serverJob = null
    Log.i(tag, "Embedded HTTP Server stopped")
  }
}
