package com.pockettaco.salsa.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettaco.salsa.*

private sealed interface Screen {
    data object Home : Screen
    data class Games(val system: GameSystem) : Screen
    data object Apps : Screen
    data object GetEmulators : Screen
}

/**
 * Console-style browser: glowing bubbles for each system, then a list of
 * game cards. D-pad moves, A picks, B goes back one level.
 */
@Composable
fun GameBrowser(
    activity: MainActivity,
    library: Library,
    scan: ScanState,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit = {},
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var lastSystem by remember { mutableStateOf<GameSystem?>(null) }
    var accent by remember { mutableStateOf(Color(0xFF4B2FD6)) }
    // Checked again whenever you come back to the launcher (say, after installing one).
    val emulators = remember(FocusKick.count.intValue) { installedEmulators(activity) }
    // A phone with no emulators: offer downloads first (once per visit).
    var offeredDownloads by remember { mutableStateOf(false) }
    LaunchedEffect(emulators.isEmpty()) {
        if (emulators.isEmpty() && !offeredDownloads) { offeredDownloads = true; screen = Screen.GetEmulators }
    }
    val tacoName by activity.watcher.deviceName.collectAsStateWithLifecycle()

    DisposableEffect(screen) {
        val handler: () -> Boolean = {
            if (screen != Screen.Home) { screen = Screen.Home; true } else false
        }
        activity.onBackRequest = handler
        onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = null }
    }

    ConsoleBackground(accent, Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            StatusBar(activity, tacoName)
            if (scan.scanning) {
                Text("Looking for games… ${scan.count} found", color = TextDim, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp))
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    color = accent, trackColor = Color.White.copy(alpha = 0.1f),
                )
            }
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = targetState != Screen.Home
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.Home -> HomeBubbles(
                        library, emulators.size, scan, lastSystem,
                        onFocusSystem = { accent = lookFor(it).bottom },
                        onOpenSystem = { lastSystem = it; screen = Screen.Games(it) },
                        onOpenApps = { screen = if (emulators.isEmpty()) Screen.GetEmulators else Screen.Apps },
                        onOpenSettings = onOpenSettings,
                        onPickFolder = onPickFolder,
                    )
                    is Screen.Games -> GamesList(activity, s.system, library[s.system].orEmpty(), emulators,
                        onRescan = onRescan, onGetEmulators = { screen = Screen.GetEmulators }) {
                        screen = Screen.Home
                    }
                    Screen.Apps -> EmulatorList(activity, emulators,
                        onGetMore = { screen = Screen.GetEmulators }) { screen = Screen.Home }
                    Screen.GetEmulators -> GetEmulatorsList(activity, emulators) { screen = Screen.Home }
                }
            }
            HintBar(
                *if (screen == Screen.Home) arrayOf("A" to "Open")
                else arrayOf("A" to "Play", "B" to "Back")
            )
        }
    }
}

@Composable
private fun HomeBubbles(
    library: Library,
    emulatorCount: Int,
    scan: ScanState,
    lastSystem: GameSystem?,
    onFocusSystem: (String) -> Unit,
    onOpenSystem: (GameSystem) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFolder: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var title by remember { mutableStateOf("Games") }
    var subtitle by remember { mutableStateOf("") }
    val systems = library.entries.toList()
    // Land on the system you came back from, or the first one.
    val focusIndex = systems.indexOfFirst { it.key == lastSystem }.coerceAtLeast(0)
    LaunchedEffect(systems.size, FocusKick.count.intValue) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(title, color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextDim, fontSize = 13.sp)
        }
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (systems.isEmpty() && !scan.scanning) item {
                Bubble(SystemLook("+", Color(0xFF4CFF9A), Color(0xFF138A4F)), "Add games",
                    focusRequester = first,
                    onFocused = {
                        title = "Add your games"
                        subtitle = if (scan.hasFolder) "No games found in that folder" else "Pick your games folder once"
                    },
                    onClick = onPickFolder)
            }
            systems.forEachIndexed { i, (system, games) ->
                item(system.id) {
                    Bubble(lookFor(system.id), system.name, badge = "${games.size}",
                        focusRequester = if (i == focusIndex) first else null,
                        onFocused = {
                            title = system.name
                            subtitle = "${games.size} games"
                            onFocusSystem(system.id)
                        },
                        onClick = { onOpenSystem(system) })
                }
            }
            item("emus") {
                Bubble(EMULATORS_LOOK, "Emulators", badge = "$emulatorCount",
                    onFocused = { title = "Emulators"; subtitle = "$emulatorCount installed" },
                    onClick = onOpenApps)
            }
            item("settings") {
                Bubble(SETTINGS_LOOK, "Settings",
                    onFocused = { title = "Settings"; subtitle = "Screen size, games folder, controller" },
                    onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun GamesList(
    activity: MainActivity,
    system: GameSystem,
    games: List<Game>,
    emulators: List<Emulator>,
    onRescan: () -> Unit,
    onGetEmulators: () -> Unit,
    onBack: () -> Unit,
) {
    val look = lookFor(system.id)
    var systemFolder by remember { mutableStateOf(activity.settings.folderFor(system.id)) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.settings.setFolderFor(system.id, uri)
            systemFolder = uri
            onRescan()
        }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue) { runCatching { first.requestFocus() } }
    val choices = remember(emulators) { emulatorChoices(activity, system, emulators) }
    var current by remember(choices) { mutableStateOf(currentEmulator(activity, system, emulators)) }
    var askSetup by remember { mutableStateOf<Game?>(null) }

    // While the prompt is up it replaces the list, so the D-pad can't wander behind it.
    if (askSetup == null) Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(system.name, color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${games.size} games · ${current?.name ?: "no emulator yet"}", color = TextDim, fontSize = 13.sp)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            item("back") { GameCard("Back", "or press B", SETTINGS_LOOK, tile = "←", onClick = onBack) }
            item("emu") {
                if (choices.isEmpty()) {
                    GameCard("Get an emulator", "RetroArch recommended: free, plays almost everything",
                        EMULATORS_LOOK, tile = "↓", onClick = onGetEmulators)
                } else GameCard(
                    "Open ${current?.name}",
                    "Games below play with it",
                    EMULATORS_LOOK, tile = "▶",
                ) {
                    current?.let { openAppFitted(activity, it.pkg) }
                }
            }
            if (choices.size > 1) item("switch") {
                GameCard("Switch emulator", "Now: ${current?.name}", EMULATORS_LOOK, tile = "⇄") {
                    val next = choices[(choices.indexOf(current) + 1) % choices.size]
                    current = next
                    activity.settings.setEmulatorFor(system.id, next.pkg)
                }
            }
            item("folder") {
                GameCard(
                    if (systemFolder == null) "Add a folder for ${system.name}" else "Folder: ${folderLabel(systemFolder)}",
                    if (systemFolder == null) "Games also come from your main folder" else "Press A to pick a different folder",
                    SETTINGS_LOOK, tile = "📁",
                ) { folderPicker.launch(null) }
            }
            items(games, key = { it.uri.toString() }) { game ->
                GameCard(game.title, look = look,
                    focusRequester = if (game == games.first()) first else null) {
                    val emu = current
                    if (emu != null && isRetroArch(emu) && !activity.settings.retroArchPadReady) {
                        askSetup = game   // ask first; they can still play anyway
                    } else launchGame(activity, system, emu, choices, game)
                }
            }
        }
    }
    askSetup?.let { game ->
        RetroArchSetupPrompt(
            activity,
            onSetUp = { current?.let { openAppFitted(activity, it.pkg) }; askSetup = null },
            onPlay = { askSetup = null; launchGame(activity, system, current, choices, game) },
            onDone = {
                activity.settings.retroArchPadReady = true
                askSetup = null
                launchGame(activity, system, current, choices, game)
            },
            onCancel = { askSetup = null },
        )
    }
}

private fun isRetroArch(e: Emulator) = e.pkg.startsWith("com.retroarch") || "retroarch" in e.name.lowercase()

private fun launchGame(activity: MainActivity, system: GameSystem, emu: Emulator?, choices: List<Emulator>, game: Game) {
    if (emu == null) {
        runCatching { activity.startActivity(openWithAnyApp(game.uri)) }
        return
    }
    // Try the chosen emulator, then any RetroArch, and only then ask Android.
    val tries = listOf(emu) + choices.filter { it != emu && isRetroArch(it) }
    val worked = tries.firstOrNull { e ->
        runCatching { startFitted(activity, launchIntent(activity, e, system, game.uri), system) }.isSuccess
    }
    if (worked == null) {
        Toast.makeText(activity, "${emu.name} couldn't open that game. Pick another app.", Toast.LENGTH_LONG).show()
        runCatching { activity.startActivity(openWithAnyApp(game.uri)) }
    } else if (worked != emu) {
        Toast.makeText(activity, "Opened with ${worked.name}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shown over the game list before a RetroArch game when the Taco hasn't been set up in
 * RetroArch yet. Taco Launcher can't look inside RetroArch's files, so it asks.
 */
@Composable
private fun RetroArchSetupPrompt(
    activity: MainActivity,
    onSetUp: () -> Unit,
    onPlay: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue) { runCatching { first.requestFocus() } }
    // B closes the prompt; give back whatever B did before.
    DisposableEffect(Unit) {
        val previous = activity.onBackRequest
        val handler: () -> Boolean = { onCancel(); true }
        activity.onBackRequest = handler
        onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = previous }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
        Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
            Text("Set up your Taco in RetroArch?", color = TextBright, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "RetroArch doesn't know the Pocket Taco's buttons yet. In RetroArch: Settings → Input → " +
                    "Port 1 Controls → Set All Controls, then Save Controller Profile. You only do this once.",
                color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                item { GameCard("Set up now", "Opens RetroArch's menu", EMULATORS_LOOK, tile = "⚙",
                    focusRequester = first, onClick = onSetUp) }
                item { GameCard("Play anyway", "Ask again next time", SETTINGS_LOOK, tile = "▶", onClick = onPlay) }
                item { GameCard("Already set up, don't ask again", null, SETTINGS_LOOK, tile = "✓", onClick = onDone) }
            }
        }
    }
}

@Composable
private fun EmulatorList(activity: MainActivity, emulators: List<Emulator>, onGetMore: () -> Unit, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text("Emulators", color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("${emulators.size} installed", color = TextDim, fontSize = 13.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            item("back") { GameCard("Back", "or press B", SETTINGS_LOOK, tile = "←", onClick = onBack) }
            items(emulators, key = { it.pkg }) { emu ->
                // Which systems use this emulator right now.
                val usedFor = SYSTEMS.filter { currentEmulator(activity, it, emulators)?.pkg == emu.pkg }
                GameCard(emu.name,
                    if (usedFor.isEmpty()) "Not used for any system yet" else "Plays: " + usedFor.joinToString(" · ") { lookFor(it.id).short },
                    EMULATORS_LOOK,
                    focusRequester = if (emu == emulators.first()) first else null) {
                    openAppFitted(activity, emu.pkg)
                }
            }
            item("more") { GameCard("Get more emulators", "Free downloads", EMULATORS_LOOK, tile = "↓", onClick = onGetMore) }
        }
    }
}

/** Download suggestions, RetroArch first. Shown on its own when the phone has no emulators. */
@Composable
private fun GetEmulatorsList(activity: MainActivity, emulators: List<Emulator>, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue) { runCatching { first.requestFocus() } }
    val have = emulators.map { it.pkg }.toSet()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(if (emulators.isEmpty()) "No emulators yet" else "Get emulators",
                color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Games need an emulator app. Start with RetroArch.", color = TextDim, fontSize = 13.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            SUGGESTED_EMULATORS.forEachIndexed { i, s ->
                item(s.pkg) {
                    GameCard(s.name, if (s.pkg in have) "Installed" else s.why, EMULATORS_LOOK,
                        tile = if (s.pkg in have) "✓" else "↓",
                        focusRequester = if (i == 0) first else null) { openStorePage(activity, s.pkg) }
                }
            }
            item("back") { GameCard("Back", "or press B", SETTINGS_LOOK, tile = "←", onClick = onBack) }
        }
    }
}
