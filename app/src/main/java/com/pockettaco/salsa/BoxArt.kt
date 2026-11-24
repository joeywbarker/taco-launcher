package com.pockettaco.salsa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cover pictures for games, downloaded from the free libretro-thumbnails collection (the same
 * pictures RetroArch shows) the first time a game appears on screen, then kept on the phone.
 *
 * Game file names rarely match the collection's names exactly ("Pokemon - Crystal Version"
 * vs "Pokemon - Crystal Version (USA, Europe) (Rev 1)"), so each console's list of names is
 * fetched once and the closest title is picked, preferring the USA release.
 */
object BoxArt {
    /** Taco Launcher's console ids → the collection's names. Arcade is left out: its file names are short codes. */
    private val COLLECTION = mapOf(
        "gba" to "Nintendo - Game Boy Advance", "gbc" to "Nintendo - Game Boy Color", "gb" to "Nintendo - Game Boy",
        "nes" to "Nintendo - Nintendo Entertainment System", "snes" to "Nintendo - Super Nintendo Entertainment System",
        "genesis" to "Sega - Mega Drive - Genesis", "sms" to "Sega - Master System - Mark III", "gg" to "Sega - Game Gear",
        "pce" to "NEC - PC Engine - TurboGrafx 16", "ngp" to "SNK - Neo Geo Pocket Color", "nds" to "Nintendo - Nintendo DS",
        "n64" to "Nintendo - Nintendo 64", "psx" to "Sony - PlayStation", "psp" to "Sony - PlayStation Portable",
        "dreamcast" to "Sega - Dreamcast", "saturn" to "Sega - Saturn", "segacd" to "Sega - Mega-CD - Sega CD",
        "sega32x" to "Sega - 32X", "sg1000" to "Sega - SG-1000", "ps2" to "Sony - PlayStation 2",
        "gc" to "Nintendo - GameCube", "wii" to "Nintendo - Wii", "3ds" to "Nintendo - Nintendo 3DS",
        "fds" to "Nintendo - Family Computer Disk System", "virtualboy" to "Nintendo - Virtual Boy",
        "pokemini" to "Nintendo - Pokemon Mini", "pcecd" to "NEC - PC Engine CD - TurboGrafx-CD",
        "ws" to "Bandai - WonderSwan", "wsc" to "Bandai - WonderSwan Color", "atari2600" to "Atari - 2600",
        "atari5200" to "Atari - 5200", "atari7800" to "Atari - 7800", "lynx" to "Atari - Lynx",
        "jaguar" to "Atari - Jaguar", "colecovision" to "Coleco - ColecoVision", "intellivision" to "Mattel - Intellivision",
        "msx" to "Microsoft - MSX", "c64" to "Commodore - 64",
        "ps3" to "Sony - PlayStation 3", "xbox" to "Microsoft - Xbox", "x360" to "Microsoft - Xbox 360",
        "wiiu" to "Nintendo - Wii U", "vita" to "Sony - PlayStation Vita",
    )

    private lateinit var root: File
    private val memory = mutableMapOf<String, ImageBitmap?>()
    /** Bumped when a picture arrives, so lists redraw. */
    val version = mutableIntStateOf(0)
    private val busy = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val gate = java.util.concurrent.Semaphore(3)

    fun init(context: Context) { root = File(context.filesDir, "boxart") }

    fun supports(system: String) = system in COLLECTION

    private fun key(game: Game) = "${game.system.id}/" + game.file.substringBeforeLast('.').replace('/', '_')
    private fun picFile(game: Game) = File(root, key(game) + ".jpg")
    private fun missFile(game: Game) = File(root, key(game) + ".none")

    /** The picture if it's on the phone already (never waits for the internet). */
    fun cached(game: Game): ImageBitmap? {
        version.intValue // redraw when pictures arrive
        val k = key(game)
        return synchronized(memory) {
            memory.getOrPut(k) {
                picFile(game).takeIf { it.exists() }?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
            }
        }
    }

    /** Downloads the picture if we don't have it and haven't already looked. Safe to call often. */
    suspend fun fetch(game: Game) = withContext(Dispatchers.IO) {
        val system = game.system.id
        val collection = COLLECTION[system] ?: return@withContext
        if (picFile(game).exists() || missFile(game).exists()) return@withContext
        if (!busy.add(key(game))) return@withContext
        gate.acquire()
        try {
            val names = index(system, collection) ?: return@withContext
            val best = closest(game.file.substringBeforeLast('.'), names)
            val out = picFile(game).apply { parentFile?.mkdirs() }
            if (best == null) { missFile(game).writeText(""); return@withContext }
            val url = "https://raw.githubusercontent.com/libretro-thumbnails/${repo(collection)}/master/Named_Boxarts/" +
                URLEncoder.encode("$best.png", "UTF-8").replace("+", "%20")
            val bytes = get(url) ?: return@withContext
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run { missFile(game).writeText(""); return@withContext }
            val scale = minOf(1f, 400f / maxOf(bmp.width, bmp.height))
            val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            out.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            synchronized(memory) { memory.remove(key(game)) }
            withContext(Dispatchers.Main) { version.intValue++ }
        } catch (_: Exception) {
            // No internet or a hiccup: try again next time.
        } finally {
            gate.release(); busy.remove(key(game))
        }
    }

    /** Deletes every downloaded picture (they come back as games show up again). */
    fun clear() {
        if (::root.isInitialized) root.deleteRecursively()
        synchronized(memory) { memory.clear() }
        version.intValue++
    }

    private fun repo(collection: String) = collection.replace(' ', '_')

    /** The names of every cover for one console, fetched once a month and kept. */
    private fun index(system: String, collection: String): List<String>? {
        val file = File(root, "$system/_index.txt")
        val month = 30L * 24 * 3600 * 1000
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < month) return file.readLines()
        val json = runCatching {
            get("https://api.github.com/repos/libretro-thumbnails/${repo(collection)}/git/trees/master:Named_Boxarts")
        }.getOrNull()?.decodeToString()
        if (json == null) {
            if (file.exists()) return file.readLines()
            // Couldn't get the list: don't ask again for a day.
            file.parentFile?.mkdirs(); file.writeText("")
            file.setLastModified(System.currentTimeMillis() - month + 24L * 3600 * 1000)
            return null
        }
        val tree = JSONObject(json).optJSONArray("tree") ?: return null
        val names = (0 until tree.length()).map { tree.getJSONObject(it).getString("path") }
            .filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }
        file.parentFile?.mkdirs()
        file.writeText(names.joinToString("\n"))
        return names
    }

    /** "Pokemon - Crystal Version (USA) [!]" → "pokemoncrystalversion". */
    private fun plain(s: String) = s.replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), "")
        .replace("&", "and").lowercase().filter { it.isLetterOrDigit() }

    private fun closest(file: String, names: List<String>): String? {
        names.firstOrNull { it == file }?.let { return it }
        val want = plain(file)
        if (want.isEmpty()) return null
        val same = names.filter { plain(it) == want }
        if (same.isEmpty()) return null
        fun rank(n: String) = when {
            "(USA" in n -> 0; "(World" in n -> 1; "(Europe" in n -> 2; else -> 3
        } * 10 + (if ("Rev" in n || "Beta" in n || "Proto" in n) 1 else 0)
        return same.minByOrNull(::rank)
    }

    private fun get(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "TacoLauncher")
        return try {
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } finally { c.disconnect() }
    }
}

/** A game's cover picture: shows it if it's here, and fetches it in the background if not. */
@Composable
fun rememberBoxArt(context: Context, game: Game): ImageBitmap? {
    val on = remember { Settings(context).boxArt }
    if (!on || !BoxArt.supports(game.system.id)) return null
    LaunchedEffect(game.uri) { BoxArt.fetch(game) }
    return BoxArt.cached(game)
}
