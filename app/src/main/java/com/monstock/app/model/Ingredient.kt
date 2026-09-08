package com.monstock.app.model

/**
 * Reproduit les colonnes du tableau Excel "Gestionnaire de stock" :
 * - price / stockActuel / achatDuJour sont saisis par l'utilisateur.
 * - stockPresent et depenseDuJour se calculent automatiquement (voir CurrencyFormatter / adapter),
 *   exactement comme les colonnes marquées "auto" dans le fichier Excel :
 *     Stock présent (FCFA) = Stock actuel (FCFA) + Achat du jour (FCFA)
 *     Dépensé du jour (FCFA) = Achat du jour (FCFA)
 */
data class Ingredient(
    var id: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var stockActuel: Double = 0.0,
    var achatDuJour: Double = 0.0,
    var ownerId: String = ""
) {
    val stockPresent: Double get() = stockActuel + achatDuJour
    val depenseDuJour: Double get() = achatDuJour
}
