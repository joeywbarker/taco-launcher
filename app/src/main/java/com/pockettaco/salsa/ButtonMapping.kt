package com.pockettaco.salsa

import android.content.Context
import android.view.KeyEvent

/** What a button (or combo) can do. */
enum class MapAction(val label: String) {
    EXIT_GAME("Leave game, back to menu"),
    EMULATOR_MENU("Emulator menu (RetroArch menu)"),
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

/**
 * Defaults: Select+Start = the emulator's menu (RetroArch's menu, the game keeps going),
 * Home = leave the game and come back to Taco Launcher.
 */
@Suppress("UNUSED_PARAMETER")
fun defaultMappings(context: Context): List<Mapping> = listOf(
    Mapping(listOf(KeyEvent.KEYCODE_BUTTON_MODE), MapAction.EXIT_GAME),
    Mapping(SELECT_START, MapAction.EMULATOR_MENU),
)

private val SELECT_START = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START).sorted()

/** Bump when the defaults change in a way saved lists should pick up. */
private const val MAPPINGS_VERSION = 5

fun loadMappings(context: Context): List<Mapping> {
    val prefs = context.getSharedPreferences("taco", Context.MODE_PRIVATE)
    val raw = prefs.getString("mappings", null) ?: return defaultMappings(context)
    var list = parseMappings(raw)
    val version = prefs.getInt("mappingsVersion", 1)
    if (version >= MAPPINGS_VERSION) return list
    if (version < 3) {
        // Old default Home -> open launcher didn't leave the game: upgrade it.
        list = list.map { m ->
            val isOldHome = m.keys == listOf(KeyEvent.KEYCODE_BUTTON_MODE) && m.action == MapAction.TACO_LAUNCHER
            if (isOldHome) m.copy(action = MapAction.EXIT_GAME) else m
        }
    }
    // Version 5: Select+Start opens the emulator's menu instead of leaving the game, and the
    // short-lived Select+B menu combo goes (on this Taco, B can send the same signal as A).
    if (version == 4) {
        list = list.filterNot { it.action == MapAction.EMULATOR_MENU && it.keys.size == 2 && KeyEvent.KEYCODE_BUTTON_SELECT in it.keys && it.keys != SELECT_START }
    }
    list = list.map { if (it.keys == SELECT_START && it.action == MapAction.EXIT_GAME) it.copy(action = MapAction.EMULATOR_MENU) else it }
    defaultMappings(context).forEach { d ->
        if (list.none { it.keys == d.keys } && list.none { it.action == d.action }) list = list + d
    }
    saveMappings(context, list)
    return list
}

private fun parseMappings(raw: String): List<Mapping> {
    return raw.lines().mapNotNull { line ->
        val (keys, action) = line.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val codes = keys.split('+').mapNotNull { it.toIntOrNull() }.sorted()
        val act = runCatching { MapAction.valueOf(action) }.getOrNull() ?: return@mapNotNull null
        if (codes.isEmpty()) null else Mapping(codes, act)
    }
}

fun saveMappings(context: Context, list: List<Mapping>) {
    context.getSharedPreferences("taco", Context.MODE_PRIVATE).edit()
        .putInt("mappingsVersion", MAPPINGS_VERSION)
        .putString("mappings", list.joinToString("\n") { m -> m.keys.sorted().joinToString("+") + "=" + m.action.name })
        .apply()
    ButtonMapperState.reload = true
}

/** Shared between the screen and the background service (same app process). */
object ButtonMapperState {
    /** True while Settings is listening for a new button press, so the service stays out of the way. */
    @Volatile var capturing = false
    @Volatile var reload = true
    /** The last controller button the background service saw, shown in Settings as a check. */
    @Volatile var lastKey: String? = null
    /** True while Taco Launcher's own menus have the screen; the service then stays out of the way. */
    @Volatile var launcherInFront = false
    /**
     * True from the moment we start a game until the launcher is really back on top. While a
     * game is open the hotkeys always work, even if Android is slow to say the game came to the front.
     */
    @Volatile var gameOpen = false
    /** The app the last game was started in. */
    @Volatile var gamePkg: String? = null
}

/** Asks the open menu screen to put focus back on its first (or last used) item. */
object FocusKick {
    val count = androidx.compose.runtime.mutableIntStateOf(0)
    fun kick() { count.intValue++ }
}

/** Button name as printed on the Taco, using the Select (A) and Back (B) buttons taught in Settings. */
fun tacoButtonName(code: Int, settings: Settings): String =
    TacoButton.entries.firstOrNull { settings.signalFor(it).first == code }?.label ?: when (code) {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B -> buttonName(code) + "?"
        else -> buttonName(code)
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
