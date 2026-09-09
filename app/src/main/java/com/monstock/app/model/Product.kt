package com.monstock.app.model

data class Product(
    var id: String = "",
    var name: String = "",
    var quantity: Long = 0,
    var price: Double = 0.0,
    var costPrice: Double = 0.0,
    var photoBase64: String = "",
    var ownerId: String = ""
)
