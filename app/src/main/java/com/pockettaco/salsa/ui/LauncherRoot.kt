package com.pockettaco.salsa.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettaco.salsa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Top of the UI. Picks the normal layout or the Taco layout the instant the
 * controller connects or disconnects.
 */
@Composable
fun LauncherRoot(activity: MainActivity) {
    val tacoOn by activity.watcher.connected.collectAsStateWithLifecycle()
    // The real controller, even when "Always use the Taco layout" keeps the layout on.
    val tacoName by activity.watcher.deviceName.collectAsStateWithLifecycle()
    var covered by remember { mutableFloatStateOf(activity.settings.coveredFraction) }
    // Full screen ("Taco off"): nothing is held back for the Taco and the screen can turn.
    val full by ScreenMode.full
    val coveredNow = if (full) 0f else covered
    var romFolder by remember { mutableStateOf(activity.settings.romFolder) }
    // Start with the saved list so games show up instantly, then refresh it.
    var library by remember { mutableStateOf(loadLibrary(activity)) }
    var scanning by remember { mutableStateOf(false) }
    var scanCount by remember { mutableIntStateOf(0) }
    var rescanKey by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var setupDone by remember { mutableStateOf(activity.settings.setupDone) }
    // The intro plays on a fresh start and each time the Taco is attached (not when you come
    // back from a game). It's decided before drawing, so the menus never flash first.
    // The menus load underneath while it plays, so there's no wait after it.
    val splashWanted = SplashState.request.intValue
    var splashDone by remember {
        mutableIntStateOf(if (activity.settings.showSplash) SplashState.played else splashWanted)
    }
    var revealedFor by remember { mutableIntStateOf(splashDone) }
    if (splashDone != splashWanted && (!activity.settings.showSplash || activity.gameWindowOpen.value)) {
        // Switched off, or a game is open: skip this one.
        splashDone = splashWanted; revealedFor = splashWanted; SplashState.played = splashWanted
    }
    val splashGone = splashDone == splashWanted
    val revealed = revealedFor == splashWanted
    // Let the splash draw its first frames, then build the menus behind it.
    var mounted by remember { mutableStateOf(splashGone) }
    LaunchedEffect(Unit) { if (!mounted) { kotlinx.coroutines.delay(150); mounted = true } }
    val enter by androidx.compose.animation.core.animateFloatAsState(
        if (revealed) 1f else 0f, androidx.compose.animation.core.tween(450), label = "enter")

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            com.pockettaco.salsa.keepFolderAccess(activity, uri)
            activity.settings.romFolder = uri
            romFolder = uri
        }
    }

    LaunchedEffect(romFolder, rescanKey) {
        val folder = romFolder
        if (folder != null && !canStillRead(activity, folder)) {
            // Android forgot our access (rare). Ask once instead of showing nothing.
            romFolder = null
            activity.settings.romFolder = null
        }
        val anySystemFolder = SYSTEMS.any { activity.settings.folderFor(it.id) != null }
        if (romFolder == null && !anySystemFolder) return@LaunchedEffect
        scanning = true
        scanCount = 0
        val fresh = withContext(Dispatchers.IO) {
            scanAll(activity, activity.settings) { n -> scanCount = n }
        }
        library = fresh
        withContext(Dispatchers.IO) { saveLibrary(activity, fresh) }
        scanning = false
    }
    val scanState = ScanState(scanning, scanCount, hasFolder = romFolder != null)

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101014)) {
          Box(Modifier.fillMaxSize()) {
            // As the splash fades, the menus zoom gently into place.
            if (mounted && !setupDone) FirstRunSetup(
                activity, covered,
                onCoveredChange = { covered = it; activity.settings.coveredFraction = it },
                hasFolder = romFolder != null,
                onPickFolder = { picker.launch(null) },
                onDone = { activity.settings.setupDone = true; setupDone = true; activity.updateScreenMode() },
            ) else if (mounted) AnimatedContent(
                modifier = Modifier.graphicsLayer {
                    alpha = enter; scaleX = 0.92f + 0.08f * enter; scaleY = 0.92f + 0.08f * enter
                },targetState = Triple(tacoOn, showSettings, full), label = "mode") { (taco, settingsOpen, fullScreen) ->
                when {
                    settingsOpen -> TacoLayout(covered = if (taco && !fullScreen) covered else 0f, tacoName = tacoName,
                        sidePadding = fullScreen) {
                        SettingsScreen(
                            activity, covered,
                            onCoveredChange = { covered = it; activity.settings.coveredFraction = it },
                            onPickFolder = { picker.launch(null) },
                            onRescan = { rescanKey++ },
                            onClose = { showSettings = false },
                        )
                    }
                    (taco || fullScreen) && activity.gameWindowOpen.value -> Box(Modifier.fillMaxSize().background(Color.Black)) {
                        // Behind the game window: plain black for now. A spot for something fun later.
                    }
                    taco || fullScreen -> TacoLayout(covered = if (fullScreen) 0f else covered, tacoName = tacoName,
                        edgeToEdge = true, sidePadding = fullScreen) {
                        GameBrowser(
                            activity, library, scanState,
                            onPickFolder = { picker.launch(null) },
                            onOpenSettings = { showSettings = true },
                            onRescan = { rescanKey++ },
                        )
                    }
                    else -> NormalLayout(activity, onOpenSettings = { showSettings = true })
                }
            }
            if (!splashGone) {
                TacoLayout(covered = if (tacoOn) coveredNow else 0f, tacoName = tacoName, sidePadding = full) {
                    key(splashWanted) {
                        Splash(
                            onReveal = { revealedFor = splashWanted },
                            onDone = {
                                revealedFor = splashWanted; splashDone = splashWanted
                                SplashState.played = splashWanted
                            },
                        )
                    }
                }
            }
          }
        }
    }
}

/**
 * Splits the screen: games on top, a blank strip where the Taco sits.
 * The blank strip is plain black so it costs almost no battery on OLED screens
 * and nothing important is ever hidden under the controller.
 */
@Composable
fun TacoLayout(
    covered: Float, tacoName: String? = null,
    /** Draw right up to the top edge, around the camera (the content handles the camera itself). */
    edgeToEdge: Boolean = false,
    /** Full screen: keep clear of a camera hole or phone buttons on the sides (when turned sideways). */
    sidePadding: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().then(if (sidePadding) Modifier.windowInsetsPadding(
        WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal)) else Modifier)) {
        Box(
            Modifier.fillMaxWidth().weight(1f - covered)
                .then(if (edgeToEdge) Modifier
                else Modifier.windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout)))
        ) { content() }
        // The part under the Taco: plain black (nothing there can be seen anyway).
        if (covered > 0f) Box(Modifier.fillMaxWidth().weight(covered).background(Color.Black))
    }
}

/** What the game list is doing right now, for the progress bar. */
data class ScanState(val scanning: Boolean, val count: Int, val hasFolder: Boolean)
