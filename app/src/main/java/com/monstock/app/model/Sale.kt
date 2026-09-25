package com.monstock.app.model

data class Sale(
    var id: String = "",
    var productId: String = "",
    var productName: String = "",
    var quantity: Long = 0,
    var unitPrice: Double = 0.0,
    var costPrice: Double = 0.0,
    var total: Double = 0.0,
    var timestamp: Long = 0,
    // Pour une vente issue d'une commande : heure à laquelle la commande a été passée
    // (timestamp = heure à laquelle elle a été prise / vendue).
    var orderTimestamp: Long = 0,
    var paymentMethod: String = "Espèces",
    var fromOrder: Boolean = false,
    var employeeName: String = "",
    var ownerId: String = ""
)
