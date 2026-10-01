package com.example.proxy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ProxyServiceRule(
  val id: String,
  val name: String,
  val category: String,
  val sourceDomain: String,
  val description: String,
  val defaultProxyDomain: String,
  val availableInstances: List<String>,
  var selectedInstance: String,
  var isEnabled: Boolean = true,
  var stripTracking: Boolean = true,
  var latencyMs: Long? = null,
  var isHealthy: Boolean = true
)

data class CustomUpstreamRoute(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val pathPrefix: String, // e.g., "/api/upstream"
  val targetBaseUrl: String, // e.g., "https://target-api.com"
  val isEnabled: Boolean = true,
  val injectCors: Boolean = true,
  val customHeaders: Map<String, String> = emptyMap()
)

data class ProxyTrafficLog(
  val id: String = UUID.randomUUID().toString(),
  val timestamp: Long = System.currentTimeMillis(),
  val method: String,
  val originalUrl: String,
  val targetUrl: String,
  val statusCode: Int,
  val durationMs: Long,
  val clientIp: String = "127.0.0.1",
  val bytesTransferred: Long = 0
)

object ReverseProxyManager {
  private const val TAG = "ReverseProxyManager"
  private const val PREFS_NAME = "reverse_proxy_prefs"
  private const val KEY_GLOBAL_ENABLED = "global_proxy_enabled"
  private const val KEY_STRIP_TRACKING_GLOBAL = "strip_tracking_global"
  private const val KEY_FORCE_HTTPS = "force_https"
  private const val KEY_INJECT_CORS = "inject_cors_global"
  private const val KEY_USER_AGENT_MODE = "user_agent_mode"
  private const val KEY_CUSTOM_RULES_JSON = "custom_rules_json"
  private const val KEY_SERVICE_CONFIGS_JSON = "service_configs_json"

  private val scope = CoroutineScope(Dispatchers.IO)
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val defaultServices = listOf(
    ProxyServiceRule(
      id = "youtube",
      name = "YouTube → Invidious / Piped",
      category = "Video & Media",
      sourceDomain = "youtube.com,youtu.be,m.youtube.com",
      description = "Redirects YouTube videos, channels and playlists to privacy-respecting frontends.",
      defaultProxyDomain = "https://yewtu.be",
      availableInstances = listOf(
        "https://yewtu.be",
        "https://inv.tux.pizza",
        "https://invidious.nerdvpn.de",
        "https://piped.video",
        "https://piped.mha.fi",
        "https://vid.puffyan.us"
      ),
      selectedInstance = "https://yewtu.be"
    ),
    ProxyServiceRule(
      id = "twitter",
      name = "Twitter / X → Nitter",
      category = "Social",
      sourceDomain = "twitter.com,x.com",
      description = "Lightweight, ad-free, tracking-free frontend for Twitter/X.",
      defaultProxyDomain = "https://nitter.privacydev.net",
      availableInstances = listOf(
        "https://nitter.privacydev.net",
        "https://nitter.poast.org",
        "https://nitter.lucabased.xyz",
        "https://nitter.lanterne-rouge.info"
      ),
      selectedInstance = "https://nitter.privacydev.net"
    ),
    ProxyServiceRule(
      id = "reddit",
      name = "Reddit → Redlib",
      category = "Forums",
      sourceDomain = "reddit.com,old.reddit.com,www.reddit.com",
      description = "Private, fast, JavaScript-optional Reddit frontend.",
      defaultProxyDomain = "https://redlib.tux.pizza",
      availableInstances = listOf(
        "https://redlib.tux.pizza",
        "https://redlib.privacydev.net",
        "https://redlib.catsarch.com",
        "https://safereddit.com"
      ),
      selectedInstance = "https://redlib.tux.pizza"
    ),
    ProxyServiceRule(
      id = "tiktok",
      name = "TikTok → ProxiTok",
      category = "Video & Media",
      sourceDomain = "tiktok.com,www.tiktok.com",
      description = "Open source alternative frontend for TikTok videos without telemetry.",
      defaultProxyDomain = "https://proxitok.pabloferreiro.es",
      availableInstances = listOf(
        "https://proxitok.pabloferreiro.es",
        "https://tok.habedieeh.re",
        "https://proxitok.privacydev.net"
      ),
      selectedInstance = "https://proxitok.pabloferreiro.es"
    ),
    ProxyServiceRule(
      id = "instagram",
      name = "Instagram → Proxigram",
      category = "Social",
      sourceDomain = "instagram.com,www.instagram.com",
      description = "Privacy focused frontend for Instagram posts and profiles.",
      defaultProxyDomain = "https://proxigram.lunar.icu",
      availableInstances = listOf(
        "https://proxigram.lunar.icu",
        "https://ig.privacydev.net"
      ),
      selectedInstance = "https://proxigram.lunar.icu"
    ),
    ProxyServiceRule(
      id = "medium",
      name = "Medium → Scribe",
      category = "Articles & Blogs",
      sourceDomain = "medium.com",
      description = "Alternative frontend to read Medium articles without paywalls and trackers.",
      defaultProxyDomain = "https://scribe.rip",
      availableInstances = listOf(
        "https://scribe.rip",
        "https://scribe.privacydev.net"
      ),
      selectedInstance = "https://scribe.rip"
    ),
    ProxyServiceRule(
      id = "wikipedia",
      name = "Wikipedia → Wikiless",
      category = "Knowledge",
      sourceDomain = "wikipedia.org,en.wikipedia.org",
      description = "Free open source alternative Wikipedia frontend.",
      defaultProxyDomain = "https://wikiless.org",
      availableInstances = listOf(
        "https://wikiless.org",
        "https://wikiless.tiekoetter.com"
      ),
      selectedInstance = "https://wikiless.org"
    ),
    ProxyServiceRule(
      id = "imgur",
      name = "Imgur → Rimgo",
      category = "Images",
      sourceDomain = "imgur.com,i.imgur.com",
      description = "An alternative, privacy-respecting frontend for Imgur.",
      defaultProxyDomain = "https://rimgo.total独立.org",
      availableInstances = listOf(
        "https://rimgo.totaltelepath.com",
        "https://rimgo.privacydev.net",
        "https://rimgo.catsarch.com"
      ),
      selectedInstance = "https://rimgo.privacydev.net"
    ),
    ProxyServiceRule(
      id = "search",
      name = "Google Search → SearXNG",
      category = "Search Engines",
      sourceDomain = "google.com/search,google.de/search",
      description = "Privacy-respecting, metasearch engine proxying Google results.",
      defaultProxyDomain = "https://search.privacydev.net",
      availableInstances = listOf(
        "https://search.privacydev.net",
        "https://searx.be",
        "https://priv.au"
      ),
      selectedInstance = "https://search.privacydev.net"
    )
  )

  private val _services = MutableStateFlow<List<ProxyServiceRule>>(defaultServices)
  val services: StateFlow<List<ProxyServiceRule>> = _services.asStateFlow()

  private val _customRoutes = MutableStateFlow<List<CustomUpstreamRoute>>(emptyList())
  val customRoutes: StateFlow<List<CustomUpstreamRoute>> = _customRoutes.asStateFlow()

  private val _isGlobalEnabled = MutableStateFlow(true)
  val isGlobalEnabled: StateFlow<Boolean> = _isGlobalEnabled.asStateFlow()

  private val _stripTrackingGlobal = MutableStateFlow(true)
  val stripTrackingGlobal: StateFlow<Boolean> = _stripTrackingGlobal.asStateFlow()

  private val _forceHttps = MutableStateFlow(true)
  val forceHttps: StateFlow<Boolean> = _forceHttps.asStateFlow()

  private val _injectCors = MutableStateFlow(true)
  val injectCors: StateFlow<Boolean> = _injectCors.asStateFlow()

  private val _userAgentMode = MutableStateFlow("Desktop Chrome (Spoofed)")
  val userAgentMode: StateFlow<String> = _userAgentMode.asStateFlow()

  private val _trafficLogs = MutableStateFlow<List<ProxyTrafficLog>>(emptyList())
  val trafficLogs: StateFlow<List<ProxyTrafficLog>> = _trafficLogs.asStateFlow()

  private val _isBenchmarking = MutableStateFlow(false)
  val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

  private var isInitialized = false

  fun init(context: Context) {
    if (isInitialized) return
    isInitialized = true
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    _isGlobalEnabled.value = prefs.getBoolean(KEY_GLOBAL_ENABLED, true)
    _stripTrackingGlobal.value = prefs.getBoolean(KEY_STRIP_TRACKING_GLOBAL, true)
    _forceHttps.value = prefs.getBoolean(KEY_FORCE_HTTPS, true)
    _injectCors.value = prefs.getBoolean(KEY_INJECT_CORS, true)
    _userAgentMode.value = prefs.getString(KEY_USER_AGENT_MODE, "Desktop Chrome (Spoofed)") ?: "Desktop Chrome (Spoofed)"

    // Load custom routes
    val customJson = prefs.getString(KEY_CUSTOM_RULES_JSON, null)
    if (!customJson.isNullOrBlank()) {
      try {
        val arr = JSONArray(customJson)
        val list = mutableListOf<CustomUpstreamRoute>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            CustomUpstreamRoute(
              id = obj.optString("id", UUID.randomUUID().toString()),
              name = obj.optString("name", "Custom Route"),
              pathPrefix = obj.optString("pathPrefix", "/api"),
              targetBaseUrl = obj.optString("targetBaseUrl", "https://example.com"),
              isEnabled = obj.optBoolean("isEnabled", true),
              injectCors = obj.optBoolean("injectCors", true)
            )
          )
        }
        _customRoutes.value = list
      } catch (e: Exception) {
        Log.e(TAG, "Error loading custom routes: ${e.message}")
      }
    }

    // Load saved service instance configs
    val serviceJson = prefs.getString(KEY_SERVICE_CONFIGS_JSON, null)
    if (!serviceJson.isNullOrBlank()) {
      try {
        val obj = JSONObject(serviceJson)
        val current = _services.value.toMutableList()
        for (i in current.indices) {
          val item = current[i]
          if (obj.has(item.id)) {
            val saved = obj.getJSONObject(item.id)
            current[i] = item.copy(
              selectedInstance = saved.optString("selectedInstance", item.selectedInstance),
              isEnabled = saved.optBoolean("isEnabled", item.isEnabled),
              stripTracking = saved.optBoolean("stripTracking", item.stripTracking)
            )
          }
        }
        _services.value = current
      } catch (e: Exception) {
        Log.e(TAG, "Error loading service configs: ${e.message}")
      }
    }
  }

  fun toggleGlobalEnabled(context: Context, enabled: Boolean) {
    _isGlobalEnabled.value = enabled
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_GLOBAL_ENABLED, enabled)
      .apply()
  }

  fun toggleStripTrackingGlobal(context: Context, enabled: Boolean) {
    _stripTrackingGlobal.value = enabled
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_STRIP_TRACKING_GLOBAL, enabled)
      .apply()
  }

  fun toggleForceHttps(context: Context, enabled: Boolean) {
    _forceHttps.value = enabled
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_FORCE_HTTPS, enabled)
      .apply()
  }

  fun toggleInjectCors(context: Context, enabled: Boolean) {
    _injectCors.value = enabled
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_INJECT_CORS, enabled)
      .apply()
  }

  fun setUserAgentMode(context: Context, mode: String) {
    _userAgentMode.value = mode
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_USER_AGENT_MODE, mode)
      .apply()
  }

  fun updateServiceRule(context: Context, serviceId: String, selectedInstance: String? = null, isEnabled: Boolean? = null) {
    val current = _services.value.toMutableList()
    val index = current.indexOfFirst { it.id == serviceId }
    if (index >= 0) {
      val old = current[index]
      val updated = old.copy(
        selectedInstance = selectedInstance ?: old.selectedInstance,
        isEnabled = isEnabled ?: old.isEnabled
      )
      current[index] = updated
      _services.value = current
      saveServices(context)
    }
  }

  fun addCustomRoute(context: Context, route: CustomUpstreamRoute) {
    val list = _customRoutes.value.toMutableList()
    list.add(route)
    _customRoutes.value = list
    saveCustomRoutes(context)
  }

  fun removeCustomRoute(context: Context, routeId: String) {
    val list = _customRoutes.value.filter { it.id != routeId }
    _customRoutes.value = list
    saveCustomRoutes(context)
  }

  fun toggleCustomRoute(context: Context, routeId: String, enabled: Boolean) {
    val list = _customRoutes.value.map {
      if (it.id == routeId) it.copy(isEnabled = enabled) else it
    }
    _customRoutes.value = list
    saveCustomRoutes(context)
  }

  private fun saveServices(context: Context) {
    val obj = JSONObject()
    _services.value.forEach { s ->
      val sObj = JSONObject().apply {
        put("selectedInstance", s.selectedInstance)
        put("isEnabled", s.isEnabled)
        put("stripTracking", s.stripTracking)
      }
      obj.put(s.id, sObj)
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_SERVICE_CONFIGS_JSON, obj.toString())
      .apply()
  }

  private fun saveCustomRoutes(context: Context) {
    val arr = JSONArray()
    _customRoutes.value.forEach { r ->
      val obj = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("pathPrefix", r.pathPrefix)
        put("targetBaseUrl", r.targetBaseUrl)
        put("isEnabled", r.isEnabled)
        put("injectCors", r.injectCors)
      }
      arr.put(obj)
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_CUSTOM_RULES_JSON, arr.toString())
      .apply()
  }

  fun benchmarkAllInstances(context: Context) {
    if (_isBenchmarking.value) return
    _isBenchmarking.value = true
    scope.launch {
      val currentList = _services.value.toMutableList()
      for (i in currentList.indices) {
        val s = currentList[i]
        val target = s.selectedInstance
        val start = System.currentTimeMillis()
        try {
          val req = Request.Builder()
            .url(target)
            .head()
            .build()
          val res = withContext(Dispatchers.IO) {
            httpClient.newCall(req).execute()
          }
          val duration = System.currentTimeMillis() - start
          val healthy = res.isSuccessful || res.code in 200..399
          currentList[i] = s.copy(latencyMs = duration, isHealthy = healthy)
        } catch (_: Exception) {
          currentList[i] = s.copy(latencyMs = null, isHealthy = false)
        }
      }
      _services.value = currentList
      _isBenchmarking.value = false
    }
  }

  fun handleReportOfflineInstance(context: Context, instanceUrl: String) {
    val current = _services.value.toMutableList()
    var updated = false
    for (i in current.indices) {
      val s = current[i]
      if (s.selectedInstance.equals(instanceUrl, ignoreCase = true) || s.availableInstances.contains(instanceUrl)) {
        val next = s.availableInstances.firstOrNull { !it.equals(instanceUrl, ignoreCase = true) } ?: s.defaultProxyDomain
        current[i] = s.copy(selectedInstance = next, isHealthy = false)
        updated = true
        Log.w(TAG, "Instance $instanceUrl reported dead. Auto-switched ${s.name} to $next")
      }
    }
    if (updated) {
      _services.value = current
      saveServices(context)
    }
  }

  fun recordTraffic(log: ProxyTrafficLog) {
    val current = _trafficLogs.value.toMutableList()
    current.add(0, log)
    if (current.size > 200) {
      current.removeAt(current.size - 1)
    }
    _trafficLogs.value = current
  }

  fun clearTrafficLogs() {
    _trafficLogs.value = emptyList()
  }

  /**
   * Execute real HTTP reverse proxy forwarding to upstream target
   */
  fun forwardHttpRequest(
    method: String,
    targetUrl: String,
    incomingHeaders: Map<String, String>,
    requestBody: String?,
    clientIp: String = "127.0.0.1"
  ): ProxyForwardResponse {
    val start = System.currentTimeMillis()
    try {
      var finalUrl = targetUrl
      if (_forceHttps.value && finalUrl.startsWith("http://") && !finalUrl.startsWith("http://127.0.0.1") && !finalUrl.startsWith("http://localhost")) {
        finalUrl = finalUrl.replaceFirst("http://", "https://")
      }

      if (_stripTrackingGlobal.value) {
        finalUrl = stripTrackingParameters(finalUrl)
      }

      val reqBuilder = Request.Builder().url(finalUrl)

      // Forward incoming headers with overrides
      incomingHeaders.forEach { (k, v) ->
        val keyLower = k.lowercase(Locale.US)
        if (keyLower != "host" && keyLower != "content-length") {
          reqBuilder.addHeader(k, v)
        }
      }

      // User Agent Override
      val ua = when (_userAgentMode.value) {
        "Desktop Chrome (Spoofed)" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        "Mobile Safari" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
        else -> incomingHeaders["User-Agent"] ?: "NgrokAgent/1.0"
      }
      reqBuilder.header("User-Agent", ua)
      reqBuilder.header("X-Forwarded-For", clientIp)
      reqBuilder.header("X-Forwarded-Proto", if (finalUrl.startsWith("https")) "https" else "http")

      // Body if POST/PUT
      if (method in listOf("POST", "PUT", "PATCH") && !requestBody.isNullOrBlank()) {
        val contentType = (incomingHeaders["Content-Type"] ?: "application/json").toMediaTypeOrNull()
        reqBuilder.method(method, requestBody.toRequestBody(contentType))
      } else if (method == "GET") {
        reqBuilder.get()
      } else if (method == "DELETE") {
        reqBuilder.delete()
      }

      val response = httpClient.newCall(reqBuilder.build()).execute()
      val duration = System.currentTimeMillis() - start
      val responseBodyString = response.body?.string() ?: ""
      val bytesCount = responseBodyString.toByteArray().size.toLong()

      recordTraffic(
        ProxyTrafficLog(
          method = method,
          originalUrl = targetUrl,
          targetUrl = finalUrl,
          statusCode = response.code,
          durationMs = duration,
          clientIp = clientIp,
          bytesTransferred = bytesCount
        )
      )

      val responseHeaders = mutableMapOf<String, String>()
      response.headers.forEach { (name, value) ->
        responseHeaders[name] = value
      }

      if (_injectCors.value) {
        responseHeaders["Access-Control-Allow-Origin"] = "*"
        responseHeaders["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS, PATCH"
        responseHeaders["Access-Control-Allow-Headers"] = "*"
      }

      return ProxyForwardResponse(
        statusCode = response.code,
        headers = responseHeaders,
        body = responseBodyString,
        contentType = response.header("Content-Type") ?: "text/html; charset=utf-8"
      )

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - start
      recordTraffic(
        ProxyTrafficLog(
          method = method,
          originalUrl = targetUrl,
          targetUrl = targetUrl,
          statusCode = 502,
          durationMs = duration,
          clientIp = clientIp,
          bytesTransferred = 0
        )
      )
      return ProxyForwardResponse(
        statusCode = 502,
        headers = mapOf("Content-Type" to "application/json"),
        body = JSONObject().apply {
          put("error", "Bad Gateway / Reverse Proxy Failure")
          put("message", e.message)
          put("target_url", targetUrl)
        }.toString(),
        contentType = "application/json"
      )
    }
  }

  fun stripTrackingParameters(urlStr: String): String {
    try {
      val uri = URI(urlStr)
      val query = uri.query ?: return urlStr
      val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "si", "fbclid", "gclid", "ref", "igshid", "feature", "share_id", "source"
      )
      val filteredQuery = query.split("&")
        .filter { param ->
          val key = param.substringBefore("=").lowercase(Locale.US)
          key !in trackingParams
        }
        .joinToString("&")

      val newQuery = if (filteredQuery.isBlank()) null else filteredQuery
      return URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment).toString()
    } catch (_: Exception) {
      return urlStr
    }
  }

  fun getFullConfigJson(): JSONObject {
    val root = JSONObject()
    root.put("global_enabled", _isGlobalEnabled.value)
    root.put("strip_tracking", _stripTrackingGlobal.value)
    root.put("force_https", _forceHttps.value)
    root.put("inject_cors", _injectCors.value)
    root.put("user_agent_mode", _userAgentMode.value)

    val servicesArr = JSONArray()
    _services.value.forEach { s ->
      val sObj = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("category", s.category)
        put("source_domains", JSONArray(s.sourceDomain.split(",")))
        put("selected_instance", s.selectedInstance)
        put("is_enabled", s.isEnabled)
        put("strip_tracking", s.stripTracking)
        put("is_healthy", s.isHealthy)
        put("latency_ms", s.latencyMs ?: -1)
      }
      servicesArr.put(sObj)
    }
    root.put("services", servicesArr)

    val customArr = JSONArray()
    _customRoutes.value.forEach { r ->
      val rObj = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("path_prefix", r.pathPrefix)
        put("target_base_url", r.targetBaseUrl)
        put("is_enabled", r.isEnabled)
        put("inject_cors", r.injectCors)
      }
      customArr.put(rObj)
    }
    root.put("custom_routes", customArr)

    return root
  }
}

data class ProxyForwardResponse(
  val statusCode: Int,
  val headers: Map<String, String>,
  val body: String,
  val contentType: String
)
