package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PhoneTelemetry(
  val deviceModel: String,
  val manufacturer: String,
  val androidVersion: String,
  val sdkInt: Int,
  val batteryPercent: Int,
  val isCharging: Boolean,
  val chargingType: String,
  val batteryTempCelsius: Float,
  val networkType: String,
  val localIpAddresses: List<String>,
  val storageFreeGb: Double,
  val storageTotalGb: Double,
  val ramFreeMb: Long,
  val ramTotalMb: Long,
  val uptimeFormatted: String
)

object PhoneDeviceManager {

  fun getTelemetry(context: Context): PhoneTelemetry {
    // 1. Battery Information
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100

    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val chargePlug = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
    val chargingType = when (chargePlug) {
      BatteryManager.BATTERY_PLUGGED_USB -> "USB"
      BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Charger"
      BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
      else -> if (isCharging) "Charging" else "On Battery"
    }

    val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val batteryTempCelsius = rawTemp / 10.0f

    // 2. Network Information
    val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val activeNetwork = connManager?.activeNetwork
    val caps = connManager?.getNetworkCapabilities(activeNetwork)
    val networkType = when {
      caps == null -> "Disconnected"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular Mobile Data"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
      else -> "Connected"
    }

    val ipList = mutableListOf<String>()
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces.hasMoreElements()) {
        val iface = interfaces.nextElement()
        val addrs = iface.inetAddresses
        while (addrs.hasMoreElements()) {
          val addr = addrs.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            ipList.add("${iface.name}: ${addr.hostAddress}")
          }
        }
      }
    } catch (_: Exception) {}
    if (ipList.isEmpty()) {
      ipList.add("lo: 127.0.0.1")
    }

    // 3. Storage Information
    val dataDir = Environment.getDataDirectory()
    val statFs = StatFs(dataDir.path)
    val blockSize = statFs.blockSizeLong
    val totalBlocks = statFs.blockCountLong
    val availableBlocks = statFs.availableBlocksLong
    val storageTotalGb = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
    val storageFreeGb = (availableBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)

    // 4. Memory / RAM
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actManager?.getMemoryInfo(memInfo)
    val ramTotalMb = memInfo.totalMem / (1024 * 1024)
    val ramFreeMb = memInfo.availMem / (1024 * 1024)

    // 5. Uptime
    val uptimeMillis = android.os.SystemClock.elapsedRealtime()
    val hours = uptimeMillis / (1000 * 60 * 60)
    val minutes = (uptimeMillis % (1000 * 60 * 60)) / (1000 * 60)
    val uptimeFormatted = "${hours}h ${minutes}m"

    return PhoneTelemetry(
      deviceModel = Build.MODEL,
      manufacturer = Build.MANUFACTURER,
      androidVersion = Build.VERSION.RELEASE,
      sdkInt = Build.VERSION.SDK_INT,
      batteryPercent = batteryPct,
      isCharging = isCharging,
      chargingType = chargingType,
      batteryTempCelsius = batteryTempCelsius,
      networkType = networkType,
      localIpAddresses = ipList,
      storageFreeGb = String.format(Locale.US, "%.1f", storageFreeGb).toDoubleOrNull() ?: storageFreeGb,
      storageTotalGb = String.format(Locale.US, "%.1f", storageTotalGb).toDoubleOrNull() ?: storageTotalGb,
      ramFreeMb = ramFreeMb,
      ramTotalMb = ramTotalMb,
      uptimeFormatted = uptimeFormatted
    )
  }

  fun getTelemetryJson(context: Context): JSONObject {
    val t = getTelemetry(context)
    return JSONObject().apply {
      put("device_model", t.deviceModel)
      put("manufacturer", t.manufacturer)
      put("android_version", t.androidVersion)
      put("sdk_int", t.sdkInt)
      put("battery", JSONObject().apply {
        put("percent", t.batteryPercent)
        put("is_charging", t.isCharging)
        put("charging_type", t.chargingType)
        put("temperature_c", t.batteryTempCelsius)
      })
      put("network", JSONObject().apply {
        put("type", t.networkType)
        put("ip_addresses", JSONArray(t.localIpAddresses))
      })
      put("storage", JSONObject().apply {
        put("free_gb", t.storageFreeGb)
        put("total_gb", t.storageTotalGb)
      })
      put("ram", JSONObject().apply {
        put("free_mb", t.ramFreeMb)
        put("total_mb", t.ramTotalMb)
      })
      put("uptime", t.uptimeFormatted)
      put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
    }
  }
}
