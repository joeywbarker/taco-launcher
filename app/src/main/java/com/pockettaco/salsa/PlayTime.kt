package com.pockettaco.salsa

import android.content.Context

/**
 * How long you've played each game and when you last played it. A session starts when a
 * game opens and ends when Taco Launcher is back in front or the screen turns off. Sessions
 * under 10 seconds don't count, and one session counts for at most 6 hours.
 */
object PlayTime {
    private var playing: String? = null
    private var since = 0L

    private fun prefs(c: Context) = c.getSharedPreferences("taco", Context.MODE_PRIVATE)
    private fun key(game: Game) = "${game.system.id}|${game.file}"

    fun start(context: Context, game: Game) {
        stop(context)
        playing = key(game); since = System.currentTimeMillis()
        prefs(context).edit().putLong("lastplay_${key(game)}", since).apply()
    }

    fun stop(context: Context) {
        val k = playing ?: return
        playing = null
        val secs = (System.currentTimeMillis() - since) / 1000
        if (secs < 10) return
        val p = prefs(context)
        p.edit().putLong("playtime_$k", p.getLong("playtime_$k", 0) + secs.coerceAtMost(6 * 3600)).apply()
    }

    fun seconds(context: Context, game: Game) = prefs(context).getLong("playtime_${key(game)}", 0)
    fun lastPlayed(context: Context, game: Game) = prefs(context).getLong("lastplay_${key(game)}", 0)

    /** "3 h 12 min played · last played 2 days ago", or null if never played. */
    fun summary(context: Context, game: Game): String? {
        val last = lastPlayed(context, game)
        if (last == 0L) return null
        val s = seconds(context, game)
        val played = when {
            s < 60 -> "Under a minute played"
            s < 3600 -> "${s / 60} min played"
            else -> "${s / 3600} h ${(s % 3600) / 60} min played"
        }
        val ago = android.text.format.DateUtils.getRelativeTimeSpanString(
            last, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
        return "$played · last played $ago"
    }
}
