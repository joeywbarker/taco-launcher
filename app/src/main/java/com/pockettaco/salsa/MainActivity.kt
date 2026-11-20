package com.pockettaco.salsa

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pockettaco.salsa.ui.LauncherRoot

class MainActivity : ComponentActivity() {

    lateinit var settings: Settings
    lateinit var watcher: ControllerWatcher

    /** Set by Settings while it waits for you to press buttons for a new mapping. */
    var onKeyCapture: ((KeyEvent) -> Unit)? = null

    /** Set by the UI so the controller's B button can step back a screen. */
    var onBackRequest: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = Settings(this)
        watcher = ControllerWatcher(this, settings)
        setContent { LauncherRoot(this) }
        if (settings.openOnConnect && !hasBluetoothPermission(this) && android.os.Build.VERSION.SDK_INT >= 31) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
    }

    override fun onStart() { super.onStart(); watcher.start() }
    override fun onStop() { watcher.stop(); super.onStop() }

    /** Pressing Home while already home: do nothing special, just stay. */
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent) }

    /**
     * Controller buttons -> launcher actions.
     * A = select (same as tapping), B = back. A launcher must never close itself
     * on Back, so at the top level B simply does nothing.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        onKeyCapture?.let { it(event); return true }
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> return super.dispatchKeyEvent(
                KeyEvent(event.action, KeyEvent.KEYCODE_DPAD_CENTER)
            )
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_UP) onBackRequest?.invoke()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
