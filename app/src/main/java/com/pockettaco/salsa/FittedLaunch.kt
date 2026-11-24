package com.pockettaco.salsa

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect

/**
 * Starts another app (an emulator) so it only fills the top part of the screen.
 *
 * Android normally opens every app full screen and a home screen app can't
 * change that. The exception is "pop-up"/freeform windows, which Samsung
 * supports: we ask Android to open the app as a window with exact edges.
 * If the phone refuses, the app just opens full screen as before.
 */
fun startFitted(activity: MainActivity, intent: Intent, system: GameSystem? = null) {
    // A game: make sure the emulator starts fresh. "Leave game" keeps the emulator running
    // in the background, and RetroArch shows a black screen if a second game window opens
    // while the old one is still alive. Android only lets us close it once it's out of sight.
    if (system != null) closeLeftoverEmulator(activity, intent)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // A game: RetroArch shows a black screen when a game starts while an old game screen of
    // its is still alive (and Android 14+ no longer lets us close other apps). So do what other
    // frontends (ES-DE, Daijishou) do: replace its old screen, and have Android close the game
    // screen once you leave it ("no history"), so it shuts down properly and saves as it goes.
    if (system != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (intent.component?.className?.contains("RetroActivity") == true) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
    }
    if (system != null) {
        ButtonMapperState.gameOpen = true
        ButtonMapperState.gamePkg = intent.component?.packageName ?: intent.`package`
    }
    val retroArch = intent.component?.className?.contains("RetroActivity") == true
    if (system != null) {
        ButtonMapperState.retroArchCommands = false
        if (retroArch && activity.settings.retroArchFullScreen) {
            val ok = RetroArchPlacement.apply(activity, intent, system)
            if (!ok) android.widget.Toast.makeText(activity,
                "🌮 Couldn't set up RetroArch (${RetroArchPlacement.lastError}). The picture and hotkeys use RetroArch's own settings.",
                android.widget.Toast.LENGTH_LONG).show()
            else if (activity.settings.raCommands) checkRetroArchLater(activity)
        }
    }
    val fit = activity.settings.fitEmulators && activity.watcher.connected.value &&
        !(retroArch && activity.settings.retroArchFullScreen)
    if (!fit) {
        activity.startActivity(intent)
        return
    }
    val screen = activity.windowManager.currentWindowMetrics.bounds
    val rect = gameWindowRect(activity.settings, screen.width(), screen.height(), system?.let { SCREEN_SHAPES[it.id] })
    val options = ActivityOptions.makeBasic().setLaunchBounds(rect)
    // 5 = "freeform" window mode. The method is hidden, so call it by name.
    runCatching {
        ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            .invoke(options, 5)
    }
    runCatching { activity.startActivity(intent, options.toBundle()) }
        .onFailure { activity.startActivity(intent) }
    activity.gameWindowOpen.value = true
}

/**
 * A few seconds after a RetroArch game starts, check that RetroArch answers. If it does, the
 * hotkeys talk to it directly; if not, say so and leave RetroArch's own buttons in charge.
 */
private fun checkRetroArchLater(activity: MainActivity) {
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    Thread {
        var ok = false
        for (i in 0 until 6) {
            Thread.sleep(if (i == 0) 3000 else 1500)
            if (RetroArchPlacement.isListening()) { ok = true; break }
        }
        ButtonMapperState.retroArchCommands = ok
        val before = activity.settings.raCommandsWork
        activity.settings.raCommandsWork = ok
        main.post {
            val msg = when {
                ok && !before -> "🌮 RetroArch connected: Select + Start opens its menu, Home closes it."
                !ok -> "🌮 RetroArch isn't answering Taco Launcher, so its own buttons stay in charge. Tell Claude."
                else -> null
            }
            if (msg != null) android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }.start()
}

/** Opens an installed app by package name, fitted when Taco mode is on. */
fun openAppFitted(activity: MainActivity, pkg: String) {
    activity.packageManager.getLaunchIntentForPackage(pkg)?.let { startFitted(activity, it) }
}


/** Width ÷ height of each console's picture, used to size the window so the game fills it. */
val SCREEN_SHAPES = mapOf(
    "gba" to 3f / 2f, "gb" to 10f / 9f, "gbc" to 10f / 9f, "gg" to 10f / 9f, "ngp" to 20f / 19f,
    "nes" to 4f / 3f, "snes" to 4f / 3f, "genesis" to 4f / 3f, "sms" to 4f / 3f, "pce" to 4f / 3f,
    "n64" to 4f / 3f, "psx" to 4f / 3f, "psp" to 16f / 9f,
    "nds" to 2f / 3f, // two screens stacked
    "dreamcast" to 4f / 3f, "saturn" to 4f / 3f, "segacd" to 4f / 3f, "sega32x" to 4f / 3f,
    "ps2" to 4f / 3f, "gc" to 4f / 3f, "wii" to 16f / 9f, "3ds" to 5f / 6f, "fds" to 4f / 3f,
    "ws" to 14f / 9f, "wsc" to 14f / 9f, "lynx" to 160f / 102f, "neogeo" to 4f / 3f, "arcade" to 4f / 3f,
)

/**
 * Where the game window goes. It always stays in the space above the Taco;
 * [Settings.windowPosition] slides it from the top of that space (0) down to
 * just above the controller (1). Leftover space shows black.
 */
fun gameWindowRect(settings: Settings, width: Int, height: Int, shape: Float?): Rect =
    gameWindowRect(settings.coveredFraction, settings.autoFitGame, settings.windowHeight,
        settings.windowPosition, width, height, shape)

fun gameWindowRect(
    covered: Float, autoFit: Boolean, heightShare: Float, position: Float,
    width: Int, height: Int, shape: Float?,
): Rect {
    val available = height * (1f - covered)
    val wanted = if (autoFit && shape != null) width / shape else height * heightShare
    val h = wanted.coerceAtMost(available)
    val top = ((available - h) * position).toInt()
    return Rect(0, top, width, top + h.toInt())
}

private fun closeLeftoverEmulator(activity: MainActivity, intent: Intent) {
    val pkg = intent.component?.packageName ?: intent.`package` ?: return
    if (pkg == activity.packageName) return
    val am = activity.getSystemService(android.app.ActivityManager::class.java) ?: return
    // Android doesn't tell us whether another app is running, so always ask; it's a no-op if not.
    runCatching { am.killBackgroundProcesses(pkg) }
    // Give Android a moment to finish closing it before the new game window opens.
    Thread.sleep(400)
}
