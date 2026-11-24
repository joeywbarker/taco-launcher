package com.pockettaco.salsa.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockettaco.salsa.currentAppIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When the intro should play: on a fresh start, and each time the Taco is attached.
 * Each new play bumps [request]; the screen shows the splash until it has played that one.
 */
object SplashState {
    val request = androidx.compose.runtime.mutableIntStateOf(1)
    /** When the Taco's Bluetooth last connected (set even while the launcher is in the background). */
    @Volatile var attachedAt = 0L
    /** The last request that finished playing (kept if the screen is rebuilt). */
    var played = 0

    fun play() {
        attachedAt = 0L
        // Already waiting to play (or playing): don't start it over.
        if (request.intValue == played) request.intValue++
    }

    /** The launcher just came to the front: play the intro if the Taco was attached a moment ago. */
    fun playIfJustAttached() {
        val at = attachedAt
        if (at != 0L && android.os.SystemClock.elapsedRealtime() - at < 8000) {
            attachedAt = 0L
            play()
        }
    }
}

/**
 * A short intro when Taco Launcher starts: the icon pops in, the name fades in under it,
 * a small credit at the bottom, then everything fades out (about two seconds).
 * Tap to skip.
 */
@Composable
fun Splash(onReveal: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val icon = remember { currentAppIcon(context).preview }
    val iconAlpha = remember { Animatable(0f) }
    val iconScale = remember { Animatable(0.6f) }
    val nameAlpha = remember { Animatable(0f) }
    val nameShift = remember { Animatable(12f) }
    val creditAlpha = remember { Animatable(0f) }
    val all = remember { Animatable(1f) }
    val done by rememberUpdatedState(onDone)
    val reveal by rememberUpdatedState(onReveal)

    LaunchedEffect(Unit) {
        launch { iconAlpha.animateTo(1f, tween(450)) }
        launch { iconScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        delay(350)
        launch { nameAlpha.animateTo(1f, tween(500)) }
        launch { nameShift.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
        delay(350)
        creditAlpha.animateTo(1f, tween(400))
        delay(700)
        reveal()   // the menus start zooming in while this fades out
        all.animateTo(0f, tween(450))
        done()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = all.value }
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1550), Color(0xFF0A0820))))
            .clickable(remember { MutableInteractionSource() }, indication = null) { done() },
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(128.dp)
                    .graphicsLayer { alpha = iconAlpha.value; scaleX = iconScale.value; scaleY = iconScale.value }
                    .shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = Color(0xFFFFD25A), spotColor = Color(0xFFFFD25A))
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF1B1550)),
            ) {
                Image(painterResource(icon), contentDescription = "Taco Launcher", modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "TACO LAUNCHER",
                color = Color(0xFFFFD25A), fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
                modifier = Modifier.graphicsLayer { alpha = nameAlpha.value; translationY = nameShift.value * density },
            )
        }
        // The credit: small and quiet at the bottom.
        Text(
            "by Joey Wayne Erickson",
            color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                .graphicsLayer { alpha = creditAlpha.value },
        )
    }
}
