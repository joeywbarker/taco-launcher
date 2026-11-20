package com.pockettaco.salsa

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The icon styles the user can pick for the home screen. */
enum class AppIcon(val alias: String, val label: String, val preview: Int) {
    TACO(".IconTaco", "Taco", R.mipmap.ic_taco_fg),
    TACO_NAME(".IconTacoName", "Taco + name", R.mipmap.ic_taco_name_fg),
    CATAPULT(".IconCatapult", "Catapult", R.mipmap.ic_catapult_fg),
}

/** Which icon is switched on right now. */
fun currentAppIcon(context: Context): AppIcon {
    val pm = context.packageManager
    return AppIcon.entries.firstOrNull { icon ->
        val state = pm.getComponentEnabledSetting(ComponentName(context, context.packageName + icon.alias))
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == AppIcon.TACO)
    } ?: AppIcon.TACO
}

/**
 * Swaps the home-screen icon by switching on one app-drawer entry and off the others.
 * The phone's launcher can take a few seconds to show the change.
 */
fun setAppIcon(context: Context, chosen: AppIcon) {
    val pm = context.packageManager
    // Turn the new one on first so the app never has zero icons.
    val ordered = listOf(chosen) + AppIcon.entries.filter { it != chosen }
    for (icon in ordered) {
        pm.setComponentEnabledSetting(
            ComponentName(context, context.packageName + icon.alias),
            if (icon == chosen) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
