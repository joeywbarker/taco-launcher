package com.pockettaco.salsa.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pockettaco.salsa.*

private sealed interface Screen {
    data object Home : Screen
    data class Games(val system: GameSystem) : Screen
    data object Apps : Screen
}

/**
 * Controller-first browser: Systems -> Games -> launch.
 * D-pad moves, A picks, B goes back one level.
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
    val emulators = remember { installedEmulators(activity) }
    val firstItem = remember(screen) { FocusRequester() }

    DisposableEffect(screen) {
        val handler: () -> Boolean = {
            if (screen != Screen.Home) { screen = Screen.Home; true } else false
        }
        activity.onBackRequest = handler
        onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = null }
    }
    // Put the highlight on the first item so the controller works straight away.
    LaunchedEffect(screen, library) { runCatching { firstItem.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Text(
            when (val s = screen) {
                Screen.Home -> "Games"
                is Screen.Games -> s.system.name
                Screen.Apps -> "Emulators"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(16.dp),
        )
        if (scan.scanning) {
            Text(
                "Looking for games… ${scan.count} found",
                color = Color(0xFFAAAAAA),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            when (val s = screen) {
                Screen.Home -> {
                    if (library.isEmpty() && !scan.scanning) item {
                        FocusRow(
                            "Choose your games folder",
                            if (scan.hasFolder) "No games found in that folder" else "You only need to do this once",
                            firstItem, onPickFolder,
                        )
                    }
                    library.entries.forEachIndexed { i, (system, games) ->
                        item(system.id) {
                            FocusRow(system.name, "${games.size} games",
                                if (i == 0) firstItem else null) { screen = Screen.Games(system) }
                        }
                    }
                    item("emus") {
                        FocusRow("Emulators", "${emulators.size} installed") { screen = Screen.Apps }
                    }
                    item("settings") { FocusRow("Settings", onClick = onOpenSettings) }
                }
                is Screen.Games -> {
                    item("back") { FocusRow("← Back", "or press B") { screen = Screen.Home } }
                    val choices = emulators.filter { s.system.id in it.systems }
                    val chosen = choices.firstOrNull { it.pkg == activity.settings.emulatorFor(s.system.id) }
                        ?: choices.firstOrNull()
                    item("emu") {
                        var current by remember { mutableStateOf(chosen) }
                        if (choices.isEmpty()) {
                            FocusRow(
                                "Get RetroArch (free, plays every system)",
                                "Until then, Android will ask which app to use",
                            ) { runCatching { activity.startActivity(getRetroArchIntent()) } }
                        } else FocusRow(
                            "Play with: ${current?.name}",
                            if (choices.size > 1) "Press A to switch" else null,
                        ) {
                            val next = choices[(choices.indexOf(current) + 1) % choices.size]
                            current = next
                            activity.settings.setEmulatorFor(s.system.id, next.pkg)
                        }
                    }
                    val games = library[s.system].orEmpty()
                    items(games, key = { it.uri.toString() }) { game ->
                        FocusRow(game.title, focusRequester = if (game == games.first()) firstItem else null) {
                            val emu = emulators.firstOrNull { it.pkg == activity.settings.emulatorFor(s.system.id) }
                                ?: choices.firstOrNull()
                            if (emu == null) runCatching {
                                activity.startActivity(openWithAnyApp(game.uri))
                            } else runCatching {
                                startFitted(activity, launchIntent(activity, emu, s.system, game.uri))
                            }.onFailure {
                                Toast.makeText(activity, "${emu.name} couldn't open that game. Pick another app.", Toast.LENGTH_LONG).show()
                                runCatching { activity.startActivity(openWithAnyApp(game.uri)) }
                            }
                        }
                    }
                }
                Screen.Apps -> {
                    item("back") { FocusRow("← Back", "or press B") { screen = Screen.Home } }
                    emulators.forEachIndexed { i, emu ->
                        item(emu.pkg) {
                            FocusRow(emu.name, focusRequester = if (i == 0) firstItem else null) {
                                openAppFitted(activity, emu.pkg)
                            }
                        }
                    }
                }
            }
        }
    }
}
