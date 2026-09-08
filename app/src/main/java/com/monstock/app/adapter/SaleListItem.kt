package com.monstock.app.adapter

import com.monstock.app.model.Sale

sealed class SaleListItem {
    data class Header(val label: String, val colorIndex: Int) : SaleListItem()
    data class Row(val sale: Sale) : SaleListItem()
}

/** Palette cyclique : chaque journée reçoit une couleur différente de celle de la veille. */
val DAY_COLOR_PALETTE = listOf(
    com.monstock.app.R.color.primary,
    com.monstock.app.R.color.accent,
    com.monstock.app.R.color.orange_money,
    com.monstock.app.R.color.col_achat,
    com.monstock.app.R.color.wave,
    com.monstock.app.R.color.col_present,
    com.monstock.app.R.color.cash
)
