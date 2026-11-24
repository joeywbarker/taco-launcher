package com.pockettaco.salsa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockettaco.salsa.FocusKick
import com.pockettaco.salsa.MainActivity

/**
 * Shown once, the first time Taco Launcher opens on a phone. It asks the one thing that
 * differs from phone to phone (how much screen the Taco hides), with a live preview, then
 * offers to pick the games folder. Every step can be skipped and changed later in Settings.
 *
 * The controller works throughout: D-pad to move between buttons, A to press.
 */
@Composable
fun FirstRunSetup(
    activity: MainActivity,
    covered: Float,
    onCoveredChange: (Float) -> Unit,
    hasFolder: Boolean,
    onPickFolder: () -> Unit,
    onDone: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    // B goes back a step (and does nothing on the first one: a launcher never closes itself).
    DisposableEffect(Unit) {
        val handler: () -> Boolean = { if (step > 0) step--; true }
        activity.onBackRequest = handler
        onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = null }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(step, FocusKick.count.intValue) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
        kotlinx.coroutines.delay(300)
        runCatching { first.requestFocus() }
    }
    // Controllers disagree on which signal the A button sends (some send "B"), so the first
    // page simply asks you to press A, and learns it. B becomes the other one.
    if (step == 0) DisposableEffect(Unit) {
        val learn: (android.view.KeyEvent) -> Unit = { e ->
            val a = android.view.KeyEvent.KEYCODE_BUTTON_A
            val b = android.view.KeyEvent.KEYCODE_BUTTON_B
            if (e.action == android.view.KeyEvent.ACTION_UP && (e.keyCode == a || e.keyCode == b)) {
                val st = activity.settings
                st.swapAB = e.keyCode == b
                st.selectKey = e.keyCode; st.selectScan = 0
                st.backKey = if (e.keyCode == a) b else a; st.backScan = 0
                activity.onKeyCapture = null
                step = 1
            }
        }
        activity.onKeyCapture = learn
        onDispose { if (activity.onKeyCapture === learn) activity.onKeyCapture = null }
    }
    when (step) {
        0 -> SetupPage(
            "🌮 Welcome to Taco Launcher",
            "A home screen for playing retro games with the GameSir Pocket Taco.\n\n" +
                "Clip your phone into the Taco and switch the Taco to Android mode (hold M + A for 2 seconds).\n\n" +
                "Then press the A button on your Taco to start. (Taco Launcher learns which button is A, " +
                "so it's right on any controller.)",
        ) {
            GameCard("Let's go", "Or tap here to set up by touch", SETTINGS_LOOK, tile = "▶", focusRequester = first) { step = 1 }
            GameCard("Skip setup", "Use the usual settings. Change anything later in Settings.", SETTINGS_LOOK, tile = "⏭") { onDone() }
        }
        1 -> CoverStep(covered, onCoveredChange, first, onNext = { step = 2 })
        2 -> SetupPage(
            "📁 Where are your games?",
            "Pick the folder that holds your game files (for example a \"ROMs\" folder). " +
                "Taco Launcher sorts them by console for you, including folders inside it.\n\n" +
                "Taco Launcher doesn't come with any games. You also need an emulator app such as RetroArch.",
        ) {
            GameCard(if (hasFolder) "Pick a different folder" else "Pick my games folder",
                if (hasFolder) "✓ Folder chosen" else "Opens Android's folder picker", SETTINGS_LOOK,
                tile = "📁", focusRequester = first) { onPickFolder() }
            GameCard(if (hasFolder) "Next" else "Skip for now", "You can pick it later in Settings → Games & systems",
                SETTINGS_LOOK, tile = "▶") { step = 3 }
        }
        else -> SetupPage(
            "✓ All set",
            "Everything else has sensible settings already. To change anything, open Settings: every option " +
                "explains what it does and says when it takes effect.\n\n" +
                "Tip: Select + Start opens the emulator's menu in a game, and Home brings you back here.",
        ) {
            GameCard("Start playing", null, SETTINGS_LOOK, tile = "🎮", focusRequester = first) { onDone() }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupCheckSection(activity, onPickFolder)
            }
        }
    }
}

@Composable
private fun SetupPage(title: String, body: String, buttons: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Color(0xFFFFD25A), fontSize = 24.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 24.dp))
        Text(body, color = Color(0xFFDDDDE6), fontSize = 15.sp, lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(4.dp))
        buttons()
    }
}

/**
 * The live preview: the yellow stripes fill exactly the part Taco Launcher will leave empty.
 * Move the edge until the stripes just disappear under the Taco.
 */
@Composable
private fun CoverStep(covered: Float, onChange: (Float) -> Unit, first: FocusRequester, onNext: () -> Unit) {
    val now by rememberUpdatedState(covered)
    fun nudge(d: Float) = onChange(((covered + d) * 100).let { kotlin.math.round(it) / 100f }.coerceIn(0.15f, 0.6f))
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f - covered).statusBarsPadding().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("📏 How much screen does the Taco hide?", color = Color(0xFFFFD25A), fontSize = 20.sp,
                fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 24.dp))
            Text("With the phone clipped in, move the edge until the yellow stripes just disappear under the Taco. " +
                "Everything in Taco Launcher then fits in the part you can see. Now: ${(covered * 100).toInt()}% hidden.",
                color = Color(0xFFDDDDE6), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp))
            GameCard("Edge down", "Less screen hidden (if you can see stripes)", SETTINGS_LOOK, tile = "▼",
                focusRequester = first) { nudge(-0.01f) }
            GameCard("Edge up", "More screen hidden (if games would sit under the Taco)", SETTINGS_LOOK, tile = "▲") { nudge(0.01f) }
            GameCard("Looks right", "Next step", SETTINGS_LOOK, tile = "✓") { onNext() }
        }
        Box(
            Modifier.fillMaxWidth().weight(covered)
                .background(Brush.linearGradient(
                    listOf(Color(0xFFFFC83D), Color(0xFF8A5A00)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(40f, 40f),
                    tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
                ))
                // Or drag the edge with a finger.
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy ->
                        val screenH = size.height / now.coerceAtLeast(0.01f)
                        onChange((now - dy / screenH).coerceIn(0.15f, 0.6f))
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Text("This part is left empty for the Taco. Drag here to move the edge.", color = Color.Black,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp))
        }
    }
}
