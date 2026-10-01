package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.chat.ChatgptOAuthPkceManager
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleOAuthIntent(intent)

    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          MainScreen()
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleOAuthIntent(intent)
  }

  private fun handleOAuthIntent(intent: Intent?) {
    val data: Uri? = intent?.data
    if (data != null && data.scheme == "ngrokagent" && data.host == "oauth-callback") {
      val code = data.getQueryParameter("code")
      val state = data.getQueryParameter("state")
      if (!code.isNullOrBlank()) {
        lifecycleScope.launch {
          val success = ChatgptOAuthPkceManager.exchangeCodeForToken(this@MainActivity, code, state)
          if (success) {
            Toast.makeText(this@MainActivity, "ChatGPT OAuth 2.0 PKCE Connected Successfully!", Toast.LENGTH_LONG).show()
          } else {
            Toast.makeText(this@MainActivity, "OAuth PKCE token exchange returned error", Toast.LENGTH_SHORT).show()
          }
        }
      }
    }
  }
}
