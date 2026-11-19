package com.pockettaco.salsa

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract

/** A game system and the file endings its games use. */
data class GameSystem(val id: String, val name: String, val extensions: Set<String>)

/** In the order they appear on screen: classic 2D systems first. */
val SYSTEMS = listOf(
    GameSystem("gba", "Game Boy Advance", setOf("gba")),
    GameSystem("gbc", "Game Boy Color", setOf("gbc")),
    GameSystem("gb", "Game Boy", setOf("gb")),
    GameSystem("nes", "NES", setOf("nes")),
    GameSystem("snes", "SNES", setOf("sfc", "smc")),
    GameSystem("genesis", "Genesis / Mega Drive", setOf("md", "gen", "smd")),
    GameSystem("sms", "Master System", setOf("sms")),
    GameSystem("gg", "Game Gear", setOf("gg")),
    GameSystem("pce", "TurboGrafx-16 / PC Engine", setOf("pce")),
    GameSystem("ngp", "Neo Geo Pocket", setOf("ngp", "ngc")),
    GameSystem("nds", "Nintendo DS", setOf("nds")),
    GameSystem("n64", "Nintendo 64", setOf("n64", "z64", "v64")),
    GameSystem("psx", "PlayStation", setOf("cue", "chd", "pbp")),
    GameSystem("psp", "PSP", setOf("iso", "cso")),
)

/** Emulator apps we know about. Add more here; the list is just a lookup table. */
data class Emulator(val pkg: String, val name: String, val systems: Set<String>)

val KNOWN_EMULATORS = listOf(
    Emulator("com.retroarch", "RetroArch", SYSTEMS.map { it.id }.toSet()),
    Emulator("com.retroarch.aarch64", "RetroArch (64-bit)", SYSTEMS.map { it.id }.toSet()),
    Emulator("com.swordfish.lemuroid", "Lemuroid",
        setOf("gb", "gbc", "gba", "nes", "snes", "genesis", "sms", "gg", "pce", "ngp",
            "n64", "nds", "psx", "psp")),
    Emulator("com.retroarch.ra32", "RetroArch (32-bit)", SYSTEMS.map { it.id }.toSet()),
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
)

fun installedEmulators(context: Context): List<Emulator> =
    KNOWN_EMULATORS.filter { isInstalled(context, it.pkg) }

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
fun launchIntent(emu: Emulator, system: GameSystem, game: Uri): Intent {
    if (emu.pkg.startsWith("com.retroarch")) {
        val core = RETROARCH_CORES[system.id] ?: "mgba"
        return Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(emu.pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", toFilePath(game))
            putExtra("LIBRETRO", "/data/data/${emu.pkg}/cores/${core}_libretro_android.so")
            putExtra("CONFIGFILE", "/storage/emulated/0/Android/data/${emu.pkg}/files/retroarch.cfg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(game, "application/octet-stream")
        setPackage(emu.pkg)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private val RETROARCH_CORES = mapOf(
    "gb" to "gambatte", "gbc" to "gambatte", "gba" to "mgba", "nes" to "fceumm",
    "snes" to "snes9x", "n64" to "mupen64plus_next", "nds" to "melonds",
    "genesis" to "genesis_plus_gx", "sms" to "genesis_plus_gx", "gg" to "genesis_plus_gx",
    "pce" to "mednafen_pce_fast", "ngp" to "mednafen_ngp", "psx" to "swanstation", "psp" to "ppsspp",
)

/** Turns a picker Uri like ".../primary:ROMs/gba/x.gba" into "/storage/emulated/0/ROMs/gba/x.gba". */
private fun toFilePath(uri: Uri): String {
    val docId = DocumentsContract.getDocumentId(uri)
    val (volume, path) = docId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return if (volume == "primary") "/storage/emulated/0/$path" else "/storage/$volume/$path"
}
