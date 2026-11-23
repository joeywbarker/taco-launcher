package com.pockettaco.salsa.ui

import androidx.compose.ui.layout.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Bubble(
    look: SystemLook,
    label: String,
    badge: String? = null,
    focusRequester: FocusRequester? = null,
    image: ImageBitmap? = null,
    size: Dp = 76.dp,
    labelWidth: Dp? = null,
    labelSize: Float? = null,
    vPad: Dp = 10.dp,
    /** Leave room above for a count pill even without one, so icons in a row line up. */
    reserveBadgeRoom: Boolean = false,
    highlight: Boolean = false,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.55f), label = "s")
    // The count sits off the icon's top-right edge; leave room above for it.
    val badgeFont = badgeFontFor(size)
    val raise = if (badge != null || reserveBadgeRoom) badgeRaise(size) else 0.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    Column(
        Modifier
            .padding(top = vPad + raise, bottom = vPad)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .combinedClickable(remember { MutableInteractionSource() }, indication = null,
                onLongClick = onLongClick, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val small = size < 70.dp
        Box(Modifier.size(size + 8.dp).scale(scale), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(size)
                    .shadow(if (focused) 18.dp else 6.dp, CircleShape,
                        ambientColor = look.top, spotColor = look.top)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(look.top, look.bottom)))
                    .border(if (focused || highlight) 3.dp else 1.dp,
                        if (highlight) Color(0xFFFFD25A) else if (focused) Color.White else Color.White.copy(alpha = 0.25f),
                        CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    Image(image, contentDescription = label,
                        contentScale = if (image.hasAlpha) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                } else {
                    Text(look.short, color = Color.White, fontWeight = FontWeight.Black,
                        // Grows and shrinks with the icon (19 at the normal 76 size).
                        fontSize = (size.value * (if (look.short.length > 3) 0.2f else 0.25f)).coerceIn(9f, 40f).sp)
                }
                // Glossy highlight across the top half.
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.White.copy(alpha = 0.35f), 0.5f to Color.Transparent)))
            }
            if (badge != null) CountBadge(badge, badgeFont, Modifier.align(Alignment.TopStart).cornerOf(size, 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        val w = labelWidth ?: if (small) 68.dp else 100.dp
        Text(label, color = if (focused) TextBright else TextDim,
            // Shrinks so the whole name fits its space instead of being cut off.
            // Sizes are in dp (not the phone's text-size setting) so the layout math always holds.
            fontSize = with(density) { fitFont(label, w, labelSize ?: if (small) 10f else 12f).dp.toSp() },
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center, softWrap = false,
            modifier = Modifier.width(w))
    }
}

/** A font size (dp) that lets [text] fit in [width] on one line, never bigger than [max]. */
fun fitFont(text: String, width: Dp, max: Float, min: Float = 5f): Float =
    minOf(max, width.value / (text.length.coerceAtLeast(1) * 0.66f)).coerceAtLeast(min)

// Sizes used both to draw a bubble and to work out how big bubbles can be so they all fit.
fun badgeFontFor(size: Dp): Float = (size.value * 0.16f).coerceIn(9f, 14f)
fun badgeHeight(font: Float): Dp = (font * 1.3f + 4f).dp
fun badgeWidth(text: String, font: Float): Dp = (text.length * font * 0.64f + font * 1.1f + 4f).dp
/** How far the count pill rises above the icon's box. */
fun badgeRaise(size: Dp): Dp = (badgeHeight(badgeFontFor(size)) - size * 0.146f - 4.dp).coerceAtLeast(0.dp)
/** How far the pill sticks out past the icon's box on the right. */
fun badgeOverhang(size: Dp, text: String): Dp =
    (badgeWidth(text, badgeFontFor(size)) - size * 0.146f - 4.dp).coerceAtLeast(0.dp)
/** The full height of a [Bubble]. */
fun bubbleHeight(size: Dp, labelSize: Float, vPad: Dp, badge: Boolean): Dp =
    vPad * 2 + (if (badge) badgeRaise(size) else 0.dp) + size + 8.dp + 6.dp + (labelSize * 1.45f).dp

/** The game count pill. */
@Composable
fun CountBadge(text: String, font: Float, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fs = with(density) { font.dp.toSp() }
    val lh = with(density) { (font * 1.3f).dp.toSp() }
    Box(
        modifier.background(Color(0xFF0B1030), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = (font * 0.55f).dp, vertical = 1.dp)
    ) { Text(text, color = TextBright, fontSize = fs, fontWeight = FontWeight.Bold, lineHeight = lh, softWrap = false, maxLines = 1) }
}

/**
 * Places a badge just outside a circle of [circle] size (starting [inset] from the parent's
 * top-left): its bottom-left corner touches the circle's top-right edge, so it never covers it.
 */
fun Modifier.cornerOf(circle: Dp, inset: Dp = 0.dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        val x = (inset + circle * 0.854f).roundToPx()
        val y = (inset + circle * 0.146f).roundToPx() - p.height
        p.place(x, y)
    }
}

/**
 * One game in a list: a coloured letter tile plus the title, on a frosted card
 * that lights up in the system's colours when selected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    title: String,
    subtitle: String? = null,
    look: SystemLook,
    tile: String = title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
    focusRequester: FocusRequester? = null,
    image: ImageBitmap? = null,
    highlight: Boolean = false,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
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
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clip(shape)
            .background(
                if (focused) Brush.horizontalGradient(listOf(look.top.copy(alpha = 0.9f), look.bottom.copy(alpha = 0.9f)))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.04f)))
            )
            .border(if (focused || highlight) 2.dp else 1.dp,
                if (highlight) Color(0xFFFFD25A) else if (focused) Color.White else Color.White.copy(alpha = 0.08f), shape)
            .combinedClickable(remember { MutableInteractionSource() }, indication = null,
                onLongClick = onLongClick, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(look.top, look.bottom)))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) Image(image, contentDescription = title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            else Text(tile, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
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

/** A game as a square tile with its title underneath (the Grid view). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameTile(
    title: String, look: SystemLook, focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "s")
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .padding(6.dp)
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .combinedClickable(remember { MutableInteractionSource() }, indication = null,
                onLongClick = onLongClick, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                .background(Brush.linearGradient(listOf(look.top, look.bottom)))
                .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.2f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, color = if (focused) TextBright else TextDim, fontSize = 11.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

/** A game as one slim line of text (the Compact view): fits many more on screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameRow(
    title: String, look: SystemLook, focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Brush.horizontalGradient(listOf(look.top, look.bottom))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
            .combinedClickable(remember { MutableInteractionSource() }, indication = null,
                onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(look.top))
        Spacer(Modifier.width(10.dp))
        Text(title, color = if (focused) Color.White else TextBright, fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** A small pill button for the slim toolbar above a game list. */
@Composable
fun ToolbarButton(icon: String, label: String, look: SystemLook, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(end = 6.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Brush.horizontalGradient(listOf(look.top, look.bottom))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (focused) Color.White else TextBright, fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}
