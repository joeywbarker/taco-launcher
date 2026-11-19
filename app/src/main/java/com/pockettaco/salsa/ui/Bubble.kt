package com.pockettaco.salsa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A round, glossy icon. Grows and glows when the D-pad lands on it.
 */
@Composable
fun Bubble(
    look: SystemLook,
    label: String,
    badge: String? = null,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.55f), label = "s")
    Column(
        Modifier
            .padding(vertical = 10.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).scale(scale), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(76.dp)
                    .shadow(if (focused) 18.dp else 6.dp, CircleShape,
                        ambientColor = look.top, spotColor = look.top)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(look.top, look.bottom)))
                    .border(if (focused) 3.dp else 1.dp,
                        if (focused) Color.White else Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Glossy highlight across the top half.
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.White.copy(alpha = 0.35f), 0.5f to Color.Transparent)))
                Text(look.short, color = Color.White, fontWeight = FontWeight.Black,
                    fontSize = if (look.short.length > 3) 15.sp else 19.sp)
            }
            if (badge != null) Box(
                Modifier.align(Alignment.TopEnd)
                    .background(Color(0xFF0B1030), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) { Text(badge, color = TextBright, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (focused) TextBright else TextDim, fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.width(100.dp))
    }
}

/**
 * One game in a list: a coloured letter tile plus the title, on a frosted card
 * that lights up in the system's colours when selected.
 */
@Composable
fun GameCard(
    title: String,
    subtitle: String? = null,
    look: SystemLook,
    tile: String = title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "s")
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (focused) Brush.horizontalGradient(listOf(look.top.copy(alpha = 0.9f), look.bottom.copy(alpha = 0.9f)))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.04f)))
            )
            .border(if (focused) 2.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.08f), shape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(look.top, look.bottom)))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(tile, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextBright, fontSize = 16.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = if (focused) Color.White else TextDim,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (focused) Text("▶", color = Color.White, fontSize = 14.sp)
    }
}
