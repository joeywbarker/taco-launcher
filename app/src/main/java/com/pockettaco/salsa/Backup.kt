package com.pockettaco.salsa

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Saves all of Taco Launcher's settings (and your pictures) into one file you keep, and puts
 * them back from it. Handy before reinstalling, or when moving to a new phone.
 *
 * Folder permissions can't be carried over (Android ties them to the install), so after a
 * restore Taco Launcher asks for your games folder again.
 */
object Backup {
    private val PREFS = listOf("taco", "taco_custom")
    private const val MARK = "taco-launcher-backup.json"

    fun write(context: Context, uri: Uri): Result<Unit> = runCatching {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
        ZipOutputStream(out.buffered()).use { zip ->
            val all = JSONObject()
            PREFS.forEach { name -> all.put(name, prefsToJson(context.getSharedPreferences(name, Context.MODE_PRIVATE).all)) }
            all.put("version", 1)
            zip.putNextEntry(ZipEntry(MARK)); zip.write(all.toString(1).toByteArray()); zip.closeEntry()
            val root = context.filesDir
            // Box art is left out: it downloads again by itself and would make the file big.
            root.walkTopDown().onEnter { it.name != "boxart" }.filter { it.isFile }.forEach { f ->
                zip.putNextEntry(ZipEntry("files/" + f.relativeTo(root).path))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Puts a backup back. Checks the whole file first, so a wrong or broken file changes nothing. */
    fun restore(context: Context, uri: Uri): Result<Unit> = runCatching {
        var prefs: JSONObject? = null
        val files = mutableMapOf<String, ByteArray>()
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.isDirectory -> Unit
                    e.name == MARK -> prefs = JSONObject(zip.readBytes().decodeToString())
                    e.name.startsWith("files/") && !e.name.contains("..") -> files[e.name.removePrefix("files/")] = zip.readBytes()
                }
            }
        }
        val saved = prefs ?: error("This isn't a Taco Launcher backup")
        PREFS.forEach { name ->
            val data = saved.optJSONObject(name) ?: return@forEach
            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            data.keys().forEach { k ->
                val v = data.getJSONObject(k)
                when (v.getString("t")) {
                    "b" -> edit.putBoolean(k, v.getBoolean("v"))
                    "i" -> edit.putInt(k, v.getInt("v"))
                    "l" -> edit.putLong(k, v.getLong("v"))
                    "f" -> edit.putFloat(k, v.getDouble("v").toFloat())
                    "s" -> edit.putString(k, v.getString("v"))
                    "ss" -> edit.putStringSet(k, v.getJSONArray("v").let { a -> (0 until a.length()).map(a::getString).toSet() })
                }
            }
            edit.putBoolean("setupDone", true)
            edit.commit()
        }
        files.forEach { (path, bytes) ->
            val f = File(context.filesDir, path)
            if (!f.canonicalPath.startsWith(context.filesDir.canonicalPath)) return@forEach
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
    }

    /** Starts Taco Launcher again so everything reloads from the restored settings. */
    fun restartApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }

    private fun prefsToJson(all: Map<String, *>): JSONObject = JSONObject().apply {
        all.forEach { (k, v) ->
            val entry = when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> JSONObject().put("t", "s").put("v", v)
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                else -> null
            }
            if (entry != null) put(k, entry)
        }
    }
}
