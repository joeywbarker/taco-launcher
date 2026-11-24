package com.pockettaco.salsa

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** [title] is the file name without its extension; [file] is the whole file name. */
data class Game(val title: String, val uri: Uri, val system: GameSystem, val file: String = title) {
    /** The name to show, following the "Game names" choices in Settings. */
    val label: String get() = GameNames.label(file, title, system.id)
}

typealias Library = Map<GameSystem, List<Game>>

/**
 * Looks through the folder the user picked (and its subfolders) for game files
 * and sorts them by system. Runs off the main thread and calls [onProgress]
 * with the number of games found so far, so the screen can show a progress bar.
 *
 * Uses one database-style query per folder instead of asking about every file
 * one at a time, which is much faster with thousands of games.
 */
fun scanRoms(
    context: Context,
    folder: Uri,
    onlySystem: GameSystem? = null,
    onProgress: (Int) -> Unit = {},
): Library = organise(scanList(context, folder, onlySystem, onProgress))

/**
 * Scans the main games folder plus any per-system folders set in Settings.
 * A system with its own folder takes its games ONLY from that folder (the main
 * folder's copies of that system are left out, so nothing is listed twice).
 * In a per-system folder, every matching file (and every .zip/.7z) counts as that system.
 */
fun scanAll(context: Context, settings: Settings, onProgress: (Int) -> Unit = {}): Library {
    val all = mutableListOf<Game>()
    fun add(list: List<Game>) { all += list; onProgress(all.size) }
    val ownFolder = SYSTEMS.filter { s ->
        settings.folderFor(s.id)?.let { canStillRead(context, it) } == true
    }.toSet()
    settings.romFolder?.takeIf { canStillRead(context, it) }?.let { main ->
        add(scanList(context, main, null) { n -> onProgress(all.size + n) }.filter { it.system !in ownFolder })
    }
    for (system in ownFolder) {
        val folder = settings.folderFor(system.id) ?: continue
        add(scanList(context, folder, system) { n -> onProgress(all.size + n) })
    }
    // The same file reached through two folders has two different addresses; match on the file itself.
    return organise(all.distinctBy { "${it.system.id}/" + fileKey(it.uri) })
}

/** "primary:ROMs/GBA/Game.gba"-style id of a file, the same however it was reached. */
fun fileKey(uri: Uri): String =
    runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: uri.toString()

/** A short, readable name for a picked folder, like "ROMs/GBA". */
fun folderLabel(folder: Uri?): String {
    if (folder == null) return "not set"
    val id = runCatching { DocumentsContract.getTreeDocumentId(folder) }.getOrNull() ?: return folder.toString()
    return id.substringAfter(':').ifEmpty { "Phone storage" }
}

private fun scanList(context: Context, folder: Uri, onlySystem: GameSystem?, onProgress: (Int) -> Unit): List<Game> {
    val byExt = SYSTEMS.flatMap { s -> s.extensions.map { it to s } }.toMap()
    val found = mutableListOf<Game>()
    val resolver = context.contentResolver
    val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )

    fun walk(docId: String, depth: Int, folderSystem: GameSystem?) {
        if (depth > 5) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, docId)
        val subfolders = mutableListOf<Pair<String, String>>()
        runCatching {
            resolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subfolders += id to name
                        continue
                    }
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val base = name.substringBeforeLast('.')
                    // .zip/.7z could be any console: use a tag in the name ("# NDS")
                    // or the folder it sits in ("nds", "Nintendo DS").
                    val fromArchive = if (ext in ARCHIVES) (systemFromTag(base) ?: folderSystem) else null
                    // Inside a folder named for a system (gamegear, dreamcast, "Sega - Saturn"...),
                    // that system wins for any of its file types, including shared ones like .iso.
                    val sys = if (onlySystem != null) {
                        if (fits(onlySystem, ext)) onlySystem else continue
                    } else folderSystem?.takeIf { fits(it, ext) && systemFromTag(base) == null }
                        ?: byExt[ext] ?: fromArchive ?: continue
                    found += Game(base, DocumentsContract.buildDocumentUriUsingTree(folder, id), sys, name)
                    if (found.size % 25 == 0) onProgress(found.size)
                }
            }
        }
        for ((id, name) in subfolders) walk(id, depth + 1, systemFromText(name) ?: folderSystem)
    }
    walk(DocumentsContract.getTreeDocumentId(folder), 0, onlySystem)
    onProgress(found.size)
    return found
}

private fun organise(games: List<Game>): Library = games.groupBy { it.system }
    .mapValues { (_, list) -> list.sortedBy { GameNames.sortKey(it.title) } }
    .toSortedMap(compareBy { SYSTEMS.indexOf(it) })

/** True if Android still lets us read the folder the user picked before. */
fun canStillRead(context: Context, folder: Uri): Boolean =
    context.contentResolver.persistedUriPermissions.any { it.uri == folder && it.isReadPermission }

/* ---------- Saved game list, so it appears instantly next time ---------- */

private fun cacheFile(context: Context) = File(context.filesDir, "library.tsv")

fun saveLibrary(context: Context, library: Library) = runCatching {
    cacheFile(context).bufferedWriter().use { w ->
        library.values.flatten().forEach { w.write("${it.system.id}\t${it.uri}\t${it.title}\t${it.file}\n") }
    }
}

fun loadLibrary(context: Context): Library {
    val file = cacheFile(context)
    if (!file.exists()) return emptyMap()
    val systems = SYSTEMS.associateBy { it.id }
    val games = runCatching {
        file.readLines().mapNotNull { line ->
            val parts = line.split('\t', limit = 4)
            if (parts.size < 3) return@mapNotNull null
            val sys = systems[parts[0]] ?: return@mapNotNull null
            Game(parts[2], Uri.parse(parts[1]), sys, parts.getOrElse(3) { parts[2] })
        }
    }.getOrDefault(emptyList())
    return organise(games)
}

private val ARCHIVES = setOf("zip", "7z")

/** True if a file with ending [ext] can be a game for [s] when it sits in that system's folder. */
private fun fits(s: GameSystem, ext: String) = ext in s.extensions || ext in s.inFolder || ext in ARCHIVES

/** Folder names people commonly use for each system (compared without spaces or punctuation). */
private val FOLDER_NAMES = mapOf(
    "gb" to listOf("gb", "gameboy"),
    "gbc" to listOf("gbc", "gameboycolor"),
    "gba" to listOf("gba", "gameboyadvance"),
    "nes" to listOf("nes", "famicom", "nintendoentertainmentsystem"),
    "snes" to listOf("snes", "supernintendo", "superfamicom", "sfc", "supernintendoentertainmentsystem", "snesna"),
    "n64" to listOf("n64", "nintendo64"),
    "sms" to listOf("sms", "mastersystem", "segamastersystem"),
    "gg" to listOf("gg", "gamegear", "segagamegear"),
    "pce" to listOf("pce", "pcengine", "tg16", "turbografx", "turbografx16", "supergrafx"),
    "ngp" to listOf("ngp", "ngpc", "neogeopocket", "neogeopocketcolor"),
    "nds" to listOf("nds", "ds", "nintendods"),
    "genesis" to listOf("genesis", "megadrive", "md", "segagenesis", "segamegadrive"),
    "psx" to listOf("psx", "ps1", "playstation", "playstation1", "sonyplaystation"),
    "psp" to listOf("psp", "playstationportable"),
    "dreamcast" to listOf("dreamcast", "dc", "segadreamcast"),
    "saturn" to listOf("saturn", "segasaturn"),
    "segacd" to listOf("segacd", "megacd", "segamegacd"),
    "sega32x" to listOf("sega32x", "32x", "megadrive32x", "genesis32x"),
    "sg1000" to listOf("sg1000", "segasg1000"),
    "ps2" to listOf("ps2", "playstation2", "sonyplaystation2"),
    "gc" to listOf("gc", "gamecube", "nintendogamecube"),
    "wii" to listOf("wii", "nintendowii"),
    "3ds" to listOf("3ds", "n3ds", "nintendo3ds"),
    "ps3" to listOf("ps3", "playstation3", "sonyplaystation3"),
    "xbox" to listOf("xbox", "microsoftxbox", "xboxoriginal"),
    "x360" to listOf("x360", "xbox360", "microsoftxbox360"),
    "fds" to listOf("fds", "famicomdisksystem"),
    "virtualboy" to listOf("virtualboy", "vb"),
    "pokemini" to listOf("pokemini", "pokemonmini"),
    "pcecd" to listOf("pcecd", "pcenginecd", "tgcd", "turbografxcd", "turbografx16cd"),
    "ws" to listOf("ws", "wonderswan"),
    "wsc" to listOf("wsc", "wonderswancolor"),
    "neogeo" to listOf("neogeo", "neogeoaes", "neogeomvs"),
    "arcade" to listOf("arcade", "mame", "fbneo", "fba", "finalburnneo", "cps1", "cps2", "cps3", "mame2003", "mame2003plus"),
    "atari2600" to listOf("atari2600", "2600", "a2600"),
    "atari5200" to listOf("atari5200", "5200"),
    "atari7800" to listOf("atari7800", "7800"),
    "lynx" to listOf("lynx", "atarilynx"),
    "jaguar" to listOf("jaguar", "atarijaguar"),
    "colecovision" to listOf("colecovision", "coleco"),
    "intellivision" to listOf("intellivision", "intv"),
    "msx" to listOf("msx", "msx1", "msx2"),
    "c64" to listOf("c64", "commodore64"),
)

/** "Sega - Game Gear", "game_gear", "GameGear" all become "segagamegear"/"gamegear". */
private fun normalise(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

private val MAKERS = listOf("nintendo", "sega", "sony", "nec", "snk", "atari", "bandai", "microsoft", "commodore")

private fun systemFromText(text: String): GameSystem? {
    val t = normalise(text)
    if (t.isEmpty()) return null
    val bare = MAKERS.firstOrNull { t.startsWith(it) && t.length > it.length }?.let { t.removePrefix(it) }
    val id = FOLDER_NAMES.entries.firstOrNull { (_, names) -> t in names || (bare != null && bare in names) }?.key
    return SYSTEMS.firstOrNull { it.id == id }
}

/** Reads tags like "Contra 4 # NDS" or "Contra 4 (NDS)" or "[GBA]". */
private fun systemFromTag(name: String): GameSystem? {
    val tag = Regex("""(?:#\s*|[(\[])\s*([A-Za-z0-9 ]+?)\s*[)\]]?\s*$""").find(name)
        ?.groupValues?.get(1) ?: return null
    return systemFromText(tag)
}

