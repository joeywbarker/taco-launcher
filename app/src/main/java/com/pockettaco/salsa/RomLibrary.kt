package com.pockettaco.salsa

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class Game(val title: String, val uri: Uri, val system: GameSystem)

/**
 * Looks through the folder the user picked (and its subfolders) for game files
 * and sorts them by system using the file ending. Runs off the main thread.
 */
fun scanRoms(context: Context, folder: Uri): Map<GameSystem, List<Game>> {
    val root = DocumentFile.fromTreeUri(context, folder) ?: return emptyMap()
    val byExt = SYSTEMS.flatMap { s -> s.extensions.map { it to s } }.toMap()
    val found = mutableListOf<Game>()

    fun walk(dir: DocumentFile, depth: Int, folderSystem: GameSystem?) {
        if (depth > 4) return
        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) walk(f, depth + 1, systemFromText(name) ?: folderSystem)
            else {
                val ext = name.substringAfterLast('.', "").lowercase()
                val base = name.substringBeforeLast('.')
                // .zip/.7z could be any console: use a tag in the name ("# NDS")
                // or the folder it sits in ("nds", "Nintendo DS").
                val fromArchive = if (ext in ARCHIVES) (systemFromTag(base) ?: folderSystem) else null
                val sys = byExt[ext] ?: fromArchive ?: continue
                found += Game(cleanTitle(base), f.uri, sys)
            }
        }
    }
    walk(root, 0, null)
    return found.groupBy { it.system }
        .mapValues { (_, games) -> games.sortedBy { it.title.lowercase() } }
        .toSortedMap(compareBy { SYSTEMS.indexOf(it) })
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
