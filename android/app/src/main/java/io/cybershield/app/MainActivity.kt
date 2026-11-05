package io.cybershield.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.cybershield.app.ui.CyberShieldApp
import io.cybershield.app.ui.Incoming
import io.cybershield.app.ui.Page

/**
 * Single entry point. It is exported because it is the launcher activity and a Sharesheet /
 * text-selection target. All incoming data is treated as untrusted: text is length-limited,
 * only image MIME types are accepted for streams, and nothing is opened or sent automatically.
 */
class MainActivity : AppCompatActivity() {
    private val incoming = mutableStateOf<Incoming?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The launch screen shows the brand mark; remove it the moment Compose draws, because the
        // intro starts from the same mark in the same place, so the hand-off is invisible.
        installSplashScreen().setOnExitAnimationListener { it.remove() }
        // The app is always light (white into blue), so system bars need dark icons regardless of system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) accept(intent)
        setContent { CyberShieldApp(incoming = incoming.value, onIncomingConsumed = { incoming.value = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }

    private fun accept(intent: Intent?) {
        intent ?: return
        incoming.value = when (intent.action) {
            Intent.ACTION_SEND -> when {
                intent.type?.startsWith("image/") == true -> streamExtra(intent)?.let { Incoming.SharedImage(it) }
                intent.type == "text/plain" -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(MAX_TEXT)
                    ?.takeIf { it.isNotBlank() }?.let { Incoming.SharedText(it) }
                else -> null
            }
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.take(MAX_TEXT)
                ?.takeIf { it.isNotBlank() }?.let { Incoming.SharedText(it) }
            ACTION_SCAN_QR -> Incoming.OpenPage(Page.QR)
            ACTION_CHECK_LINK -> Incoming.OpenPage(Page.LINK)
            ACTION_CHECK_MESSAGE -> Incoming.OpenPage(Page.MESSAGE)
            else -> null
        } ?: incoming.value
    }

    private fun streamExtra(intent: Intent): Uri? {
        // The Sharesheet mirrors EXTRA_STREAM into ClipData, which is what carries the read grant.
        val uri = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        // Only content:// URIs granted by the sharing app; never file:// paths into our own storage.
        return uri?.takeIf { it.scheme == "content" }
    }

    companion object {
        const val MAX_TEXT = 10_000
        const val ACTION_SCAN_QR = "io.cybershield.app.action.SCAN_QR"
        const val ACTION_CHECK_LINK = "io.cybershield.app.action.CHECK_LINK"
        const val ACTION_CHECK_MESSAGE = "io.cybershield.app.action.CHECK_MESSAGE"
    }
}
