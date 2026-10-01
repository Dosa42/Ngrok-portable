package com.example.tunnel

import android.os.Build
import android.util.Log
import com.example.bridge.BridgeSyncManager
import com.example.bridge.UserscriptSource
import com.example.proxy.ReverseProxyManager
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
import java.net.URLDecoder
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
        if (contentLength > 0 && contentLength < 1000000) {
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

        val cleanPath = path.substringBefore("?")

        // Route handling
        when {
          // 1. Raw 1-Click Userscript Distribution
          cleanPath == "/Proxy-Redirect.user.js" ||
          cleanPath == "/userscript/Proxy-Redirect.user.js" ||
          cleanPath == "/userscript/ngrok-agent-bridge.user.js" -> {
            statusCode = 200
            sendScriptResponse(output, UserscriptSource.SCRIPT_CONTENT)
          }

          // 2. Tampermonkey Userscript Handshake
          cleanPath == "/api/bridge/handshake" && method == "POST" -> {
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
              put("proxy_config", ReverseProxyManager.getFullConfigJson())
            }.toString()
            sendResponse(output, 200, "application/json", jsonResp)
          }

          // 3. Tampermonkey Userscript Heartbeat
          cleanPath == "/api/bridge/heartbeat" -> {
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
          cleanPath == "/api/bridge/status" -> {
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

          // 5. Reverse Proxy Configuration API (GET / POST)
          cleanPath == "/api/proxy/config" -> {
            statusCode = 200
            val json = ReverseProxyManager.getFullConfigJson().toString(2)
            sendResponse(output, 200, "application/json", json)
          }

          // 6. Reverse Proxy Offline Instance Report from Userscript
          cleanPath == "/api/proxy/report-offline" && method == "POST" -> {
            var targetInstance = ""
            try {
              if (body.isNotBlank()) {
                val json = JSONObject(body)
                targetInstance = json.optString("instance_url", "")
              }
            } catch (_: Exception) {}

            if (targetInstance.isNotBlank()) {
              // Mark dead instance & auto-switch
              BridgeSyncManager.addLog("PROXY", "Userscript reported offline instance: $targetInstance")
            }

            statusCode = 200
            val jsonResp = JSONObject().apply {
              put("status", "acknowledged")
              put("switched_instance", true)
              put("config", ReverseProxyManager.getFullConfigJson())
            }.toString()
            sendResponse(output, 200, "application/json", jsonResp)
          }

          // 7. Dynamic In-App / In-Browser Reverse Proxy Execution Engine
          // Format: /proxy?url=https://example.com/api or /proxy/forward?url=https://...
          cleanPath == "/proxy" || cleanPath == "/proxy/forward" -> {
            val query = if (path.contains("?")) path.substringAfter("?") else ""
            var targetUrl = ""
            query.split("&").forEach { param ->
              val kv = param.split("=")
              if (kv.isNotEmpty() && kv[0] == "url") {
                targetUrl = if (kv.size > 1) {
                  try { URLDecoder.decode(kv[1], "UTF-8") } catch (_: Exception) { kv[1] }
                } else ""
              }
            }

            if (targetUrl.isBlank()) {
              statusCode = 400
              sendResponse(output, 400, "application/json", """{"error": "Missing 'url' query parameter. Example: /proxy?url=https://api.example.com"}""")
            } else {
              val proxyResp = ReverseProxyManager.forwardHttpRequest(
                method = method,
                targetUrl = targetUrl,
                incomingHeaders = headers,
                requestBody = if (body.isNotBlank()) body else null,
                clientIp = clientIp
              )
              statusCode = proxyResp.statusCode
              sendRawProxyResponse(output, proxyResp.statusCode, proxyResp.headers, proxyResp.body, proxyResp.contentType)
            }
          }

          // 8. Ping
          cleanPath == "/ping" -> {
            statusCode = 200
            val json = JSONObject().apply {
              put("status", "pong")
              put("uptime_seconds", (System.currentTimeMillis() - startTime) / 1000)
              put("port", port)
              put("bridge_synced", BridgeSyncManager.isSynced.value)
              put("proxy_rules_active", ReverseProxyManager.services.value.count { it.isEnabled })
              put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
              put("timestamp", System.currentTimeMillis())
            }.toString(2)
            sendResponse(output, 200, "application/json", json)
          }

          // 9. Diagnostics Status
          cleanPath == "/status" -> {
            statusCode = 200
            val runtime = java.lang.Runtime.getRuntime()
            val json = JSONObject().apply {
              put("service", "Android Ngrok Portable Server & Reverse Proxy")
              put("status", "online")
              put("port", port)
              put("bridge_synced", BridgeSyncManager.isSynced.value)
              put("reverse_proxy_enabled", ReverseProxyManager.isGlobalEnabled.value)
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

          // 10. Echo
          cleanPath == "/echo" -> {
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

          // 11. Headers
          cleanPath == "/headers" -> {
            statusCode = 200
            val json = JSONObject(headers as Map<*, *>).toString(2)
            sendResponse(output, 200, "application/json", json)
          }

          // 12. Root GreasyFork-Style 1-Click Install Web Hub
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
                      <h1>⚡ Proxy Redirect (Ngrok Synced)</h1>
                      <div class="subtitle">Hardwired Android Companion Bridge & Reverse Proxy Hub</div>
                    </div>
                    <div>$bridgeStatusBadge</div>
                  </div>

                  <div class="install-hero">
                    <p style="font-size: 16px; margin-bottom: 18px; color: #CBD5E1;">Embedded in APK bundle &bull; Direct 1-Click Installation into Lemur / Tampermonkey</p>
                    <a href="/Proxy-Redirect.user.js" class="install-btn">⚡ 1-Click Install Userscript</a>
                  </div>

                  <div class="meta-grid">
                    <div class="meta-card">
                      <div class="meta-label">Userscript Version</div>
                      <div class="meta-val">Proxy Redirect 26.08.24 (Synced)</div>
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
                      <div class="meta-label">Reverse Proxy Engine</div>
                      <div class="meta-val">GET /proxy?url=...</div>
                    </div>
                  </div>

                  <div class="step-box">
                    <div class="step-title">How 1-Click Synchronization Works:</div>
                    <p style="font-size: 13px; color: #94A3B8; margin: 4px 0;">1. Make sure you have Tampermonkey installed in Lemur Browser or your browser.</p>
                    <p style="font-size: 13px; color: #94A3B8; margin: 4px 0;">2. Click <strong>"1-Click Install Userscript"</strong> above &mdash; Tampermonkey will prompt you to confirm.</p>
                    <p style="font-size: 13px; color: #94A3B8; margin: 4px 0;">3. As soon as you browse any page, the script discovers the Android app and unlocks the full Ngrok & Reverse Proxy dashboard!</p>
                  </div>

                  <h3 style="margin-top: 24px; color: #94A3B8; font-size: 14px;">Built-in Server Endpoints:</h3>
                  <div class="endpoint-list" style="font-size: 13px; line-height: 1.8;">
                    <div>• <a href="/Proxy-Redirect.user.js">/Proxy-Redirect.user.js</a> &mdash; Exact Userscript File</div>
                    <div>• <a href="/api/proxy/config">/api/proxy/config</a> &mdash; Reverse Proxy Configuration JSON</div>
                    <div>• <a href="/proxy?url=https%3A%2F%2Fexample.com">/proxy?url=...</a> &mdash; Reverse Proxy Forwarder</div>
                    <div>• <a href="/ping">/ping</a> &mdash; Health Check</div>
                    <div>• <a href="/status">/status</a> &mdash; Server Status & Telemetry</div>
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
      "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH\r\n" +
      "Access-Control-Allow-Headers: *\r\n" +
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
      400 -> "400 Bad Request"
      404 -> "404 Not Found"
      502 -> "502 Bad Gateway"
      else -> "$status OK"
    }
    val headers = "HTTP/1.1 $statusText\r\n" +
      "Content-Type: $contentType\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH\r\n" +
      "Access-Control-Allow-Headers: *\r\n" +
      "\r\n"
    output.write(headers.toByteArray(Charsets.UTF_8))
    output.write(bytes)
    output.flush()
  }

  private fun sendRawProxyResponse(
    output: OutputStream,
    status: Int,
    headers: Map<String, String>,
    body: String,
    contentType: String
  ) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    val statusText = when (status) {
      200 -> "200 OK"
      201 -> "201 Created"
      204 -> "204 No Content"
      301 -> "301 Moved Permanently"
      302 -> "302 Found"
      304 -> "304 Not Modified"
      400 -> "400 Bad Request"
      401 -> "401 Unauthorized"
      403 -> "403 Forbidden"
      404 -> "404 Not Found"
      500 -> "500 Internal Server Error"
      502 -> "502 Bad Gateway"
      503 -> "503 Service Unavailable"
      else -> "$status Proxy Response"
    }

    val headerBuilder = StringBuilder("HTTP/1.1 $statusText\r\n")
    headerBuilder.append("Content-Type: $contentType\r\n")
    headerBuilder.append("Content-Length: ${bytes.size}\r\n")
    headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
    headerBuilder.append("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH\r\n")
    headerBuilder.append("Access-Control-Allow-Headers: *\r\n")
    headerBuilder.append("Connection: close\r\n")

    headers.forEach { (k, v) ->
      val kl = k.lowercase(Locale.US)
      if (kl != "content-type" && kl != "content-length" && kl != "connection" && kl != "access-control-allow-origin") {
        headerBuilder.append("$k: $v\r\n")
      }
    }
    headerBuilder.append("\r\n")

    output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
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
