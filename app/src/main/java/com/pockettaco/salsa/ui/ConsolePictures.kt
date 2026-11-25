package com.pockettaco.salsa.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockettaco.salsa.Custom
import com.pockettaco.salsa.FocusKick
import com.pockettaco.salsa.MainActivity
import com.pockettaco.salsa.SYSTEMS
import com.pockettaco.salsa.savePicture
import com.pockettaco.salsa.systemName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything about one console's pictures: see them all, turn any off, change the order,
 * add up to 10 of your own and remove those. Works with just the D-pad and A/B.
 * Put it inside a scrolling column.
 */
@Composable
fun ConsolePicturesEditor(activity: MainActivity, id: String, onDone: () -> Unit) {
    val system = SYSTEMS.firstOrNull { it.id == id }
    val look = lookFor(id)
    val scope = rememberCoroutineScope()
    var selected by remember(id) { mutableStateOf<String?>(null) }
    var confirmRemove by remember(id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // B goes from one picture's choices back to the list (then the usual B after that).
    if (selected != null) DisposableEffect(Unit) {
        val previous = activity.onBackRequest
        val handler: () -> Boolean = { selected = null; confirmRemove = false; true }
        activity.onBackRequest = handler
        onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = previous }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            var added = 0; var full = 0; var error: String? = null
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val out = Custom.newOwnFile(id)
                    if (out == null) { full++; continue }
                    val e = runCatching { savePicture(activity, uri, out, maxSide = 1024, square = true) }
                        .getOrElse { "Couldn't open that picture." }
                    if (e == null && out.exists()) {
                        added++
                        withContext(Dispatchers.Main) { Custom.ownPictureAdded(id, out) }
                    } else { out.delete(); error = e }
                }
            }
            busy = false
            val msg = buildList {
                if (added > 0) add(if (added == 1) "Added 1 picture." else "Added $added pictures.")
                if (full > 0) add("$full didn't fit: each console holds ${Custom.MAX_OWN_PICTURES} of your own. Remove one to make room.")
                if (error != null) add(error!!)
            }.joinToString(" ")
            if (msg.isNotEmpty()) Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        }
    }

    val pics = Custom.pictures(id)
    val first = remember(id, selected == null, confirmRemove) { FocusRequester() }
    LaunchedEffect(id, selected == null, confirmRemove, FocusKick.count.intValue) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    val name = system?.let { systemName(it) } ?: id
    val pick = pics.firstOrNull { it.key == selected }
    if (selected != null && pick == null) { selected = null; return }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (pick == null) {
            val own = pics.count { !it.builtIn }
            val onCount = pics.count { it.on }
            Text("Pictures for $name", color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp))
            Text("The pictures that are on take turns on the carousel, in this order. The first one that's on is " +
                "also the console's icon in the other views. If you turn them all off, the console shows its plain " +
                "colored circle with its name. Press A on a picture to turn it off, move it or remove it.",
                color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            Text("$onCount of ${pics.size} on · $own of ${Custom.MAX_OWN_PICTURES} of your own",
                color = Color(0xFF8FD3FF), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            GameCard(
                if (busy) "Adding pictures…" else "Add your own pictures",
                if (own >= Custom.MAX_OWN_PICTURES) "Full (${Custom.MAX_OWN_PICTURES}). Remove one of yours to add another."
                else "Pick one or several from any folder. See-through PNGs stay see-through.",
                look, tile = "+", focusRequester = first,
            ) {
                if (busy) return@GameCard
                if (own >= Custom.MAX_OWN_PICTURES) Toast.makeText(activity,
                    "This console already has ${Custom.MAX_OWN_PICTURES} of your pictures. Remove one first.",
                    Toast.LENGTH_LONG).show()
                else runCatching { picker.launch(arrayOf("image/*")) }.onFailure {
                    Toast.makeText(activity, "Couldn't open the file browser.", Toast.LENGTH_LONG).show()
                }
            }
            if (pics.isEmpty()) Text("No pictures yet. Add your own above.", color = TextDim, fontSize = 13.sp,
                modifier = Modifier.padding(16.dp))
            pics.forEachIndexed { i, pic ->
                PictureCard(
                    pic.label,
                    (if (pic.on) "On" else "Off, not shown") + " · ${i + 1} of ${pics.size}" +
                        if (!pic.builtIn) " · yours" else "",
                    Custom.picImage(id, pic.key), pic.on, look,
                ) { selected = pic.key; confirmRemove = false }
            }
            if (Custom.picturesChanged(id)) GameCard("Turn all back on, usual order",
                "Your own pictures are kept", SETTINGS_LOOK, tile = "↺") { Custom.resetPictures(id) }
            GameCard("Done", null, SETTINGS_LOOK, tile = "✓", onClick = onDone)
        } else {
            val index = pics.indexOf(pick)
            Text(pick.label, color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp))
            Text((if (pick.on) "On" else "Off, not shown") + " · ${index + 1} of ${pics.size} for $name",
                color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Box(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center) {
                val img = Custom.picImage(id, pick.key)
                if (img != null) Image(img, contentDescription = pick.label, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().alpha(if (pick.on) 1f else 0.4f))
                else Text("Can't show this picture", color = TextDim)
            }
            if (confirmRemove) {
                Text("Remove this picture from Taco Launcher? The original stays in your phone's files.",
                    color = Color(0xFFFFC107), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
                GameCard("Keep it", null, SETTINGS_LOOK, tile = "←", focusRequester = first) { confirmRemove = false }
                GameCard("Yes, remove it", null, DANGER_PIC_LOOK, tile = "✕") {
                    Custom.removeOwnPicture(id, pick.key); confirmRemove = false; selected = null
                }
            } else {
                GameCard(if (pick.on) "Turn off" else "Turn on",
                    if (pick.on) "It stops showing; you can turn it back on any time" else "It shows again",
                    look, tile = if (pick.on) "○" else "●", focusRequester = first) {
                    Custom.setPictureOn(id, pick.key, !pick.on)
                }
                GameCard("◀  Move earlier", if (index == 0) "Already first" else null, look, tile = "◀") {
                    Custom.movePicture(id, pick.key, -1)
                }
                GameCard("Move later  ▶", if (index == pics.size - 1) "Already last" else null, look, tile = "▶") {
                    Custom.movePicture(id, pick.key, 1)
                }
                GameCard("Make it first", if (index == 0) "Already first" else "Shows first, and becomes the icon if it's on",
                    look, tile = "⇤") {
                    Custom.movePicture(id, pick.key, -1000)
                }
                if (!pick.builtIn) GameCard("Remove this picture…", "Asks first", DANGER_PIC_LOOK, tile = "✕") {
                    confirmRemove = true
                }
                else Text("Built-in pictures can't be deleted, only turned off.", color = TextDim, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp))
                GameCard("Back to all pictures", null, SETTINGS_LOOK, tile = "←") { selected = null }
            }
        }
    }
}

private val DANGER_PIC_LOOK = SystemLook("!", Color(0xFFFF6B6B), Color(0xFF8A1020))

/** One picture in the list, with a big preview; dimmed when it's off. */
@Composable
private fun PictureCard(title: String, subtitle: String, image: androidx.compose.ui.graphics.ImageBitmap?,
                        on: Boolean, look: SystemLook, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "s")
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (focused) Brush.horizontalGradient(listOf(look.top.copy(alpha = 0.9f), look.bottom.copy(alpha = 0.9f)))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.04f))))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), shape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center) {
            if (image != null) Image(image, contentDescription = title, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().alpha(if (on) 1f else 0.35f))
            else Text("?", color = TextDim, fontSize = 22.sp)
            if (!on) Text("OFF", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp,
                modifier = Modifier.background(Color(0xCC8A1020), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextBright, fontSize = 16.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = if (focused) Color.White else TextDim, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        if (focused) Text("▶", color = Color.White, fontSize = 14.sp)
    }
}

/** Settings page: pick a console, then change its pictures. */
@Composable
fun ConsolePicturesPage(activity: MainActivity) {
    var chosen by remember { mutableStateOf<String?>(null) }
    val c = chosen
    if (c != null) {
        DisposableEffect(Unit) {
            val previous = activity.onBackRequest
            val handler: () -> Boolean = { chosen = null; true }
            activity.onBackRequest = handler
            onDispose { if (activity.onBackRequest === handler) activity.onBackRequest = previous }
        }
        ConsolePicturesEditor(activity, c) { chosen = null }
        return
    }
    SlideshowSettings()
    SettingHeader("Pictures for each console",
        "Pick a console to see all its pictures, turn some off, change their order, or add your own. " +
            "You can also get here from the home screen: press Start on a console, then Pictures.")
    SYSTEMS.forEach { s ->
        val pics = Custom.pictures(s.id)
        val off = pics.count { !it.on }
        val own = pics.count { !it.builtIn }
        GameCard(systemName(s),
            "${pics.size} pictures" + (if (own > 0) " · $own yours" else "") + (if (off > 0) " · $off off" else ""),
            lookFor(s.id), tile = lookFor(s.id).short, image = Custom.icon(s.id)) { chosen = s.id }
    }
}

/** The slideshow switches: on/off and how long each picture stays. */
@Composable
fun SlideshowSettings() {
    SettingSwitch("Console picture slideshow",
        "On the carousel, a console's pictures slowly fade from one to the next while you rest on it. " +
            "Turn off to always show just its first picture.",
        Custom.slideshow) { Custom.slideshow = it }
    if (Custom.slideshow) SettingChoice("Time for each picture", null,
        listOf(
            Triple(2000, "Quick", "About 2 seconds per picture"),
            Triple(3500, "Normal", "About 3½ seconds per picture (the usual)"),
            Triple(6000, "Slow", "About 6 seconds per picture"),
            Triple(10000, "Very slow", "About 10 seconds per picture"),
        ),
        Custom.slideMillis.let { m -> listOf(2000, 3500, 6000, 10000).minByOrNull { kotlin.math.abs(it - m) }!! },
    ) { Custom.slideMillis = it }
}
