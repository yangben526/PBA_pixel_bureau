package app.pixelbureau

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.pixelbureau.ui.BureauApp
import app.pixelbureau.ui.BureauViewModel
import app.pixelbureau.ui.PixelBureauTheme

class MainActivity : ComponentActivity() {
    private val model: BureauViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 draws behind the system bars. Without edge-to-edge, tablets
        // (taskbar) report empty navigation insets and a stuck IME visibility flag,
        // so the Office / Roster / Settings bar is either covered or never composed.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.parseColor("#161410")),
            navigationBarStyle = SystemBarStyle.dark(Color.parseColor("#1C1A17")),
        )
        setContent {
            PixelBureauTheme {
                BureauApp(model)
            }
        }
        applyDebugPreview(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDebugPreview(intent)
    }

    /**
     * Debuggable builds only: `adb shell am start -n app.pixelbureau/.MainActivity
     * --es pba_preview_items aquarium,arcade_cabinet` shows those shop items on the
     * map (in memory, no stars spent, nothing saved) for screenshots. Restarting the
     * app or sending an empty list clears it.
     */
    private fun applyDebugPreview(intent: Intent?) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val raw = intent?.getStringExtra(EXTRA_PREVIEW_ITEMS) ?: return
        model.previewShopItems(raw.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }

    private companion object {
        const val EXTRA_PREVIEW_ITEMS = "pba_preview_items"
    }
}
