package com.apkmanager.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.apkmanager.app.ui.navigation.NavGraph
import com.apkmanager.app.ui.theme.ApkManagerTheme

/**
 * Main entry point for the APK Manager app.
 * Handles incoming intents to open or share APK files.
 */
class MainActivity : ComponentActivity() {

    private var incomingApkUris by mutableStateOf<List<Uri>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            ApkManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(
                        incomingApkUris = incomingApkUris,
                        onUrisConsumed = { incomingApkUris = null }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as ApkManagerApplication).adbRepository.onForeground()
    }

    override fun onStop() {
        super.onStop()
        (application as ApkManagerApplication).adbRepository.onBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uris = extractApkUris(intent)
        if (uris.isNotEmpty()) {
            incomingApkUris = uris
        }
    }

    private fun extractApkUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val uris = mutableListOf<Uri>()

        when (intent.action) {
            Intent.ACTION_VIEW,
            @Suppress("DEPRECATION") Intent.ACTION_INSTALL_PACKAGE -> {
                intent.data?.let { uris.add(it) }
                intent.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        val itemUri = clipData.getItemAt(i).uri
                        if (itemUri != null && !uris.contains(itemUri)) {
                            uris.add(itemUri)
                        }
                    }
                }
            }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val extraUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                extraUris?.forEach { uri ->
                    if (uri != null && !uris.contains(uri)) {
                        uris.add(uri)
                    }
                }
            }
        }
        return uris
    }
}
