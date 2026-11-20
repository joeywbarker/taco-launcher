package com.pockettaco.salsa.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var lastSystem by remember { mutableStateOf<GameSystem?>(null) }
    var accent by remember { mutableStateOf(Color(0xFF4B2FD6)) }
    val emulators = remember { installedEmulators(activity) }
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
                        onOpenApps = { screen = Screen.Apps },
                        onOpenSettings = onOpenSettings,
                        onPickFolder = onPickFolder,
                    )
                    is Screen.Games -> GamesList(activity, s.system, library[s.system].orEmpty(), emulators) {
                        screen = Screen.Home
                    }
                    Screen.Apps -> EmulatorList(activity, emulators) { screen = Screen.Home }
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
    onBack: () -> Unit,
) {
    val look = lookFor(system.id)
    val first = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue) { runCatching { first.requestFocus() } }
    val choices = emulators.filter { system.id in it.systems }
    var current by remember {
        mutableStateOf(choices.firstOrNull { it.pkg == activity.settings.emulatorFor(system.id) } ?: choices.firstOrNull())
    }

    Column(Modifier.fillMaxSize()) {
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
                    GameCard("Get RetroArch", "Free, plays every system", EMULATORS_LOOK, tile = "↓") {
                        runCatching { activity.startActivity(getRetroArchIntent()) }
                    }
                } else GameCard(
                    "Play with ${current?.name}",
                    if (choices.size > 1) "Press A to switch emulator" else null,
                    EMULATORS_LOOK, tile = "▶",
                ) {
                    val next = choices[(choices.indexOf(current) + 1) % choices.size]
                    current = next
                    activity.settings.setEmulatorFor(system.id, next.pkg)
                }
            }
            items(games, key = { it.uri.toString() }) { game ->
                GameCard(game.title, look = look,
                    focusRequester = if (game == games.first()) first else null) {
                    val emu = current
                    if (emu == null) runCatching {
                        activity.startActivity(openWithAnyApp(game.uri))
                    } else {
                        // Try the chosen emulator, then any RetroArch, and only then ask Android.
                        val tries = listOf(emu) + choices.filter {
                            it != emu && (it.pkg.startsWith("com.retroarch") || "retroarch" in it.name.lowercase())
                        }
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
                }
            }
        }
    }
}

@Composable
private fun EmulatorList(activity: MainActivity, emulators: List<Emulator>, onBack: () -> Unit) {
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
                GameCard(emu.name, emu.systems.joinToString(" · ") { lookFor(it).short }, EMULATORS_LOOK,
                    focusRequester = if (emu == emulators.first()) first else null) {
                    openAppFitted(activity, emu.pkg)
                }
            }
        }
    }
}
