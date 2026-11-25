package com.pockettaco.salsa

import android.content.Context
import android.net.Uri

/** Small saved preferences. Plain SharedPreferences keeps things simple. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("taco", Context.MODE_PRIVATE)

    /**
     * A first guess at how much of this phone's screen the Taco covers. The Taco hides about
     * 6.3 cm of screen (40% of a Galaxy S26 Ultra), so a shorter phone loses a bigger share.
     * The first-run setup lets you fine-tune it with a live preview.
     */
    private val guessedCover: Float = runCatching {
        val m = context.resources.displayMetrics
        val tallPx = maxOf(m.heightPixels, m.widthPixels).toFloat()
        val dpi = if (m.heightPixels >= m.widthPixels) m.ydpi else m.xdpi
        val tallCm = tallPx / dpi * 2.54f
        if (tallCm in 8f..30f) (6.3f / tallCm).coerceIn(0.25f, 0.6f) else 0.40f
    }.getOrDefault(0.40f)

    /** How much of the screen bottom the Taco covers, 0.0 to 0.6. */
    var coveredFraction: Float
        get() = prefs.getFloat("covered", guessedCover)
        set(v) = prefs.edit().putFloat("covered", v.coerceIn(0f, 0.6f)).apply()

    /**
     * False until the first-run setup is finished (or skipped). Someone updating from an older
     * version already set things up, so they never see it, and their choices are kept as they are.
     */
    var setupDone: Boolean
        get() {
            if (!prefs.contains("setupDone")) {
                val existing = prefs.all.isNotEmpty()
                prefs.edit().apply {
                    // Keep the screen split an existing user has always had.
                    if (existing && !prefs.contains("covered")) putFloat("covered", 0.40f)
                    putBoolean("setupDone", existing)
                }.apply()
                return existing
            }
            return prefs.getBoolean("setupDone", false)
        }
        set(v) = prefs.edit().putBoolean("setupDone", v).apply()

    /** Treat any gamepad as a Taco (handy if the name ever changes). */
    var anyGamepadCounts: Boolean
        get() = prefs.getBoolean("anyPad", true)
        set(v) = prefs.edit().putBoolean("anyPad", v).apply()

    /** Always use the Taco layout, even with nothing connected. On by default (the user asked). */
    var forceTacoMode: Boolean
        get() = prefs.getBoolean("force", true)
        set(v) = prefs.edit().putBoolean("force", v).apply()

    /**
     * "taco" (default): everything stays above the Pocket Taco, upright.
     * "full": Taco off. The whole screen is used and it can turn sideways.
     */
    var screenMode: String
        get() = prefs.getString("screenMode", "taco").takeIf { it == "taco" || it == "full" } ?: "taco"
        set(v) = prefs.edit().putString("screenMode", if (v == "full") "full" else "taco").apply()

    /** Full screen turns "sideways" (default), "turn" (with the phone) or "upright". */
    var fullRotation: String
        get() = prefs.getString("fullRotation", "sideways").takeIf { it in setOf("sideways", "turn", "upright") } ?: "sideways"
        set(v) = prefs.edit().putString("fullRotation", v).apply()

    /** Turning the phone sideways switches to full screen; upright brings Taco mode back. Off by default. */
    var autoFullSideways: Boolean
        get() = prefs.getBoolean("autoFullSideways", false)
        set(v) = prefs.edit().putBoolean("autoFullSideways", v).apply()

    /** Open emulators in a window that fits above the Taco (Samsung pop-up window). */
    var fitEmulators: Boolean
        get() = prefs.getBoolean("fit", true)
        set(v) = prefs.edit().putBoolean("fit", v).apply()

    /**
     * Open RetroArch games full screen (no pop-up window frame). RetroArch itself keeps the
     * picture at the top, above the Taco, with its "Viewport Anchor Bias Y (Portrait)" setting.
     */
    var retroArchFullScreen: Boolean
        get() = prefs.getBoolean("retroArchFullScreen", true)
        set(v) = prefs.edit().putBoolean("retroArchFullScreen", v).apply()

    // ---- RetroArch games (applied through Taco Launcher's own RetroArch settings file) ----

    /** Where the picture goes when a game has no layout of its own: "auto", "top" or "tall". */
    var raPictureDefault: String
        get() = prefs.getString("raPictureDefault", "auto") ?: "auto"
        set(v) = prefs.edit().putString("raPictureDefault", v).apply()

    /** Taco Launcher opens RetroArch's menu (Select + Start) and closes it properly (Home). */
    var raCommands: Boolean
        get() = prefs.getBoolean("raCommands", true)
        set(v) = prefs.edit().putBoolean("raCommands", v).apply()

    /** Also let Start on its own open RetroArch's menu (RetroArch's own button). */
    var raStartOpensMenu: Boolean
        get() = prefs.getBoolean("raStartOpensMenu", false)
        set(v) = prefs.edit().putBoolean("raStartOpensMenu", v).apply()

    /** Ask "still playing?" before a different RetroArch game replaces the one you left. */
    var raAskBeforeSwitch: Boolean
        get() = prefs.getBoolean("raAskBeforeSwitch", true)
        set(v) = prefs.edit().putBoolean("raAskBeforeSwitch", v).apply()

    /** Select + R1 saves, Select + L1 loads a save state. */
    var raStateHotkeys: Boolean
        get() = prefs.getBoolean("raStateHotkeys", true)
        set(v) = prefs.edit().putBoolean("raStateHotkeys", v).apply()

    /**
     * Every game starts with Taco Launcher's settings: RetroArch skips the extra settings and
     * button files it may have saved for one game, one folder or one core.
     */
    /** RetroArch's menu is drawn inside the game picture (above the Taco) instead of full screen. */
    var raMenuAboveTaco: Boolean
        get() = prefs.getBoolean("raMenuAboveTaco", true)
        set(v) = prefs.edit().putBoolean("raMenuAboveTaco", v).apply()

    /**
     * Which of the extras you save inside RetroArch it may use for games started here:
     * "none" = neither, "remaps" = your saved button layouts only (default),
     * "all" = button layouts and saved settings ("overrides").
     * Replaces the older on/off "raSameForEveryGame" (off meant "all").
     */
    var raExtras: String
        get() = prefs.getString("raExtras", null)
            ?: if (prefs.contains("raSameForEveryGame") && !prefs.getBoolean("raSameForEveryGame", true)) "all" else "remaps"
        set(v) = prefs.edit().putString("raExtras", v).apply()

    /** RetroArch hotkeys: hold [raHotkeyButton], press [raSaveButton] / [raLoadButton]. */
    var raHotkeyButton: TacoButton
        get() = tacoButtonPref("raHotkeyButton", TacoButton.SELECT)
        set(v) = prefs.edit().putString("raHotkeyButton", v.name).apply()
    var raSaveButton: TacoButton
        get() = tacoButtonPref("raSaveButton", TacoButton.R)
        set(v) = prefs.edit().putString("raSaveButton", v.name).apply()
    var raLoadButton: TacoButton
        get() = tacoButtonPref("raLoadButton", TacoButton.L)
        set(v) = prefs.edit().putString("raLoadButton", v.name).apply()
    private fun tacoButtonPref(key: String, default: TacoButton) =
        prefs.getString(key, null)?.let { n -> TacoButton.entries.firstOrNull { it.name == n } } ?: default

    /** Hold button + L2 / R2 = previous / next shader in RetroArch (try looks while playing). */
    var raShaderHotkeys: Boolean
        get() = prefs.getBoolean("raShaderHotkeys", true)
        set(v) = prefs.edit().putBoolean("raShaderHotkeys", v).apply()

    /** "next": every save goes to a new slot and loading picks the newest. "same": one slot, overwritten. */
    var raSaveSlots: String
        get() = prefs.getString("raSaveSlots", "next") ?: "next"
        set(v) = prefs.edit().putString("raSaveSlots", v).apply()

    /** 0 = leave it to RetroArch, 1 = on, 2 = off. */
    fun raChoice(key: String): Int = prefs.getInt("ra_$key", 0)
    fun setRaChoice(key: String, v: Int) = prefs.edit().putInt("ra_$key", v).apply()

    /** Jump to the launcher the moment the Pocket Taco connects. */
    var openOnConnect: Boolean
        get() = prefs.getBoolean("openOnConnect", true)
        set(v) = prefs.edit().putBoolean("openOnConnect", v).apply()

    /** Size the game window to each console's screen shape (so the game fills it, no gaps). */
    var autoFitGame: Boolean
        get() = prefs.getBoolean("autoFitGame", true)
        set(v) = prefs.edit().putBoolean("autoFitGame", v).apply()

    /** Game window height as a share of the whole screen, when not auto-sized. */
    var windowHeight: Float
        get() = prefs.getFloat("windowHeight", (1f - coveredFraction).coerceIn(0.2f, 1f))
        set(v) = prefs.edit().putFloat("windowHeight", v.coerceIn(0.2f, 1f)).apply()

    /** Where the game window sits in the space above the Taco: 0 = top, 1 = just above the Taco. */
    var windowPosition: Float
        get() = prefs.getFloat("windowPosition", 1f)
        set(v) = prefs.edit().putFloat("windowPosition", v.coerceIn(0f, 1f)).apply()

    /** Pocket Taco uses Game Boy style labels: swap so the button marked B goes back. */
    var swapAB: Boolean
        get() = prefs.getBoolean("swapAB", true)
        set(v) = prefs.edit().putBoolean("swapAB", v).apply()

    /** The controller button that selects (A) in the menus. Taught in Settings. */
    var selectKey: Int
        get() = prefs.getInt("selectKey",
            if (swapAB) android.view.KeyEvent.KEYCODE_BUTTON_B else android.view.KeyEvent.KEYCODE_BUTTON_A)
        set(v) = prefs.edit().putInt("selectKey", v).apply()

    /** The controller button that goes back (B) in the menus. Taught in Settings. */
    var backKey: Int
        get() = prefs.getInt("backKey",
            if (swapAB) android.view.KeyEvent.KEYCODE_BUTTON_A else android.view.KeyEvent.KEYCODE_BUTTON_B)
        set(v) = prefs.edit().putInt("backKey", v).apply()

    /** Extra detail for the taught buttons: the controller's own number for the button (0 = any). */
    var selectScan: Int
        get() = prefs.getInt("selectScan", 0)
        set(v) = prefs.edit().putInt("selectScan", v).apply()
    var backScan: Int
        get() = prefs.getInt("backScan", 0)
        set(v) = prefs.edit().putInt("backScan", v).apply()

    /**
     * The signal each Taco button sends (taught in Settings → Teach buttons). A and B use
     * [selectKey]/[backKey]; the rest are kept as "code:scan" (scan 0 = any).
     */
    fun signalFor(b: TacoButton): Pair<Int, Int> = when (b) {
        TacoButton.A -> selectKey to selectScan
        TacoButton.B -> backKey to backScan
        else -> prefs.getString("btn_${b.name}", null)?.split(':')?.let {
            (it[0].toIntOrNull() ?: b.defaultCode) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
        } ?: (b.defaultCode to 0)
    }

    fun setSignal(b: TacoButton, code: Int, scan: Int) {
        when (b) {
            TacoButton.A -> { selectKey = code; selectScan = scan }
            TacoButton.B -> { backKey = code; backScan = scan }
            else -> prefs.edit().putString("btn_${b.name}", "$code:$scan").apply()
        }
    }

    /** Which Taco button this key press is, using what was taught (A and B win ties). */
    fun buttonFor(e: android.view.KeyEvent): TacoButton? = TacoButton.entries.firstOrNull { b ->
        val (code, scan) = signalFor(b)
        code != 0 && e.keyCode == code && (scan == 0 || e.scanCode == scan)
    }

    /** The button that adds or removes a favorite in a game list (null = off). Y unless changed. */
    var favoriteButton: TacoButton?
        get() = prefs.getString("favoriteButton", TacoButton.Y.name)?.let { n -> TacoButton.entries.firstOrNull { it.name == n } }
        set(v) = prefs.edit().putString("favoriteButton", v?.name ?: "OFF").apply()
    fun isFavorite(e: android.view.KeyEvent) = favoriteButton != null && buttonFor(e) == favoriteButton

    fun isSelect(e: android.view.KeyEvent) = e.keyCode == selectKey && (selectScan == 0 || e.scanCode == selectScan)
    fun isBack(e: android.view.KeyEvent) = e.keyCode == backKey && (backScan == 0 || e.scanCode == backScan)

    // ---- Touch controls (Settings > Touch controls; see TouchControls) ----

    var touchEnabled: Boolean
        get() = prefs.getBoolean("touchEnabled", true)
        set(v) = prefs.edit().putBoolean("touchEnabled", v).apply()
    var touchTapHints: Boolean
        get() = prefs.getBoolean("touchTapHints", true)
        set(v) = prefs.edit().putBoolean("touchTapHints", v).apply()
    var touchHintStyle: String
        get() = prefs.getString("touchHintStyle", "auto").takeIf { it in setOf("auto", "buttons", "touch", "hidden") } ?: "auto"
        set(v) = prefs.edit().putString("touchHintStyle", v).apply()
    var touchBigHints: Boolean
        get() = prefs.getBoolean("touchBigHints", false)
        set(v) = prefs.edit().putBoolean("touchBigHints", v).apply()
    var touchSwipe: Boolean
        get() = prefs.getBoolean("touchSwipe", true)
        set(v) = prefs.edit().putBoolean("touchSwipe", v).apply()
    var touchHold: Boolean
        get() = prefs.getBoolean("touchHold", true)
        set(v) = prefs.edit().putBoolean("touchHold", v).apply()
    var touchDrag: Boolean
        get() = prefs.getBoolean("touchDrag", true)
        set(v) = prefs.edit().putBoolean("touchDrag", v).apply()
    var touchCloseItems: Boolean
        get() = prefs.getBoolean("touchCloseItems", true)
        set(v) = prefs.edit().putBoolean("touchCloseItems", v).apply()

    /** Play the short intro when Taco Launcher starts. */
    /** Download cover pictures for games (needs the internet the first time each one shows). */
    var boxArt: Boolean
        get() = prefs.getBoolean("boxArt", true)
        set(v) = prefs.edit().putBoolean("boxArt", v).apply()

    var showSplash: Boolean
        get() = prefs.getBoolean("showSplash", true)
        set(v) = prefs.edit().putBoolean("showSplash", v).apply()

    /** Game name choices (whole file name, region, year...), all off by default. */
    fun nameFlag(key: String) = prefs.getBoolean(key, false)
    fun setNameFlag(key: String, on: Boolean) = prefs.edit().putBoolean(key, on).apply()

    /** A system's own game-name switches (the ones turned on), or null to follow the main ones. */
    fun systemNameFlags(system: String): Set<String>? =
        if (prefs.getBoolean("names_own_$system", false)) prefs.getStringSet("names_$system", null)?.toSet() ?: emptySet()
        else null
    fun setSystemNameFlags(system: String, flags: Set<String>?) {
        if (flags == null) prefs.edit().remove("names_own_$system").remove("names_$system").apply()
        else prefs.edit().putBoolean("names_own_$system", true).putStringSet("names_$system", flags.toSet()).apply()
    }

    /** The emulator picked for one game (overrides its system's), or null. */
    fun emulatorForGame(game: Game): String? = prefs.getString("emu_game_${game.system.id}|${game.file}", null)
    fun setEmulatorForGame(game: Game, pkg: String?) =
        prefs.edit().putString("emu_game_${game.system.id}|${game.file}", pkg).apply()

    /** How RetroArch places one game's picture (see RetroArchPlacement.Layout), or null for Auto. */
    /** Set when you chose not to keep your spot: the game's next start skips its automatic save once. */
    fun freshStart(system: String, file: String): Boolean = prefs.getBoolean("fresh_$system|$file", false)
    fun setFreshStart(system: String, file: String, v: Boolean) =
        prefs.edit().putBoolean("fresh_$system|$file", v).apply()

    /** RetroArch saves your spot when a game closes (needed for the "keep my spot?" choice). */
    val raKeepsSpot: Boolean get() = raChoice("resume").let { it == 1 || (it == 0 && raAskBeforeSwitch) }

    fun layoutForGame(system: String, file: String): String? = prefs.getString("layout_game_$system|$file", null)
    fun setLayoutForGame(system: String, file: String, layout: String?) =
        prefs.edit().putString("layout_game_$system|$file", layout).apply()

    /** The RetroArch core picked for one game, or null to use its system's. */
    fun coreForGame(game: Game): String? = prefs.getString("core_game_${game.system.id}|${game.file}", null)
    fun setCoreForGame(game: Game, core: String?) =
        prefs.edit().putString("core_game_${game.system.id}|${game.file}", core).apply()

    /** The RetroArch core picked for a system, or null for the usual one. */
    fun coreFor(system: String): String? = prefs.getString("core_$system", null)
    fun setCoreFor(system: String, core: String?) = prefs.edit().putString("core_$system", core).apply()

    /** The person said the Taco is set up in RetroArch, so stop asking before games. */
    var retroArchPadReady: Boolean
        get() = prefs.getBoolean("retroArchPadReady", false)
        set(v) = prefs.edit().putBoolean("retroArchPadReady", v).apply()

    /** The ROMs folder the user picked (a "tree" Uri from the system picker). */
    var romFolder: Uri?
        get() = prefs.getString("roms", null)?.let(Uri::parse)
        set(v) = prefs.edit().putString("roms", v?.toString()).apply()

    /** Which emulator app the user chose for each system, e.g. "gba" -> package. */
    fun emulatorFor(system: String): String? = prefs.getString("emu_$system", null)
    fun setEmulatorFor(system: String, pkg: String?) =
        prefs.edit().putString("emu_$system", pkg).apply()

    /** An extra games folder just for one system (on top of the main folder), or null. */
    fun folderFor(system: String): Uri? = prefs.getString("folder_$system", null)?.let(Uri::parse)
    fun setFolderFor(system: String, folder: Uri?) =
        prefs.edit().putString("folder_$system", folder?.toString()).apply()
}

/** The buttons printed on the Pocket Taco, in the order Settings asks for them. */
enum class TacoButton(val label: String, val defaultCode: Int) {
    A("A", android.view.KeyEvent.KEYCODE_BUTTON_A),
    B("B", android.view.KeyEvent.KEYCODE_BUTTON_B),
    X("X", android.view.KeyEvent.KEYCODE_BUTTON_X),
    Y("Y", android.view.KeyEvent.KEYCODE_BUTTON_Y),
    L("L", android.view.KeyEvent.KEYCODE_BUTTON_L1),
    R("R", android.view.KeyEvent.KEYCODE_BUTTON_R1),
    SELECT("Select", android.view.KeyEvent.KEYCODE_BUTTON_SELECT),
    START("Start", android.view.KeyEvent.KEYCODE_BUTTON_START),
    HOME("Home", android.view.KeyEvent.KEYCODE_BUTTON_MODE),
}
