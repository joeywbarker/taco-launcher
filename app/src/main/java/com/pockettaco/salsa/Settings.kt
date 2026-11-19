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

    /** The ROMs folder the user picked (a "tree" Uri from the system picker). */
    var romFolder: Uri?
        get() = prefs.getString("roms", null)?.let(Uri::parse)
        set(v) = prefs.edit().putString("roms", v?.toString()).apply()

    /** Which emulator app the user chose for each system, e.g. "gba" -> package. */
    fun emulatorFor(system: String): String? = prefs.getString("emu_$system", null)
    fun setEmulatorFor(system: String, pkg: String) =
        prefs.edit().putString("emu_$system", pkg).apply()
}
