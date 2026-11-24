package com.pockettaco.salsa

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Puts the game picture in the right spot when RetroArch opens full screen.
 *
 * RetroArch reads its settings from the file we name when we start it. We write a small
 * settings file of our own that sets where the picture sits (above the Taco, the same spot
 * the pop-up window used), followed by "#include" of RetroArch's normal settings, so all your
 * other RetroArch settings (buttons, hotkeys, cores...) still apply. Our lines come first,
 * and in RetroArch's settings files the first value for a setting wins.
 *
 * The file goes in your games folder, because RetroArch can always read that folder
 * (it loads the games from there).
 */
object RetroArchPlacement {
    private const val FILE_NAME = ".taco-retroarch.cfg"
    const val PORT = 55355

    /** Sends RetroArch a command ("MENU_TOGGLE", "QUIT"...) on this phone. */
    fun send(command: String) {
        Thread {
            runCatching {
                java.net.DatagramSocket().use { s ->
                    val data = command.toByteArray()
                    s.send(java.net.DatagramPacket(data, data.size, java.net.InetAddress.getByName("127.0.0.1"), PORT))
                }
            }
        }.start()
    }

    /** Rewrites [intent]'s settings file so the picture lands where it should. False if it couldn't. */
    fun apply(activity: MainActivity, intent: Intent, system: GameSystem): Boolean = runCatching {
        val mainConfig = intent.getStringExtra("CONFIGFILE") ?: return false
        val folder = activity.settings.folderFor(system.id) ?: activity.settings.romFolder ?: return false
        val screen = activity.windowManager.currentWindowMetrics.bounds
        val w = screen.width().toFloat()
        val h = screen.height().toFloat()
        val shape = SCREEN_SHAPES[system.id] ?: 4f / 3f
        // Where the pop-up window would have gone; centre the picture on that spot.
        val spot = gameWindowRect(activity.settings, screen.width(), screen.height(), shape)
        // RetroArch fits the picture to the width in portrait, keeping its shape.
        val picH = minOf(w / shape, h)
        val bias = if (h - picH < 1f) 0f
        else ((spot.exactCenterY() - picH / 2f) / (h - picH)).coerceIn(0f, 1f)
        val text = buildString {
            append("# Written by Taco Launcher each time it starts a game. Your own RetroArch\n")
            append("# settings are in the file included below; change them by opening RetroArch itself.\n")
            append("video_viewport_bias_portrait_x = \"0.500000\"\n")
            append("video_viewport_bias_portrait_y = \"").append(String.format(java.util.Locale.US, "%.6f", bias)).append("\"\n")
            append("video_scale_integer = \"false\"\n")
            // Lets Taco Launcher's hotkeys open RetroArch's menu and quit it properly
            // (quitting for real is what lets the next game start without a black screen).
            append("network_cmd_enable = \"true\"\n")
            append("network_cmd_port = \"$PORT\"\n")
            append("quit_press_twice = \"false\"\n")
            append("#include \"").append(mainConfig).append("\"\n")
        }
        val doc = findOrCreate(activity, folder) ?: return false
        activity.contentResolver.openOutputStream(doc, "wt")?.use { it.write(text.toByteArray()) } ?: return false
        intent.putExtra("CONFIGFILE", toFilePath(doc))
        true
    }.getOrDefault(false)

    private fun findOrCreate(activity: MainActivity, tree: Uri): Uri? {
        val resolver = activity.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        resolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == FILE_NAME) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        return DocumentsContract.createDocument(resolver, parent, "application/octet-stream", FILE_NAME)
    }
}
