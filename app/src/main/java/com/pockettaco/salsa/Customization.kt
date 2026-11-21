package com.pockettaco.salsa

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** How the home screen shows systems. Select on the controller steps through these. */
enum class HomeView(val label: String) { BIG("Big icons"), SMALL("Small icons"), LIST("List") }

/** How a system's game list looks. Select on the controller steps through these. */
enum class GameView(val label: String) { LIST("List"), GRID("Grid"), COMPACT("Compact") }

/** A background choice for the menus. */
data class Backdrop(val name: String, val top: Color, val bottom: Color, val glow: Color)

val BACKDROPS = listOf(
    Backdrop("Night", Color(0xFF0B1030), Color(0xFF05060F), Color(0xFF2B6BFF)),
    Backdrop("Taco", Color(0xFF3A1A05), Color(0xFF120600), Color(0xFFFFB020)),
    Backdrop("Ocean", Color(0xFF06263A), Color(0xFF02101A), Color(0xFF00C2FF)),
    Backdrop("Forest", Color(0xFF0E2A12), Color(0xFF050F07), Color(0xFF4CFF9A)),
    Backdrop("Grape", Color(0xFF2A0B3A), Color(0xFF0E0414), Color(0xFFFF5AD6)),
    Backdrop("Black", Color(0xFF000000), Color(0xFF000000), Color(0xFF222222)),
)

/** Colours you can give a system's icon (top and bottom of its gradient). */
val ICON_COLORS = listOf(
    "Purple" to (Color(0xFF8E7CFF) to Color(0xFF4B2FD6)),
    "Gold" to (Color(0xFFFFD25A) to Color(0xFFE8892B)),
    "Green" to (Color(0xFFB8D86B) to Color(0xFF5E8C31)),
    "Red" to (Color(0xFFFF6B6B) to Color(0xFFB3202A)),
    "Blue" to (Color(0xFF5AA9FF) to Color(0xFF1F4FB8)),
    "Teal" to (Color(0xFF4DD0B0) to Color(0xFF0F7C6A)),
    "Pink" to (Color(0xFFFF7AC6) to Color(0xFFB0247A)),
    "Orange" to (Color(0xFFFF8A65) to Color(0xFFC2410C)),
    "Silver" to (Color(0xFFE0E6F0) to Color(0xFF8792A8)),
    "Charcoal" to (Color(0xFF5B6273) to Color(0xFF1C2030)),
)

/**
 * Everything you change about how the home screen looks: view, order, names,
 * colours, pictures and background. Saved on the phone; reading [version] in the UI
 * makes the screen redraw when anything changes.
 */
object Custom {
    private var prefs: SharedPreferences? = null
    private var dir: File? = null
    val version = mutableIntStateOf(0)
    private val images = HashMap<String, ImageBitmap?>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("taco_custom", Context.MODE_PRIVATE)
        dir = context.filesDir
    }

    fun changed() { images.clear(); version.intValue++ }
    private fun p() = prefs!!
    private fun read() = version.intValue  // registers a read so the screen updates

    var homeView: HomeView
        get() { read(); return runCatching { HomeView.valueOf(p().getString("view", "BIG")!!) }.getOrDefault(HomeView.BIG) }
        set(v) { p().edit().putString("view", v.name).apply(); changed() }

    fun nextView() { homeView = HomeView.entries[(homeView.ordinal + 1) % HomeView.entries.size] }

    fun gameView(id: String): GameView {
        read(); return runCatching { GameView.valueOf(p().getString("gview_$id", "LIST")!!) }.getOrDefault(GameView.LIST)
    }
    fun nextGameView(id: String) {
        val next = GameView.entries[(gameView(id).ordinal + 1) % GameView.entries.size]
        p().edit().putString("gview_$id", next.name).apply(); changed()
    }

    /** System ids in the order you arranged them (systems not listed go after, in the usual order). */
    var order: List<String>
        get() { read(); return p().getString("order", "")!!.split(',').filter { it.isNotBlank() } }
        set(v) { p().edit().putString("order", v.joinToString(",")).apply(); changed() }

    var hidden: Set<String>
        get() { read(); return p().getStringSet("hidden", emptySet())!!.toSet() }
        set(v) { p().edit().putStringSet("hidden", v).apply(); changed() }

    fun name(id: String): String? { read(); return p().getString("name_$id", null) }
    fun setName(id: String, name: String?) {
        p().edit().putString("name_$id", name?.trim()?.takeIf { it.isNotEmpty() }).apply(); changed()
    }

    fun colorIndex(id: String): Int { read(); return p().getInt("color_$id", -1) }
    fun setColor(id: String, index: Int) { p().edit().putInt("color_$id", index).apply(); changed() }

    /** Background: an index into [BACKDROPS], or -1 for your own picture. */
    var background: Int
        get() { read(); return p().getInt("background", 0) }
        set(v) { p().edit().putInt("background", v).apply(); changed() }

    fun iconFile(id: String) = File(dir, "icons/$id.png")
    fun backgroundFile() = File(dir, "background.jpg")

    fun icon(id: String): ImageBitmap? { read(); return image(iconFile(id)) }
    fun backgroundImage(): ImageBitmap? { read(); return image(backgroundFile()) }

    private fun image(file: File): ImageBitmap? = images.getOrPut(file.path) {
        if (!file.exists()) null
        else runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
    }

    /** Favorite games, saved as "system|file" so each system keeps its own. */
    var favorites: Set<String>
        get() { read(); return p().getStringSet("favorites", emptySet())!!.toSet() }
        private set(v) { p().edit().putStringSet("favorites", v).apply(); changed() }

    fun isFavorite(game: Game) = favoriteKey(game) in favorites

    /** The games in [games] that are favorites, keeping their order. */
    fun favoritesIn(games: List<Game>): List<Game> {
        val keys = favorites
        return if (keys.isEmpty()) emptyList() else games.filter { favoriteKey(it) in keys }
    }

    /** Adds or removes a favorite; returns true if it's now a favorite. */
    fun toggleFavorite(game: Game): Boolean {
        val key = favoriteKey(game)
        val now = key !in favorites
        favorites = if (now) favorites + key else favorites - key
        return now
    }

    /** The last 10 different games you started, newest first, as "system|address". */
    val recent: List<String>
        get() { read(); return p().getString("recent", "")!!.split('\n').filter { it.isNotBlank() } }

    fun addRecent(game: Game) {
        val key = "${game.system.id}|${game.uri}"
        val list = (listOf(key) + recent.filter { it != key }).take(10)
        p().edit().putString("recent", list.joinToString("\n")).apply(); changed()
    }

    /** The recent games that are still in your library, newest first. */
    fun recentIn(library: Library): List<Game> {
        val byKey = library.values.flatten().associateBy { "${it.system.id}|${it.uri}" }
        return recent.mapNotNull { byKey[it] }
    }

    fun removeIcon(id: String) { iconFile(id).delete(); changed() }

    /** Clears the view, order, names, colours, pictures and background. */
    fun resetAll() {
        // Favorites and recently played aren't "look"; keep them.
        val keep = p().getStringSet("favorites", emptySet())!!.toSet()
        val recentGames = p().getString("recent", "")
        p().edit().clear().putStringSet("favorites", keep).putString("recent", recentGames).apply()
        File(dir, "icons").deleteRecursively()
        backgroundFile().delete()
        changed()
    }
}

private fun favoriteKey(game: Game) = "${game.system.id}|" + fileKey(game.uri)

/** The name shown for a system: yours if you renamed it. */
fun systemName(system: GameSystem): String = Custom.name(system.id) ?: system.name

/** Systems in your chosen order, without the hidden ones. */
fun <T> arrange(items: List<Pair<GameSystem, T>>): List<Pair<GameSystem, T>> {
    val order = Custom.order
    val hidden = Custom.hidden
    return items.filter { it.first.id !in hidden }.sortedBy { (s, _) ->
        val i = order.indexOf(s.id)
        if (i >= 0) i else order.size + SYSTEMS.indexOf(s)
    }
}

/** Moves one system earlier (-1) or later (+1) among [visible]. */
fun moveSystem(id: String, delta: Int, visible: List<String>) {
    val list = visible.toMutableList()
    val i = list.indexOf(id)
    val j = (i + delta).coerceIn(0, list.size - 1)
    if (i < 0 || i == j) return
    list.removeAt(i); list.add(j, id)
    Custom.order = list + Custom.order.filter { it !in list }
}

/** Puts [id] where [targetId] is now. */
fun placeSystem(id: String, targetId: String, visible: List<String>) {
    val list = visible.toMutableList()
    val target = list.indexOf(targetId)
    if (!list.remove(id) || target < 0) return
    list.add(target.coerceAtMost(list.size), id)
    Custom.order = list + Custom.order.filter { it !in list }
}

/** Pictures bigger than this are refused; anything smaller is resized automatically. */
const val MAX_PICTURE_BYTES = 30L * 1024 * 1024

/**
 * Copies a picture into the app, shrunk to fit [maxSide] pixels. With [square] it is
 * centre-cropped to a square, like a folder picture. Returns an error to show, or null.
 */
fun savePicture(context: Context, uri: Uri, out: File, maxSide: Int, square: Boolean): String? {
    val size = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    }.getOrNull()
    if (size != null && size > MAX_PICTURE_BYTES) return "That picture is over 30 MB. Pick a smaller one."
    val bitmap = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width; val h = info.size.height
            val side = if (square) min(w, h) else max(w, h)
            if (side > maxSide) {
                val scale = maxSide.toFloat() / side
                decoder.setTargetSize(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()))
            }
        }
    }.getOrNull() ?: return "Couldn't open that picture."
    val result = if (square) {
        val s = min(bitmap.width, bitmap.height)
        Bitmap.createBitmap(bitmap, (bitmap.width - s) / 2, (bitmap.height - s) / 2, s, s)
    } else bitmap
    return runCatching {
        out.parentFile?.mkdirs()
        FileOutputStream(out).use {
            if (square) result.compress(Bitmap.CompressFormat.PNG, 100, it)
            else result.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        null
    }.getOrElse { "Couldn't save that picture." }
}

/** Deletes game files. Returns (deleted, failed). */
fun deleteGames(context: Context, games: List<Game>): Pair<Int, Int> {
    var ok = 0; var failed = 0
    for (g in games) {
        val done = runCatching { DocumentsContract.deleteDocument(context.contentResolver, g.uri) }.getOrDefault(false)
        if (done) ok++ else failed++
    }
    return ok to failed
}

/** Keep read and (when Android allows) write access to a picked folder, so deleting games works. */
fun keepFolderAccess(context: Context, uri: Uri) {
    val rw = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { context.contentResolver.takePersistableUriPermission(uri, rw) }.onFailure {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
