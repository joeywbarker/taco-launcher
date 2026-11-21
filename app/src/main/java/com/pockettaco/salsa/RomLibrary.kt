package com.pockettaco.salsa

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

data class Game(val title: String, val uri: Uri, val system: GameSystem)

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
private fun fileKey(uri: Uri): String =
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
                    val sys = if (onlySystem != null) {
                        if (ext in onlySystem.extensions || ext in ARCHIVES) onlySystem else continue
                    } else byExt[ext] ?: fromArchive ?: continue
                    found += Game(cleanTitle(base), DocumentsContract.buildDocumentUriUsingTree(folder, id), sys)
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
    .mapValues { (_, list) -> list.sortedBy { it.title.lowercase() } }
    .toSortedMap(compareBy { SYSTEMS.indexOf(it) })

/** True if Android still lets us read the folder the user picked before. */
fun canStillRead(context: Context, folder: Uri): Boolean =
    context.contentResolver.persistedUriPermissions.any { it.uri == folder && it.isReadPermission }

/* ---------- Saved game list, so it appears instantly next time ---------- */

private fun cacheFile(context: Context) = File(context.filesDir, "library.tsv")

fun saveLibrary(context: Context, library: Library) = runCatching {
    cacheFile(context).bufferedWriter().use { w ->
        library.values.flatten().forEach { w.write("${it.system.id}\t${it.uri}\t${it.title}\n") }
    }
}

fun loadLibrary(context: Context): Library {
    val file = cacheFile(context)
    if (!file.exists()) return emptyMap()
    val systems = SYSTEMS.associateBy { it.id }
    val games = runCatching {
        file.readLines().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size < 3) return@mapNotNull null
            val sys = systems[parts[0]] ?: return@mapNotNull null
            Game(parts[2], Uri.parse(parts[1]), sys)
        }
    }.getOrDefault(emptyList())
    return organise(games)
}

private val ARCHIVES = setOf("zip", "7z")

/** Folder names people commonly use for each system. */
private val FOLDER_NAMES = mapOf(
    "gb" to listOf("gb", "gameboy", "game boy"),
    "gbc" to listOf("gbc", "gameboy color", "game boy color"),
    "gba" to listOf("gba", "gameboy advance", "game boy advance"),
    "nes" to listOf("nes", "famicom"),
    "snes" to listOf("snes", "super nintendo", "super famicom", "sfc"),
    "n64" to listOf("n64", "nintendo 64"),
    "sms" to listOf("sms", "master system", "mastersystem"),
    "gg" to listOf("gg", "game gear", "gamegear"),
    "pce" to listOf("pce", "pc engine", "pcengine", "tg16", "turbografx", "turbografx-16", "turbografx16"),
    "ngp" to listOf("ngp", "ngpc", "neo geo pocket", "neogeo pocket"),
    "nds" to listOf("nds", "ds", "nintendo ds"),
    "genesis" to listOf("genesis", "megadrive", "mega drive", "md"),
    "psx" to listOf("psx", "ps1", "playstation"),
    "psp" to listOf("psp"),
)

private fun systemFromText(text: String): GameSystem? {
    val t = text.lowercase().trim()
    val id = FOLDER_NAMES.entries.firstOrNull { (_, names) -> t in names }?.key
    return SYSTEMS.firstOrNull { it.id == id }
}

/** Reads tags like "Contra 4 # NDS" or "Contra 4 (NDS)" or "[GBA]". */
private fun systemFromTag(name: String): GameSystem? {
    val tag = Regex("""(?:#\s*|[(\[])\s*([A-Za-z0-9 ]+?)\s*[)\]]?\s*$""").find(name)
        ?.groupValues?.get(1) ?: return null
    return systemFromText(tag)
}

private fun cleanTitle(name: String): String =
    name.replace(Regex("""\s*#\s*[A-Za-z0-9 ]+$"""), "").trim()
