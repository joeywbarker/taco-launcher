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

    /** True while a game is open in its window; the launcher shows plain black behind it. */
    val gameWindowOpen = androidx.compose.runtime.mutableStateOf(false)

    /** You came back to the launcher (Home button, or tapped it): show the menus again. */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (isTopResumedActivity) { gameWindowOpen.value = false; ButtonMapperState.gameOpen = false }
    }

    /** Name of the last controller button pressed, shown in Settings to help set things up. */
    val lastButton = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Set by Settings while it waits for you to press buttons for a new mapping. */
    var onKeyCapture: ((KeyEvent) -> Unit)? = null

    /** Set by the UI so the controller's B button can step back a screen. */
    var onBackRequest: (() -> Boolean)? = null

    /** Set by the home screen: Start opens the menu for the selected system, Select switches view. */
    var onStartButton: (() -> Boolean)? = null
    var onSelectButton: (() -> Boolean)? = null

    /** Set by a game list: the favorite button (Y unless changed in Settings) stars the selected game. */
    var onFavoriteButton: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = Settings(this)
        BoxArt.init(this)
        Custom.init(this)
        GameNames.load(settings)
        watcher = ControllerWatcher(this, settings)
        watcher.onAttached = { com.pockettaco.salsa.ui.SplashState.play() }
        // Coming back to the launcher, Android briefly shows a saved picture of the last
        // screen. Turn that off so attaching the Taco goes straight to the intro, not the menus.
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        hidePhoneStatusBar()
        setContent { LauncherRoot(this) }
        // Newer Android can send a Back button straight to the "back" system instead of to
        // dispatchKeyEvent. Catch it there too, so teaching B and going back always work.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val capture = onKeyCapture
                if (capture != null) {
                    capture(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                    capture(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                } else onBackRequest?.invoke()
            }
        })
        if (settings.openOnConnect && !hasBluetoothPermission(this) && android.os.Build.VERSION.SDK_INT >= 31) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
    }

    /**
     * When the launcher gets the screen back (after a game, Home, or a popup), make sure a
     * menu item has focus again. Without it, A has nothing to press.
     */
    /**
     * Hide the phone's own status bar (time, icons, battery) while Taco Launcher is open:
     * our top line already shows the clock and battery, so it was doubled. Swipe down from
     * the top edge to peek at it; it tucks away again by itself.
     */
    private fun hidePhoneStatusBar() {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hidePhoneStatusBar()
        ButtonMapperState.launcherInFront = hasFocus
        if (hasFocus) FocusKick.kick()
    }

    override fun onStart() {
        super.onStart()
        // Decide on the intro before the first frame, so the menus never flash first.
        com.pockettaco.salsa.ui.SplashState.playIfJustAttached()
        watcher.start()
    }
    override fun onStop() { watcher.stop(); super.onStop() }

    /** Pressing Home while already home: do nothing special, just stay. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        com.pockettaco.salsa.ui.SplashState.playIfJustAttached()
    }

    /**
     * Controller buttons -> launcher actions.
     * A = select (same as tapping), B = back. A launcher must never close itself
     * on Back, so at the top level B simply does nothing.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        onKeyCapture?.let { it(event); return true }
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastButton.value = "${buttonName(event.keyCode)} (code ${event.keyCode}, scan ${event.scanCode})"
        }
        // Whatever codes the controller sends, use the buttons taught in Settings.
        val button = settings.buttonFor(event)
        if (button != null && button == settings.favoriteButton && button != TacoButton.A && button != TacoButton.B) {
            onFavoriteButton?.let { hook ->
                if (event.action == KeyEvent.ACTION_UP) hook()
                return true
            }
        }
        val code = when (button) {
            TacoButton.B -> KeyEvent.KEYCODE_BUTTON_B
            TacoButton.A -> KeyEvent.KEYCODE_BUTTON_A
            TacoButton.START -> KeyEvent.KEYCODE_BUTTON_START
            TacoButton.SELECT -> KeyEvent.KEYCODE_BUTTON_SELECT
            TacoButton.HOME -> KeyEvent.KEYCODE_UNKNOWN   // Home does nothing in the menus
            else -> if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A || event.keyCode == KeyEvent.KEYCODE_BUTTON_B)
                KeyEvent.KEYCODE_UNKNOWN else event.keyCode
        }
        when (code) {
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_MENU -> {
                val hook = if (code == KeyEvent.KEYCODE_BUTTON_SELECT) onSelectButton else onStartButton
                if (hook != null) {
                    if (event.action == KeyEvent.ACTION_UP) hook()
                    return true
                }
            }
            KeyEvent.KEYCODE_UNKNOWN -> return true
            KeyEvent.KEYCODE_BUTTON_A -> {
                // Keep the controller's details on the event; just make it a "press".
                val handled = super.dispatchKeyEvent(KeyEvent(
                    event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER,
                    event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
                ))
                // Nothing focused, so nothing was pressed: put focus back on the menu.
                if (!handled && event.action == KeyEvent.ACTION_UP) FocusKick.kick()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_UP) onBackRequest?.invoke()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
