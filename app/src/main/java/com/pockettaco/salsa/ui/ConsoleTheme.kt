package com.pockettaco.salsa.ui

import android.content.Context
import android.os.BatteryManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Each system gets its own pair of colours for its bubble and cards. */
data class SystemLook(val short: String, val top: Color, val bottom: Color)

val SYSTEM_LOOKS = mapOf(
    "gba" to SystemLook("GBA", Color(0xFF8E7CFF), Color(0xFF4B2FD6)),
    "gbc" to SystemLook("GBC", Color(0xFFFFD25A), Color(0xFFE8892B)),
    "gb" to SystemLook("GB", Color(0xFFB8D86B), Color(0xFF5E8C31)),
    "nes" to SystemLook("NES", Color(0xFFFF6B6B), Color(0xFFB3202A)),
    "snes" to SystemLook("SNES", Color(0xFFB9A7FF), Color(0xFF6A4FC8)),
    "genesis" to SystemLook("GEN", Color(0xFF5AA9FF), Color(0xFF1F4FB8)),
    "sms" to SystemLook("SMS", Color(0xFFFF8A65), Color(0xFFC2410C)),
    "gg" to SystemLook("GG", Color(0xFF4DD0B0), Color(0xFF0F7C6A)),
    "pce" to SystemLook("PCE", Color(0xFFFF7AC6), Color(0xFFB0247A)),
    "ngp" to SystemLook("NGP", Color(0xFF7FD3FF), Color(0xFF2A7FB8)),
    "nds" to SystemLook("DS", Color(0xFFE0E6F0), Color(0xFF8792A8)),
    "n64" to SystemLook("N64", Color(0xFF6EE7A0), Color(0xFF1F8A4C)),
    "psx" to SystemLook("PS1", Color(0xFFC9CED8), Color(0xFF5B6273)),
    "psp" to SystemLook("PSP", Color(0xFF6C7CFF), Color(0xFF1E2A8A)),
)
val EMULATORS_LOOK = SystemLook("EMU", Color(0xFF3DDCFF), Color(0xFF0A6E9E))
val SETTINGS_LOOK = SystemLook("⚙", Color(0xFF9AA3B5), Color(0xFF3C4354))

fun lookFor(id: String): SystemLook {
    val base = SYSTEM_LOOKS[id] ?: SystemLook(id.uppercase(), Color(0xFF888888), Color(0xFF444444))
    val pick = com.pockettaco.salsa.Custom.colorIndex(id)
    val colors = com.pockettaco.salsa.ICON_COLORS.getOrNull(pick)?.second ?: return base
    return base.copy(top = colors.first, bottom = colors.second)
}

val TextBright = Color(0xFFF4F6FF)
val TextDim = Color(0xFFB4BCD6)

/**
 * Slowly drifting coloured light behind everything. Drawn in code, so it costs
 * nothing to download and looks smooth at any screen size.
 */
@Composable
fun ConsoleBackground(accent: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t by rememberInfiniteTransition(label = "bg").animateFloat(
        0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "t"
    )
    val glow by animateColorAsState(accent, tween(600), label = "accent")
    val choice = com.pockettaco.salsa.Custom.background
    val picture = if (choice < 0) com.pockettaco.salsa.Custom.backgroundImage() else null
    val backdrop = com.pockettaco.salsa.BACKDROPS.getOrElse(choice) { com.pockettaco.salsa.BACKDROPS[0] }
    Box(modifier) {
        if (picture != null) {
            androidx.compose.foundation.Image(picture, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
            // Darken it a little so text stays readable.
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
        } else Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(backdrop.top, backdrop.bottom)))
            val w = size.width; val h = size.height
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(w * (0.15f + 0.5f * t), h * 0.15f), radius = w * 0.7f),
                radius = w * 0.7f, center = Offset(w * (0.15f + 0.5f * t), h * 0.15f),
            )
            drawCircle(
                Brush.radialGradient(listOf(backdrop.glow.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(w * (0.9f - 0.4f * t), h * 0.85f), radius = w * 0.6f),
                radius = w * 0.6f, center = Offset(w * (0.9f - 0.4f * t), h * 0.85f),
            )
            // Faint diagonal light streaks.
            for (i in 0..4) {
                val x = w * (i / 4f) + w * 0.2f * t
                drawLine(Color.White.copy(alpha = 0.035f), Offset(x, 0f), Offset(x - w * 0.5f, h), strokeWidth = w * 0.04f)
            }
        }
        content()
    }
}

/** Clock, battery and controller status along the top. */
@Composable
fun StatusBar(context: Context, tacoName: String?, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val battery = remember(now) {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(
            if (tacoName != null) Color(0xFF4CFF9A) else Color(0xFF59607A), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(if (tacoName != null) "Controller ready" else "No controller",
            color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        if (battery >= 0) Text("$battery%", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(now),
            color = TextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Button hints along the bottom of the play area. */
@Composable
fun HintBar(vararg hints: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (button, label) ->
            Box(Modifier.size(20.dp).background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center) {
                Text(button, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Text(label, color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.width(16.dp))
        }
    }
}

val FAVORITES_LOOK = SystemLook("★", Color(0xFFFFD25A), Color(0xFFB8740B))
val RANDOM_LOOK = SystemLook("🎲", Color(0xFFFF8AD8), Color(0xFF8A1F7A))
val RECENT_LOOK = SystemLook("🕘", Color(0xFF8CFFB4), Color(0xFF1E7A4A))
