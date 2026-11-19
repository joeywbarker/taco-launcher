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
fun startFitted(activity: MainActivity, intent: Intent) {
    val fit = activity.settings.fitEmulators && activity.watcher.connected.value
    if (!fit) {
        activity.startActivity(intent)
        return
    }
    val screen = activity.windowManager.currentWindowMetrics.bounds
    val bottom = (screen.height() * (1f - activity.settings.coveredFraction)).toInt()
    val options = ActivityOptions.makeBasic().setLaunchBounds(Rect(0, 0, screen.width(), bottom))
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

