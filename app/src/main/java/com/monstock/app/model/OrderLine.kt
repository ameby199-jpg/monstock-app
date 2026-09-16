package com.monstock.app.model

data class OrderLine(
    var productId: String = "",
    var productName: String = "",
    var quantity: Long = 0,
    var unitPrice: Double = 0.0,
    var costPrice: Double = 0.0
)
