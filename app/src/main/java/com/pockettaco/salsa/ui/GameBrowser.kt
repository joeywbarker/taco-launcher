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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
    /** A system's games; [favorites] shows only its ★ favorites. */
    data class Games(val system: GameSystem, val favorites: Boolean = false, val recent: Boolean = false) : Screen
    /** Favorites from every system. */
    data object Favorites : Screen
    /** The last 10 different games you played. */
    data object Recent : Screen
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

    // Play a game with its system's emulator; ask about RetroArch setup first if needed.
    var askSetup by remember { mutableStateOf<Game?>(null) }
    val play: (Game) -> Unit = { game ->
        val emu = emulatorForGame(activity, game, emulators)
        if (emu != null && isRetroArch(emu) && !activity.settings.retroArchPadReady) {
            askSetup = game
        } else {
            launchGame(activity, game.system, emu, emulatorChoices(activity, game.system, emulators), game)
        }
    }
    val playRandom: (List<Game>) -> Unit = { list ->
        if (list.isEmpty()) Toast.makeText(activity, "No games here yet", Toast.LENGTH_SHORT).show()
        else {
            val game = list.random()
            Toast.makeText(activity, "🎲 ${game.label}", Toast.LENGTH_SHORT).show()
            play(game)
        }
    }
    // Worked out once per change, not on every redraw (thousands of games make this slow).
    val customVersion = Custom.version.intValue
    val shown = remember(library, customVersion) { arrange(library.entries.map { it.key to it.value }) }
    val allGames = remember(shown) { shown.flatMap { it.second } }
    val allFavorites = remember(allGames, customVersion) { Custom.favoritesIn(allGames) }
    val recent = remember(library, customVersion) { Custom.recentIn(library) }

    DisposableEffect(screen) {
        val handler: () -> Boolean = {
            val s = screen
            when {
                s is Screen.Games && (s.favorites || s.recent) -> { screen = Screen.Games(s.system); true }
                s != Screen.Home -> { screen = Screen.Home; true }
                else -> false
            }
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
            val setupFor = askSetup
            if (setupFor != null) Box(Modifier.weight(1f)) {
                val emu = emulatorForGame(activity, setupFor, emulators)
                val choices = emulatorChoices(activity, setupFor.system, emulators)
                RetroArchSetupPrompt(
                    activity,
                    onSetUp = { emu?.let { openAppFitted(activity, it.pkg) }; askSetup = null },
                    onPlay = { askSetup = null; launchGame(activity, setupFor.system, emu, choices, setupFor) },
                    onDone = {
                        activity.settings.retroArchPadReady = true
                        askSetup = null
                        launchGame(activity, setupFor.system, emu, choices, setupFor)
                    },
                    onCancel = { askSetup = null },
                )
            } else AnimatedContent(
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
                    Screen.Home -> HomeScreen(
                        activity, library, emulators.size, scan, lastSystem,
                        onFocusSystem = { accent = lookFor(it).bottom },
                        onOpenSystem = { lastSystem = it; screen = Screen.Games(it) },
                        favoriteCount = allFavorites.size,
                        onOpenFavorites = { screen = Screen.Favorites },
                        onRandom = { playRandom(allGames) },
                        lastPlayed = recent.firstOrNull(),
                        onLastPlayed = {
                            val last = recent.firstOrNull()
                            if (last != null) play(last)
                            else Toast.makeText(activity, "Play any game once, then Quick Start jumps right back into it.", Toast.LENGTH_LONG).show()
                        },
                        recentCount = recent.size,
                        onOpenRecent = { screen = Screen.Recent },
                        onOpenApps = { screen = if (emulators.isEmpty()) Screen.GetEmulators else Screen.Apps },
                        onOpenSettings = onOpenSettings,
                        onPickFolder = onPickFolder,
                        onRescan = onRescan,
                    )
                    is Screen.Games -> GamesList(activity, s.system, library[s.system].orEmpty(), emulators,
                        favoritesOnly = s.favorites, recentOnly = s.recent, onPlay = play, onRandom = playRandom,
                        onOpenFavorites = { screen = Screen.Games(s.system, favorites = true) },
                        onOpenRecent = { screen = Screen.Games(s.system, recent = true) },
                        onRescan = onRescan, onGetEmulators = { screen = Screen.GetEmulators }) {
                        screen = if (s.favorites || s.recent) Screen.Games(s.system) else Screen.Home
                    }
                    Screen.Favorites -> AllGamesList(activity, "★ Favorites", "${allFavorites.size} games from every system",
                        allFavorites, "Random favorite", null, onPlay = play, onRandom = playRandom) {
                        screen = Screen.Home
                    }
                    Screen.Recent -> AllGamesList(activity, "🕘 Recently played", "Your last ${recent.size} different games",
                        recent, "Random recent", "Nothing played yet. Games you start show up here, newest first.",
                        onPlay = play, onRandom = playRandom) {
                        screen = Screen.Home
                    }
                    Screen.Apps -> EmulatorList(activity, emulators,
                        onGetMore = { screen = Screen.GetEmulators }) { screen = Screen.Home }
                    Screen.GetEmulators -> GetEmulatorsList(activity, emulators) { screen = Screen.Home }
                }
            }
            HintBar(
                *if (screen == Screen.Home) arrayOf("A" to "Open", "≡" to "Start: menu", "◫" to "Select: view")
                else arrayOf("A" to "Play", "B" to "Back",
                    (activity.settings.favoriteButton?.label ?: "Y") to "Favorite", "≡" to "Start: menu")
            )
        }
    }
}

@Composable
private fun GamesList(
    activity: MainActivity,
    system: GameSystem,
    games: List<Game>,
    emulators: List<Emulator>,
    favoritesOnly: Boolean,
    recentOnly: Boolean,
    onPlay: (Game) -> Unit,
    onRandom: (List<Game>) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onRescan: () -> Unit,
    onGetEmulators: () -> Unit,
    onBack: () -> Unit,
) {
    val look = lookFor(system.id)
    var systemFolder by remember { mutableStateOf(activity.settings.folderFor(system.id)) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                com.pockettaco.salsa.keepFolderAccess(activity, uri)
            }
            activity.settings.setFolderFor(system.id, uri)
            systemFolder = uri
            onRescan()
        }
    }
    val favorites = Custom.favoritesIn(games)
    val recentGames = Custom.recentFor(games)
    val inFolder = favoritesOnly || recentOnly
    val list = when {
        favoritesOnly -> favorites
        recentOnly -> recentGames
        else -> games
    }
    val choices = remember(emulators) { emulatorChoices(activity, system, emulators) }
    var current by remember(choices) { mutableStateOf(currentEmulator(activity, system, emulators)) }
    var menuFor by remember { mutableStateOf<Game?>(null) }
    var focused by remember { mutableStateOf<Game?>(null) }
    val gridState = rememberLazyGridState()

    GameButtons(activity, menuOpen = menuFor != null, focused = { focused?.takeIf { it in games } },
        onMenu = { menuFor = it }, onSelect = { Custom.nextGameView(system.id) })

    menuFor?.let { game ->
        GameMenu(activity, game, onPlay = { menuFor = null; onPlay(game) }) { menuFor = null }
        return
    }

    // Focus the game you were on (or the first one), also after a menu closes.
    val target = focused?.takeIf { it in list } ?: list.firstOrNull()
    val toolbarFocus = remember { FocusRequester() }
    val gameFocus = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue, list.isEmpty()) {
        withFrameNanos { }
        runCatching { if (target != null) gameFocus.requestFocus() else toolbarFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (favoritesOnly) "★ Favorites" else if (recentOnly) "🕘 Recently played" else systemName(system),
                    color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (favoritesOnly) "${systemName(system)} · ${list.size} favorites"
                    else if (recentOnly) "${systemName(system)} · last ${list.size} games, newest first"
                    else "${games.size} games · ${current?.name ?: "no emulator yet"}",
                    color = TextDim, fontSize = 13.sp,
                )
            }
        }
        val view = Custom.gameView(system.id)
        // One slim row of small buttons instead of big cards, so the games get the screen.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton("←", "Back", SETTINGS_LOOK, toolbarFocus, onBack)
            ToolbarButton("🎲", "Random", RANDOM_LOOK) { onRandom(list) }
            if (choices.isEmpty()) ToolbarButton("↓", "Get an emulator", EMULATORS_LOOK, onClick = onGetEmulators)
            else ToolbarButton("▶", "Open ${current?.name ?: "emulator"}", EMULATORS_LOOK) {
                current?.let { openAppFitted(activity, it.pkg) }
            }
            if (choices.size > 1) ToolbarButton("⇄", "Switch emulator", EMULATORS_LOOK) {
                val next = choices[(choices.indexOf(current) + 1) % choices.size]
                current = next
                activity.settings.setEmulatorFor(system.id, next.pkg)
            }
            ToolbarButton("◫", view.label, SETTINGS_LOOK) { Custom.nextGameView(system.id) }
            if (!inFolder) ToolbarButton("📁", if (systemFolder == null) "Add folder" else folderLabel(systemFolder), SETTINGS_LOOK) {
                folderPicker.launch(null)
            }
        }
        if (recentOnly && list.isEmpty()) {
            Text("Nothing played on ${systemName(system)} yet. Games you start show up here, newest first.",
                color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            return@Column
        }
        if (favoritesOnly && list.isEmpty()) {
            NoFavoritesYet(activity)
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Fixed(if (view == GameView.GRID) 3 else 1),
            Modifier.fillMaxSize().padding(horizontal = if (view == GameView.GRID) 10.dp else 0.dp),
            state = gridState,
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            // Jump back into this system's last game.
            val last = if (inFolder) null else Custom.lastIn(games)
            if (last != null) item("continue", span = { GridItemSpan(maxLineSpan) }) {
                GameRow("▶  Continue: ${last.label}", look, onLongClick = { menuFor = last }) { onPlay(last) }
            }
            // This system's own favorites folder, at the top of its list.
            if (!inFolder) item("recent", span = { GridItemSpan(maxLineSpan) }) {
                GameRow("🕘  Recently played (${recentGames.size})", RECENT_LOOK, onClick = onOpenRecent)
            }
            if (!inFolder) item("favorites", span = { GridItemSpan(maxLineSpan) }) {
                GameRow("★  Favorites (${favorites.size})", FAVORITES_LOOK, onClick = onOpenFavorites)
            }
            items(list, key = { it.uri.toString() }) { game ->
                val focus = if (game == target) gameFocus else null
                val title = if (!favoritesOnly && game in favorites) "★ ${game.label}" else game.label
                val onFocused = { focused = game }
                val onHold = { menuFor = game }
                when (view) {
                    GameView.LIST -> GameCard(title, look = look, focusRequester = focus,
                        onFocused = onFocused, onLongClick = onHold) { onPlay(game) }
                    GameView.GRID -> GameTile(title, look, focus, onFocused, onHold) { onPlay(game) }
                    GameView.COMPACT -> GameRow(title, look, focus, onFocused, onHold) { onPlay(game) }
                }
            }
        }
    }
}

/** Games from every system in one list: Favorites or Recently played, on the home screen. */
@Composable
private fun AllGamesList(
    activity: MainActivity,
    title: String,
    subtitle: String,
    favorites: List<Game>,
    randomLabel: String,
    emptyText: String?,
    onPlay: (Game) -> Unit,
    onRandom: (List<Game>) -> Unit,
    onBack: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<Game?>(null) }
    var focused by remember { mutableStateOf<Game?>(null) }
    val listState = rememberLazyListState()
    GameButtons(activity, menuOpen = menuFor != null, focused = { focused?.takeIf { it in favorites } },
        onMenu = { menuFor = it }, onSelect = null)
    menuFor?.let { game ->
        GameMenu(activity, game, onPlay = { menuFor = null; onPlay(game) }) { menuFor = null }
        return
    }
    val target = focused?.takeIf { it in favorites } ?: favorites.firstOrNull()
    val toolbarFocus = remember { FocusRequester() }
    val gameFocus = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue, favorites.isEmpty()) {
        withFrameNanos { }
        runCatching { if (target != null) gameFocus.requestFocus() else toolbarFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(title, color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextDim, fontSize = 13.sp)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton("←", "Back", SETTINGS_LOOK, toolbarFocus, onBack)
            ToolbarButton("🎲", randomLabel, RANDOM_LOOK) { onRandom(favorites) }
        }
        if (favorites.isEmpty()) {
            if (emptyText == null) NoFavoritesYet(activity)
            else Text(emptyText, color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 6.dp)) {
            items(favorites, key = { it.uri.toString() }) { game ->
                val look = lookFor(game.system.id)
                val star = if (emptyText != null && Custom.isFavorite(game)) "★ " else ""
                GameCard(star + game.label, systemName(game.system), look, tile = look.short,
                    focusRequester = if (game == target) gameFocus else null,
                    image = Custom.icon(game.system.id),
                    onFocused = { focused = game }, onLongClick = { menuFor = game }) { onPlay(game) }
            }
        }
    }
}

@Composable
private fun NoFavoritesYet(activity: MainActivity) {
    val button = activity.settings.favoriteButton?.label ?: "the favorite button"
    Text(
        "No favorites yet. On any game, press $button, or hold A (or press Start) and pick Add to favorites.",
        color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/**
 * Controller buttons in a game list: the favorite button (Y unless changed) stars the
 * selected game, Start opens its menu, Select switches the view.
 */
@Composable
private fun GameButtons(
    activity: MainActivity,
    menuOpen: Boolean,
    focused: () -> Game?,
    onMenu: (Game) -> Unit,
    onSelect: (() -> Unit)?,
) {
    val open by rememberUpdatedState(menuOpen)
    val current by rememberUpdatedState(focused)
    val menu by rememberUpdatedState(onMenu)
    val select by rememberUpdatedState(onSelect)
    DisposableEffect(Unit) {
        val fav: () -> Boolean = {
            if (!open) current()?.let { game ->
                val now = Custom.toggleFavorite(game)
                Toast.makeText(activity, if (now) "★ Added to favorites" else "Removed from favorites",
                    Toast.LENGTH_SHORT).show()
            }
            true
        }
        val start: () -> Boolean = { if (!open) current()?.let { menu(it) }; true }
        val sel: () -> Boolean = { if (!open && !Custom.viewLocked) select?.invoke(); true }
        activity.onFavoriteButton = fav
        activity.onStartButton = start
        activity.onSelectButton = sel
        onDispose {
            if (activity.onFavoriteButton === fav) activity.onFavoriteButton = null
            if (activity.onStartButton === start) activity.onStartButton = null
            if (activity.onSelectButton === sel) activity.onSelectButton = null
        }
    }
}

/** Hold A on a game (or press Start) for this. */
@Composable
private fun GameMenu(activity: MainActivity, game: Game, onPlay: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(FocusKick.count.intValue) { withFrameNanos { }; runCatching { first.requestFocus() } }
    DisposableEffect(Unit) {
        val previous = activity.onBackRequest
        val handler: () -> Boolean = { onClose(); true }
        activity.onBackRequest = handler
        onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = previous }
    }
    val fav = Custom.isFavorite(game)
    val look = lookFor(game.system.id)
    var picking by remember { mutableStateOf(false) }
    var pickingCore by remember { mutableStateOf(false) }
    var pickingLayout by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val installed = remember { installedEmulators(activity) }
    val own = remember(version) { activity.settings.emulatorForGame(game) }
    val systemEmu = remember(version) { currentEmulator(activity, game.system, installed) }
    val gameEmu = remember(version) { emulatorForGame(activity, game, installed) }
    if (pickingLayout) {
        val current = RetroArchPlacement.Layout.of(activity.settings.layoutForGame(game.system.id, game.file))
        val layoutFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { layoutFocus.requestFocus() } }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("Screen layout for this game", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Used the next time you start ${game.label}", color = TextDim, fontSize = 13.sp, maxLines = 1)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                RetroArchPlacement.Layout.entries.forEachIndexed { i, layout ->
                    item(layout.key) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                            LayoutPreview(activity, game.system, layout)
                            Box(Modifier.weight(1f)) {
                                GameCard(layout.label + if (layout == current) "  ✓" else "", layout.about,
                                    EMULATORS_LOOK, tile = "▭", focusRequester = if (i == 0) layoutFocus else null) {
                                    activity.settings.setLayoutForGame(game.system.id, game.file,
                                        if (layout == RetroArchPlacement.Layout.AUTO) null else layout.key)
                                    version++; pickingLayout = false
                                }
                            }
                        }
                    }
                }
                item("back") { GameCard("Back", null, SETTINGS_LOOK, tile = "←") { pickingLayout = false } }
            }
        }
        return
    }
    if (pickingCore) {
        // Which RetroArch core plays just this game (some arcade games only run on one).
        val options = CORE_OPTIONS[game.system.id].orEmpty()
        val ownCore = activity.settings.coreForGame(game)
        val systemCore = coreFor(activity, game.system.id)
        val coreFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { coreFocus.requestFocus() } }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("RetroArch core for this game", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("The core must be downloaded in RetroArch (Online Updater → Core Downloader)",
                    color = TextDim, fontSize = 13.sp)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                item("system") {
                    GameCard("Same as ${systemName(game.system)}" + if (ownCore == null) "  ✓" else "",
                        options.firstOrNull { it.first == systemCore }?.second ?: systemCore,
                        SETTINGS_LOOK, tile = "↺", focusRequester = coreFocus) {
                        activity.settings.setCoreForGame(game, null); version++; pickingCore = false
                    }
                }
                items(options, key = { it.first }) { (core, name) ->
                    GameCard(name + if (ownCore == core) "  ✓" else "", null, EMULATORS_LOOK, tile = "▶") {
                        activity.settings.setCoreForGame(game, core); version++; pickingCore = false
                    }
                }
                item("back") { GameCard("Back", null, SETTINGS_LOOK, tile = "←") { pickingCore = false } }
            }
        }
        return
    }
    if (picking) {
        // Which app opens just this game.
        val choices = emulatorChoices(activity, game.system, installed)
        val pickFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { pickFocus.requestFocus() } }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("Emulator for this game", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(game.label, color = TextDim, fontSize = 13.sp, maxLines = 1)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                item("system") {
                    GameCard("Same as ${systemName(game.system)}" + if (own == null) "  ✓" else "",
                        systemEmu?.name ?: "No emulator installed", SETTINGS_LOOK, tile = "↺", focusRequester = pickFocus) {
                        activity.settings.setEmulatorForGame(game, null); version++; picking = false
                    }
                }
                items(choices, key = { it.pkg }) { emu ->
                    GameCard(emu.name + if (own == emu.pkg) "  ✓" else "", null, EMULATORS_LOOK, tile = "▶") {
                        activity.settings.setEmulatorForGame(game, emu.pkg); version++; picking = false
                    }
                }
                item("back") { GameCard("Back", null, SETTINGS_LOOK, tile = "←") { picking = false } }
            }
        }
        return
    }
    // Coming back from the emulator list: put focus back on the menu.
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(game.label, color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text("${systemName(game.system)} · B to close", color = TextDim, fontSize = 13.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            item("play") { GameCard("Play", null, look, tile = "▶", focusRequester = first, onClick = onPlay) }
            item("fav") {
                GameCard(if (fav) "Remove from favorites" else "Add to favorites",
                    if (fav) null else "Shows up in ★ Favorites here and on the home screen",
                    FAVORITES_LOOK, tile = if (fav) "☆" else "★") {
                    Custom.toggleFavorite(game); onClose()
                }
            }
            item("emu") {
                GameCard("Emulator for this game", (gameEmu?.name ?: "None installed") +
                    if (own == null) " (same as ${systemName(game.system)})" else " (just this game)",
                    EMULATORS_LOOK, tile = "⇄") { picking = true }
            }
            if (gameEmu != null && isRetroArch(gameEmu) && CORE_OPTIONS.containsKey(game.system.id)) item("core") {
                val core = remember(version) { coreFor(activity, game.system.id, game.file) }
                val own = remember(version) { activity.settings.coreForGame(game) }
                GameCard("RetroArch core for this game",
                    (CORE_OPTIONS[game.system.id]?.firstOrNull { it.first == core }?.second ?: core) +
                        if (own == null) " (same as ${systemName(game.system)})" else " (just this game)",
                    EMULATORS_LOOK, tile = "⚙") { pickingCore = true }
            }
            if (gameEmu != null && isRetroArch(gameEmu) && activity.settings.retroArchFullScreen) item("layout") {
                val layout = remember(version) {
                    RetroArchPlacement.Layout.of(activity.settings.layoutForGame(game.system.id, game.file))
                }
                GameCard("Screen layout", layout.label + " · where the picture sits in RetroArch",
                    EMULATORS_LOOK, tile = "▭") { pickingLayout = true }
            }
            item("cancel") { GameCard("Cancel", null, SETTINGS_LOOK, tile = "✕", onClick = onClose) }
        }
    }
}

/** A tiny phone: the Taco's part at the bottom, and where the game picture would go. */
@Composable
fun LayoutPreview(activity: MainActivity, system: GameSystem, layout: RetroArchPlacement.Layout) {
    val screen = remember { activity.windowManager.currentWindowMetrics.bounds }
    val covered = activity.settings.coveredFraction
    val shape = SCREEN_SHAPES[system.id] ?: 4f / 3f
    val spot = remember { gameWindowRect(activity.settings, screen.width(), screen.height(), shape) }
    val r = RetroArchPlacement.pictureRect(layout, screen.width().toFloat(), screen.height().toFloat(),
        covered, spot.exactCenterY(), shape)
    val look = lookFor(system.id)
    androidx.compose.foundation.Canvas(Modifier.size(width = 40.dp, height = 86.dp)) {
        val cr = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
        drawRoundRect(Color(0xFF15182A), cornerRadius = cr)
        // The Taco's part of the screen.
        drawRect(Color(0xFF6B4A1E), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * (1f - covered)),
            size = androidx.compose.ui.geometry.Size(size.width, size.height * covered))
        drawRect(look.top, topLeft = androidx.compose.ui.geometry.Offset(r.left * size.width, r.top * size.height),
            size = androidx.compose.ui.geometry.Size(r.width() * size.width, r.height() * size.height))
        drawRoundRect(Color.White.copy(alpha = 0.4f), cornerRadius = cr,
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}

private fun isRetroArch(e: Emulator) = e.pkg.startsWith("com.retroarch") || "retroarch" in e.name.lowercase()

private var warnedHotkeysOff = false

private fun launchGame(activity: MainActivity, system: GameSystem, emu: Emulator?, choices: List<Emulator>, game: Game) {
    // Hotkeys (Select + Start, Select + B...) need the button helper switched on in Android's
    // Accessibility settings. Android can switch it off by itself, so say so once.
    if (!warnedHotkeysOff && !isMapperOn(activity)) {
        warnedHotkeysOff = true
        Toast.makeText(activity, "Hotkeys are off: Android switched off Taco Launcher's button helper. " +
            "Turn it back on in Settings → Controller (or phone Settings → Accessibility).", Toast.LENGTH_LONG).show()
    }
    if (emu == null) {
        runCatching { activity.startActivity(openWithAnyApp(game.uri)) }
        Custom.addRecent(game)
        return
    }
    // Only one RetroArch game at a time: if you left a different one with Home, ask first.
    val previous = ButtonMapperState.lastGame
    if (isRetroArch(emu) && activity.settings.raAskBeforeSwitch && previous != null &&
        previous.uri != game.uri) {
        askStillPlaying(activity, previous, game,
            resume = { startGame(activity, system, emu, choices, previous) },
            switch = { startGame(activity, system, emu, choices, game) })
        return
    }
    startGame(activity, system, emu, choices, game)
}

/** "Are you still playing?" with big, controller-friendly choices. Back or B just cancels. */
private fun askStillPlaying(activity: MainActivity, old: Game, new: Game, resume: () -> Unit, switch: () -> Unit) {
    val dialog = android.app.AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle("Still playing ${old.label}?")
        .setItems(
            if (activity.settings.raKeepsSpot) arrayOf(
                "▶  Go back to ${old.label}",
                "💾  Quit it, keep my spot, and start ${new.label}",
                "✕  Quit it, don't keep my spot, and start ${new.label}",
                "Cancel",
            ) else arrayOf(
                "▶  Go back to ${old.label}",
                "✕  Quit it and start ${new.label}",
                "Cancel",
            ),
        ) { d, which ->
            d.dismiss()
            val keeps = activity.settings.raKeepsSpot
            when {
                which == 0 -> resume()
                which == 1 -> switch()
                keeps && which == 2 -> {
                    // RetroArch already saved when it closed; skip that save next time instead.
                    activity.settings.setFreshStart(old.system.id, old.file, true)
                    switch()
                }
            }
        }
        .setCancelable(true)
        .create()
    // A picks the highlighted choice, B cancels (Android's lists only know the D-pad's centre).
    // Only act on a press that started here, not the release of the A that opened this.
    var aDown = false
    dialog.setOnKeyListener { d, code, ev ->
        val list = dialog.listView
        when (code) {
            android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                if (ev.action == android.view.KeyEvent.ACTION_DOWN) aDown = true
                if (ev.action == android.view.KeyEvent.ACTION_UP && aDown && list != null) {
                    val pos = list.selectedItemPosition.takeIf { it >= 0 } ?: 0
                    list.performItemClick(list.getChildAt(pos - list.firstVisiblePosition), pos, pos.toLong())
                }
                true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_B -> { if (ev.action == android.view.KeyEvent.ACTION_UP) d.cancel(); true }
            else -> false
        }
    }
    dialog.setOnShowListener { dialog.listView?.apply { requestFocus(); setSelection(0) } }
    dialog.show()
}

private fun startGame(activity: MainActivity, system: GameSystem, emu: Emulator, choices: List<Emulator>, game: Game) {
    val sys = if (game.system.id == system.id) system else game.system
    // Use the emulator you picked. If it's RetroArch, another RetroArch build may step in;
    // never quietly swap to a different emulator.
    val tries = listOf(emu) + if (isRetroArch(emu)) choices.filter { it != emu && isRetroArch(it) } else emptyList()
    val worked = tries.firstOrNull { e ->
        val intent = launchIntent(activity, e, sys, game.uri, game.file)
        val accepts = intent.component != null ||
            activity.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        accepts && runCatching { startFitted(activity, intent, sys) }.isSuccess
    }
    if (worked != null) {
        if (isRetroArch(worked)) ButtonMapperState.lastGame = game
        Custom.addRecent(game)
        if (worked != emu) Toast.makeText(activity, "Opened with ${worked.name}", Toast.LENGTH_SHORT).show()
        return
    }
    // Some emulators (Lemuroid is one) won't let other apps hand them a game.
    Toast.makeText(activity,
        "${emu.name} doesn't let other apps start a game, so it's opening for you to pick " +
            "\"${game.label}\" there. To play straight from Taco Launcher, use ⇄ Switch emulator.",
        Toast.LENGTH_LONG).show()
    openAppFitted(activity, emu.pkg)
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
                    "Port 1 Controls → Set All Controls, then Save Controller Profile. You only do this once.\n" +
                    "In a game: Select + Start opens RetroArch's menu, Home comes back here.",
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
