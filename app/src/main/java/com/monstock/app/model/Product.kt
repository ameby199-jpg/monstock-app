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
    var ownerId: String = ""
)
