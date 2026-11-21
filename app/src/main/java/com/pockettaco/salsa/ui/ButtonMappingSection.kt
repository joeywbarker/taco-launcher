package com.pockettaco.salsa.ui

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettaco.salsa.*
import kotlinx.coroutines.delay

/**
 * Settings section: one row per phone action. Pick "Set" on a row, then press the
 * Taco button (or hold two together) you want for it.
 */
@Composable
fun ButtonMappingSection(activity: MainActivity) {
    var mapperOn by remember { mutableStateOf(isMapperOn(activity)) }
    var lastKey by remember { mutableStateOf(ButtonMapperState.lastKey) }
    LaunchedEffect(Unit) {
        while (true) { mapperOn = isMapperOn(activity); lastKey = ButtonMapperState.lastKey; delay(500) }
    }
    var mappings by remember { mutableStateOf(loadMappings(activity)) }
    /** The action we're waiting on a button press for, or null. */
    var listeningFor by remember { mutableStateOf<MapAction?>(null) }
    val captured = remember { mutableStateListOf<Int>() }
    fun name(keys: List<Int>) = keys.joinToString(" + ") { tacoButtonName(it, activity.settings) }

    fun update(list: List<Mapping>) { mappings = list; saveMappings(activity, list) }

    fun stopListening() {
        listeningFor = null
        activity.onKeyCapture = null
        ButtonMapperState.capturing = false
    }

    fun listen(action: MapAction) {
        captured.clear()
        val down = mutableSetOf<Int>()
        listeningFor = action
        ButtonMapperState.capturing = true
        activity.onKeyCapture = { e ->
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                down += e.keyCode
                if (e.keyCode !in captured && captured.size < 2) captured += e.keyCode
            } else if (e.action == KeyEvent.ACTION_UP && e.keyCode in down) {
                down -= e.keyCode
                if (down.isEmpty() && captured.isNotEmpty()) {
                    val keys = captured.sorted()
                    // These buttons now do this action only; this action uses only these buttons.
                    update(mappings.filter { it.keys.sorted() != keys && it.action != action } + Mapping(keys, action))
                    stopListening()
                }
            }
        }
    }
    // Give up after 8 seconds with no press, so you can never get stuck.
    LaunchedEffect(listeningFor) {
        if (listeningFor != null) { delay(8000); if (captured.isEmpty()) stopListening() }
    }
    DisposableEffect(Unit) { onDispose { stopListening() } }

    Text("Button mapping", color = Color.White, fontWeight = FontWeight.Bold)
    if (!mapperOn) {
        Text(
            "To make buttons work inside games, switch on \"Taco Launcher buttons\" in Accessibility. " +
                "If Android says it's restricted: Settings → Apps → Taco Launcher → ⋮ → Allow restricted settings, then try again.",
            color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                activity.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("Turn on button mapping") }
            // App info is where the ⋮ → "Allow restricted settings" option lives.
            OutlinedButton(onClick = {
                activity.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${activity.packageName}")))
            }) { Text("App info") }
        }
    } else {
        Text("On: these work inside games. In this menu A and B always work normally.",
            color = Color(0xFF4CFF9A), style = MaterialTheme.typography.bodySmall)
        Text("Last button seen in a game: " + (lastKey ?: "none yet"),
            color = Color(0xFFBBBBBB), style = MaterialTheme.typography.bodySmall)
    }
    if (mappings.any { KeyEvent.KEYCODE_BUTTON_SELECT in it.keys && it.keys.size > 1 }) Text(
        "Heads up: a Select combo here blocks RetroArch's Select hotkeys (like Select + B for its menu) inside games.",
        color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall,
    )
    Text("Choose Set, then press the button you want (or hold two together for a combo).",
        color = Color(0xFF999999), style = MaterialTheme.typography.bodySmall)

    MapAction.entries.forEach { action ->
        val keys = mappings.filter { it.action == action }
        val listening = listeningFor == action
        Row(
            Modifier.fillMaxWidth()
                .then(if (listening) Modifier.border(2.dp, Color(0xFF4CFF9A), RoundedCornerShape(12.dp)) else Modifier)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(action.label, color = Color.White)
                Text(
                    when {
                        listening && captured.isEmpty() -> "Press a button now…"
                        listening -> name(captured) + " (let go to save)"
                        keys.isEmpty() -> "Not set"
                        else -> keys.joinToString("  or  ") { name(it.keys) }
                    },
                    color = if (listening) Color(0xFF4CFF9A) else Color(0xFFAAAAAA),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // The buttons stay in place (never vanish), so the controller doesn't lose its spot.
            OutlinedButton(
                onClick = { if (listening) stopListening() else listen(action) },
                enabled = listeningFor == null || listening,
            ) { Text(if (listening) "Cancel" else "Set") }
            TextButton(onClick = { update(mappings.filter { it.action != action }) },
                enabled = listeningFor == null) { Text("Clear") }
        }
    }
    TextButton(onClick = { update(DEFAULT_MAPPINGS) }, enabled = listeningFor == null) {
        Text("Reset to defaults")
    }
}
