package com.pockettaco.salsa.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
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
import com.pockettaco.salsa.buttonName
import com.pockettaco.salsa.FocusKick
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
            val backFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(FocusKick.count.intValue) { runCatching { backFocus.requestFocus() } }
            OutlinedButton(onClick = onClose,
                modifier = androidx.compose.ui.Modifier.focusRequester(backFocus)) { Text("← Back") }
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        MenuButtonsSection(activity)

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

        val version = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull()
        Text("Taco Launcher version ${version ?: "?"}",
            color = Color(0xFF777777), style = MaterialTheme.typography.bodySmall)

        TextButton(onClick = onClose) { Text("Done") }
    }
}

/**
 * Teach the launcher which Taco button is Select (A) and which is Back (B), so it works
 * whatever codes the controller sends.
 */
@Composable
private fun MenuButtonsSection(activity: MainActivity) {
    var selectKey by remember { mutableIntStateOf(activity.settings.selectKey) }
    var backKey by remember { mutableIntStateOf(activity.settings.backKey) }
    /** 0 = not teaching, 1 = waiting for A, 2 = waiting for B. */
    var step by remember { mutableIntStateOf(0) }
    var pickedKey by remember { mutableIntStateOf(0) }
    var pickedScan by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }

    fun stop() { step = 0; activity.onKeyCapture = null }
    fun start() {
        step = 1
        note = null
        activity.onKeyCapture = { e ->
            if (e.action == android.view.KeyEvent.ACTION_UP) {
                val sig = "${buttonName(e.keyCode)} (code ${e.keyCode}, scan ${e.scanCode})"
                activity.lastButton.value = sig
                if (step == 1) {
                    pickedKey = e.keyCode; pickedScan = e.scanCode; step = 2
                } else if (step == 2) {
                    if (e.keyCode == pickedKey && e.scanCode == pickedScan) {
                        note = "That's the same signal as A: $sig. Press the B button."
                    } else {
                        // Only keep the scan detail when it's needed to tell the two apart.
                        val sameCode = e.keyCode == pickedKey
                        activity.settings.selectKey = pickedKey
                        activity.settings.selectScan = if (sameCode) pickedScan else 0
                        activity.settings.backKey = e.keyCode
                        activity.settings.backScan = if (sameCode) e.scanCode else 0
                        selectKey = pickedKey
                        backKey = e.keyCode
                        note = "Saved."
                        stop()
                    }
                }
            }
        }
    }
    LaunchedEffect(step) { if (step != 0) { kotlinx.coroutines.delay(20000); stop() } }
    DisposableEffect(Unit) { onDispose { stop() } }

    Text("A and B buttons", color = Color.White, fontWeight = FontWeight.Bold)
    Text(
        when (step) {
            1 -> "Press the button marked A on your Taco (select)…"
            2 -> (note ?: "Now press the button marked B (back)…") +
                "\nA was: ${buttonName(pickedKey)} (code $pickedKey, scan $pickedScan)"
            else -> "A (select) = ${buttonName(selectKey)} (code $selectKey)   ·   " +
                "B (back) = ${buttonName(backKey)} (code $backKey)\n" +
                "Last button pressed: ${activity.lastButton.value ?: "none"}" +
                (note?.let { "\n$it" } ?: "")
        },
        color = if (step != 0) Color(0xFF4CFF9A) else Color(0xFFAAAAAA),
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (step == 0) start() else stop() }) {
            Text(if (step == 0) "Teach A and B" else "Cancel")
        }
        if (step == 0) TextButton(onClick = {
            val st = activity.settings
            val k = st.selectKey; val sc = st.selectScan
            st.selectKey = st.backKey; st.selectScan = st.backScan
            st.backKey = k; st.backScan = sc
            selectKey = st.selectKey; backKey = st.backKey
        }) { Text("Swap them") }
    }
}
