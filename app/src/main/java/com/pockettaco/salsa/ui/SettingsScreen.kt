package com.pockettaco.salsa.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pockettaco.salsa.MainActivity

@Composable
fun SettingsScreen(
    activity: MainActivity,
    covered: Float,
    onCoveredChange: (Float) -> Unit,
    onPickFolder: () -> Unit,
    onClose: () -> Unit,
) {
    var anyPad by remember { mutableStateOf(activity.settings.anyGamepadCounts) }
    var force by remember { mutableStateOf(activity.settings.forceTacoMode) }
    DisposableEffect(Unit) {
        activity.onBackRequest = { onClose(); true }
        onDispose { activity.onBackRequest = null }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Color.White)

        Text("Screen covered by the Pocket Taco: ${(covered * 100).toInt()}%", color = Color.White)
        Slider(value = covered, onValueChange = onCoveredChange, valueRange = 0f..0.6f, steps = 11)

        Button(onClick = onPickFolder) { Text("Choose games folder") }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Treat any controller as a Pocket Taco", color = Color.White, modifier = Modifier.weight(1f))
            Switch(anyPad, { anyPad = it; activity.settings.anyGamepadCounts = it; activity.watcher.refresh() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Preview Taco layout now (for testing)", color = Color.White, modifier = Modifier.weight(1f))
            Switch(force, { force = it; activity.settings.forceTacoMode = it; activity.watcher.refresh() })
        }
        Text("Connected controller: ${activity.watcher.deviceName.value ?: "none"}",
            color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)

        TextButton(onClick = onClose) { Text("Done") }
    }
}
