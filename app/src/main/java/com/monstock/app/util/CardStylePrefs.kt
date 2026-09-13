package com.monstock.app.util

import android.content.Context
import android.graphics.Color

/** Couleurs choisies par l'utilisateur pour le nom et le prix affichés sur les cartes produits. */
object CardStylePrefs {

    private const val PREFS_NAME = "monstock_card_style"
    private const val KEY_NAME_COLOR = "name_color"
    private const val KEY_PRICE_COLOR = "price_color"

    private val DEFAULT_NAME_COLOR = Color.parseColor("#000000")
    private val DEFAULT_PRICE_COLOR = Color.parseColor("#43A047")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getNameColor(context: Context): Int =
        prefs(context).getInt(KEY_NAME_COLOR, DEFAULT_NAME_COLOR)

    fun getPriceColor(context: Context): Int =
        prefs(context).getInt(KEY_PRICE_COLOR, DEFAULT_PRICE_COLOR)

    fun setNameColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_NAME_COLOR, color).apply()
    }

    fun setPriceColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_PRICE_COLOR, color).apply()
    }
}
