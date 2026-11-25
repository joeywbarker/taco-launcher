package com.pockettaco.salsa.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.pockettaco.salsa.MainActivity
import com.pockettaco.salsa.TouchControls

/**
 * Settings > Touch controls. Everything works by touch out of the box; these let you turn
 * parts of it off. None of them can lock you out: with no controller connected touch always
 * works, and five quick taps turn it back on.
 */
@Composable
fun TouchControlsSection(activity: MainActivity) {
    val st = activity.settings
    val t = TouchControls
    val controller by activity.watcher.deviceName.collectAsState()

    Text("Every menu in Taco Launcher works by touch as well as with a controller. Tap to open or play, " +
        "hold for a menu, and tap the button hints along the bottom. Turn off anything you don't want here.",
        color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)

    SettingHeader("Touch on or off")
    SettingSwitch("Touch controls",
        "Off: taps and swipes on Taco Launcher's menus are ignored while a controller is connected, so a stray " +
            "finger never starts a game. With no controller connected, touch always works, so you can't get " +
            "locked out. Tapping 5 times quickly also turns it back on.",
        t.enabled.value) { t.enabled.value = it; st.touchEnabled = it }
    if (!t.enabled.value) Text(
        if (controller != null) "Touch is off now. Use your controller here, or tap 5 times quickly to turn it back on."
        else "No controller is connected, so touch keeps working until one is.",
        color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall)

    SettingHeader("Button hints", "The little button labels along the bottom of the home screen and game lists.")
    SettingSwitch("Tap the button hints",
        "Tapping a hint, such as B Back or Start menu, does the same as pressing that button on the controller.",
        t.tapHints.value) { t.tapHints.value = it; st.touchTapHints = it }
    SettingChoice("What the hints show", null,
        listOf(
            Triple("auto", "Follow me", "Controller buttons while you use the controller, touch tips (Tap, Hold) after you touch the screen."),
            Triple("buttons", "Buttons", "Always show the controller's buttons (A, B, Start...)."),
            Triple("touch", "Touch", "Always show touch tips, for playing without a controller."),
            Triple("hidden", "Hidden", "No hint bar, so the games get a little more room."),
        ),
        t.hintStyle.value) { t.hintStyle.value = it; st.touchHintStyle = it }
    SettingSwitch("Bigger hint buttons",
        "Makes the hints larger and easier to hit with a thumb. They take a little more room at the bottom.",
        t.bigHints.value) { t.bigHints.value = it; st.touchBigHints = it }

    SettingHeader("Gestures")
    SettingSwitch("Swipe between consoles",
        "On the big one-console view, swipe left or right to go to the next console. Off: tap the dots above " +
            "the picture instead.",
        t.swipe.value) { t.swipe.value = it; st.touchSwipe = it }
    SettingSwitch("Hold for a menu",
        "Press and hold a console or a game to open its menu (favorite, emulator, rename and more). " +
            "Off: open menus with Start or the Menu hint. Holding A on the controller still works.",
        t.hold.value) { t.hold.value = it; st.touchHold = it }
    SettingSwitch("Hold and drag to move consoles",
        "On the icon views, hold a console and drag it to a new spot. Off: move consoles from their menu " +
            "(Move) instead.",
        t.drag.value) { t.drag.value = it; st.touchDrag = it }

    SettingHeader("Menus")
    SettingSwitch("Close and Cancel buttons in menus",
        "Adds a Close or Cancel item to menus that would otherwise only close with the B button. " +
            "The phone's own back gesture also works everywhere.",
        t.closeItems.value) { t.closeItems.value = it; st.touchCloseItems = it }
}
