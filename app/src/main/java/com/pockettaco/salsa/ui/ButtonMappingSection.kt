package com.pockettaco.salsa.ui

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

/** Settings section: map controller buttons and two-button combos to phone actions. */
@Composable
fun ButtonMappingSection(activity: MainActivity) {
    var mapperOn by remember { mutableStateOf(isMapperOn(activity)) }
    LaunchedEffect(Unit) { while (true) { mapperOn = isMapperOn(activity); delay(1000) } }
    var mappings by remember { mutableStateOf(loadMappings(activity)) }
    var capturing by remember { mutableStateOf(false) }
    val captured = remember { mutableStateListOf<Int>() }

    fun update(list: List<Mapping>) { mappings = list; saveMappings(activity, list) }

    fun stopCapture() {
        capturing = false
        activity.onKeyCapture = null
        ButtonMapperState.capturing = false
    }

    fun startCapture() {
        captured.clear()
        val down = mutableSetOf<Int>()
        capturing = true
        ButtonMapperState.capturing = true
        activity.onKeyCapture = { e ->
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                down += e.keyCode
                if (e.keyCode !in captured && captured.size < 2) captured += e.keyCode
            } else if (e.action == KeyEvent.ACTION_UP) {
                down -= e.keyCode
                if (down.isEmpty() && captured.isNotEmpty()) {
                    val keys = captured.sorted()
                    update(mappings.filter { it.keys.sorted() != keys } + Mapping(keys, MapAction.TACO_LAUNCHER))
                    stopCapture()
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { stopCapture() } }

    Text("Button mapping", color = Color.White, fontWeight = FontWeight.Bold)
    if (!mapperOn) {
        Text(
            "To make buttons work inside emulators, switch on \"Taco Launcher buttons\" in Accessibility. " +
                "If Android says it's restricted: Settings → Apps → Taco Launcher → ⋮ → Allow restricted settings, then try again.",
            color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = {
            activity.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) { Text("Turn on button mapping") }
    } else {
        Text("On: these work everywhere, even inside games.", color = Color(0xFF4CFF9A),
            style = MaterialTheme.typography.bodySmall)
    }

    mappings.forEachIndexed { i, m ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.label, color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                val next = MapAction.entries[(m.action.ordinal + 1) % MapAction.entries.size]
                update(mappings.toMutableList().also { it[i] = m.copy(action = next) })
            }) { Text(m.action.label) }
            TextButton(onClick = { update(mappings.filterIndexed { j, _ -> j != i }) }) { Text("✕") }
        }
    }
    Text("Tap an action to change it.", color = Color(0xFF777777), style = MaterialTheme.typography.bodySmall)

    if (capturing) {
        Box(
            Modifier.fillMaxWidth()
                .border(2.dp, Color(0xFF4CFF9A), RoundedCornerShape(12.dp))
                .clickable { stopCapture() }
                .padding(16.dp)
        ) {
            Text(
                if (captured.isEmpty()) "Press one button, or hold two together…\n(tap here to cancel)"
                else "Got: " + captured.joinToString(" + ") { buttonName(it) } + " (let go to save)",
                color = Color.White,
            )
        }
    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { startCapture() }) { Text("Add button or combo") }
        TextButton(onClick = { update(DEFAULT_MAPPINGS) }) { Text("Reset") }
    }
}
