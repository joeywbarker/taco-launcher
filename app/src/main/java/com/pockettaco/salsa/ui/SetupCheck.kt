package com.pockettaco.salsa.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettaco.salsa.*

/** One thing Taco Launcher needs, whether it's in place, and a button that fixes it. */
data class SetupItem(val ok: Boolean, val title: String, val about: String, val fixLabel: String, val fix: () -> Unit)

/** Everything a new user might be missing. Each is checked live, so fixing one ticks it off. */
fun setupItems(activity: MainActivity, library: Library, onPickFolder: () -> Unit): List<SetupItem> {
    val st = activity.settings
    fun open(intent: Intent) = runCatching { activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val appDetails = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))

    val home = activity.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName == activity.packageName

    val folders = listOfNotNull(st.romFolder) + SYSTEMS.mapNotNull { st.folderFor(it.id) }
    val folderOk = folders.any { canStillRead(activity, it) }

    val installed = installedEmulators(activity)
    val noEmulator = library.filter { (sys, games) -> games.isNotEmpty() && emulatorChoices(activity, sys, installed).isEmpty() }
        .keys.map { it.name }

    val items = mutableListOf(
        SetupItem(home, "Taco Launcher is your Home app",
            "Pressing Home brings you here, and games return here when you leave them.",
            "Choose Home app") { open(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) },
        SetupItem(isMapperOn(activity), "Hotkeys are on",
            "Exit game, save states and the other hotkeys need Taco Launcher's button helper. " +
                "In the list, tap Taco Launcher (it may be under \"Installed apps\") and turn it on.",
            "Open Accessibility") { open(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        SetupItem(folderOk, "Your games folder is picked",
            "The folder with your game files. Taco Launcher sorts them by console.",
            "Pick folder") { onPickFolder() },
        SetupItem(installed.isNotEmpty() && noEmulator.isEmpty(), "An emulator is installed",
            when {
                installed.isEmpty() -> "Games need an emulator app to play. RetroArch is free and plays almost every system here."
                noEmulator.isNotEmpty() -> "Nothing installed can play: ${noEmulator.joinToString()}. RetroArch plays most of them."
                else -> "Every system with games has an app that plays it."
            },
            "Get RetroArch") { openStorePage(activity, "com.retroarch") },
    )
    if (st.openOnConnect) {
        val bt = Build.VERSION.SDK_INT < 31 || hasBluetoothPermission(activity)
        items += SetupItem(bt && canAppearOnTop(activity), "Opens by itself when the Taco connects",
            "Android needs two permissions for this: \"Nearby devices\" and \"Appear on top\". " +
                "Or turn the feature off in Settings → Controller & buttons.",
            if (!canAppearOnTop(activity)) "Allow Appear on top" else "Allow Nearby devices") {
            if (!canAppearOnTop(activity)) open(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")))
            else open(appDetails)
        }
    }
    return items
}

/** The Setup check page: a tick or a fix button for each item. */
@Composable
fun SetupCheckSection(activity: MainActivity, onPickFolder: () -> Unit) {
    val library = remember { loadLibrary(activity) }
    var items by remember { mutableStateOf(setupItems(activity, library, onPickFolder)) }
    // Re-check every couple of seconds, so coming back from Android's settings ticks it off.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            items = setupItems(activity, library, onPickFolder)
        }
    }
    val missing = items.count { !it.ok }
    Text(if (missing == 0) "✓ Everything's set up." else "$missing thing${if (missing == 1) "" else "s"} to sort out. Each button takes you straight there.",
        color = if (missing == 0) Color(0xFF4CFF9A) else Color(0xFFFFD25A), fontWeight = FontWeight.Bold)
    items.forEach { item ->
        if (item.ok) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("✓ ${item.title}", color = Color.White)
                Text(item.about, color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            GameCard("⚠ ${item.title}: ${item.fixLabel}", item.about, SETTINGS_LOOK, tile = "!") { item.fix() }
        }
    }
}
