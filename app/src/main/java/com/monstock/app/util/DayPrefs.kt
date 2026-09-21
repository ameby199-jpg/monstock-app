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
    private const val KEY_BOUNDARIES = "day_boundaries"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDayStart(context: Context): Long {
        val stored = prefs(context).getLong(KEY_DAY_START, -1L)
        if (stored > 0) return stored
        return startOfToday()
    }

    /**
     * Démarre une nouvelle journée à partir de maintenant (appelé après "Terminer la journée").
     * L'instant est aussi ajouté à l'historique des frontières, pour que Ventes puisse regrouper
     * les anciennes ventes par "journée métier" (entre deux clics sur "Terminer la journée")
     * plutôt que par jour calendaire.
     */
    fun startNewDay(context: Context) {
        val now = System.currentTimeMillis()
        val history = getBoundaries(context).toMutableList()
        history.add(now)
        prefs(context).edit()
            .putLong(KEY_DAY_START, now)
            .putString(KEY_BOUNDARIES, history.joinToString(","))
            .apply()
    }

    /** Toutes les frontières de journée déjà validées via "Terminer la journée", triées du plus ancien au plus récent. */
    fun getBoundaries(context: Context): List<Long> {
        val raw = prefs(context).getString(KEY_BOUNDARIES, null) ?: return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.sorted()
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
