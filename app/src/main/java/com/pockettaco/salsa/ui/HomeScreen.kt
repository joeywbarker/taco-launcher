package com.pockettaco.salsa.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
    data class Menu(val id: String?, val spot: String? = id?.let { "sys:$it" }) : Panel
    data class Move(val id: String) : Panel
    data class Rename(val id: String) : Panel
    data class Colors(val id: String) : Panel
    data class Delete(val id: String, val step: Int) : Panel
    data class Emulator(val id: String) : Panel
    data class Core(val id: String) : Panel
    data class Names(val id: String) : Panel
    data class Pictures(val id: String) : Panel
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
    favoriteCount: Int,
    onOpenFavorites: () -> Unit,
    onRandom: () -> Unit,
    lastPlayed: Game?,
    onLastPlayed: () -> Unit,
    recentCount: Int,
    onOpenRecent: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
) {
    val view = Custom.homeView
    val systems = arrange(library.entries.map { it.key to it.value })
    val visibleIds = systems.map { it.first.id }
    // When Taco Launcher opens on a chosen console (Start menu > Always start here), select it.
    var focusedId by remember {
        mutableStateOf<String?>(lastSystem?.id ?: Custom.startSpot?.takeIf { !Custom.startHandled && it.startsWith("sys:") }
            ?.removePrefix("sys:"))
    }
    LaunchedEffect(Unit) { if (Custom.startSpot?.startsWith("sys:") == true) Custom.startHandled = true }
    // What's selected: a console ("sys:<id>") or a top-row button ("quick", "settings", ...).
    var focusedSpot by remember { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var title by remember { mutableStateOf("Games") }
    var subtitle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Start = menu for the selected system, Select = next view.
    DisposableEffect(Unit) {
        val start: () -> Boolean = {
            if (panel == null) {
                val id = focusedId?.takeIf { it in visibleIds }
                panel = Panel.Menu(id, if (id != null) "sys:$id" else focusedSpot)
            }
            true
        }
        val select: () -> Boolean = { if (panel == null && !Custom.viewLocked) Custom.nextView(); true }
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
        // The Taco Launcher name now sits in the status line above (between controller and clock).
        when (val p = panel) {
            null -> {
                // Name and details on one line, to save room (the Taco covers a lot of screen).
                androidx.compose.foundation.layout.BoxWithConstraints(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val tf = fitFont(title, maxWidth * 0.55f, 20f, 12f)
                    val titleW = (title.length * tf * 0.62f).dp
                    val sf = fitFont(subtitle, (maxWidth - titleW - 12.dp).coerceAtLeast(20.dp), 13f, 7f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = TextBright, fontSize = with(density) { tf.dp.toSp() },
                            fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(10.dp))
                        Text(subtitle, color = TextDim, fontSize = with(density) { sf.dp.toSp() },
                            maxLines = 1, softWrap = false)
                    }
                }
                SystemsView(
                    view, systems, emulatorCount, scan, focusedId,
                    onFocused = { id, t, s ->
                        focusedId = id; title = t; subtitle = s
                        focusedSpot = if (id != null) "sys:$id" else shortcutKey(t)
                        if (id != null && id in visibleIds) onFocusSystem(id)
                    },
                    onOpenSystem = onOpenSystem,
                    onMenu = { id -> panel = Panel.Menu(id) },
                    favoriteCount = favoriteCount,
                    onOpenFavorites = onOpenFavorites,
                    onRandom = onRandom,
                    lastPlayed = lastPlayed,
                    onLastPlayed = onLastPlayed,
                    recentCount = recentCount,
                    onOpenRecent = onOpenRecent,
                    onOpenApps = onOpenApps,
                    onOpenSettings = onOpenSettings,
                    onPickFolder = onPickFolder,
                )
            }
            is Panel.Menu -> SystemMenu(
                activity,
                p.id?.let { id -> SYSTEMS.firstOrNull { it.id == id } },
                gameCount = p.id?.let { id -> systems.firstOrNull { it.first.id == id }?.second?.size } ?: 0,
                spot = p.spot,
                onChoose = { action ->
                    val id = p.id
                    panel = null
                    when (action) {
                        "move" -> if (id != null) panel = Panel.Move(id)
                        "rename" -> if (id != null) panel = Panel.Rename(id)
                        "picture" -> if (id != null) panel = Panel.Pictures(id)
                        "color" -> if (id != null) panel = Panel.Colors(id)
                        "emulator" -> if (id != null) panel = Panel.Emulator(id)
                        "core" -> if (id != null) panel = Panel.Core(id)
                        "names" -> if (id != null) panel = Panel.Names(id)
                        "hide" -> if (id != null) Custom.hidden = Custom.hidden + id
                        "delete" -> if (id != null) panel = Panel.Delete(id, 1)
                        "start" -> {
                            Custom.startSpot = p.spot
                            if (p.spot?.startsWith("sys:") != true) Custom.startInList = false
                            Toast.makeText(activity, "Taco Launcher will now start on ${startSpotName(p.spot)}.", Toast.LENGTH_LONG).show()
                        }
                        "nostart" -> { Custom.startSpot = null; Custom.startInList = false
                            Toast.makeText(activity, "Taco Launcher will start where you left off, as usual.", Toast.LENGTH_LONG).show() }
                        "startlist" -> { Custom.startSpot = p.spot; Custom.startInList = true
                            Toast.makeText(activity, "Taco Launcher will now open straight into ${startSpotName(p.spot)}'s games.", Toast.LENGTH_LONG).show() }
                        "nostartlist" -> Custom.startInList = false
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
            is Panel.Emulator -> EmulatorPanel(activity, p.id) { panel = null }
            is Panel.Core -> CorePanel(activity, p.id) { panel = null }
            is Panel.Names -> NamesPanel(activity, p.id) { panel = null }
            is Panel.Pictures -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ConsolePicturesEditor(activity, p.id) { panel = null }
            }
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
    favoriteCount: Int,
    onOpenFavorites: () -> Unit,
    onRandom: () -> Unit,
    lastPlayed: Game?,
    onLastPlayed: () -> Unit,
    recentCount: Int,
    onOpenRecent: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFolder: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val focusIndex = systems.indexOfFirst { it.first.id == focusedId }.coerceAtLeast(0)
    // "Always start here" on a top-row button: that button is selected when Taco Launcher opens,
    // and stays selected (even while games are still being found) until you move.
    val startSpot = Custom.startSpot
    val spotFocus = remember { FocusRequester() }
    var holdSpot by remember {
        mutableStateOf(!Custom.startHandled && startSpot != null && !startSpot.startsWith("sys:"))
    }
    val onFocusedOuter = onFocused
    @Suppress("NAME_SHADOWING")
    val onFocused: (String?, String, String) -> Unit = { id, t, sub ->
        if (holdSpot && (if (id != null) "sys:$id" else shortcutKey(t)) != startSpot) holdSpot = false
        onFocusedOuter(id, t, sub)
    }
    LaunchedEffect(systems.size, view, FocusKick.count.intValue) {
        if (holdSpot) {
            Custom.startHandled = true
            withFrameNanos { }
            runCatching { spotFocus.requestFocus() }
            kotlinx.coroutines.delay(400)
            if (holdSpot) runCatching { spotFocus.requestFocus() }
        } else runCatching { first.requestFocus() }
    }
    val spotPair = if (holdSpot) startSpot to spotFocus else null
    val empty = systems.isEmpty() && !scan.scanning
    val addTitle = "Add your games"
    val addSub = if (scan.hasFolder) "No games found in that folder" else "Pick your games folder once"

    if (view == HomeView.LIST) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
            if (empty) item {
                GameCard(addTitle, addSub, SystemLook("+", Color(0xFF4CFF9A), Color(0xFF138A4F)), tile = "+",
                    focusRequester = first, onFocused = { onFocused(null, addTitle, addSub) }, onClick = onPickFolder)
            }
            // Quick Start: always first, so it's always in the same spot.
            if (systems.isNotEmpty()) item("last") {
                val sub = lastPlayed?.let { "${it.label} · ${systemName(it.system)}" } ?: "Play any game once, then this jumps right back in"
                GameCard(if (lastPlayed != null) "Quick Start: ${lastPlayed.label}" else "Quick Start",
                    lastPlayed?.let { systemName(it.system) } ?: sub,
                    lastPlayed?.let { lookFor(it.system.id) } ?: RECENT_LOOK, tile = "▶",
                    onFocused = { onFocused(null, "▶ Quick Start", sub) }, onClick = onLastPlayed)
            }
            if (lastPlayed != null) item("recent") {
                GameCard("Recently played", "Your last $recentCount different games", RECENT_LOOK, tile = "🕘",
                    onFocused = { onFocused(null, "🕘 Recently played", "Your last $recentCount different games") },
                    onClick = onOpenRecent)
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
            if (systems.isNotEmpty()) {
                item("favorites") {
                    GameCard("Favorites", "$favoriteCount games from every system", FAVORITES_LOOK, tile = "★",
                        onFocused = { onFocused(null, "★ Favorites", "$favoriteCount games from every system") },
                        onClick = onOpenFavorites)
                }
                item("random") {
                    GameCard("Random game", "Picks any game from any system", RANDOM_LOOK, tile = "🎲",
                        onFocused = { onFocused(null, "🎲 Random game", "Picks any game from any system") },
                        onClick = onRandom)
                }
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

    if (view == HomeView.CAROUSEL && !empty) {
        // While Quick Start is selected, the big picture shows the game it will play (its cover,
        // or its console's picture) instead of a console. Down goes to the consoles as before.
        // On any other top-row button, the consoles take turns there (like a showcase) until you
        // go down to them.
        var stripSpot by remember { mutableStateOf<String?>(null) }
        val stripFocused: (String?, String, String) -> Unit = { id, t, sub ->
            if (id == null) stripSpot = shortcutKey(t)
            onFocused(id, t, sub)
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            ShortcutStrip(systems.isNotEmpty(), lastPlayed, recentCount, favoriteCount, emulatorCount, stripFocused,
                onLastPlayed, onOpenRecent, onOpenFavorites, onRandom, onOpenApps, onOpenSettings, spotPair)
            if (systems.isNotEmpty()) Carousel(systems, focusIndex, first, onFocusedOuter, onOpenSystem, onMenu,
                quickGame = if (stripSpot == "quick") lastPlayed else null, onQuickGame = onLastPlayed,
                showcase = stripSpot != null && !(stripSpot == "quick" && lastPlayed != null),
                onRealFocus = { holdSpot = false; stripSpot = null })
        }
        return
    }

    // Columns and rows come from Settings; the icons grow or shrink so that many fit.
    val cols = Custom.gridColumns(view)
    val rows = Custom.gridRows(view)
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
    // Shortcuts get their own strip of small icons along the top; the systems fill the grid below.
    ShortcutStrip(systems.isNotEmpty(), lastPlayed, recentCount, favoriteCount, emulatorCount, onFocused,
        onLastPlayed, onOpenRecent, onOpenFavorites, onRandom, onOpenApps, onOpenSettings, spotPair)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
    // Everything fits on one screen, never scrolls: with more systems than the chosen
    // columns x rows there are more rows (and, if icons would get too tiny, more columns),
    // and the icons, counts and names all shrink to fit.
    val count = systems.size + (if (empty) 1 else 0)
    val longestBadge = (systems.maxOfOrNull { it.second.size } ?: 0).toString()
    val fit = remember(maxWidth, maxHeight, cols, rows, count, longestBadge) {
        gridFit(maxWidth, maxHeight, cols, rows, count, longestBadge)
    }
    val effCols = fit.cols
    val size = fit.size
    val vPad = fit.vPad
    val labelSize = fit.labelSize
    val labelW = fit.labelW
    // Touch: press and hold a system, then drag it; the others shuffle out of the way.
    // Hold without moving opens its menu (Start does the same with the controller).
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragAbs by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var dragGrab by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val ids by rememberUpdatedState(systems.map { it.first.id })
    val menu by rememberUpdatedState(onMenu)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun itemOffset(key: String) = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        ?.let { androidx.compose.ui.geometry.Offset(it.offset.x.toFloat(), it.offset.y.toFloat()) }
    fun dragModifier(id: String) = Modifier
        .zIndex(if (dragId == id) 1f else 0f)
        .pointerInput(id) {
            awaitEachGesture {
                val pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
                // A tap, or a move before the hold time (scrolling), is left to the normal handlers.
                val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val c = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (!c.pressed) return@withTimeoutOrNull true
                        if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                    }
                    @Suppress("UNREACHABLE_CODE") true
                }
                if (early != null) return@awaitEachGesture
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                val start = itemOffset(id) ?: androidx.compose.ui.geometry.Offset.Zero
                dragId = id; dragGrab = down.position; dragAbs = start + down.position
                var moved = 0f
                while (true) {
                    val event = awaitPointerEvent(pass)
                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = c.position - c.previousPosition
                    c.consume()
                    if (!c.pressed) break
                    dragAbs += delta
                    moved += delta.getDistance()
                    val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                        val key = info.key as? String
                        key != null && key != id && key in ids &&
                            dragAbs.x >= info.offset.x && dragAbs.x < info.offset.x + info.size.width &&
                            dragAbs.y >= info.offset.y && dragAbs.y < info.offset.y + info.size.height
                    }
                    if (hit != null) placeSystem(id, hit.key as String, ids)
                }
                dragId = null
                if (moved < viewConfiguration.touchSlop) menu(id)
            }
        }
        .graphicsLayer {
            if (dragId == id) {
                val o = itemOffset(id)
                if (o != null) {
                    translationX = dragAbs.x - o.x - dragGrab.x
                    translationY = dragAbs.y - o.y - dragGrab.y
                }
                scaleX = 1.15f; scaleY = 1.15f; alpha = 0.92f
            }
        }
    LazyVerticalGrid(
        GridCells.Fixed(effCols),
        Modifier.fillMaxSize(),
        state = gridState,
        horizontalArrangement = Arrangement.SpaceEvenly,
        userScrollEnabled = false,
    ) {
        if (empty) item {
            Bubble(SystemLook("+", Color(0xFF4CFF9A), Color(0xFF138A4F)), "Add games", size = size, labelWidth = labelW,
                labelSize = labelSize, vPad = vPad,
                focusRequester = first, onFocused = { onFocused(null, addTitle, addSub) }, onClick = onPickFolder)
        }
        systems.forEachIndexed { i, (system, games) ->
            item(system.id) {
                val name = systemName(system)
                Box(dragModifier(system.id), contentAlignment = Alignment.Center) {
                    Bubble(lookFor(system.id), name, badge = "${games.size}", size = size, labelWidth = labelW,
                        labelSize = labelSize, vPad = vPad,
                        focusRequester = if (i == focusIndex) first else null,
                        image = Custom.icon(system.id),
                        onFocused = { onFocused(system.id, name, "${games.size} games") },
                        // Holding is handled by the drag above (hold = menu, hold and move = drag).
                        onClick = { onOpenSystem(system) })
                }
            }
        }
    }
    }
    }
}

private data class GridFit(val cols: Int, val size: Dp, val vPad: Dp, val labelSize: Float, val labelW: Dp)

/** Works out the biggest icons that let [count] systems all fit in a [w] x [h] space. */
private fun gridFit(w: Dp, h: Dp, cols: Int, rows: Int, count: Int, badge: String): GridFit {
    fun tryCols(c: Int): GridFit {
        val r = maxOf(rows, (count + c - 1) / c)
        val cellW = w / c
        val cellH = h / r
        val vPad = (cellH * 0.06f).coerceIn(2.dp, 10.dp)
        val labelSize = (cellH.value * 0.11f).coerceIn(7f, 12f)
        val labelW = (cellW - 4.dp).coerceAtLeast(10.dp)
        // Shrink from the largest size until the whole bubble (count pill above, name below)
        // fits the cell's height, and the pill fits beside the icon inside the cell's width.
        var size = minOf(cellW - 12.dp, 170.dp)
        while (size > 12.dp) {
            val tall = bubbleHeight(size, labelSize, vPad, badge = true) * 1.04f <= cellH
            val sideRoom = (cellW - size - 8.dp) / 2
            val wide = size <= cellW - 12.dp && badgeOverhang(size, badge) <= sideRoom
            if (tall && wide) break
            size -= 2.dp
        }
        return GridFit(c, size.coerceAtLeast(12.dp), vPad, labelSize, labelW)
    }
    var best = tryCols(cols)
    var c = cols + 1
    // Very many systems on few columns: add columns while that gives bigger icons.
    while (c <= 12 && best.size < 40.dp) {
        val next = tryCols(c)
        if (next.size > best.size) best = next
        c++
    }
    return best
}

/** Quick Start, Recent, Favorites, Random, Emulators and Settings as small icons on one line. */
@Composable
private fun ShortcutStrip(
    hasSystems: Boolean, lastPlayed: Game?, recentCount: Int, favoriteCount: Int, emulatorCount: Int,
    onFocused: (id: String?, title: String, subtitle: String) -> Unit,
    onLastPlayed: () -> Unit, onOpenRecent: () -> Unit, onOpenFavorites: () -> Unit, onRandom: () -> Unit,
    onOpenApps: () -> Unit, onOpenSettings: () -> Unit,
    spotFocus: Pair<String?, FocusRequester>? = null,
) {
    fun fr(key: String) = spotFocus?.takeIf { it.first == key }?.second
    // All shortcuts share the width evenly (no sideways scrolling); names shrink to fit.
    val items = 2 + (if (hasSystems) 3 else 0) + (if (lastPlayed != null) 1 else 0)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth / items
        val badge = maxOf(recentCount, favoriteCount, emulatorCount).toString()
        // The icon leaves room so a count pill never reaches the next icon.
        var s = minOf(w * 0.62f, 40.dp)
        while (s > 16.dp && badgeOverhang(s, badge) > w - s - 8.dp) s -= 1.dp
        val v = 4.dp
        val ls = 10f
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (hasSystems) {
                val sub = lastPlayed?.let { systemName(it.system) } ?: "Play any game once, then this jumps right back in"
                val look = lastPlayed?.let { lookFor(it.system.id) } ?: RECENT_LOOK
                Bubble(SystemLook("▶", look.top, look.bottom), "Quick Start", focusRequester = fr("quick"), size = s, labelWidth = w, labelSize = ls, vPad = v, reserveBadgeRoom = true,
                    onFocused = { onFocused(null, "▶ Quick Start" + (lastPlayed?.let { ": ${it.label}" } ?: ""), sub) },
                    onClick = onLastPlayed)
            }
            if (lastPlayed != null) Bubble(RECENT_LOOK, "Recent", focusRequester = fr("recent"), badge = "$recentCount", size = s, labelWidth = w, labelSize = ls, vPad = v, reserveBadgeRoom = true,
                onFocused = { onFocused(null, "🕘 Recently played", "Your last $recentCount different games") },
                onClick = onOpenRecent)
            if (hasSystems) {
                Bubble(FAVORITES_LOOK, "Favorites", focusRequester = fr("favorites"), badge = "$favoriteCount", size = s, labelWidth = w, labelSize = ls, vPad = v, reserveBadgeRoom = true,
                    onFocused = { onFocused(null, "★ Favorites", "$favoriteCount games from every system") },
                    onClick = onOpenFavorites)
                Bubble(RANDOM_LOOK, "Random", focusRequester = fr("random"), size = s, labelWidth = w, labelSize = ls, vPad = v, reserveBadgeRoom = true,
                    onFocused = { onFocused(null, "🎲 Random game", "Picks any game from any system") },
                    onClick = onRandom)
            }
            Bubble(EMULATORS_LOOK, "Emulators", focusRequester = fr("emulators"), badge = "$emulatorCount", size = s, labelWidth = w, labelSize = ls, vPad = v, reserveBadgeRoom = true,
                onFocused = { onFocused(null, "Emulators", "$emulatorCount installed") }, onClick = onOpenApps)
            Bubble(SETTINGS_LOOK, "Settings", focusRequester = fr("settings"), size = s, labelWidth = w, labelSize = ls, vPad = v, reserveBadgeRoom = true,
                onFocused = { onFocused(null, "Settings", "Screen size, games folder, controller, look") },
                onClick = onOpenSettings)
        }
    }
        // A thin line between the two areas.
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Color.White.copy(alpha = 0.15f)))
}

/** The list of choices for a system (or the whole home screen). */
@Composable
private fun SystemMenu(activity: MainActivity, system: GameSystem?, gameCount: Int, spot: String?, onChoose: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue) { runCatching { first.requestFocus() } }
    val look = system?.let { lookFor(it.id) } ?: SETTINGS_LOOK
    val installed = remember { installedEmulators(activity) }
    val options = buildList {
        if (system != null) {
            val emu = currentEmulator(activity, system, installed)
            add(Triple("emulator", "Emulator", (emu?.name ?: "None installed") +
                if (activity.settings.emulatorFor(system.id) == null && emu != null) " (automatic)" else ""))
            if (emu != null && isRetroArchEmu(emu) && CORE_OPTIONS.containsKey(system.id)) {
                val core = coreFor(activity, system.id)
                add(Triple("core", "RetroArch core", CORE_OPTIONS[system.id]?.firstOrNull { it.first == core }?.second ?: core))
            }
            add(Triple("names", "Game names", if (GameNames.systemFlags.containsKey(system.id)) "Its own settings" else "Same as Settings"))
            add(Triple("move", "Move", "Change where it sits"))
            add(Triple("rename", "Rename", "Now: ${systemName(system)}"))
            Custom.pictures(system.id).let { pics ->
                add(Triple("picture", "Pictures", "${pics.count { it.on }} of ${pics.size} on · add your own, turn off, reorder"))
            }
            add(Triple("color", "Change color", null))
            add(Triple("hide", "Hide from home", "Games stay on your phone. Unhide in Settings."))
            add(Triple("delete", "Delete system and games…", "$gameCount games. Asks twice."))
        }
        if (spot != null) {
            val here = Custom.startSpot == spot
            val name = startSpotName(spot)
            if (!here || (system != null && Custom.startInList)) add(Triple("start", "Always start here",
                "Taco Launcher opens with $name selected"))
            if (system != null) {
                if (here && Custom.startInList) add(Triple("nostartlist", "Stop opening straight into these games",
                    "Opens with $name selected on the home screen instead"))
                else add(Triple("startlist", "Always open this game list",
                    "Taco Launcher opens straight into $name's games. B goes to the home screen."))
            }
            if (here) add(Triple("nostart", "Stop always starting here", "Opens where you left off, as usual"))
        }
        add(Triple("view", "Switch view", "Now: ${Custom.homeView.label}" + if (Custom.viewLocked) " (locked, Select won't change it)" else " (or press Select)"))
        add(Triple("az", "Sort A to Z", null))
        add(Triple("count", "Sort by number of games", null))
        add(Triple("reset", "Usual order", null))
        if (Custom.hidden.isNotEmpty()) add(Triple("unhide", "Show hidden systems", "${Custom.hidden.size} hidden"))
    }
    PanelList(if (system != null) systemName(system) else if (spot != null) startSpotName(spot) else "Home screen", "B to close") {
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

private fun isRetroArchEmu(e: com.pockettaco.salsa.Emulator) =
    e.pkg.startsWith("com.retroarch") || "retroarch" in e.name.lowercase()

/** Which app opens this system's games. */
@Composable
private fun EmulatorPanel(activity: MainActivity, id: String, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    val system = SYSTEMS.first { it.id == id }
    val installed = remember { installedEmulators(activity) }
    val choices = remember { emulatorChoices(activity, system, installed) }
    val chosen = activity.settings.emulatorFor(id)
    val auto = choices.firstOrNull { e -> installed.any { it.pkg == e.pkg && id in it.systems } }
    PanelList("Emulator for ${systemName(system)}", "Which app opens these games") {
        item("auto") {
            GameCard("Automatic" + if (chosen == null) "  ✓" else "",
                auto?.let { "Best installed: ${it.name}" } ?: "No emulator for this system installed",
                SETTINGS_LOOK, tile = "★", focusRequester = first) {
                activity.settings.setEmulatorFor(id, null); onDone()
            }
        }
        items(choices, key = { it.pkg }) { emu ->
            GameCard(emu.name + if (emu.pkg == chosen) "  ✓" else "", null, EMULATORS_LOOK, tile = "▶") {
                activity.settings.setEmulatorFor(id, emu.pkg); onDone()
            }
        }
        if (choices.isEmpty()) item("none") {
            GameCard("No emulator found", "Get one from Emulators on the home screen", SETTINGS_LOOK, tile = "!") { onDone() }
        }
        item("back") { GameCard("Back", null, SETTINGS_LOOK, tile = "←", onClick = onDone) }
    }
}

/** Which RetroArch core plays this system (arcade games often need a different one). */
@Composable
private fun CorePanel(activity: MainActivity, id: String, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    val system = SYSTEMS.first { it.id == id }
    val options = CORE_OPTIONS[id].orEmpty()
    val current = coreFor(activity, id)
    PanelList("RetroArch core for ${systemName(system)}",
        "The core must be downloaded in RetroArch: Main Menu → Online Updater → Core Downloader") {
        options.forEachIndexed { i, (core, name) ->
            item(core) {
                GameCard(name + if (core == current) "  ✓" else "", if (i == 0) "Usual choice" else null,
                    EMULATORS_LOOK, tile = "▶", focusRequester = if (i == 0) first else null) {
                    activity.settings.setCoreFor(id, if (i == 0) null else core); onDone()
                }
            }
        }
        item("back") { GameCard("Back", null, SETTINGS_LOOK, tile = "←", onClick = onDone) }
    }
}

/** This system's own game-name switches, or "same as Settings". */
@Composable
private fun NamesPanel(activity: MainActivity, id: String, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    val system = SYSTEMS.first { it.id == id }
    val own = GameNames.systemFlags[id]
    val flags = own ?: GameNames.mainFlags()
    fun save(f: Set<String>?) {
        if (f == null) GameNames.systemFlags.remove(id) else GameNames.systemFlags[id] = f
        activity.settings.setSystemNameFlags(id, f)
    }
    val options = listOf(
        "nameFull" to "Show the entire file name",
        "nameRegion" to "Show country / region",
        "nameYear" to "Show year",
        "namePublisher" to "Show developer / publisher",
        "nameExtension" to "Show file extension",
        "nameTags" to "Show extra tags (Prototype, Beta, version)",
    )
    PanelList("Game names for ${systemName(system)}", "Press A to switch. B when done.") {
        item("follow") {
            GameCard("Same as Settings" + if (own == null) "  ✓" else "",
                if (own == null) "Following the main Game names switches. Change any switch below to give this system its own."
                else "Press to go back to the main switches",
                SETTINGS_LOOK, tile = "↺", focusRequester = first) { save(null) }
        }
        options.forEach { (key, label) ->
            item(key) {
                val on = key in flags
                GameCard(label, if (on) "On" else "Off", if (on) lookFor(id) else SETTINGS_LOOK,
                    tile = if (on) "✓" else "○") { save(GameNames.toggled(flags, key, !on)) }
            }
        }
        item("done") { GameCard("Done", null, SETTINGS_LOOK, tile = "✓", onClick = onDone) }
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

/**
 * One big system at a time. Left/right (D-pad or swipe) moves to the next system and wraps
 * around at the ends. The dots above are a map: one per system in its colour, the current one
 * highlighted. A (or tap) opens it; Start (or hold) opens its menu.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.Carousel(
    systems: List<Pair<GameSystem, List<Game>>>,
    startIndex: Int,
    focus: FocusRequester,
    onFocused: (id: String?, title: String, subtitle: String) -> Unit,
    onOpenSystem: (GameSystem) -> Unit,
    onMenu: (String?) -> Unit,
    quickGame: Game? = null,
    onQuickGame: () -> Unit = {},
    showcase: Boolean = false,
    onRealFocus: () -> Unit = {},
) {
    val n = systems.size
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, n - 1)) }
    var direction by remember { mutableIntStateOf(1) }
    if (index >= n) index = 0
    fun go(step: Int) { direction = step; index = ((index + step) % n + n) % n }
    val (system, games) = systems[index]
    val name = systemName(system)
    LaunchedEffect(system.id, games.size) { onFocused(system.id, name, "${games.size} games") }

    // The map: one dot per system, the current one bigger with a white ring.
    // Dots shrink (and sit closer) so every system's dot fits on one line.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val per = (maxWidth - 32.dp) / n
    val gap = minOf(6.dp, per * 0.35f)
    val dot = minOf(12.dp, per - gap).coerceAtLeast(2.dp)
    val grow = minOf(8.dp, dot)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        systems.forEachIndexed { i, (s, _) ->
            val look = lookFor(s.id)
            val on = i == index
            Box(Modifier.padding(horizontal = gap / 2).size(if (on) dot + grow else dot)
                .background(Brush.linearGradient(listOf(look.top, look.bottom)), CircleShape)
                .then(if (on) Modifier.border(if (dot < 8.dp) 1.dp else 2.dp, Color.White, CircleShape) else Modifier)
                // Tap-only: the controller skips the dots (they're too small to show a highlight,
                // so stopping on them felt like an invisible extra line).
                .focusProperties { canFocus = false }
                .clickable { direction = if (i > index) 1 else -1; index = i })
        }
    }
    }

    var dragged by remember { mutableFloatStateOf(0f) }
    var focused by remember { mutableStateOf(false) }
    val gameShown = if (focused) null else quickGame
    // Showcase: while you're on the top row, every console takes a turn here. Going down lands
    // on the one showing.
    val showing = showcase && !focused && gameShown == null
    var showIndex by remember { mutableIntStateOf(index) }
    if (showing) LaunchedEffect(Unit) {
        showIndex = index
        val wait = maxOf(Custom.slideMillis, 2500).toLong()
        while (true) { kotlinx.coroutines.delay(wait); direction = 1; showIndex = (showIndex + 1) % n }
    }
    if (showIndex >= n) showIndex = 0
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().weight(1f)
            .focusRequester(focus)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    if (showing && showIndex != index) { direction = 1; index = showIndex }
                    onRealFocus()
                }
                focused = it.isFocused
            }
            .onPreviewKeyEvent { e ->
                if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    androidx.compose.ui.input.key.Key.DirectionLeft -> { go(-1); true }
                    androidx.compose.ui.input.key.Key.DirectionRight -> { go(1); true }
                    else -> false
                }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragged > 60f) go(-1) else if (dragged < -60f) go(1); dragged = 0f },
                    onDragCancel = { dragged = 0f },
                ) { change, amount -> change.consume(); dragged += amount }
            }
            .combinedClickable(remember { MutableInteractionSource() }, indication = null,
                onLongClick = { onMenu(system.id) },
                onClick = {
                    if (gameShown != null) onQuickGame()
                    else if (showing) onOpenSystem(systems[showIndex.coerceIn(0, n - 1)].first)
                    else onOpenSystem(system)
                }),
        contentAlignment = Alignment.Center,
    ) {
        // The picture gets every bit of room left: the full width, and the full height apart
        // from a slim line on top for the count and the name underneath. It's scaled evenly
        // (never stretched).
        val pillFont = (maxHeight.value * 0.05f).coerceIn(13f, 20f)
        val pillH = badgeHeight(pillFont) + 2.dp
        val nameMax = 28f
        val nameH = (nameMax * 1.2f).dp + 4.dp
        val boxW = maxWidth - 8.dp
        val boxH = (maxHeight - pillH - nameH).coerceAtLeast(40.dp)
        if (gameShown != null) {
            QuickStartPicture(gameShown, boxW, boxH + pillH - 18.dp, nameMax)
            return@BoxWithConstraints
        }
        androidx.compose.animation.AnimatedContent(
            targetState = if (showing) showIndex else index,
            // A smooth glide: eased slide plus a gentle fade and shrink. No clipping box and no
            // drop shadow during the move (those showed as a square around the icon).
            transitionSpec = {
                val d = direction
                val ease = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(
                    320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                val fade = androidx.compose.animation.core.tween<Float>(260)
                (androidx.compose.animation.slideInHorizontally(ease) { it * d / 2 } +
                    androidx.compose.animation.fadeIn(fade) +
                    androidx.compose.animation.scaleIn(fade, initialScale = 0.85f)) togetherWith
                    (androidx.compose.animation.slideOutHorizontally(ease) { -it * d / 2 } +
                        androidx.compose.animation.fadeOut(fade) +
                        androidx.compose.animation.scaleOut(fade, targetScale = 0.85f)) using
                    androidx.compose.animation.SizeTransform(clip = false)
            },
            label = "carousel",
        ) { i ->
            val (s, g) = systems[i.coerceIn(0, n - 1)]
            val look = lookFor(s.id)
            // Built-in console art: while you rest on a console, its pictures slowly take turns,
            // dissolving into each other. Which ones, and their order, is up to you (Pictures in
            // the console's menu, or Settings > Console pictures). Each one opens only when shown.
            val keys = Custom.carouselKeys(s.id)
            var shown by remember(s.id) { mutableIntStateOf(0) }
            val cycling = keys.size > 1 && Custom.slideshow
            val wait = Custom.slideMillis
            if (cycling) LaunchedEffect(s.id, keys.size, wait) {
                while (true) { kotlinx.coroutines.delay(wait.toLong()); shown = (shown + 1) % keys.size }
            }
            val img = keys.getOrNull(if (cycling) shown % keys.size else 0)?.let { Custom.picImage(s.id, it) }
            val cutout = img != null && img.hasAlpha && img.width > 0 && img.height > 0
            val (iw, ih) = if (cycling) boxW to boxH else if (cutout && img != null) {
                val aspect = img.width.toFloat() / img.height
                if (boxW / boxH > aspect) (boxH * aspect) to boxH else boxW to (boxW / aspect)
            } else minOf(boxW, boxH).let { it to it }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The count sits in its own slim line just above the picture's top-right
                // corner, so it never covers it.
                Box(Modifier.size(iw, ih + pillH)) {
                    Box(Modifier.align(Alignment.BottomCenter).size(iw, ih)) {
                        if (cycling) {
                            // A slow "breathing" zoom under the dissolve, so it feels alive.
                            val breathe by androidx.compose.animation.core.rememberInfiniteTransition(label = "breathe")
                                .animateFloat(0.96f, 1.03f, androidx.compose.animation.core.infiniteRepeatable(
                                    androidx.compose.animation.core.tween(3500,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                    androidx.compose.animation.core.RepeatMode.Reverse), label = "zoom")
                            androidx.compose.animation.Crossfade(img, Modifier.fillMaxSize()
                                .graphicsLayer { scaleX = breathe; scaleY = breathe },
                                animationSpec = androidx.compose.animation.core.tween(1100), label = "pics") { pic ->
                                BigSystemIcon(look, pic, minOf(iw, ih), focused)
                            }
                        } else BigSystemIcon(look, img, minOf(iw, ih), focused)
                    }
                    CountBadge("${g.size}", pillFont, Modifier.align(Alignment.TopEnd))
                }
                val name = systemName(s)
                val density = androidx.compose.ui.platform.LocalDensity.current
                val nf = fitFont(name, boxW, nameMax, 10f)
                // The name shrinks to fit the width instead of being cut off.
                Text(name, color = TextBright, fontWeight = FontWeight.Black,
                    fontSize = with(density) { nf.dp.toSp() }, lineHeight = with(density) { (nf * 1.2f).dp.toSp() },
                    maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** The game Quick Start will play: its cover (when box art is on), else its console's picture, and its name. */
@Composable
private fun QuickStartPicture(game: Game, w: Dp, h: Dp, nameMax: Float) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cover = rememberBoxArt(context, game)
    val look = lookFor(game.system.id)
    val img = cover ?: Custom.icon(game.system.id)
    val density = androidx.compose.ui.platform.LocalDensity.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("▶ Play again", color = TextDim, fontSize = 13.sp, maxLines = 1, modifier = Modifier.height(18.dp))
        Box(Modifier.size(w, h), contentAlignment = Alignment.Center) {
            if (cover != null) Image(cover, contentDescription = game.label, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
            else Box(Modifier.size(minOf(w, h))) { BigSystemIcon(look, img, minOf(w, h), false) }
        }
        val name = game.label
        val nf = fitFont(name, w, nameMax, 10f)
        Text(name, color = TextBright, fontWeight = FontWeight.Black,
            fontSize = with(density) { nf.dp.toSp() }, lineHeight = with(density) { (nf * 1.2f).dp.toSp() },
            maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** The big round icon; a see-through picture is shown as-is, without the circle behind it. */
@Composable
private fun BigSystemIcon(look: SystemLook, image: ImageBitmap?, size: Dp, focused: Boolean) {
    val ring = if (focused) Color.White else Color.White.copy(alpha = 0.5f)
    if (image != null && image.hasAlpha) {
        Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        return
    }
    // A soft glow painted inside the icon's own space (a real shadow gets cut off square
    // while the icon fades during the slide).
    Box(
        Modifier.fillMaxSize()
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(look.top.copy(alpha = 0.45f), Color.Transparent),
                        center = center, radius = this.size.minDimension / 2),
                    radius = this.size.minDimension / 2,
                )
            }
            .padding(size * 0.06f)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(look.top, look.bottom)))
            .border(if (focused) 6.dp else 4.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        else Text(look.short, color = Color.White, fontWeight = FontWeight.Black,
            fontSize = (size.value * (if (look.short.length > 3) 0.2f else 0.27f)).sp)
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.3f), 0.5f to Color.Transparent)))
    }
}
