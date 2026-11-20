package com.pockettaco.salsa.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.pockettaco.salsa.AppIcon
import com.pockettaco.salsa.MainActivity
import com.pockettaco.salsa.currentAppIcon
import com.pockettaco.salsa.setAppIcon
import com.pockettaco.salsa.canAppearOnTop
import com.pockettaco.salsa.hasBluetoothPermission

@Composable
fun SettingsScreen(
    activity: MainActivity,
    covered: Float,
    onCoveredChange: (Float) -> Unit,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
) {
    var anyPad by remember { mutableStateOf(activity.settings.anyGamepadCounts) }
    var openOnConnect by remember { mutableStateOf(activity.settings.openOnConnect) }
    var btOk by remember { mutableStateOf(hasBluetoothPermission(activity)) }
    var topOk by remember { mutableStateOf(canAppearOnTop(activity)) }
    val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { btOk = it }
    // Re-check "Appear on top" when coming back from Android's settings page.
    LaunchedEffect(Unit) {
        while (true) { topOk = canAppearOnTop(activity); kotlinx.coroutines.delay(1000) }
    }
    var fit by remember { mutableStateOf(activity.settings.fitEmulators) }
    var force by remember { mutableStateOf(activity.settings.forceTacoMode) }
    DisposableEffect(Unit) {
        val handler: () -> Boolean = { onClose(); true }
        activity.onBackRequest = handler
        onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = null }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onClose) { Text("← Back") }
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        var swap by remember { mutableStateOf(activity.settings.swapAB) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Swap A and B", color = Color.White)
                Text("Turn on if B selects instead of going back. Last button pressed: ${activity.lastButton.value ?: "none"}",
                    color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
            }
            Switch(swap, { swap = it; activity.settings.swapAB = it })
        }

        Text("Screen covered by the Pocket Taco: ${(covered * 100).toInt()}%", color = Color.White)
        Slider(value = covered, onValueChange = onCoveredChange, valueRange = 0f..0.6f, steps = 11)
        GameWindowSection(activity, covered)

        ButtonMappingSection(activity)

        Text("App icon", color = Color.White, fontWeight = FontWeight.Bold)
        var icon by remember { mutableStateOf(currentAppIcon(activity)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon.entries.forEach { option ->
                val picked = option == icon
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .border(if (picked) 3.dp else 1.dp,
                            if (picked) Color.White else Color(0xFF444444), RoundedCornerShape(16.dp))
                        .clickable {
                            if (!picked) { setAppIcon(activity, option); icon = option }
                        }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(option.preview), contentDescription = option.label,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1B1550)))
                    Text(option.label, color = if (picked) Color.White else Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Your home screen can take a few seconds to show the new icon.",
            color = Color(0xFF777777), style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPickFolder) { Text("Choose games folder") }
            OutlinedButton(onClick = onRescan) { Text("Rescan games") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Treat any controller as a Pocket Taco", color = Color.White, modifier = Modifier.weight(1f))
            Switch(anyPad, { anyPad = it; activity.settings.anyGamepadCounts = it; activity.watcher.refresh() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Open this launcher when the Pocket Taco connects", color = Color.White, modifier = Modifier.weight(1f))
            Switch(openOnConnect, { openOnConnect = it; activity.settings.openOnConnect = it })
        }
        if (openOnConnect && (!btOk || !topOk)) {
            Text("To open by itself, Android needs two permissions:", color = Color(0xFFFFC107),
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!btOk && Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = {
                    askBt.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }) { Text("Nearby devices") }
                if (!topOk) OutlinedButton(onClick = {
                    activity.startActivity(
                        Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}"))
                    )
                }) { Text("Appear on top") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fit emulators above the Pocket Taco (pop-up window)", color = Color.White, modifier = Modifier.weight(1f))
            Switch(fit, { fit = it; activity.settings.fitEmulators = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Always use the Taco layout", color = Color.White, modifier = Modifier.weight(1f))
            Switch(force, { force = it; activity.settings.forceTacoMode = it; activity.watcher.refresh() })
        }
        val taco by activity.watcher.deviceName.collectAsState()
        val devices by activity.watcher.allDevices.collectAsState()
        Text("Pocket Taco: ${taco ?: "not detected"}",
            color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
        Text("Devices Android sees: ${devices.joinToString().ifEmpty { "none" }}",
            color = Color(0xFF777777), style = MaterialTheme.typography.bodySmall)

        TextButton(onClick = onClose) { Text("Done") }
    }
}
