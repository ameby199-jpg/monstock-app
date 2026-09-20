package com.monstock.app.model

/**
 * Une "photo" de l'état des ingrédients au moment où l'utilisateur appuie sur ⛓️.
 * Sert d'historique consultable à tout moment via le bouton 💾.
 */
data class IngredientHistoryItem(
    val name: String = "",
    val stockActuel: Double = 0.0,
    val achatDuJour: Double = 0.0,
    val nouveauStock: Double = 0.0
) {
    val chiffreValue: Double get() = nouveauStock - stockActuel
}

data class IngredientHistoryEntry(
    val id: String = "",
    val timestamp: Long = 0L,
    val items: List<IngredientHistoryItem> = emptyList()
) {
    val totalStockActuel: Double get() = items.sumOf { it.stockActuel }
    val totalAchat: Double get() = items.sumOf { it.achatDuJour }
    val totalNouveau: Double get() = items.sumOf { it.nouveauStock }
    val totalChiffres: Double get() = items.sumOf { it.chiffreValue }
}
