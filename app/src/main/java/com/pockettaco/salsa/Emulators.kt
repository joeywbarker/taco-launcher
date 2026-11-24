package com.pockettaco.salsa

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract

/**
 * A game system. [extensions] are file endings that belong only to it, so a game with one of
 * them is recognised anywhere. [inFolder] are extra endings it also uses (shared ones like .bin
 * or .iso), which only count inside that system's own folder.
 */
data class GameSystem(
    val id: String, val name: String, val extensions: Set<String>,
    val inFolder: Set<String> = emptySet(),
)

/** In the order they appear on screen: classic 2D systems first. */
val SYSTEMS = listOf(
    GameSystem("gba", "Game Boy Advance", setOf("gba")),
    GameSystem("gbc", "Game Boy Color", setOf("gbc")),
    GameSystem("gb", "Game Boy", setOf("gb")),
    GameSystem("nes", "NES", setOf("nes")),
    GameSystem("snes", "SNES", setOf("sfc", "smc")),
    GameSystem("genesis", "Genesis / Mega Drive", setOf("md", "gen", "smd"), setOf("bin")),
    GameSystem("sms", "Master System", setOf("sms")),
    GameSystem("gg", "Game Gear", setOf("gg")),
    GameSystem("pce", "TurboGrafx-16 / PC Engine", setOf("pce")),
    GameSystem("ngp", "Neo Geo Pocket", setOf("ngp", "ngc")),
    GameSystem("nds", "Nintendo DS", setOf("nds"), setOf("dsi")),
    GameSystem("n64", "Nintendo 64", setOf("n64", "z64", "v64")),
    GameSystem("psx", "PlayStation", setOf("cue", "chd", "pbp"), setOf("m3u", "iso", "img", "ccd")),
    GameSystem("psp", "PSP", setOf("iso", "cso")),
    GameSystem("dreamcast", "Dreamcast", setOf("cdi", "gdi")),
    GameSystem("saturn", "Sega Saturn", emptySet(), setOf("cue", "chd", "iso", "ccd", "mds", "m3u")),
    GameSystem("segacd", "Sega CD / Mega CD", emptySet(), setOf("cue", "chd", "iso", "m3u")),
    GameSystem("sega32x", "Sega 32X", setOf("32x"), setOf("bin")),
    GameSystem("sg1000", "SG-1000", setOf("sg"), setOf("bin")),
    GameSystem("ps2", "PlayStation 2", emptySet(), setOf("iso", "chd", "cso", "gz", "bin")),
    GameSystem("gc", "GameCube", setOf("gcm", "gcz"), setOf("iso", "rvz", "ciso")),
    GameSystem("wii", "Wii", setOf("wbfs"), setOf("iso", "rvz", "wad", "wia")),
    GameSystem("3ds", "Nintendo 3DS", setOf("3ds", "cia", "cci", "cxi")),
    // Newer consoles, ready for when Android emulators for them are good enough. Their games
    // only count inside their own folder, since .iso is shared with many other systems.
    GameSystem("ps3", "PlayStation 3", emptySet(), setOf("iso", "pkg")),
    GameSystem("xbox", "Xbox", emptySet(), setOf("iso", "xiso")),
    GameSystem("x360", "Xbox 360", emptySet(), setOf("iso", "xex", "zar")),
    GameSystem("wiiu", "Wii U", setOf("wua", "wud", "wux", "rpx")),
    GameSystem("switch", "Nintendo Switch", setOf("nsp", "xci", "nsz", "xcz")),
    GameSystem("switch2", "Nintendo Switch 2", emptySet(), setOf("nsp", "xci")),
    GameSystem("vita", "PlayStation Vita", setOf("vpk"), setOf("zip")),
    GameSystem("fds", "Famicom Disk System", setOf("fds")),
    GameSystem("virtualboy", "Virtual Boy", setOf("vb", "vboy")),
    GameSystem("pokemini", "Pokémon Mini", setOf("min")),
    GameSystem("pcecd", "PC Engine CD / TurboGrafx-CD", emptySet(), setOf("cue", "chd", "ccd", "m3u")),
    GameSystem("ws", "WonderSwan", setOf("ws")),
    GameSystem("wsc", "WonderSwan Color", setOf("wsc")),
    GameSystem("neogeo", "Neo Geo", emptySet()),
    GameSystem("arcade", "Arcade", emptySet()),
    GameSystem("atari2600", "Atari 2600", setOf("a26"), setOf("bin")),
    GameSystem("atari5200", "Atari 5200", setOf("a52"), setOf("bin")),
    GameSystem("atari7800", "Atari 7800", setOf("a78"), setOf("bin")),
    GameSystem("lynx", "Atari Lynx", setOf("lnx")),
    GameSystem("jaguar", "Atari Jaguar", setOf("j64", "jag")),
    GameSystem("colecovision", "ColecoVision", setOf("col"), setOf("rom", "bin")),
    GameSystem("intellivision", "Intellivision", setOf("int"), setOf("rom", "bin")),
    GameSystem("msx", "MSX", setOf("mx1", "mx2"), setOf("rom", "dsk")),
    GameSystem("c64", "Commodore 64", setOf("d64", "t64", "prg", "crt")),
)

/** Systems RetroArch has no core for, so it isn't offered (or counted) for them. */
private val NOT_IN_RETROARCH = setOf("ps3", "xbox", "x360", "wiiu", "switch", "switch2", "vita")
private val RETROARCH_SYSTEMS = SYSTEMS.map { it.id }.toSet() - NOT_IN_RETROARCH

/** Emulator apps we know about. Add more here; the list is just a lookup table. */
data class Emulator(val pkg: String, val name: String, val systems: Set<String>)

val KNOWN_EMULATORS = listOf(
    Emulator("com.retroarch", "RetroArch", RETROARCH_SYSTEMS),
    Emulator("com.retroarch.aarch64", "RetroArch (64-bit)", RETROARCH_SYSTEMS),
    Emulator("com.swordfish.lemuroid", "Lemuroid",
        setOf("gb", "gbc", "gba", "nes", "snes", "genesis", "sms", "gg", "pce", "ngp",
            "n64", "nds", "psx", "psp")),
    Emulator("com.retroarch.ra32", "RetroArch (32-bit)", RETROARCH_SYSTEMS),
    Emulator("com.fastemulator.gba", "My Boy!", setOf("gba")),
    Emulator("com.fastemulator.gbc", "My OldBoy!", setOf("gb", "gbc")),
    Emulator("it.dbtecno.pizzaboy", "Pizza Boy GBC", setOf("gb", "gbc")),
    Emulator("it.dbtecno.pizzaboypro", "Pizza Boy GBC Pro", setOf("gb", "gbc")),
    Emulator("it.dbtecno.pizzaboygbapro", "Pizza Boy GBA Pro", setOf("gba")),
    Emulator("com.johnemulators.johngbac", "John GBAC", setOf("gb", "gbc", "gba")),
    Emulator("com.explusalpha.GbcEmu", "GBC.emu", setOf("gb", "gbc")),
    Emulator("com.explusalpha.GbaEmu", "GBA.emu", setOf("gba")),
    Emulator("com.explusalpha.NesEmu", "NES.emu", setOf("nes")),
    Emulator("com.explusalpha.Snes9xPlus", "Snes9x EX+", setOf("snes")),
    Emulator("com.explusalpha.MdEmu", "MD.emu", setOf("genesis", "sms", "gg")),
    Emulator("org.mupen64plusae.v3.fzurita", "M64Plus FZ", setOf("n64")),
    Emulator("it.dbtecno.pizzaboygba", "Pizza Boy GBA", setOf("gba")),
    Emulator("me.magnum.melonds", "melonDS", setOf("nds")),
    Emulator("com.dsemu.drastic", "DraStic", setOf("nds")),
    Emulator("org.ppsspp.ppsspp", "PPSSPP", setOf("psp")),
    Emulator("org.ppsspp.ppssppgold", "PPSSPP Gold", setOf("psp")),
    Emulator("com.github.stenzek.duckstation", "DuckStation", setOf("psx")),
    Emulator("com.flycast.emulator", "Flycast", setOf("dreamcast", "arcade")),
    Emulator("io.recompiled.redream", "Redream", setOf("dreamcast")),
    Emulator("org.dolphinemu.dolphinemu", "Dolphin", setOf("gc", "wii")),
    Emulator("xyz.aethersx2.android", "AetherSX2 / NetherSX2", setOf("ps2")),
    Emulator("org.citra.emu", "Citra", setOf("3ds")),
    Emulator("io.github.lime3ds.android", "Lime3DS", setOf("3ds")),
    Emulator("org.azahar_emu.azahar", "Azahar", setOf("3ds")),
    Emulator("org.devmiyax.yabasanshioro2", "Yaba Sanshiro 2", setOf("saturn")),
    Emulator("com.seleuco.mame4d2024", "MAME4droid 2024", setOf("arcade", "neogeo")),
    Emulator("com.seleuco.mame4droid", "MAME4droid", setOf("arcade", "neogeo")),
)

// Looking through every installed app's name takes a second or two on a phone with many apps,
// and menus ask for this often. So the answer is kept until Android says an app was added,
// removed or updated (its package change counter moves).
private val emulatorLock = Any()
private var emulatorCache: List<Emulator>? = null
private var packageSeq = 0

fun installedEmulators(context: Context): List<Emulator> = synchronized(emulatorLock) {
    val pm = context.packageManager
    val changed = runCatching { pm.getChangedPackages(packageSeq) }.getOrNull()
    if (changed != null) { packageSeq = changed.sequenceNumber; emulatorCache = null }
    emulatorCache ?: findEmulators(context).also { emulatorCache = it }
}

private fun findEmulators(context: Context): List<Emulator> {
    val pm = context.packageManager
    val byPackage = KNOWN_EMULATORS.filter { isInstalled(context, it.pkg) }
    // Also match by app name, in case an emulator uses a package name we don't know.
    // Only names are read here (app icons are slow to load and aren't needed).
    val known = byPackage.map { it.pkg }.toSet()
    val byName = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.packageName !in known }
        .distinctBy { it.activityInfo.packageName }
        .mapNotNull { info ->
            val label = info.loadLabel(pm).toString()
            NAME_HINTS.entries.firstOrNull { (hint, _) -> hint in label.lowercase() }
                ?.let { (_, systems) -> Emulator(info.activityInfo.packageName, label, systems) }
        }
    return byPackage + byName
}

private val ALL = SYSTEMS.map { it.id }.toSet()
private val NAME_HINTS = linkedMapOf(
    "retroarch" to RETROARCH_SYSTEMS,
    // Matched by app name, so any of these works once it's installed.
    "aps3e" to setOf("ps3"),
    "rpcs3" to setOf("ps3"),
    "xemu" to setOf("xbox"),
    "ax360e" to setOf("x360"),
    "xenia" to setOf("x360"),
    "cemu" to setOf("wiiu"),
    "vita3k" to setOf("vita"),
    "eden" to setOf("switch"),
    "citron" to setOf("switch"),
    "sudachi" to setOf("switch"),
    "yuzu" to setOf("switch"),
    "strato" to setOf("switch"),
    "lemuroid" to setOf("gb", "gbc", "gba", "nes", "snes", "genesis", "sms", "gg", "pce", "ngp",
        "n64", "nds", "psx", "fds", "virtualboy", "ws", "wsc", "lynx", "atari2600", "atari7800", "segacd", "sega32x"),
    "my boy" to setOf("gba"),
    "my oldboy" to setOf("gb", "gbc"),
    "pizza boy gba" to setOf("gba"),
    "pizza boy" to setOf("gb", "gbc"),
    "john gba" to setOf("gb", "gbc", "gba"),
    "gba.emu" to setOf("gba"),
    "gbc.emu" to setOf("gb", "gbc"),
    "nes.emu" to setOf("nes"),
    "snes9x" to setOf("snes"),
    "md.emu" to setOf("genesis", "sms", "gg"),
    "melonds" to setOf("nds"),
    "drastic" to setOf("nds"),
    "ppsspp" to setOf("psp"),
    "duckstation" to setOf("psx"),
    "m64plus" to setOf("n64"),
    "mupen" to setOf("n64"),
    "flycast" to setOf("dreamcast", "arcade"),
    "redream" to setOf("dreamcast"),
    "dolphin" to setOf("gc", "wii"),
    "aethersx2" to setOf("ps2"),
    "nethersx2" to setOf("ps2"),
    "citra" to setOf("3ds"),
    "lime3ds" to setOf("3ds"),
    "azahar" to setOf("3ds"),
    "yaba" to setOf("saturn"),
    "mame" to setOf("arcade", "neogeo"),
)

/**
 * The apps you can pick for a system: installed emulators that play it, plus whatever
 * app you chose for it in Settings (any installed app is allowed).
 */
fun emulatorChoices(context: Context, system: GameSystem, installed: List<Emulator>): List<Emulator> {
    // Best first. Arcade: RetroArch, then MAME; Flycast only plays a few special arcade
    // boards (Naomi), so it goes last instead of grabbing ordinary arcade games.
    val list = installed.filter { system.id in it.systems }
        .sortedBy { preference(it, system.id) }.toMutableList()
    val chosen = context.getSharedPreferences("taco", Context.MODE_PRIVATE).getString("emu_${system.id}", null)
    if (chosen != null && list.none { it.pkg == chosen }) {
        appLabel(context, chosen)?.let { list.add(0, Emulator(chosen, it, setOf(system.id))) }
    }
    return list
}

private fun isRetroArchApp(e: Emulator) = e.pkg.startsWith("com.retroarch") || "retroarch" in e.name.lowercase()

private fun preference(e: Emulator, system: String): Int {
    val name = e.name.lowercase()
    return when {
        system == "arcade" || system == "neogeo" -> when {
            isRetroArchApp(e) -> 0
            "mame" in name -> 1
            "flycast" in name -> 3
            else -> 2
        }
        else -> 1
    }
}

private fun appLabel(context: Context, pkg: String): String? = runCatching {
    val pm = context.packageManager
    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
}.getOrNull()

/** The emulator a system uses right now: the one picked in Settings, else the best that fits. */
fun currentEmulator(context: Context, system: GameSystem, installed: List<Emulator>): Emulator? {
    val choices = emulatorChoices(context, system, installed)
    val chosen = context.getSharedPreferences("taco", Context.MODE_PRIVATE).getString("emu_${system.id}", null)
    return choices.firstOrNull { it.pkg == chosen } ?: choices.firstOrNull()
}

/** The emulator for one game: its own pick if it has one (and it's still installed), else its system's. */
fun emulatorForGame(context: Context, game: Game, installed: List<Emulator>): Emulator? {
    val own = context.getSharedPreferences("taco", Context.MODE_PRIVATE)
        .getString("emu_game_${game.system.id}|${game.file}", null)
    if (own != null) {
        installed.firstOrNull { it.pkg == own }?.let { return it }
        appLabel(context, own)?.let { return Emulator(own, it, setOf(game.system.id)) }
    }
    return currentEmulator(context, game.system, installed)
}

/** RetroArch cores you can pick between for a system (id to name). The first is the usual one. */
val CORE_OPTIONS: Map<String, List<Pair<String, String>>> = mapOf(
    "arcade" to listOf("fbneo" to "FinalBurn Neo", "mame2003_plus" to "MAME 2003-Plus", "mame2010" to "MAME 2010",
        "mame" to "MAME (current)", "mame2003" to "MAME 2003", "fbalpha2012" to "FB Alpha 2012"),
    "neogeo" to listOf("fbneo" to "FinalBurn Neo", "mame2003_plus" to "MAME 2003-Plus", "geolith" to "Geolith"),
    "nes" to listOf("fceumm" to "FCEUmm", "nestopia" to "Nestopia", "mesen" to "Mesen", "quicknes" to "QuickNES"),
    "snes" to listOf("snes9x" to "Snes9x", "bsnes" to "bsnes", "snes9x2010" to "Snes9x 2010"),
    "gba" to listOf("mgba" to "mGBA", "vbam" to "VBA-M", "gpsp" to "gpSP"),
    "gb" to listOf("mgba" to "mGBA", "gambatte" to "Gambatte", "sameboy" to "SameBoy", "gearboy" to "Gearboy"),
    "gbc" to listOf("mgba" to "mGBA", "gambatte" to "Gambatte", "sameboy" to "SameBoy", "gearboy" to "Gearboy"),
    "genesis" to listOf("genesis_plus_gx" to "Genesis Plus GX", "picodrive" to "PicoDrive"),
    "sms" to listOf("genesis_plus_gx" to "Genesis Plus GX", "picodrive" to "PicoDrive", "gearsystem" to "Gearsystem"),
    "gg" to listOf("genesis_plus_gx" to "Genesis Plus GX", "picodrive" to "PicoDrive", "gearsystem" to "Gearsystem"),
    "psx" to listOf("swanstation" to "SwanStation", "pcsx_rearmed" to "PCSX ReARMed", "mednafen_psx" to "Beetle PSX"),
    "n64" to listOf("mupen64plus_next" to "Mupen64Plus-Next", "parallel_n64" to "ParaLLEl N64"),
    "nds" to listOf("melonds" to "melonDS", "melondsds" to "melonDS DS", "desmume" to "DeSmuME"),
    "pce" to listOf("mednafen_pce_fast" to "Beetle PCE Fast", "mednafen_pce" to "Beetle PCE"),
)

/**
 * The RetroArch core to use: the one picked for this game, else the one picked for its
 * system, else the usual one.
 */
fun coreFor(context: Context, system: String, gameFile: String? = null): String {
    val prefs = context.getSharedPreferences("taco", Context.MODE_PRIVATE)
    return gameFile?.let { prefs.getString("core_game_$system|$it", null) }
        ?: prefs.getString("core_$system", null)
        ?: RETROARCH_CORES[system] ?: "mgba"
}

/** Emulators to suggest on a phone that has none. RetroArch first: it plays almost everything. */
data class Suggestion(val pkg: String, val name: String, val why: String)

val SUGGESTED_EMULATORS = listOf(
    Suggestion("com.retroarch", "RetroArch", "Recommended: one free app for GB, GBC, GBA, NES, SNES, Genesis and more"),
    Suggestion("com.swordfish.lemuroid", "Lemuroid", "Simple all-in-one for the classic systems"),
    Suggestion("me.magnum.melonds", "melonDS", "Nintendo DS"),
    Suggestion("org.ppsspp.ppsspp", "PPSSPP", "PSP"),
    Suggestion("com.github.stenzek.duckstation", "DuckStation", "PlayStation"),
    Suggestion("org.mupen64plusae.v3.fzurita", "M64Plus FZ", "Nintendo 64"),
)

/** Opens an app's Play Store page (or the web page if the Play Store app isn't there). */
fun openStorePage(context: Context, pkg: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Play Store page for RetroArch, the one free app that plays every system here. */
fun getRetroArchIntent(): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.retroarch"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** When no known emulator is installed: let Android show every app that can open the file. */
fun openWithAnyApp(game: Uri): Intent = Intent.createChooser(
    Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(game, "application/octet-stream")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    },
    "Open game with",
).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun isInstalled(context: Context, pkg: String) = try {
    context.packageManager.getPackageInfo(pkg, 0); true
} catch (_: PackageManager.NameNotFoundException) { false }

/**
 * Builds the "open this game in that emulator" request.
 *
 * Most emulators accept a standard "open this file" request. RetroArch is the
 * exception: it wants a plain file path and a "core" (the part that emulates
 * one specific console), so it gets its own branch.
 */
fun launchIntent(context: Context, emu: Emulator, system: GameSystem, game: Uri, gameFile: String? = null): Intent {
    val retroActivity = retroArchActivity(context, emu)
    if (retroActivity != null) {
        val core = coreFor(context, system.id, gameFile)
        return Intent(Intent.ACTION_MAIN).apply {
            component = retroActivity
            putExtra("ROM", toFilePath(game))
            putExtra("LIBRETRO", "/data/data/${emu.pkg}/cores/${core}_libretro_android.so")
            // Without these, RetroArch starts with no settings: "Config directory not set" and
            // controllers show as "not configured". These match what RetroArch's own menu passes.
            val sdcard = android.os.Environment.getExternalStorageDirectory().absolutePath
            val external = "$sdcard/Android/data/${emu.pkg}/files"
            putExtra("CONFIGFILE", "$external/retroarch.cfg")
            putExtra("EXTERNAL", external)
            putExtra("SDCARD", sdcard)
            putExtra("DATADIR", "/data/data/${emu.pkg}")
            runCatching { context.packageManager.getApplicationInfo(emu.pkg, 0) }.getOrNull()?.let {
                putExtra("APK", it.sourceDir)
                putExtra("DATADIR", it.dataDir)
            }
            android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            )?.let { putExtra("IME", it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(game, "application/octet-stream")
        setPackage(emu.pkg)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * Finds RetroArch's "play a game" screen inside any RetroArch build,
 * whatever its package name (official, 64-bit, EmuDeck, Plus...).
 */
private fun retroArchActivity(context: Context, emu: Emulator): ComponentName? {
    if (!emu.pkg.startsWith("com.retroarch") && "retroarch" !in emu.name.lowercase()) return null
    val activities = runCatching {
        context.packageManager.getPackageInfo(emu.pkg, PackageManager.GET_ACTIVITIES).activities
    }.getOrNull().orEmpty()
    val target = activities.firstOrNull { it.name.endsWith("RetroActivityFuture") }
        ?: activities.firstOrNull { it.name.contains("RetroActivity") }
    return ComponentName(emu.pkg, target?.name ?: "com.retroarch.browser.retroactivity.RetroActivityFuture")
}

private val RETROARCH_CORES = mapOf(
    "gb" to "mgba", "gbc" to "mgba", "gba" to "mgba", "nes" to "fceumm",
    "snes" to "snes9x", "n64" to "mupen64plus_next", "nds" to "melonds",
    "genesis" to "genesis_plus_gx", "sms" to "genesis_plus_gx", "gg" to "genesis_plus_gx",
    "pce" to "mednafen_pce_fast", "ngp" to "mednafen_ngp", "psx" to "swanstation", "psp" to "ppsspp",
    "dreamcast" to "flycast", "saturn" to "yabasanshiro", "segacd" to "genesis_plus_gx",
    "sega32x" to "picodrive", "sg1000" to "genesis_plus_gx", "gc" to "dolphin", "wii" to "dolphin",
    "3ds" to "citra", "fds" to "fceumm", "virtualboy" to "mednafen_vb", "pokemini" to "pokemini",
    "pcecd" to "mednafen_pce_fast", "ws" to "mednafen_wswan", "wsc" to "mednafen_wswan",
    "neogeo" to "fbneo", "arcade" to "fbneo", "atari2600" to "stella2014", "atari5200" to "a5200",
    "atari7800" to "prosystem", "lynx" to "handy", "jaguar" to "virtualjaguar",
    "colecovision" to "gearcoleco", "intellivision" to "freeintv", "msx" to "bluemsx", "c64" to "vice_x64",
    "ps2" to "pcsx2",
)

/** Turns a picker Uri like ".../primary:ROMs/gba/x.gba" into "/storage/emulated/0/ROMs/gba/x.gba". */
internal fun toFilePath(uri: Uri): String {
    val docId = DocumentsContract.getDocumentId(uri)
    val (volume, path) = docId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return if (volume == "primary") "/storage/emulated/0/$path" else "/storage/$volume/$path"
}
