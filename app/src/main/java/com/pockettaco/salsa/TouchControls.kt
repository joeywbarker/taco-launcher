package com.pockettaco.salsa

import androidx.compose.runtime.mutableStateOf

/**
 * Touch controls for the menus, for playing without a Pocket Taco (or alongside one).
 *
 * Every switch here is safe: turning touch off only applies while a controller is connected,
 * so with no controller touch always works and you can never be locked out. Five quick taps
 * anywhere also turn touch back on.
 */
object TouchControls {
    /** Master switch. Off: taps and swipes are ignored while a controller is connected. */
    val enabled = mutableStateOf(true)
    /** Tapping a button hint along the bottom works like pressing that button. */
    val tapHints = mutableStateOf(true)
    /** What the hint bar shows: "auto", "buttons", "touch" or "hidden". */
    val hintStyle = mutableStateOf("auto")
    /** Bigger hint buttons, easier to hit with a thumb. */
    val bigHints = mutableStateOf(false)
    /** Swipe left and right on the big console picture. */
    val swipe = mutableStateOf(true)
    /** Hold a console or game (by touch) for its menu. */
    val hold = mutableStateOf(true)
    /** Hold and drag a console on the icon views to move it. */
    val drag = mutableStateOf(true)
    /** Extra Close / Cancel items in menus that otherwise only close with B. */
    val closeItems = mutableStateOf(true)

    /** True since the last time the screen was touched; false once a controller button is pressed. */
    val usingTouch = mutableStateOf(false)

    /** Set by the home screen while one of its menus is open (so the hint bar offers Close). */
    val homePanelOpen = mutableStateOf(false)

    fun load(s: Settings) {
        enabled.value = s.touchEnabled
        tapHints.value = s.touchTapHints
        hintStyle.value = s.touchHintStyle
        bigHints.value = s.touchBigHints
        swipe.value = s.touchSwipe
        hold.value = s.touchHold
        drag.value = s.touchDrag
        closeItems.value = s.touchCloseItems
    }

    /**
     * Whether a hold that just happened should open a menu. Holding A on the controller always
     * does; a finger hold only when "Hold for a menu" is on.
     */
    fun allowHold(): Boolean = hold.value || !fingerDown

    /** A finger is on the screen right now (so a hold is a touch hold, not the A button). */
    var fingerDown = false

    /** Wraps a hold action so a finger hold is ignored when "Hold for a menu" is off. */
    fun gateHold(action: (() -> Unit)?): (() -> Unit)? = action?.let { f -> { if (allowHold()) f() } }

    /** Whether the hint bar shows touch tips (true) or controller buttons (false) right now. */
    fun touchTips(): Boolean = when (hintStyle.value) {
        "touch" -> true
        "buttons" -> false
        else -> usingTouch.value
    }
}
