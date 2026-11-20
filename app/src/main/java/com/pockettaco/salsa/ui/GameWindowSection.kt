package com.pockettaco.salsa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettaco.salsa.MainActivity
import com.pockettaco.salsa.SCREEN_SHAPES
import com.pockettaco.salsa.gameWindowRect

/** Settings for where games appear: size the window to the console, and slide it up or down. */
@Composable
fun GameWindowSection(activity: MainActivity, covered: Float) {
    val s = activity.settings
    var auto by remember { mutableStateOf(s.autoFitGame) }
    var height by remember { mutableFloatStateOf(s.windowHeight) }
    var position by remember { mutableFloatStateOf(s.windowPosition) }

    Text("Game window", color = Color.White, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fit the window to each console's screen", color = Color.White, modifier = Modifier.weight(1f))
                Switch(auto, { auto = it; s.autoFitGame = it })
            }
            if (!auto) {
                Text("Window height: ${(height * 100).toInt()}% of the screen", color = Color.White)
                Slider(height, { height = it; s.windowHeight = it }, valueRange = 0.2f..1f)
            }
            Text(
                when {
                    position > 0.95f -> "Position: just above the Pocket Taco"
                    position < 0.05f -> "Position: top of the screen"
                    else -> "Position: ${(position * 100).toInt()}% of the way down"
                },
                color = Color.White,
            )
            Slider(position, { position = it; s.windowPosition = it })
            Text("Close the game and reopen it to see changes.", color = Color(0xFF777777),
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        // Little phone preview: green = game window (a GBA game as the example), orange = Pocket Taco.
        val previewShape = if (auto) SCREEN_SHAPES["gba"] else null
        Canvas(Modifier.width(70.dp).height(150.dp)) {
            val w = size.width; val h = size.height
            drawRoundRect(Color.Black, size = size, cornerRadius = CornerRadius(14f))
            drawRoundRect(Color(0xFF555555), size = size, cornerRadius = CornerRadius(14f), style = Stroke(3f))
            val r = gameWindowRect(covered, auto, height, position, 1000, (1000 * h / w).toInt(), previewShape)
            val k = w / 1000f
            drawRect(Color(0xFF4CFF9A), Offset(0f, r.top * k), Size(w, r.height() * k))
            drawRect(Color(0xFFF2A93B), Offset(0f, h * (1 - covered)), Size(w, h * covered))
        }
    }
}
