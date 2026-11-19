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
    var covered by remember { mutableFloatStateOf(activity.settings.coveredFraction) }
    var romFolder by remember { mutableStateOf(activity.settings.romFolder) }
    var library by remember { mutableStateOf<Map<GameSystem, List<Game>>>(emptyMap()) }
    var showSettings by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            activity.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            activity.settings.romFolder = uri
            romFolder = uri
        }
    }

    LaunchedEffect(romFolder) {
        romFolder?.let { library = withContext(Dispatchers.IO) { scanRoms(activity, it) } }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101014)) {
            AnimatedContent(targetState = tacoOn to showSettings, label = "mode") { (taco, settingsOpen) ->
                when {
                    settingsOpen -> TacoLayout(covered = if (taco) covered else 0f) {
                        SettingsScreen(
                            activity, covered,
                            onCoveredChange = { covered = it; activity.settings.coveredFraction = it },
                            onPickFolder = { picker.launch(null) },
                            onClose = { showSettings = false },
                        )
                    }
                    taco -> TacoLayout(covered = covered) {
                        GameBrowser(
                            activity, library,
                            onPickFolder = { picker.launch(null) },
                            onOpenSettings = { showSettings = true },
                        )
                    }
                    else -> NormalLayout(activity, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}

/**
 * Splits the screen: games on top, a blank strip where the Taco sits.
 * The blank strip is plain black so it costs almost no battery on the S26's
 * OLED screen and nothing important is ever hidden under the controller.
 */
@Composable
fun TacoLayout(covered: Float, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().weight(1f - covered).statusBarsPadding()
        ) { content() }
        if (covered > 0f) {
            Box(
                Modifier.fillMaxWidth().weight(covered).background(Color.Black),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    "Pocket Taco connected",
                    color = Color(0xFF333333),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}
