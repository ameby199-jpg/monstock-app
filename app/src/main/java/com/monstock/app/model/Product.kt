package com.monstock.app.model

data class Product(
    var id: String = "",
    var name: String = "",
    var quantity: Long = 0,
    var price: Double = 0.0,
    var costPrice: Double = 0.0,
    var photoBase64: String = "",
    // Note libre décrivant les ingrédients/composants du produit (ex: "pain, viande, fromage, salade").
    // Simple texte de rappel, pas lié au stock des Ingrédients.
    var composants: String = "",
    // Produit "dérivé" (supplément proposé à la vente d'un autre produit : sauce, boisson, extra...).
    // Il reste visible dans Accueil comme les autres, et peut aussi être ajouté depuis la fenêtre de vente.
    var isDerived: Boolean = false,
    var ownerId: String = ""
)
