package com.pockettaco.salsa

import android.content.Context
import android.view.KeyEvent

/** What a button (or combo) can do. */
enum class MapAction(val label: String) {
    TACO_LAUNCHER("Open Taco Launcher"),
    HOME("Home"),
    BACK("Back"),
    RECENTS("Recent apps"),
    NOTIFICATIONS("Notifications"),
    QUICK_SETTINGS("Quick settings"),
    SCREENSHOT("Screenshot"),
    LOCK_SCREEN("Lock screen"),
    VOLUME_UP("Volume up"),
    VOLUME_DOWN("Volume down"),
}

/** One rule: press these buttons (one, or two together) to do [action]. */
data class Mapping(val keys: List<Int>, val action: MapAction) {
    val label: String get() = keys.joinToString(" + ") { buttonName(it) }
}

val DEFAULT_MAPPINGS = listOf(
    // The Taco's Home button does nothing in emulators by default: make it go to our launcher.
    Mapping(listOf(KeyEvent.KEYCODE_BUTTON_MODE), MapAction.TACO_LAUNCHER),
    Mapping(listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START), MapAction.BACK),
)

fun loadMappings(context: Context): List<Mapping> {
    val raw = context.getSharedPreferences("taco", Context.MODE_PRIVATE).getString("mappings", null)
        ?: return DEFAULT_MAPPINGS
    return raw.lines().mapNotNull { line ->
        val (keys, action) = line.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val codes = keys.split('+').mapNotNull { it.toIntOrNull() }.sorted()
        val act = runCatching { MapAction.valueOf(action) }.getOrNull() ?: return@mapNotNull null
        if (codes.isEmpty()) null else Mapping(codes, act)
    }
}

fun saveMappings(context: Context, list: List<Mapping>) {
    context.getSharedPreferences("taco", Context.MODE_PRIVATE).edit()
        .putString("mappings", list.joinToString("\n") { m -> m.keys.sorted().joinToString("+") + "=" + m.action.name })
        .apply()
    ButtonMapperState.reload = true
}

/** Shared between the screen and the background service (same app process). */
object ButtonMapperState {
    /** True while Settings is listening for a new button press, so the service stays out of the way. */
    @Volatile var capturing = false
    @Volatile var reload = true
}

fun buttonName(code: Int): String = when (code) {
    KeyEvent.KEYCODE_BUTTON_A -> "A"
    KeyEvent.KEYCODE_BUTTON_B -> "B"
    KeyEvent.KEYCODE_BUTTON_X -> "X"
    KeyEvent.KEYCODE_BUTTON_Y -> "Y"
    KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
    KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
    KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
    KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
    KeyEvent.KEYCODE_BUTTON_START -> "Start"
    KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
    KeyEvent.KEYCODE_BUTTON_MODE -> "Home"
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
    KeyEvent.KEYCODE_DPAD_UP -> "Up"
    KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
    KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").removePrefix("BUTTON_")
        .lowercase().replaceFirstChar { it.uppercase() }
}
