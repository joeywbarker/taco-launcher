package com.pockettaco.salsa.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockettaco.salsa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What's open on top of the home screen. */
private sealed interface Panel {
    data class Menu(val id: String?) : Panel
    data class Move(val id: String) : Panel
    data class Rename(val id: String) : Panel
    data class Colors(val id: String) : Panel
    data class Delete(val id: String, val step: Int) : Panel
}

/**
 * The home screen: systems as big icons, small icons or a list (Select switches).
 * Hold a system (or press Start on it) for its menu: move, rename, picture, colour,
 * hide or delete.
 */
@Composable
fun HomeScreen(
    activity: MainActivity,
    library: Library,
    emulatorCount: Int,
    scan: ScanState,
    lastSystem: GameSystem?,
    onFocusSystem: (String) -> Unit,
    onOpenSystem: (GameSystem) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
) {
    val view = Custom.homeView
    val systems = arrange(library.entries.map { it.key to it.value })
    val visibleIds = systems.map { it.first.id }
    var focusedId by remember { mutableStateOf<String?>(lastSystem?.id) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var title by remember { mutableStateOf("Games") }
    var subtitle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var pictureFor by remember { mutableStateOf<String?>(null) }
    val picturePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = pictureFor ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                savePicture(activity, uri, Custom.iconFile(id), maxSide = 256, square = true)
            }
            if (error != null) Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
            Custom.changed()
        }
    }

    // Start = menu for the selected system, Select = next view.
    DisposableEffect(Unit) {
        val start: () -> Boolean = { if (panel == null) panel = Panel.Menu(focusedId?.takeIf { it in visibleIds }); true }
        val select: () -> Boolean = { if (panel == null) Custom.nextView(); true }
        activity.onStartButton = start
        activity.onSelectButton = select
        onDispose {
            if (activity.onStartButton === start) activity.onStartButton = null
            if (activity.onSelectButton === select) activity.onSelectButton = null
        }
    }
    // While a panel is open, B closes it (and gives back whatever B did before).
    if (panel != null) DisposableEffect(Unit) {
        val previous = activity.onBackRequest
        val handler: () -> Boolean = { panel = null; true }
        activity.onBackRequest = handler
        onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = previous }
    }

    Column(Modifier.fillMaxSize()) {
        Brand()
        when (val p = panel) {
            null -> {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    Text(title, color = TextBright, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = TextDim, fontSize = 13.sp)
                }
                SystemsView(
                    view, systems, emulatorCount, scan, focusedId,
                    onFocused = { id, t, s ->
                        focusedId = id; title = t; subtitle = s
                        if (id != null && id in visibleIds) onFocusSystem(id)
                    },
                    onOpenSystem = onOpenSystem,
                    onMenu = { id -> panel = Panel.Menu(id) },
                    onOpenApps = onOpenApps,
                    onOpenSettings = onOpenSettings,
                    onPickFolder = onPickFolder,
                )
            }
            is Panel.Menu -> SystemMenu(
                p.id?.let { id -> SYSTEMS.firstOrNull { it.id == id } },
                gameCount = p.id?.let { id -> systems.firstOrNull { it.first.id == id }?.second?.size } ?: 0,
                hasPicture = p.id?.let { Custom.iconFile(it).exists() } == true,
                onChoose = { action ->
                    val id = p.id
                    panel = null
                    when (action) {
                        "move" -> if (id != null) panel = Panel.Move(id)
                        "rename" -> if (id != null) panel = Panel.Rename(id)
                        "picture" -> if (id != null) { pictureFor = id; picturePicker.launch("image/*") }
                        "nopicture" -> if (id != null) Custom.removeIcon(id)
                        "color" -> if (id != null) panel = Panel.Colors(id)
                        "hide" -> if (id != null) Custom.hidden = Custom.hidden + id
                        "delete" -> if (id != null) panel = Panel.Delete(id, 1)
                        "view" -> Custom.nextView()
                        "az" -> Custom.order = systems.sortedBy { systemName(it.first).lowercase() }.map { it.first.id }
                        "count" -> Custom.order = systems.sortedByDescending { it.second.size }.map { it.first.id }
                        "reset" -> Custom.order = emptyList()
                        "unhide" -> Custom.hidden = emptySet()
                    }
                },
            )
            is Panel.Move -> MovePanel(p.id, systems) { panel = null }
            is Panel.Rename -> RenamePanel(p.id) { panel = null }
            is Panel.Colors -> ColorPanel(p.id) { panel = null }
            is Panel.Delete -> {
                val system = SYSTEMS.first { it.id == p.id }
                val games = library[system].orEmpty()
                DeletePanel(system, games.size, p.step,
                    onYes = {
                        if (p.step == 1) panel = Panel.Delete(p.id, 2)
                        else {
                            panel = null
                            scope.launch {
                                val (ok, failed) = withContext(Dispatchers.IO) { deleteGames(activity, games) }
                                val msg = if (failed == 0) "Deleted $ok games from ${systemName(system)}."
                                else "Deleted $ok, couldn't delete $failed. If none were deleted, pick your games " +
                                    "folder again in Settings so Android allows deleting."
                                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                                onRescan()
                            }
                        }
                    },
                    onNo = { panel = null })
            }
        }
    }
}

/** "TACO LAUNCHER" across the top of the home screen. */
@Composable
private fun Brand() {
    Text("🌮  TACO LAUNCHER",
        color = Color(0xFFFFD25A), fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
}

@Composable
private fun SystemsView(
    view: HomeView,
    systems: List<Pair<GameSystem, List<Game>>>,
    emulatorCount: Int,
    scan: ScanState,
    focusedId: String?,
    onFocused: (id: String?, title: String, subtitle: String) -> Unit,
    onOpenSystem: (GameSystem) -> Unit,
    onMenu: (String?) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFolder: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val focusIndex = systems.indexOfFirst { it.first.id == focusedId }.coerceAtLeast(0)
    LaunchedEffect(systems.size, view, FocusKick.count.intValue) { runCatching { first.requestFocus() } }
    val empty = systems.isEmpty() && !scan.scanning
    val addTitle = "Add your games"
    val addSub = if (scan.hasFolder) "No games found in that folder" else "Pick your games folder once"

    if (view == HomeView.LIST) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
            if (empty) item {
                GameCard(addTitle, addSub, SystemLook("+", Color(0xFF4CFF9A), Color(0xFF138A4F)), tile = "+",
                    focusRequester = first, onFocused = { onFocused(null, addTitle, addSub) }, onClick = onPickFolder)
            }
            itemsIndexed(systems, key = { _, s -> s.first.id }) { i, (system, games) ->
                val name = systemName(system)
                GameCard(name, "${games.size} games", lookFor(system.id), tile = lookFor(system.id).short,
                    focusRequester = if (i == focusIndex) first else null,
                    image = Custom.icon(system.id),
                    onFocused = { onFocused(system.id, name, "${games.size} games") },
                    onLongClick = { onMenu(system.id) },
                    onClick = { onOpenSystem(system) })
            }
            item("emus") {
                GameCard("Emulators", "$emulatorCount installed", EMULATORS_LOOK, tile = "EMU",
                    onFocused = { onFocused(null, "Emulators", "$emulatorCount installed") }, onClick = onOpenApps)
            }
            item("settings") {
                GameCard("Settings", "Screen size, games folder, controller, look", SETTINGS_LOOK, tile = "⚙",
                    onFocused = { onFocused(null, "Settings", "Screen size, games folder, controller, look") },
                    onClick = onOpenSettings)
            }
        }
        return
    }

    val small = view == HomeView.SMALL
    LazyVerticalGrid(
        GridCells.Fixed(if (small) 5 else 3),
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val size = if (small) 52.dp else 76.dp
        if (empty) item {
            Bubble(SystemLook("+", Color(0xFF4CFF9A), Color(0xFF138A4F)), "Add games", size = size,
                focusRequester = first, onFocused = { onFocused(null, addTitle, addSub) }, onClick = onPickFolder)
        }
        systems.forEachIndexed { i, (system, games) ->
            item(system.id) {
                val name = systemName(system)
                Bubble(lookFor(system.id), name, badge = "${games.size}", size = size,
                    focusRequester = if (i == focusIndex) first else null,
                    image = Custom.icon(system.id),
                    onFocused = { onFocused(system.id, name, "${games.size} games") },
                    onLongClick = { onMenu(system.id) },
                    onClick = { onOpenSystem(system) })
            }
        }
        item("emus") {
            Bubble(EMULATORS_LOOK, "Emulators", badge = "$emulatorCount", size = size,
                onFocused = { onFocused(null, "Emulators", "$emulatorCount installed") }, onClick = onOpenApps)
        }
        item("settings") {
            Bubble(SETTINGS_LOOK, "Settings", size = size,
                onFocused = { onFocused(null, "Settings", "Screen size, games folder, controller, look") },
                onClick = onOpenSettings)
        }
    }
}

/** The list of choices for a system (or the whole home screen). */
@Composable
private fun SystemMenu(system: GameSystem?, gameCount: Int, hasPicture: Boolean, onChoose: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue) { runCatching { first.requestFocus() } }
    val look = system?.let { lookFor(it.id) } ?: SETTINGS_LOOK
    val options = buildList {
        if (system != null) {
            add(Triple("move", "Move", "Change where it sits"))
            add(Triple("rename", "Rename", "Now: ${systemName(system)}"))
            add(Triple("picture", "Change picture", "Use any image; it's resized for you"))
            if (hasPicture) add(Triple("nopicture", "Remove picture", "Back to the text icon"))
            add(Triple("color", "Change color", null))
            add(Triple("hide", "Hide from home", "Games stay on your phone. Unhide in Settings."))
            add(Triple("delete", "Delete system and games…", "$gameCount games. Asks twice."))
        }
        add(Triple("view", "Switch view", "Now: ${Custom.homeView.label} (or press Select)"))
        add(Triple("az", "Sort A to Z", null))
        add(Triple("count", "Sort by number of games", null))
        add(Triple("reset", "Usual order", null))
        if (Custom.hidden.isNotEmpty()) add(Triple("unhide", "Show hidden systems", "${Custom.hidden.size} hidden"))
    }
    PanelList(if (system != null) systemName(system) else "Home screen", "B to close") {
        options.forEachIndexed { i, (key, label, sub) ->
            item(key) {
                GameCard(label, sub, if (key == "delete") DANGER_LOOK else look, tile = label.first().toString(),
                    focusRequester = if (i == 0) first else null) { onChoose(key) }
            }
        }
    }
}

/** Move a system: press Earlier/Later, or tap where it should go. */
@Composable
private fun MovePanel(id: String, systems: List<Pair<GameSystem, List<Game>>>, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val ids = systems.map { it.first.id }
    PanelList("Move ${systemName(SYSTEMS.first { it.id == id })}", "Press Earlier or Later, or tap a spot") {
        item("earlier") { GameCard("◀  Earlier", null, SETTINGS_LOOK, tile = "◀", focusRequester = first) { moveSystem(id, -1, ids) } }
        item("later") { GameCard("Later  ▶", null, SETTINGS_LOOK, tile = "▶") { moveSystem(id, 1, ids) } }
        item("done") { GameCard("Done", null, SETTINGS_LOOK, tile = "✓", onClick = onDone) }
        items(systems, key = { it.first.id }) { (system, _) ->
            GameCard(systemName(system), if (system.id == id) "Moving this one" else "Tap to put it here",
                lookFor(system.id), tile = lookFor(system.id).short, image = Custom.icon(system.id),
                highlight = system.id == id) { if (system.id != id) placeSystem(id, system.id, ids) }
        }
    }
}

@Composable
private fun RenamePanel(id: String, onDone: () -> Unit) {
    val system = SYSTEMS.first { it.id == id }
    var text by remember { mutableStateOf(systemName(system)) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    PanelList("Rename", "Original name: ${system.name}") {
        item("field") {
            OutlinedTextField(text, { text = it.take(40) }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextBright, unfocusedTextColor = TextBright),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(field))
        }
        item("save") { GameCard("Save", null, lookFor(id), tile = "✓") { Custom.setName(id, text); onDone() } }
        item("default") { GameCard("Use original name", system.name, SETTINGS_LOOK, tile = "↺") { Custom.setName(id, null); onDone() } }
        item("cancel") { GameCard("Cancel", null, SETTINGS_LOOK, tile = "✕", onClick = onDone) }
    }
}

@Composable
private fun ColorPanel(id: String, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val short = lookFor(id).short
    PanelList("Change color", "for ${systemName(SYSTEMS.first { it.id == id })}") {
        item("default") {
            GameCard("Original colors", null, SYSTEM_LOOKS[id] ?: SETTINGS_LOOK, tile = short, focusRequester = first) {
                Custom.setColor(id, -1); onDone()
            }
        }
        ICON_COLORS.forEachIndexed { i, (name, colors) ->
            item(name) {
                GameCard(name, null, SystemLook(short, colors.first, colors.second), tile = short) {
                    Custom.setColor(id, i); onDone()
                }
            }
        }
    }
}

@Composable
private fun DeletePanel(system: GameSystem, count: Int, step: Int, onYes: () -> Unit, onNo: () -> Unit) {
    val no = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { no.requestFocus() } }
    val name = systemName(system)
    PanelList(
        if (step == 1) "Delete $name?" else "Are you really sure?",
        if (step == 1) "This deletes all $count $name games from your phone."
        else "All $count games will be gone for good. This can't be undone.",
    ) {
        // "No" comes first and starts selected, so a stray A press never deletes anything.
        item("no") { GameCard("No, keep them", null, SETTINGS_LOOK, tile = "✕", focusRequester = no, onClick = onNo) }
        item("yes") {
            GameCard(if (step == 1) "Yes, delete…" else "Yes, delete all $count games", null, DANGER_LOOK,
                tile = "!", onClick = onYes)
        }
    }
}

@Composable
private fun PanelList(title: String, subtitle: String?, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)))) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
            Text(title, color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = TextDim, fontSize = 13.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp), content = content)
    }
}

private val DANGER_LOOK = SystemLook("!", Color(0xFFFF6B6B), Color(0xFF8A1020))
