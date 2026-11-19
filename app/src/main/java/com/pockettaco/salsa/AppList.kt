package com.pockettaco.salsa

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(val label: String, val pkg: String, val icon: Drawable)

/** Every app with a home-screen icon, alphabetical. Used by the normal layout. */
fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun openApp(context: Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
