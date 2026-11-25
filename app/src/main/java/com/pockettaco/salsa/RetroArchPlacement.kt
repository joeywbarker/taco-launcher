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
    // A plain name (no leading dot): some phones refuse to create "hidden" files there.
    private const val FILE_NAME = "taco-launcher-retroarch.cfg"

    /** Why the last settings file couldn't be written, for the note shown at launch. */
    @Volatile var lastError: String? = null

    /** Upright arcade games (romset names). Auto puts these from the top down to the Taco. */
    private val TALL_ARCADE = setOf(
        "1941", "1942", "1943", "1943kai", "1944", "19xx", "galaga", "galaga88", "galaxian", "gaplus",
        "pacman", "puckman", "mspacman", "mspacmab", "mspacmnf", "pacplus", "superpac", "jrpacman", "pengo",
        "dkong", "dkongjr", "dkong3", "digdug", "frogger", "centiped", "milliped", "xevious", "bosco",
        "arkanoid", "invaders", "phoenix", "pleiads", "zaxxon", "ladybug", "mrdo", "amidar", "timeplt",
        "gyruss", "circusc", "bombjack", "commando", "gunsmoke", "exedexes", "ikari", "victroad", "tnk3",
        "mercs", "varth", "truxton", "truxton2", "tatsujin", "batsugun", "vfive", "dogyuun", "outzone",
        "fireshrk", "ktiger", "twincobr", "tigerh", "vimana", "sstriker", "grindstm", "kingdmgp", "shippumd",
        "batrider", "bbakraid", "raiden", "raidendx", "raiden2", "rdft", "donpachi", "ddonpach", "dfeveron",
        "esprade", "guwange", "espgal", "mmatrix", "dimahoo", "gunbird", "gunbird2", "s1945", "s1945ii",
        "s1945iii", "sonicwi", "sonicwi2", "sonicwi3", "terracre", "bgaregga",
    )

    fun isTallArcade(system: String, file: String?): Boolean {
        if (system != "arcade" || file == null) return false
        val name = file.substringBeforeLast('.').lowercase()
        return TALL_ARCADE.any { name == it || (name.startsWith(it) && name.length - it.length <= 2) }
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
                // Never lower than the Taco's edge: the bottom of the picture stays above it.
                else (spotCenterY - picH / 2f).coerceIn(0f, (free - picH).coerceAtLeast(0f))
                android.graphics.RectF(0f, top / h, 1f, (top + picH) / h)
            }
        }
    }

    /** Rewrites [intent]'s settings file so the picture lands where it should. False if it couldn't. */
    fun apply(activity: MainActivity, intent: Intent, system: GameSystem): Boolean = runCatching {
        val mainConfig = intent.getStringExtra("CONFIGFILE") ?: return false
        // Any games folder we may still write to will do (RetroArch only needs to read the file).
        // After a reinstall Android can forget one folder, so try the others too.
        val writable = activity.contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri }.toSet()
        val folders = (listOfNotNull(activity.settings.folderFor(system.id), activity.settings.romFolder) +
            SYSTEMS.mapNotNull { activity.settings.folderFor(it.id) }).distinct().filter { it in writable }
        if (folders.isEmpty()) {
            lastError = "no games folder Taco Launcher may write to. Settings → Setup check fixes it"
            return false
        }
        val screen = activity.windowManager.currentWindowMetrics.bounds
        val w = screen.width().toFloat()
        val h = screen.height().toFloat()
        val shape = SCREEN_SHAPES[system.id] ?: 4f / 3f
        // Where the pop-up window would have gone; "Auto" centres the picture on that spot.
        val spot = gameWindowRect(activity.settings, screen.width(), screen.height(), shape)
        val file = intent.getStringExtra("ROM")?.substringAfterLast('/')
        val st = activity.settings
        var layout = Layout.of(file?.let { st.layoutForGame(system.id, it) } ?: st.raPictureDefault)
        // Auto knows the well-known upright arcade games (1941, Pac-Man, Galaga...).
        if (layout == Layout.AUTO && isTallArcade(system.id, file)) layout = Layout.TALL
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
                // Some RetroArch versions and video drivers use the plain anchor (not the
                // "portrait" one) even with the phone upright; its default is the middle of the
                // screen, which put games behind the Taco. Set both so either one is right.
                for (prefix in listOf("video_viewport_bias_portrait_", "video_viewport_bias_")) {
                    append(prefix).append("x = \"0.500000\"\n")
                    append(prefix).append("y = \"").append(f(bias)).append("\"\n")
                }
            }
            append("config_save_on_exit = \"false\"\n")
            if (st.raSameForEveryGame) {
                // RetroArch can keep extra settings for one game, one games folder or one core
                // ("overrides"), and extra button layouts ("remaps"). Those load after this file
                // and win over it, so a game could end up with a different menu combo or none.
                // Skipping them gives every game the same, Taco Launcher's, setup. The files
                // themselves stay untouched, so turning this off brings them back.
                append("auto_overrides_enable = \"false\"\n")
                append("auto_remaps_enable = \"false\"\n")
            }
            if (st.raCommands) {
                // RetroArch's own Select + Start menu combo (4 = START_SELECT), so the menu opens
                // without any help from Taco Launcher.
                append("input_menu_toggle_gamepad_combo = \"4\"\n")
                // No "press again to quit" (older and newer RetroArch names).
                append("quit_press_twice = \"false\"\n")
                append("confirm_quit = \"false\"\n")
                if (!st.raStartOpensMenu) {
                    // Start on its own no longer opens the menu; Select + Start still does.
                    append("input_menu_toggle_btn = \"nul\"\n")
                    append("input_menu_toggle_axis = \"nul\"\n")
                }
            }
            if (st.raStateHotkeys) {
                // Android button numbers: Select 109, L1 102, R1 103.
                append("input_enable_hotkey_btn = \"109\"\n")
                append("input_save_state_btn = \"103\"\n")
                append("input_load_state_btn = \"102\"\n")
            }
            fun choice(key: String, vararg raKeys: String) {
                val v = st.raChoice(key)
                if (v != 0) raKeys.forEach { append(it).append(" = \"").append(v == 1).append("\"\n") }
            }
            choice("smooth", "video_smooth")
            choice("fps", "fps_show")
            // Resume where you left off: RetroArch saves your spot when the game closes and
            // loads it next time, unless you said not to keep it ("Still playing?" prompt).
            when {
                st.raChoice("resume") == 2 -> {
                    append("savestate_auto_save = \"false\"\n")
                    append("savestate_auto_load = \"false\"\n")
                }
                st.raKeepsSpot -> {
                    val fresh = file != null && st.freshStart(system.id, file)
                    if (fresh) st.setFreshStart(system.id, file!!, false)
                    append("savestate_auto_save = \"true\"\n")
                    append("savestate_auto_load = \"").append(!fresh).append("\"\n")
                }
            }
            choice("pauseMenu", "menu_pause_libretro")
            append("#include \"").append(mainConfig).append("\"\n")
        }
        for (folder in folders) {
            val ok = runCatching {
                val doc = findOrCreate(activity, folder) ?: error("couldn't create the file")
                activity.contentResolver.openOutputStream(doc, "wt")?.use { it.write(text.toByteArray()) }
                    ?: error("couldn't write the file")
                intent.putExtra("CONFIGFILE", toFilePath(doc))
            }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.isSuccess
            if (ok) { lastError = null; return true }
        }
        false
    }.getOrElse { lastError = it.message ?: it.javaClass.simpleName; false }

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
