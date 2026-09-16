package com.monstock.app.util

import android.content.Context
import android.graphics.Color

/** Couleur choisie par l'utilisateur pour le bouton ⏰ (commandes en attente). */
object OrderButtonPrefs {

    private const val PREFS_NAME = "monstock_order_button"
    private const val KEY_COLOR = "color"
    private val DEFAULT_COLOR = Color.parseColor("#FFA000")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getColor(context: Context): Int = prefs(context).getInt(KEY_COLOR, DEFAULT_COLOR)

    fun setColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_COLOR, color).apply()
    }
}
