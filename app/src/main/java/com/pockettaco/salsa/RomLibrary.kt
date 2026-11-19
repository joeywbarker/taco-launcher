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

    fun walk(dir: DocumentFile, depth: Int) {
        if (depth > 4) return
        for (f in dir.listFiles()) {
            if (f.isDirectory) walk(f, depth + 1)
            else {
                val name = f.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val sys = byExt[ext] ?: continue
                found += Game(name.substringBeforeLast('.'), f.uri, sys)
            }
        }
    }
    walk(root, 0)
    return found.groupBy { it.system }
        .mapValues { (_, games) -> games.sortedBy { it.title.lowercase() } }
        .toSortedMap(compareBy { SYSTEMS.indexOf(it) })
}
