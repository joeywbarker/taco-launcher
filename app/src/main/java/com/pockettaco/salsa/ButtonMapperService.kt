package com.pockettaco.salsa

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Runs in the background once the user switches it on in Accessibility settings.
 * Android lets accessibility services see key presses before any app, so this
 * works inside emulators too. It only looks at controller buttons.
 *
 * Rules:
 * - A button with its own mapping is kept from the game and acts when released
 *   (unless it was part of a combo).
 * - A combo (two buttons) fires the moment the second button goes down. The
 *   first button still reaches the game (press and release), which is normal for hotkeys;
 *   only the second button is kept from it.
 */
class ButtonMapperService : AccessibilityService() {

    private var singles = emptyMap<Int, MapAction>()
    private var combos = emptyList<Mapping>()
    private val held = mutableSetOf<Int>()
    private val swallowed = mutableSetOf<Int>()

    private fun reloadIfNeeded() {
        if (!ButtonMapperState.reload) return
        val all = loadMappings(this)
        singles = all.filter { it.keys.size == 1 }.associate { it.keys[0] to it.action }
        combos = all.filter { it.keys.size >= 2 }
        ButtonMapperState.reload = false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (ButtonMapperState.capturing) return false
        // In Taco Launcher's own menus, every button belongs to the menus (A select, B back...).
        // Only stay out of the way when both signals agree the launcher has the buttons: the
        // launcher's window has focus AND it was the last app to come to the front. Either one
        // alone can be out of date for a while (a game in a pop-up window doesn't always tell
        // Android it came to the front), which made hotkeys ignore presses until it caught up.
        val inLauncher = !ButtonMapperState.gameOpen &&
            ButtonMapperState.launcherInFront && (foreground == null || foreground == packageName)
        if (inLauncher) { held.clear(); swallowed.clear(); return false }
        val device = event.device
        val name = device?.name?.lowercase().orEmpty()
        // Some controllers report their buttons as a "keyboard", so also accept any real
        // (non-virtual) device sending gamepad button codes, and anything named like the Taco.
        val fromController = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            "gamesir" in name || "pocket" in name ||
            (device != null && !device.isVirtual && KeyEvent.isGamepadButton(event.keyCode))
        if (!fromController) return false
        reloadIfNeeded()
        val k = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            ButtonMapperState.lastKey = "${buttonName(k)} (code $k) from ${device?.name ?: "unknown"}"
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return k in swallowed || k in singles
                held += k
                val combo = combos.firstOrNull { k in it.keys && held.containsAll(it.keys) }
                if (combo != null && combo.action == MapAction.EMULATOR_MENU && retroArchInFront() &&
                    !ButtonMapperState.raGameIgnoresSelect) {
                    // RetroArch has its own Select + Start menu combo (Taco Launcher sets it up).
                    // Let both buttons reach it untouched; pressing Back on top of that would
                    // open and close the menu again, or do nothing.
                    return false
                }
                if (combo != null) {
                    // Keep the key that completed the combo from the game. The other button
                    // already reached the game when it went down, so let its release reach the
                    // game too. Swallowing that release left it "held down" in the game (a
                    // stuck Select in RetroArch blocks the menu's buttons and its own hotkeys).
                    swallowed += k
                    combo.keys.filter { it != k && it in singles }.forEach { swallowed += it }
                    perform(combo.action)
                    return true
                }
                return k in singles
            }
            KeyEvent.ACTION_UP -> {
                held -= k
                if (k in swallowed) { swallowed -= k; return true }
                singles[k]?.let { perform(it); return true }
            }
        }
        return false
    }

    private fun retroArchInFront(): Boolean {
        val pkg = (if (ButtonMapperState.gameOpen) ButtonMapperState.gamePkg else null) ?: foreground ?: return false
        return pkg.startsWith("com.retroarch")
    }

    private fun perform(action: MapAction) {
        // A short note so you can see the hotkey was caught.
        runCatching {
            android.widget.Toast.makeText(this, "🌮 ${action.label}", android.widget.Toast.LENGTH_SHORT).show()
        }
        if (action == MapAction.EXIT_GAME) ButtonMapperState.lastGame = null
        when (action) {
            MapAction.EXIT_GAME -> {
                // Home first tucks the game's pop-up window away; then bring Taco Launcher up
                // full screen. Keep trying for a moment: if another home screen (Samsung's)
                // grabs the front, we'd otherwise be left there.
                if (foreground != null && foreground != packageName) performGlobalAction(GLOBAL_ACTION_HOME)
                bringBack(attempt = 0)
            }
            // Android's Back opens RetroArch's menu (and backs out of it), with no RetroArch setup.
            MapAction.EMULATOR_MENU -> performGlobalAction(GLOBAL_ACTION_BACK)
            MapAction.TACO_LAUNCHER -> openLauncher()
            MapAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            MapAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            MapAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            MapAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            MapAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            MapAction.SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            MapAction.LOCK_SCREEN -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            MapAction.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE)
            MapAction.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER)
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun bringBack(attempt: Int) {
        handler.postDelayed({
            openLauncher()
            handler.postDelayed({
                if (foreground != packageName && attempt < 3) bringBack(attempt + 1)
            }, 300)
        }, if (attempt == 0) 300L else 200L)
    }

    private fun openLauncher() {
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        val options = android.app.ActivityOptions.makeBasic()
        // 1 = full screen window mode (hidden method, so call it by name).
        runCatching {
            android.app.ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(options, 1)
        }
        runCatching { startActivity(intent, options.toBundle()) }.onFailure { startActivity(intent) }
    }

    private fun volume(direction: Int) {
        getSystemService(AudioManager::class.java)
            ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    override fun onServiceConnected() { ButtonMapperState.reload = true; held.clear(); swallowed.clear() }
    /** The app whose window last came to the front (from Android's window events). */
    @Volatile private var foreground: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Keyboards, the status bar and system popups don't count as "the app in front".
        if (pkg == "com.android.systemui" || "inputmethod" in pkg || "honeyboard" in pkg) return
        foreground = pkg
    }
    override fun onInterrupt() {}
}

/** True if the user has switched the button mapper on in Accessibility settings. */
fun isMapperOn(context: android.content.Context): Boolean {
    val enabled = android.provider.Settings.Secure.getString(
        context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any {
        it.equals("${context.packageName}/${ButtonMapperService::class.java.name}", ignoreCase = true) ||
            it.equals("${context.packageName}/.ButtonMapperService", ignoreCase = true)
    }
}
