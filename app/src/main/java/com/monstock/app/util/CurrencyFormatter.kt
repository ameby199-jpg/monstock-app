package com.monstock.app.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formate un montant en Francs CFA (ex: 1 500 FCFA), sans décimales. */
object CurrencyFormatter {
    private val format: DecimalFormat by lazy {
        val symbols = DecimalFormatSymbols(Locale.FRANCE).apply {
            groupingSeparator = ' '
        }
        DecimalFormat("#,##0", symbols)
    }

    fun format(amount: Double): String = "${format.format(amount)} FCFA"
}
