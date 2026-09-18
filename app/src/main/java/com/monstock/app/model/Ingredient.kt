package com.monstock.app.model

/**
 * Reproduit les colonnes du tableau Excel "Gestionnaire de stock" :
 * - price / stockActuel / achatDuJour / nouveauStock sont saisis par l'utilisateur
 *   (nouveauStock via un appui direct sur sa colonne dans le tableau).
 * - chiffreValue se calcule automatiquement, exactement comme une colonne "auto" dans Excel :
 *     Chiffres (FCFA) = Nouveau stock (FCFA) - Stock actuel (FCFA)
 *   Positif (nouveau stock > stock actuel) = bénéfice, affiché en vert.
 *   Négatif (nouveau stock < stock actuel) = perte, affiché en rouge.
 */
data class Ingredient(
    var id: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var stockActuel: Double = 0.0,
    var achatDuJour: Double = 0.0,
    var nouveauStock: Double = 0.0,
    var ownerId: String = ""
) {
    val stockPresent: Double get() = stockActuel + achatDuJour
    val depenseDuJour: Double get() = achatDuJour
    val chiffreValue: Double get() = nouveauStock - stockActuel
}
