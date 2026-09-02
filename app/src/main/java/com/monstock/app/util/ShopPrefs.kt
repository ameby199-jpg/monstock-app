package com.monstock.app.util

import android.content.Context

/**
 * Le "code boutique" permet à plusieurs téléphones de partager
 * le même stock : il suffit d'utiliser le même code sur chaque appareil.
 */
object ShopPrefs {
    private const val PREFS = "monstock_prefs"
    private const val KEY_SHOP = "shop_code"

    fun getShopCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SHOP, null)
    }

    fun setShopCode(context: Context, code: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SHOP, code.trim().lowercase()).apply()
    }
}
