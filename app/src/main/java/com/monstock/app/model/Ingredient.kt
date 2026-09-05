package com.monstock.app.model

data class Ingredient(
    var id: String = "",
    var name: String = "",
    var quantity: Double = 0.0,
    var unit: String = "",
    var ownerId: String = ""
)
