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

    /** How the picture is placed, picked per game (hold A on a game → Screen layout). */
    enum class Layout(val key: String, val label: String, val about: String) {
        AUTO("auto", "Auto", "Keeps its shape, centred on your game spot above the Taco"),
        TOP("top", "Top", "Keeps its shape, starts at the very top of the screen"),
        TALL("tall", "Tall game (top down to the Taco)",
            "For upright arcade games like 1941: fills from the top down to the Taco's edge"),
        ;
        companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: AUTO }
    }

    /**
     * Where the picture goes, as a share of the screen (0..1 for left, top, right, bottom).
     * Used both to tell RetroArch and to draw the little previews.
     */
    fun pictureRect(layout: Layout, w: Float, h: Float, covered: Float, spotCenterY: Float, shape: Float): android.graphics.RectF {
        val free = h * (1f - covered)
        return when (layout) {
            Layout.TALL -> {
                // Upright arcade screens are 3:4 (narrower than tall).
                var ph = free
                var pw = ph * 3f / 4f
                if (pw > w) { pw = w; ph = pw * 4f / 3f }
                val left = (w - pw) / 2f
                android.graphics.RectF(left / w, 0f, (left + pw) / w, ph / h)
            }
            else -> {
                val picH = minOf(w / shape, h)
                val top = if (layout == Layout.TOP) 0f
                else (spotCenterY - picH / 2f).coerceIn(0f, h - picH)
                android.graphics.RectF(0f, top / h, 1f, (top + picH) / h)
            }
        }
    }

    /** Rewrites [intent]'s settings file so the picture lands where it should. False if it couldn't. */
    fun apply(activity: MainActivity, intent: Intent, system: GameSystem): Boolean = runCatching {
        val mainConfig = intent.getStringExtra("CONFIGFILE") ?: return false
        val folder = activity.settings.folderFor(system.id) ?: activity.settings.romFolder ?: return false
        val screen = activity.windowManager.currentWindowMetrics.bounds
        val w = screen.width().toFloat()
        val h = screen.height().toFloat()
        val shape = SCREEN_SHAPES[system.id] ?: 4f / 3f
        // Where the pop-up window would have gone; "Auto" centres the picture on that spot.
        val spot = gameWindowRect(activity.settings, screen.width(), screen.height(), shape)
        val file = intent.getStringExtra("ROM")?.substringAfterLast('/')
        val layout = Layout.of(file?.let { activity.settings.layoutForGame(system.id, it) })
        val r = pictureRect(layout, w, h, activity.settings.coveredFraction, spot.exactCenterY(), shape)
        fun f(v: Float) = String.format(java.util.Locale.US, "%.6f", v)
        val text = buildString {
            append("# Written by Taco Launcher each time it starts a game. Your own RetroArch\n")
            append("# settings are in the file included below; change them by opening RetroArch itself.\n")
            append("video_scale_integer = \"false\"\n")
            if (layout == Layout.TALL) {
                // An exact box: RetroArch's "custom" picture size (number 23 in its list).
                append("aspect_ratio_index = \"23\"\n")
                append("custom_viewport_x = \"").append((r.left * w).toInt()).append("\"\n")
                append("custom_viewport_y = \"").append((r.top * h).toInt()).append("\"\n")
                append("custom_viewport_width = \"").append((r.width() * w).toInt()).append("\"\n")
                append("custom_viewport_height = \"").append((r.height() * h).toInt()).append("\"\n")
            } else {
                val picH = r.height() * h
                val bias = if (h - picH < 1f) 0f else (r.top * h / (h - picH)).coerceIn(0f, 1f)
                append("aspect_ratio_index = \"22\"\n") // the game's own shape
                append("video_viewport_bias_portrait_x = \"0.500000\"\n")
                append("video_viewport_bias_portrait_y = \"").append(f(bias)).append("\"\n")
            }
            // Lets Taco Launcher's hotkeys open RetroArch's menu and quit it properly
            // (quitting for real is what lets the next game start without a black screen).
            append("network_cmd_enable = \"true\"\n")
            append("network_cmd_port = \"$PORT\"\n")
            append("quit_press_twice = \"false\"\n")
            append("config_save_on_exit = \"false\"\n")
            // Select + Start (sent by Taco Launcher) is the only way into the menu, so Start
            // on its own never opens it and the two can't cancel each other out.
            append("input_menu_toggle_btn = \"nul\"\n")
            append("input_menu_toggle_axis = \"nul\"\n")
            append("input_menu_toggle_gamepad_combo = \"0\"\n")
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
