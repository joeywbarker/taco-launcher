package com.pockettaco.salsa

import android.content.Context
import android.net.Uri

/** Small saved preferences. Plain SharedPreferences keeps things simple. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("taco", Context.MODE_PRIVATE)

    /** How much of the screen bottom the Taco covers, 0.0 to 0.6. Default 40%. */
    var coveredFraction: Float
        get() = prefs.getFloat("covered", 0.40f)
        set(v) = prefs.edit().putFloat("covered", v.coerceIn(0f, 0.6f)).apply()

    /** Treat any gamepad as a Taco (handy if the name ever changes). */
    var anyGamepadCounts: Boolean
        get() = prefs.getBoolean("anyPad", true)
        set(v) = prefs.edit().putBoolean("anyPad", v).apply()

    /** Always use the Taco layout, even with nothing connected. On by default (the user asked). */
    var forceTacoMode: Boolean
        get() = prefs.getBoolean("force", true)
        set(v) = prefs.edit().putBoolean("force", v).apply()

    /** Open emulators in a window that fits above the Taco (Samsung pop-up window). */
    var fitEmulators: Boolean
        get() = prefs.getBoolean("fit", true)
        set(v) = prefs.edit().putBoolean("fit", v).apply()

    /** Jump to the launcher the moment the Pocket Taco connects. */
    var openOnConnect: Boolean
        get() = prefs.getBoolean("openOnConnect", true)
        set(v) = prefs.edit().putBoolean("openOnConnect", v).apply()

    /** Size the game window to each console's screen shape (so the game fills it, no gaps). */
    var autoFitGame: Boolean
        get() = prefs.getBoolean("autoFitGame", true)
        set(v) = prefs.edit().putBoolean("autoFitGame", v).apply()

    /** Game window height as a share of the whole screen, when not auto-sized. */
    var windowHeight: Float
        get() = prefs.getFloat("windowHeight", 0.6f)
        set(v) = prefs.edit().putFloat("windowHeight", v.coerceIn(0.2f, 1f)).apply()

    /** Where the game window sits in the space above the Taco: 0 = top, 1 = just above the Taco. */
    var windowPosition: Float
        get() = prefs.getFloat("windowPosition", 1f)
        set(v) = prefs.edit().putFloat("windowPosition", v.coerceIn(0f, 1f)).apply()

    /** Pocket Taco uses Game Boy style labels: swap so the button marked B goes back. */
    var swapAB: Boolean
        get() = prefs.getBoolean("swapAB", true)
        set(v) = prefs.edit().putBoolean("swapAB", v).apply()

    /** The controller button that selects (A) in the menus. Taught in Settings. */
    var selectKey: Int
        get() = prefs.getInt("selectKey",
            if (swapAB) android.view.KeyEvent.KEYCODE_BUTTON_B else android.view.KeyEvent.KEYCODE_BUTTON_A)
        set(v) = prefs.edit().putInt("selectKey", v).apply()

    /** The controller button that goes back (B) in the menus. Taught in Settings. */
    var backKey: Int
        get() = prefs.getInt("backKey",
            if (swapAB) android.view.KeyEvent.KEYCODE_BUTTON_A else android.view.KeyEvent.KEYCODE_BUTTON_B)
        set(v) = prefs.edit().putInt("backKey", v).apply()

    /** Extra detail for the taught buttons: the controller's own number for the button (0 = any). */
    var selectScan: Int
        get() = prefs.getInt("selectScan", 0)
        set(v) = prefs.edit().putInt("selectScan", v).apply()
    var backScan: Int
        get() = prefs.getInt("backScan", 0)
        set(v) = prefs.edit().putInt("backScan", v).apply()

    fun isSelect(e: android.view.KeyEvent) = e.keyCode == selectKey && (selectScan == 0 || e.scanCode == selectScan)
    fun isBack(e: android.view.KeyEvent) = e.keyCode == backKey && (backScan == 0 || e.scanCode == backScan)

    /** The person said the Taco is set up in RetroArch, so stop asking before games. */
    var retroArchPadReady: Boolean
        get() = prefs.getBoolean("retroArchPadReady", false)
        set(v) = prefs.edit().putBoolean("retroArchPadReady", v).apply()

    /** The ROMs folder the user picked (a "tree" Uri from the system picker). */
    var romFolder: Uri?
        get() = prefs.getString("roms", null)?.let(Uri::parse)
        set(v) = prefs.edit().putString("roms", v?.toString()).apply()

    /** Which emulator app the user chose for each system, e.g. "gba" -> package. */
    fun emulatorFor(system: String): String? = prefs.getString("emu_$system", null)
    fun setEmulatorFor(system: String, pkg: String) =
        prefs.edit().putString("emu_$system", pkg).apply()
}
