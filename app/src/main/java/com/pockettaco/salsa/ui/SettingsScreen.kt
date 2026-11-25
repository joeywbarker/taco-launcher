package com.pockettaco.salsa.ui

import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
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
import com.pockettaco.salsa.TacoButton
import com.pockettaco.salsa.loadMappings
import com.pockettaco.salsa.saveMappings
import com.pockettaco.salsa.FocusKick
import com.pockettaco.salsa.GameNames
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
    // Settings is split into pages; null = the list of pages.
    var page by remember { mutableStateOf<String?>(null) }
    val goBack: () -> Unit = { if (page != null) page = null else onClose() }
    DisposableEffect(Unit) {
        val handler: () -> Boolean = { goBack(); true }
        activity.onBackRequest = handler
        onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = null }
    }
    val pages = listOf(
        Triple("check", "Setup check", "Spot anything missing (emulator, hotkeys, home app) and fix it"),
        Triple("controller", "Controller & buttons", "A and B, button mapping, auto-open, detection"),
        Triple("touch", "Touch controls", "Use the menus by touch, tap the button hints, or turn touch off"),
        Triple("screen", "Screen & game window", "Taco mode or full screen (Taco off), space the Taco covers, game window"),
        Triple("retroarch", "RetroArch games", "Picture position, menu and quit hotkeys, save states, resume"),
        Triple("games", "Games & systems", "Game names, folders, rescan, which app each system uses"),
        Triple("look", "Look & start", "Where the app opens, home view, background, app icon"),
        Triple("pictures", "Console pictures", "Turn pictures off, change their order, add up to 10 of your own"),
        Triple("backup", "Backup & restore", "Save all your settings to a file, or put them back"),
        Triple("about", "About", "Version and credits"),
    )
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val backFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            // Opening Settings (or a page) slides the screen in; focus asked for mid-slide can be
            // lost, which left the buttons doing nothing. Ask again once it has settled.
            LaunchedEffect(FocusKick.count.intValue, page) {
                androidx.compose.runtime.withFrameNanos { }
                runCatching { backFocus.requestFocus() }
                kotlinx.coroutines.delay(400)
                runCatching { backFocus.requestFocus() }
            }
            OutlinedButton(onClick = goBack,
                modifier = androidx.compose.ui.Modifier.focusRequester(backFocus)) { Text("← Back") }
            Spacer(Modifier.width(12.dp))
            Text(pages.firstOrNull { it.first == page }?.second ?: "Settings",
                style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        when (page) {
            null -> pages.forEach { (key, title, sub) ->
                GameCard(title, sub, SETTINGS_LOOK, tile = when (key) {
                    "controller" -> "🎮"; "touch" -> "👆"; "screen" -> "▭"; "retroarch" -> "▶"; "games" -> "📁"; "look" -> "🎨"; "pictures" -> "🖼"; "backup" -> "💾"; "check" -> "✓"; else -> "ℹ"
                }) { page = key }
            }
            "controller" -> {
            MenuButtonsSection(activity)
            FavoriteButtonSection(activity)
            ButtonMappingSection(activity)
            RetroArchHotkeysSection(activity)
            SettingSwitch("Treat any controller as a Pocket Taco",
                "Any gamepad switches on the Taco layout, not just the Pocket Taco. Handy for other clip-on controllers.",
                anyPad) { anyPad = it; activity.settings.anyGamepadCounts = it; activity.watcher.refresh() }
            SettingSwitch("Open this launcher when the Pocket Taco connects",
                "Clip the Taco on and Taco Launcher comes up by itself (with the intro unless you skip it in About).",
                openOnConnect) { openOnConnect = it; activity.settings.openOnConnect = it }
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
            val taco by activity.watcher.deviceName.collectAsState()
            val devices by activity.watcher.allDevices.collectAsState()
            Text("Pocket Taco: ${taco ?: "not detected"}",
                color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
            Text("Devices Android sees: ${devices.joinToString().ifEmpty { "none" }}",
                color = Color(0xFF777777), style = MaterialTheme.typography.bodySmall)


            }
            "screen" -> {
            ScreenModeSection(activity)
            SettingHeader("Taco mode", "How Taco Launcher keeps clear of the Pocket Taco while it's clipped on.")
            Text("Screen covered by the Pocket Taco: ${(covered * 100).toInt()}%", color = Color.White)
            Slider(value = covered, onValueChange = onCoveredChange, valueRange = 0f..0.6f, steps = 11)
            GameWindowSection(activity, covered)
            GameCard("Run the first-time setup again", "Walks you through the Taco's space with a live preview",
                SETTINGS_LOOK, tile = "📏") { activity.settings.setupDone = false; activity.recreate() }

            SettingSwitch("Fit emulators above the Pocket Taco",
                "Other emulators open in a pop-up window sized to the free part of the screen, so nothing hides behind the Taco. " +
                    "RetroArch games have their own setting on the RetroArch games page.",
                fit, Applies.NEXT_GAME) { fit = it; activity.settings.fitEmulators = it }
            SettingSwitch("Always use the Taco layout",
                "Keep the menus in the top part of the screen even when no controller is connected.",
                force) { force = it; activity.settings.forceTacoMode = it; activity.watcher.refresh() }
            }
            "games" -> {
            GameNamesSection(activity)
            Text("Games", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Main folder: ${com.pockettaco.salsa.folderLabel(activity.settings.romFolder)}",
                color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickFolder) { Text("Choose games folder") }
                OutlinedButton(onClick = onRescan) { Text("Rescan games") }
            }

            BoxArtSection(activity)
            SystemsSection(activity, onRescan)

            }
            "pictures" -> ConsolePicturesPage(activity)
            "look" -> {
                StartSpotSettings()
                HomeLookSection(activity)
                SlideshowSettings()
                GameCard("Console pictures", "Turn pictures off, change their order, add your own",
                    SETTINGS_LOOK, tile = "🖼") { page = "pictures" }
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

            }
            "touch" -> TouchControlsSection(activity)
            "retroarch" -> RetroArchGamesSection(activity)
            "check" -> SetupCheckSection(activity, onPickFolder)
            "backup" -> BackupSection(activity)
            "about" -> AboutSection(activity)
        }
    }
}

/**
 * Everything about how RetroArch games look and behave. Taco Launcher applies these itself each
 * time it starts a game (through its own RetroArch settings file), so nothing needs changing
 * inside RetroArch, and your own RetroArch settings are never overwritten.
 */
@Composable
private fun RetroArchGamesSection(activity: MainActivity) {
    val st = activity.settings
    var full by remember { mutableStateOf(st.retroArchFullScreen) }
    var picture by remember { mutableStateOf(st.raPictureDefault) }
    var commands by remember { mutableStateOf(st.raCommands) }
    var startMenu by remember { mutableStateOf(st.raStartOpensMenu) }
    var states by remember { mutableStateOf(st.raStateHotkeys) }
    var ask by remember { mutableStateOf(st.raAskBeforeSwitch) }
    var same by remember { mutableStateOf(st.raSameForEveryGame) }
    var menuAbove by remember { mutableStateOf(st.raMenuAboveTaco) }
    Text("Taco Launcher sets these up in RetroArch for you each time it starts a game. " +
        "Your own RetroArch settings stay as they are when you open RetroArch by itself.",
        color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)

    SettingHeader("Screen")
    SettingSwitch("Full screen (no window frame)",
        "Games fill the screen with no pop-up frame or title bar. Taco Launcher places the picture above the Taco. " +
            "Off: games open in a pop-up window instead, and the options below about the picture don't apply. " +
            "In full screen (Taco off) games always fill the screen and the picture sits in the middle.",
        full, Applies.NEXT_GAME) { full = it; st.retroArchFullScreen = it }
    if (full) {
        val sample = remember { com.pockettaco.salsa.SYSTEMS.first { it.id == "snes" } }
        SettingChoice("Picture position",
            "For every game, unless you pick a different one for a game (hold A on a game → Screen layout).",
            listOf(
                Triple("auto", "Auto", "Keeps the game's shape, centred on your game spot (Screen & game window page)."),
                Triple("top", "Top", "Keeps the game's shape, starting at the very top of the screen."),
                Triple("tall", "Tall", "Fills from the top of the screen down to the Taco's edge. Best for upright arcade games like 1941."),
            ), picture, Applies.NEXT_GAME,
            preview = { LayoutPreview(activity, sample, com.pockettaco.salsa.RetroArchPlacement.Layout.of(it)) },
        ) { picture = it; st.raPictureDefault = it }
        RaChoice(activity, "smooth", "Smooth picture",
            "On softens the pixels (blurry but smooth). Off keeps sharp, blocky pixels.")
        RaChoice(activity, "fps", "Show frames per second", "A small speed counter in the corner of the game.")
    }

    SettingHeader("Hotkeys")
    SettingSwitch("Taco Launcher controls the menu and quitting",
        "Select + Start opens RetroArch's menu. Leaving a game (Home, or your Exit game hotkey) closes RetroArch " +
            "completely, so the next game starts cleanly instead of on a black screen. Locking the screen also " +
            "closes the game, so turn on \"Resume where you left off\" below to keep your spot. " +
            "Turn off only if you set RetroArch's buttons up yourself.",
        commands, Applies.NEXT_GAME) { commands = it; st.raCommands = it }
    if (commands) SettingSwitch("Start on its own also opens the menu",
        "Off: only Select + Start opens it, so pressing Start in a game (to pause) never brings up the menu.",
        startMenu, Applies.NEXT_GAME) { startMenu = it; st.raStartOpensMenu = it }
    SettingSwitch("Save state hotkeys",
        "Select + R1 saves your exact spot in the game, Select + L1 loads it back.",
        states, Applies.NEXT_GAME) { states = it; st.raStateHotkeys = it }
    SettingSwitch("RetroArch's menu stays above the Taco",
        "On: Select + Start opens RetroArch's simple retro-style menu inside the game's picture, so the Taco " +
            "never covers any of it. Off: RetroArch's usual menu style, which fills the whole screen and partly " +
            "sits behind the Taco.",
        menuAbove, Applies.NEXT_GAME) { menuAbove = it; st.raMenuAboveTaco = it }
    SettingSwitch("Same settings for every game",
        "RetroArch can quietly keep its own settings for one game, or for all games of one system " +
            "(for example if \"Save Game Overrides\" or \"Save Remap File\" was picked in its menu). Those can " +
            "change or switch off the hotkeys above in just that game. On: every game starts fresh with " +
            "Taco Launcher's settings and your normal RetroArch setup, and those extra files are skipped " +
            "(not deleted). Off: RetroArch uses them again.",
        same, Applies.NEXT_GAME) { same = it; st.raSameForEveryGame = it }

    SettingHeader("Playing")
    SettingSwitch("Ask \"Still playing?\" before switching games",
        "Only one RetroArch game runs at a time. If you left a game with Home and pick a different one, " +
            "Taco Launcher asks whether to go back to it or close it and start the new one. " +
            "You can also choose whether to keep your spot in the old game: kept, it picks up right there next time; " +
            "not kept, it starts from the beginning (or your own save states). " +
            "Off: the new game just replaces the old one. Using the Exit game hotkey never asks.",
        ask, Applies.NOW) { ask = it; st.raAskBeforeSwitch = it }
    RaChoice(activity, "resume", "Resume where you left off",
        "On: leaving a game saves your spot automatically, and starting it again picks up right there. " +
            "\"RetroArch's\" does this too while \"Still playing?\" is on, so that question can offer to keep your spot.")
    RaChoice(activity, "pauseMenu", "Pause the game while the menu is open",
        "On: the game stops while RetroArch's menu is showing.")
}

/** A three-way RetroArch option: leave it to RetroArch, or force on or off. */
@Composable
private fun RaChoice(activity: MainActivity, key: String, title: String, about: String) {
    var v by remember { mutableStateOf(activity.settings.raChoice(key)) }
    SettingChoice(title, about,
        listOf(
            Triple(0, "RetroArch's", "Uses whatever RetroArch itself is set to."),
            Triple(1, "On", "Always on for games started from Taco Launcher."),
            Triple(2, "Off", "Always off for games started from Taco Launcher."),
        ), v, Applies.NEXT_GAME) { v = it; activity.settings.setRaChoice(key, it) }
}

/** View, background, hidden systems and a reset for the home screen's look. */
@Composable
private fun HomeLookSection(activity: MainActivity) {
    val custom = com.pockettaco.salsa.Custom
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.pockettaco.salsa.savePicture(activity, uri, custom.backgroundFile(), maxSide = 1600, square = false)
            }
            if (error != null) android.widget.Toast.makeText(activity, error, android.widget.Toast.LENGTH_LONG).show()
            else custom.background = -1
        }
    }
    var confirmReset by remember { mutableStateOf(false) }

    Text("Home screen look", color = Color.White, fontWeight = FontWeight.Bold)
    Text("Tip: on the home screen, Select switches the view and Start (or holding a system) opens its menu.",
        color = Color(0xFF999999), style = MaterialTheme.typography.bodySmall)
    Text("View", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.pockettaco.salsa.HomeView.entries.forEach { v ->
            if (custom.homeView == v) Button(onClick = { custom.homeView = v }) { Text(v.label) }
            else OutlinedButton(onClick = { custom.homeView = v }) { Text(v.label) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Lock the view", color = Color.White)
            Text("Select won't switch views on the home screen or in game lists. You can still change them here or from a menu.",
                color = Color(0xFF999999), style = MaterialTheme.typography.bodySmall)
        }
        Switch(custom.viewLocked, { custom.viewLocked = it })
    }
    val view = custom.homeView
    if (view == com.pockettaco.salsa.HomeView.BIG || view == com.pockettaco.salsa.HomeView.SMALL) {
        Text("Icon grid for ${view.label.lowercase()} (icons get bigger or smaller to fit)",
            color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
        @Composable
        fun stepper(label: String, value: Int, range: IntRange, set: (Int) -> Unit) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, color = Color.White, modifier = Modifier.width(70.dp))
                OutlinedButton(onClick = { set(value - 1) }, enabled = value > range.first) { Text("−") }
                Text("$value", color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OutlinedButton(onClick = { set(value + 1) }, enabled = value < range.last) { Text("+") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stepper("Columns", custom.gridColumns(view), custom.GRID_COLS) { custom.setGridColumns(view, it) }
                stepper("Rows", custom.gridRows(view), custom.GRID_ROWS) { custom.setGridRows(view, it) }
            }
            GridPreview(custom.gridColumns(view), custom.gridRows(view), activity.settings.coveredFraction)
        }
    }
    Text("Background", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.pockettaco.salsa.BACKDROPS.forEachIndexed { i, b ->
            val picked = custom.background == i
            Column(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .border(if (picked) 3.dp else 1.dp, if (picked) Color.White else Color(0xFF444444), RoundedCornerShape(12.dp))
                    .clickable { custom.background = i }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                    .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(b.glow, b.top, b.bottom))))
                Text(b.name, color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Use a picture") }
        if (custom.background == -1) TextButton(onClick = { custom.backgroundFile().delete(); custom.background = 0 }) {
            Text("Remove picture")
        }
    }
    Text("Any picture works (up to 30 MB); it's resized to fit.", color = Color(0xFF777777),
        style = MaterialTheme.typography.bodySmall)
    if (custom.hidden.isNotEmpty()) OutlinedButton(onClick = { custom.hidden = emptySet() }) {
        Text("Show ${custom.hidden.size} hidden system(s)")
    }
    if (!confirmReset) TextButton(onClick = { confirmReset = true }) { Text("Reset names, colors, pictures and order") }
    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { custom.resetAll(); confirmReset = false }) { Text("Yes, reset the look") }
        TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
    }
}

/** Cover pictures for games, downloaded as they show up. */
@Composable
private fun BoxArtSection(activity: MainActivity) {
    var on by remember { mutableStateOf(activity.settings.boxArt) }
    var cleared by remember { mutableStateOf(false) }
    SettingSwitch("Show box art",
        "Downloads each game's cover picture the first time it shows in a list (Grid and List views), from the free " +
            "libretro-thumbnails collection that RetroArch also uses. Pictures stay on your phone after that. " +
            "Works best when game files have their full names, like \"Pokemon - Crystal Version (USA)\". Not for Arcade.",
        on, Applies.NOW) { on = it; activity.settings.boxArt = it }
    if (on) OutlinedButton(onClick = { com.pockettaco.salsa.BoxArt.clear(); cleared = true }) {
        Text(if (cleared) "✓ Cleared: pictures download again" else "Download all box art again")
    }
}

/** Save everything to one file, or put it back (for reinstalling or a new phone). */
@Composable
private fun BackupSection(activity: MainActivity) {
    var note by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) note = com.pockettaco.salsa.Backup.write(activity, uri).fold(
            { "✓ Saved. Keep this file somewhere safe (it's in the folder you picked)." },
            { "Couldn't save: ${it.message}" })
    }
    var confirm by remember { mutableStateOf(false) }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) com.pockettaco.salsa.Backup.restore(activity, uri).fold(
            { com.pockettaco.salsa.Backup.restartApp(activity) },
            { note = "Nothing was changed: ${it.message}" })
    }
    Text("One file holds all your Taco Launcher settings, button mappings, per-game choices, pictures and " +
        "home screen look. Make one before reinstalling or moving to a new phone.",
        color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
    GameCard("Back up my settings", "Saves a .zip file wherever you choose (Downloads is a good spot)",
        SETTINGS_LOOK, tile = "💾") {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        save.launch("taco-launcher-backup-$day.zip")
    }
    GameCard(if (confirm) "Press again to restore" else "Restore from a backup",
        if (confirm) "Replaces all current settings with the backup's" else
            "Taco Launcher restarts afterwards and asks for your games folder again (Android needs that)",
        SETTINGS_LOOK, tile = "↺") {
        if (!confirm) confirm = true else { confirm = false; load.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    }
    note?.let { Text(it, color = Color(0xFFFFD25A), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun AboutSection(activity: MainActivity) {
    val version = runCatching {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
    }.getOrNull()
    Text("About", color = Color.White, fontWeight = FontWeight.Bold)
    Text("🌮 Taco Launcher ${version ?: ""}", color = Color(0xFFFFD25A), fontWeight = FontWeight.Bold)
    Text("Designed by: Joey Wayne Erickson (2026)", color = Color.White)
    Text("Check out his other projects @", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/joeywbarker"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }) { Text("github.com/joeywbarker") }
    Text("Taco Launcher is an independent, fan-made app. It is not affiliated with, endorsed by, or sponsored by GameSir, the makers of the Pocket Taco, RetroArch / Libretro, or any other company. All product names and trademarks belong to their respective owners and are used only to describe compatibility.", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
    Text("Console and controller pictures: Creative Commons artwork, released by its artist for RetroArch. " +
        "Used under its license; full credit is in the README.", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
    Text("No games are included. Only play games you own.", color = Color(0xFFAAAAAA),
        style = MaterialTheme.typography.bodySmall)
    var skipSplash by remember { mutableStateOf(!activity.settings.showSplash) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Skip splash screen on startup", color = Color.White)
            Text("Normally the intro plays each time the Taco is attached.",
                color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
        }
        Switch(skipSplash, { skipSplash = it; activity.settings.showSplash = !it })
    }
}

/**
 * Teach the launcher which Taco button is Select (A) and which is Back (B), so it works
 * whatever codes the controller sends.
 */
@Composable
private fun MenuButtonsSection(activity: MainActivity) {
    val st = activity.settings
    var version by remember { mutableIntStateOf(0) }   // bump to redraw after saving
    /** Index into TacoButton.entries we're waiting for, or -1 when not teaching. */
    var step by remember { mutableIntStateOf(-1) }
    val taught = remember { mutableStateMapOf<TacoButton, Pair<Int, Int>>() }
    var note by remember { mutableStateOf<String?>(null) }
    val buttons = TacoButton.entries

    fun stop() { step = -1; activity.onKeyCapture = null }
    fun save() {
        // Keep the controller's own button number only where two buttons send the same code.
        taught.forEach { (b, sig) ->
            val shared = taught.any { (o, s2) -> o != b && s2.first == sig.first }
            st.setSignal(b, sig.first, if (shared) sig.second else 0)
        }
        // The Home button leaves games: follow it if the Taco's Home sends something unusual.
        taught[TacoButton.HOME]?.first?.let { home ->
            val list = loadMappings(activity)
            if (home != android.view.KeyEvent.KEYCODE_BUTTON_MODE && list.any { it.keys == listOf(android.view.KeyEvent.KEYCODE_BUTTON_MODE) }) {
                saveMappings(activity, list.map { m ->
                    if (m.keys == listOf(android.view.KeyEvent.KEYCODE_BUTTON_MODE)) m.copy(keys = listOf(home)) else m
                })
            }
        }
        version++
    }
    fun next() {
        if (step + 1 >= buttons.size) { save(); note = "Saved all your buttons."; stop() } else step++
    }
    fun start() {
        taught.clear(); note = null; step = 0
        activity.onKeyCapture = { e ->
            if (e.action == android.view.KeyEvent.ACTION_UP && step >= 0) {
                activity.lastButton.value = "${buttonName(e.keyCode)} (code ${e.keyCode}, scan ${e.scanCode})"
                val same = taught.entries.firstOrNull { it.value == (e.keyCode to e.scanCode) }
                if (same != null) {
                    note = "That's the same signal as ${same.key.label}. Press ${buttons[step].label}, or tap Skip."
                } else {
                    taught[buttons[step]] = e.keyCode to e.scanCode
                    note = null
                    next()
                }
            }
        }
    }
    LaunchedEffect(step) { if (step >= 0) { kotlinx.coroutines.delay(30000); if (step >= 0) { save(); stop() } } }
    DisposableEffect(Unit) { onDispose { stop() } }

    Text("Teach your buttons", color = Color.White, fontWeight = FontWeight.Bold)
    Text(
        if (step >= 0) "Press the button marked ${buttons[step].label} on your Taco…  (${step + 1} of ${buttons.size})" +
            (note?.let { "\n$it" } ?: "")
        else {
            version   // read so the list redraws after saving
            buttons.joinToString("   ") { b ->
                val (code, _) = st.signalFor(b)
                "${b.label} = ${buttonName(code)}"
            } + "\nLast button pressed: ${activity.lastButton.value ?: "none"}" + (note?.let { "\n$it" } ?: "")
        },
        color = if (step >= 0) Color(0xFF4CFF9A) else Color(0xFFAAAAAA),
        style = MaterialTheme.typography.bodySmall,
    )
    Text("Taco Launcher's menus use these. Inside games, each emulator has its own button settings.",
        color = Color(0xFF999999), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (step < 0) start() else { save(); stop() } }) {
            Text(if (step < 0) "Teach buttons" else "Done")
        }
        if (step >= 0) TextButton(onClick = { note = null; next() }) { Text("Skip ${buttons[step].label}") }
        if (step < 0) TextButton(onClick = {
            val a = st.signalFor(TacoButton.A); val b = st.signalFor(TacoButton.B)
            st.setSignal(TacoButton.A, b.first, b.second); st.setSignal(TacoButton.B, a.first, a.second)
            version++
        }) { Text("Swap A and B") }
    }
}

/**
 * Batocera-style hotkeys, set up inside RetroArch: hold Select (the "hotkey" button) and
 * press another button. RetroArch does these itself, so quitting saves properly.
 */
@Composable
private fun RetroArchHotkeysSection(activity: MainActivity) {
    Text("Game hotkeys", color = Color.White, fontWeight = FontWeight.Bold)
    Text(
        "Select + Start = RetroArch's menu.  Home = leave the game and come back here.  " +
            "Select + R1 / Select + L1 = save / load your spot (RetroArch games).  " +
            "These need button mapping switched on (above). More RetroArch options: Settings → RetroArch games.",
        color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall,
    )
}

/** Which button adds a game to favorites in a game list (Y unless you change it). */
@Composable
private fun FavoriteButtonSection(activity: MainActivity) {
    val options = listOf(TacoButton.Y, TacoButton.X, TacoButton.L, TacoButton.R, null)
    var current by remember { mutableStateOf(activity.settings.favoriteButton) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Favorite button: ${current?.label ?: "Off"}", color = Color.White)
            Text("Stars the game you're on. Holding A on a game (or Start) also works.",
                color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = {
            val next = options[(options.indexOf(current) + 1) % options.size]
            activity.settings.favoriteButton = next; current = next
        }) { Text("Change") }
    }
}

/**
 * One row per system: which emulator it opens with (change to any installed app)
 * and an optional extra folder just for that system.
 */
@Composable
private fun SystemsSection(activity: MainActivity, onRescan: () -> Unit) {
    val installed = remember { com.pockettaco.salsa.installedEmulators(activity) }
    var version by remember { mutableIntStateOf(0) }   // bump to redraw after a change
    var openFor by remember { mutableStateOf<String?>(null) }  // system whose app list is open
    var showAllApps by remember { mutableStateOf(false) }
    var folderFor by remember { mutableStateOf<String?>(null) }
    val allApps = remember(showAllApps) { if (showAllApps) com.pockettaco.salsa.loadApps(activity) else emptyList() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val id = folderFor
        if (uri != null && id != null) {
            runCatching {
                com.pockettaco.salsa.keepFolderAccess(activity, uri)
            }
            activity.settings.setFolderFor(id, uri)
            version++
            onRescan()
        }
    }

    Text("Systems", color = Color.White, fontWeight = FontWeight.Bold)
    Text("Pick the app each system opens with. Give a system its own folder and it uses only that folder (instead of the main one).",
        color = Color(0xFF999999), style = MaterialTheme.typography.bodySmall)
    key(version) {
        com.pockettaco.salsa.SYSTEMS.forEach { system ->
            val chosen = activity.settings.emulatorFor(system.id)
            val current = com.pockettaco.salsa.currentEmulator(activity, system, installed)
            val folder = activity.settings.folderFor(system.id)
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(system.name, color = Color.White)
                Text(
                    "App: ${current?.name ?: "none installed"}${if (chosen == null && current != null) " (automatic)" else ""}" +
                        "   ·   Extra folder: ${if (folder == null) "none" else com.pockettaco.salsa.folderLabel(folder)}",
                    color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openFor = if (openFor == system.id) null else system.id; showAllApps = false }) {
                        Text(if (openFor == system.id) "Close" else "Change app")
                    }
                    OutlinedButton(onClick = { folderFor = system.id; picker.launch(null) }) { Text("Folder") }
                    if (folder != null) TextButton(onClick = {
                        activity.settings.setFolderFor(system.id, null); version++; onRescan()
                    }) { Text("Remove folder") }
                }
                if (openFor == system.id) {
                    fun pick(pkg: String?) {
                        activity.settings.setEmulatorFor(system.id, pkg); openFor = null; version++
                    }
                    TextButton(onClick = { pick(null) }) { Text("Automatic (best installed emulator)") }
                    installed.filter { system.id in it.systems }.forEach { emu ->
                        TextButton(onClick = { pick(emu.pkg) }) {
                            Text(emu.name + if (emu.pkg == current?.pkg) "  ✓" else "")
                        }
                    }
                    if (!showAllApps) TextButton(onClick = { showAllApps = true }) { Text("Show all apps…") }
                    else allApps.forEach { app ->
                        TextButton(onClick = { pick(app.pkg) }) {
                            Text(app.label + if (app.pkg == current?.pkg) "  ✓" else "")
                        }
                    }
                }
            }
        }
    }
}

/** How game names look: tidy title by default, with switches to add details back. */
@Composable
private fun GameNamesSection(activity: MainActivity) {
    val st = activity.settings
    Text("Game names", color = Color.White, fontWeight = FontWeight.Bold)
    val example = "Tetris (NINTENDO) 1989 USA V1.1 (Proto).gb"
    Text("Example: $example  →  ${GameNames.label(example, example.substringBeforeLast('.'))}",
        color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
    // Six switches, linked: "entire file name" means all five details are on. Turning it on
    // turns them all on; turning any detail off turns it off (and all five on turns it on).
    val details = listOf(
        "nameRegion" to "Show country / region (US, EU, JP…)",
        "nameYear" to "Show year",
        "namePublisher" to "Show developer / publisher",
        "nameExtension" to "Show file extension (.gba, .zip…)",
        "nameTags" to "Show extra tags (Prototype, Beta, version / rev numbers)",
    )
    fun get(key: String): Boolean = when (key) {
        "nameFull" -> GameNames.showFullName
        "nameRegion" -> GameNames.showRegion
        "nameYear" -> GameNames.showYear
        "namePublisher" -> GameNames.showPublisher
        "nameExtension" -> GameNames.showExtension
        else -> GameNames.showTags
    }
    fun put(key: String, on: Boolean) {
        when (key) {
            "nameFull" -> GameNames.showFullName = on
            "nameRegion" -> GameNames.showRegion = on
            "nameYear" -> GameNames.showYear = on
            "namePublisher" -> GameNames.showPublisher = on
            "nameExtension" -> GameNames.showExtension = on
            else -> GameNames.showTags = on
        }
        st.setNameFlag(key, on)
    }
    fun toggle(key: String, on: Boolean) {
        put(key, on)
        if (key == "nameFull") {
            if (on) details.forEach { put(it.first, true) }
        } else if (!on) put("nameFull", false)
        else if (details.all { get(it.first) }) put("nameFull", true)
    }
    @Composable
    fun option(key: String, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f))
            Switch(get(key), { toggle(key, it) })
        }
    }
    Text("These are for every system. To give one system its own, press Start on it on the home screen → Game names.",
        color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
    option("nameFull", "Show the entire file name")
    details.forEach { (key, label) -> option(key, label) }
}

/**
 * A little phone showing what the home screen grid will look like: the icon area on top,
 * the black strip where the Taco sits, and circles sized the same way the real screen sizes them.
 */
@Composable
private fun GridPreview(cols: Int, rows: Int, covered: Float) {
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val screenW = config.screenWidthDp.toFloat().coerceAtLeast(1f)
    val screenH = config.screenHeightDp.toFloat().coerceAtLeast(1f)
    val previewW = 86.dp
    val previewH = previewW * (screenH / screenW)
    val colors = listOf(Color(0xFF7B5CFF), Color(0xFF34C3FF), Color(0xFFFF5C8A), Color(0xFF4CFF9A),
        Color(0xFFFFC34C), Color(0xFFB45CFF))
    androidx.compose.foundation.Canvas(
        Modifier.width(previewW).height(previewH)
            .clip(RoundedCornerShape(10.dp)).background(Color(0xFF15122E))
            .border(2.dp, Color(0xFF666666), RoundedCornerShape(10.dp))
    ) {
        val scale = size.width / screenW          // pixels per real dp
        val topH = size.height * (1f - covered)
        // Same sizing as the home screen: header ~150dp, 8dp side padding, ~50dp label room.
        val header = 150f + 76f   // logo + title, then the shortcut strip
        // The strip of six small shortcut icons above the grid.
        for (k in 0 until 6) {
            drawCircle(Color(0x99FFFFFF), radius = 20f * scale,
                center = androidx.compose.ui.geometry.Offset((8f + (screenW - 16f) / 6 * (k + 0.5f)) * scale, (150f + 30f) * scale))
        }
        val gridW = screenW - 16f
        val gridH = (screenH * (1f - covered) - header).coerceAtLeast(60f)
        val cellW = gridW / cols
        val cellH = gridH / rows
        val icon = minOf(cellW - 14f, cellH - 50f).coerceIn(28f, 170f)
        val rowH = icon + 8f + 20f + 22f   // icon + ring + padding + label, like the real one
        var i = 0
        var y = header + 10f
        while (y * scale < topH) {
            for (c in 0 until cols) {
                val cx = (8f + cellW * c + cellW / 2) * scale
                val cy = (y + icon / 2) * scale
                drawCircle(colors[i++ % colors.size], radius = icon / 2 * scale,
                    center = androidx.compose.ui.geometry.Offset(cx, cy))
                drawRect(Color(0x66FFFFFF), topLeft = androidx.compose.ui.geometry.Offset(cx - icon / 3 * scale, cy + (icon / 2 + 10f) * scale),
                    size = androidx.compose.ui.geometry.Size(icon * 2 / 3 * scale, 4f * scale))
            }
            y += rowH
        }
        // The Taco strip, drawn last so icons that would scroll off are hidden under it.
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(0f, topH),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - topH))
        // Header bar (logo + title), so the preview reads like the real screen.
        drawRect(Color(0x33FFFFFF), topLeft = androidx.compose.ui.geometry.Offset(20f * scale, 50f * scale),
            size = androidx.compose.ui.geometry.Size(size.width * 0.5f, 10f * scale))
    }
}


/**
 * Taco mode or full screen ("Taco off"). The Taco still works as a Bluetooth controller when it's
 * unclipped, and so does any other controller, so full screen lets games use the whole screen and
 * turn sideways. Taco mode stays the default.
 */
@Composable
private fun ScreenModeSection(activity: MainActivity) {
    val st = activity.settings
    var mode by remember { mutableStateOf(st.screenMode) }
    var rotation by remember { mutableStateOf(st.fullRotation) }
    var auto by remember { mutableStateOf(st.autoFullSideways) }
    SettingHeader("Screen mode", "Use the Pocket Taco clipped on, or play with it off (or with another controller).")
    SettingChoice("Screen mode", null,
        listOf(
            Triple("taco", "🌮 Taco mode", "Menus and games stay in the part above the Pocket Taco, with the phone upright. " +
                "This is the normal setting."),
            Triple("full", "▭ Full screen (Taco off)", "Nothing is held back for the Taco: menus and games use the whole " +
                "screen and can turn sideways. For playing with the Taco unclipped as a Bluetooth controller, " +
                "or with any other controller. Your Taco settings are kept for when you switch back."),
        ), mode, Applies.NOW,
    ) { mode = it; st.screenMode = it; activity.updateScreenMode() }
    SettingChoice("Which way full screen faces",
        "Only for full screen. This turns Taco Launcher itself; emulators choose for themselves which way a game faces, usually by following the phone's auto-rotate.",
        listOf(
            Triple("sideways", "Sideways", "Widescreen, turned on its side (either way round)."),
            Triple("turn", "Turns with phone", "Follows the way you hold the phone, upright or sideways, even if " +
                "the phone's auto-rotate is off."),
            Triple("upright", "Upright", "Stays upright, using the whole screen top to bottom."),
        ), rotation, Applies.NOW,
    ) { rotation = it; st.fullRotation = it; activity.updateScreenMode() }
    SettingSwitch("Switch to full screen when I turn the phone sideways",
        "Off unless you turn it on. While on Taco mode: hold the phone sideways for a moment and Taco Launcher goes " +
            "full screen, then stand it upright again for Taco mode. The phone can't feel the Taco being unclipped " +
            "(it stays connected by Bluetooth), so turning the phone is the signal. A game that's already running " +
            "keeps its layout; the next game you start uses the new one." +
            if (mode == "full") " (Does nothing right now: Screen mode is already full screen.)" else "",
        auto, Applies.NOW,
    ) { auto = it; st.autoFullSideways = it; activity.updateScreenMode() }
}
