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
    val fit = activity.settings.fitEmulators && activity.watcher.connected.value
    if (!fit) {
        activity.startActivity(intent)
        return
    }
    val screen = activity.windowManager.currentWindowMetrics.bounds
    val rect = gameWindowRect(activity.settings, screen.width(), screen.height(), system?.let { SCREEN_SHAPES[it.id] })
    val options = ActivityOptions.makeBasic().setLaunchBounds(rect)
    activity.gameWindowOpen.value = true
    // 5 = "freeform" window mode. The method is hidden, so call it by name.
    runCatching {
        ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            .invoke(options, 5)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    runCatching { activity.startActivity(intent, options.toBundle()) }
        .onFailure { activity.startActivity(intent) }
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
