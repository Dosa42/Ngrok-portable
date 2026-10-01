package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

object LemurBrowserHelper {
  const val LEMUR_PACKAGE = "com.lemurbrowser.exts"
  const val LEMUR_BETA_PACKAGE = "com.lemurbrowser.exts.beta"

  fun isLemurInstalled(context: Context): Boolean {
    val pm = context.packageManager
    return try {
      pm.getPackageInfo(LEMUR_PACKAGE, 0)
      true
    } catch (_: Exception) {
      try {
        pm.getPackageInfo(LEMUR_BETA_PACKAGE, 0)
        true
      } catch (_: Exception) {
        false
      }
    }
  }

  fun openUrlInLemur(context: Context, url: String) {
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

      if (isLemurInstalled(context)) {
        intent.setPackage(LEMUR_PACKAGE)
        try {
          context.startActivity(intent)
          return
        } catch (_: Exception) {
          intent.setPackage(LEMUR_BETA_PACKAGE)
          try {
            context.startActivity(intent)
            return
          } catch (_: Exception) {
            intent.setPackage(null)
          }
        }
      }

      // Fallback: Launch default browser & notify
      intent.setPackage(null)
      context.startActivity(intent)
      Toast.makeText(
        context,
        "Opening link (Lemur Browser recommended for Tampermonkey support)",
        Toast.LENGTH_SHORT
      ).show()

    } catch (e: Exception) {
      Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun openLemurPlayStore(context: Context) {
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$LEMUR_PACKAGE")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$LEMUR_PACKAGE")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(webIntent)
    }
  }
}
