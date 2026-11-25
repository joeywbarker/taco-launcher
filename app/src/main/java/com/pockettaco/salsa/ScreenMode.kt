package com.pockettaco.salsa

import androidx.compose.runtime.mutableStateOf

/**
 * Taco mode or full screen ("Taco off"), right now.
 *
 * Taco mode keeps everything in the part of the screen above the Pocket Taco. Full screen is for
 * playing with the Taco unclipped (it still works as a Bluetooth controller) or with any other
 * controller: nothing is held back for the Taco, and the screen can turn sideways.
 */
object ScreenMode {
    /** True while the whole screen is used. Set by MainActivity.updateScreenMode(). */
    val full = mutableStateOf(false)

    /** How much of the screen the Taco hides right now (nothing in full screen). */
    fun covered(settings: Settings): Float = if (full.value) 0f else settings.coveredFraction
}
