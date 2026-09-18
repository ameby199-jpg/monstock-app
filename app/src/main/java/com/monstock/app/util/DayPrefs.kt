package com.monstock.app.util

import android.content.Context
import java.util.Calendar

/**
 * Définit ce qui compte comme "aujourd'hui" pour les rapports.
 *
 * Par défaut, c'est minuit du jour calendaire courant. Mais dès que l'utilisateur clique sur
 * "Terminer la journée" (dans Rapports), le point de départ de la journée suivante est fixé à
 * cet instant précis — pas à minuit. Ça permet de finir tard sans que le chiffre d'affaires de
 * la soirée soit coupé en deux entre "hier" et "aujourd'hui".
 */
object DayPrefs {

    private const val PREFS_NAME = "monstock_day"
    private const val KEY_DAY_START = "day_start"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDayStart(context: Context): Long {
        val stored = prefs(context).getLong(KEY_DAY_START, -1L)
        if (stored > 0) return stored
        return startOfToday()
    }

    /** Démarre une nouvelle journée à partir de maintenant (appelé après "Terminer la journée"). */
    fun startNewDay(context: Context) {
        prefs(context).edit().putLong(KEY_DAY_START, System.currentTimeMillis()).apply()
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
