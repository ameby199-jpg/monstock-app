package com.monstock.app.util

import android.content.Context
import android.graphics.Color
import com.monstock.app.R

/** Gère la couleur de thème choisie par l'utilisateur (persistée sur l'appareil). */
object ThemePrefs {

    private const val PREFS_NAME = "monstock_theme"
    private const val KEY_THEME = "selected_theme"

    enum class AppTheme(val key: String, val label: String, val styleRes: Int, val swatchColor: Int) {
        BLUE("blue", "Bleu", R.style.Theme_MonStock_Blue, Color.parseColor("#1565C0")),
        GREEN("green", "Vert", R.style.Theme_MonStock_Green, Color.parseColor("#2E7D32")),
        ORANGE("orange", "Orange", R.style.Theme_MonStock_Orange, Color.parseColor("#EF6C00")),
        PURPLE("purple", "Violet", R.style.Theme_MonStock_Purple, Color.parseColor("#6A1B9A")),
        RED("red", "Rouge", R.style.Theme_MonStock_Red, Color.parseColor("#C62828")),
        TEAL("teal", "Turquoise", R.style.Theme_MonStock_Teal, Color.parseColor("#00838F"))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedTheme(context: Context): AppTheme {
        val key = prefs(context).getString(KEY_THEME, AppTheme.BLUE.key)
        return AppTheme.values().firstOrNull { it.key == key } ?: AppTheme.BLUE
    }

    fun setSelectedTheme(context: Context, theme: AppTheme) {
        prefs(context).edit().putString(KEY_THEME, theme.key).apply()
    }
}
