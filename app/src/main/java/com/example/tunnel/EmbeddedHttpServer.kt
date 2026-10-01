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
          // 1. Raw 1-Click Userscript Distribution (Tampermonkey Auto-Interception endpoints)
          path == "/Proxy-Redirect.user.js" ||
          path == "/userscript/Proxy-Redirect.user.js" ||
          path == "/userscript/ngrok-agent-bridge.user.js" -> {
            statusCode = 200
            sendScriptResponse(output, UserscriptSource.SCRIPT_CONTENT)
          }

          // 2. Tampermonkey Userscript Handshake
          path == "/api/bridge/handshake" && method == "POST" -> {
            var scriptVersion = "26.08.24"
            var browser = "Tampermonkey Browser"
            var pageUrl = ""
            var pageTitle = ""

            try {
              if (body.isNotBlank()) {
                val json = JSONObject(body)
                scriptVersion = json.optString("script_version", "26.08.24")
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
              put("version", "26.08.24")
              put("session_id", sessionId)
              put("server_timestamp", System.currentTimeMillis())
              put("heartbeat_interval_ms", 3000)
            }.toString()
            sendResponse(output, 200, "application/json", jsonResp)
          }

          // 3. Tampermonkey Userscript Heartbeat
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

          // 4. Bridge Status Check
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

          // 9. Root GreasyFork-Style 1-Click Install Web Hub
          else -> {
            statusCode = 200
            val isSynced = BridgeSyncManager.isSynced.value
            val bridgeStatusBadge = if (isSynced)
              """<span style="background: #10B981; color: #064E3B; padding: 4px 12px; border-radius: 999px; font-weight: bold; font-size: 13px;">🟢 SYNCHRONIZED</span>"""
            else
              """<span style="background: #F59E0B; color: #78350F; padding: 4px 12px; border-radius: 999px; font-weight: bold; font-size: 13px;">🟡 WAITING FOR TAMPERMONKEY</span>"""

            val html = """
              <!DOCTYPE html>
              <html lang="en">
              <head>
                <meta charset="utf-8">
                <title>Proxy Redirect — Ngrok Agent Synced Userscript</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  * { box-sizing: border-box; }
                  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0F172A; color: #E2E8F0; margin: 0; padding: 20px; }
                  .container { max-width: 780px; margin: 0 auto; background: #1E293B; border-radius: 16px; padding: 30px; box-shadow: 0 15px 35px rgba(0,0,0,0.5); border: 1px solid #334155; }
                  .header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #334155; padding-bottom: 20px; margin-bottom: 20px; }
                  h1 { margin: 0; color: #38BDF8; font-size: 24px; display: flex; align-items: center; gap: 10px; }
                  .subtitle { color: #94A3B8; font-size: 14px; margin-top: 6px; }
                  .install-hero { background: #0B1120; border: 2px dashed #0284C7; border-radius: 14px; padding: 24px; text-align: center; margin: 24px 0; }
                  .install-btn { display: inline-block; background: #10B981; color: #064E3B; font-weight: 800; font-size: 18px; padding: 14px 28px; border-radius: 10px; text-decoration: none; box-shadow: 0 6px 20px rgba(16,185,129,0.4); transition: transform 0.15s ease; }
                  .install-btn:hover { transform: translateY(-2px); background: #34D399; }
                  .meta-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; margin: 20px 0; }
                  .meta-card { background: #0F172A; border-radius: 10px; padding: 14px; border: 1px solid #334155; font-size: 13px; }
                  .meta-label { color: #94A3B8; margin-bottom: 4px; font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; }
                  .meta-val { color: #F1F5F9; font-weight: 600; }
                  .step-box { background: #0F172A; border-radius: 10px; padding: 16px; margin-top: 20px; }
                  .step-title { font-weight: bold; color: #38BDF8; margin-bottom: 8px; font-size: 14px; }
                  code { background: #1E293B; padding: 2px 6px; border-radius: 4px; color: #A7F3D0; font-family: monospace; font-size: 12px; }
                  .endpoint-list a { color: #38BDF8; text-decoration: none; }
                </style>
              </head>
              <body>
                <div class="container">
                  <div class="header">
                    <div>
                      <h1>🥸 Proxy Redirect</h1>
                      <div class="subtitle">Hardcoded Synchronization Bridge & Companion for Ngrok Agent Android App</div>
                    </div>
                    <div>
                      $bridgeStatusBadge
                    </div>
                  </div>

                  <div class="install-hero">
                    <p style="font-size: 15px; margin-top: 0; color: #CBD5E1;">
                      Click below to trigger the native <strong>1-Click Tampermonkey / Violentmonkey</strong> installation dialog:
                    </p>
                    <a href="/Proxy-Redirect.user.js" class="install-btn">⚡ 1-Click Install Script</a>
                    <p style="font-size: 12px; color: #64748B; margin-bottom: 0; margin-top: 12px;">
                      Direct Script Endpoint: <code>http://127.0.0.1:$port/Proxy-Redirect.user.js</code>
                    </p>
                  </div>

                  <div class="meta-grid">
                    <div class="meta-card">
                      <div class="meta-label">Version</div>
                      <div class="meta-val">26.08.24-ngrok-bridge</div>
                    </div>
                    <div class="meta-card">
                      <div class="meta-label">Local Host</div>
                      <div class="meta-val">127.0.0.1:$port</div>
                    </div>
                    <div class="meta-card">
                      <div class="meta-label">Bridge Handshake</div>
                      <div class="meta-val">POST /api/bridge/handshake</div>
                    </div>
                    <div class="meta-card">
                      <div class="meta-label">Android Device</div>
                      <div class="meta-val">${Build.MANUFACTURER} ${Build.MODEL}</div>
                    </div>
                  </div>

                  <div class="step-box">
                    <div class="step-title">How 1-Click Synchronization Works:</div>
                    <p style="font-size: 13px; color: #94A3B8; margin: 4px 0;">1. Make sure you have Tampermonkey or Violentmonkey installed in your browser.</p>
                    <p style="font-size: 13px; color: #94A3B8; margin: 4px 0;">2. Click <strong>"1-Click Install Script"</strong> above — the extension will prompt you to confirm.</p>
                    <p style="font-size: 13px; color: #94A3B8; margin: 4px 0;">3. As soon as you browse any page, the script discovers the Android app and unlocks the full Ngrok dashboard!</p>
                  </div>

                  <h3 style="margin-top: 24px; color: #94A3B8; font-size: 14px;">Built-in Server Endpoints:</h3>
                  <div class="endpoint-list" style="font-size: 13px; line-height: 1.8;">
                    <div>• <a href="/Proxy-Redirect.user.js">/Proxy-Redirect.user.js</a> &mdash; Exact Userscript Binary File</div>
                    <div>• <a href="/ping">/ping</a> &mdash; Health Check</div>
                    <div>• <a href="/status">/status</a> &mdash; Server Status & Telemetry</div>
                    <div>• <a href="/api/bridge/status">/api/bridge/status</a> &mdash; Live Bridge Status JSON</div>
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

  private fun sendScriptResponse(output: OutputStream, scriptContent: String) {
    val bytes = scriptContent.toByteArray(Charsets.UTF_8)
    val headers = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: text/javascript; charset=UTF-8\r\n" +
      "Content-Disposition: inline; filename=\"Proxy-Redirect.user.js\"\r\n" +
      "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
      "Pragma: no-cache\r\n" +
      "Expires: 0\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n\r\n"
    output.write(headers.toByteArray(Charsets.UTF_8))
    output.write(bytes)
    output.flush()
  }

  private fun sendCorsPreflight(output: OutputStream) {
    val headers = "HTTP/1.1 204 No Content\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
      "Access-Control-Allow-Headers: Content-Type, Authorization, X-Ngrok-Agent-Bridge\r\n" +
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
      "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
      "Access-Control-Allow-Headers: Content-Type, Authorization, X-Ngrok-Agent-Bridge\r\n" +
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
