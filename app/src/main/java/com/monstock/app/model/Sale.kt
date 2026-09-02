package com.monstock.app.model

data class Sale(
    var id: String = "",
    var productId: String = "",
    var productName: String = "",
    var quantity: Long = 0,
    var unitPrice: Double = 0.0,
    var total: Double = 0.0,
    var timestamp: Long = 0,
    var ownerId: String = ""
)
