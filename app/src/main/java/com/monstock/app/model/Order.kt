package com.monstock.app.model

data class Order(
    var id: String = "",
    var items: List<OrderLine> = emptyList(),
    var timestamp: Long = 0,
    // Renseignés pour les commandes reçues via le lien de commande en ligne (WhatsApp).
    var customerName: String = "",
    var customerPhone: String = "",
    var note: String = "",
    var source: String = ""
)

/** Une ligne d'une commande envoyée par un client depuis la page web (seuls produit + quantité comptent). */
data class OnlineOrderLine(val productId: String, val quantity: Long)

/** Commande brute reçue du web, avant d'être transformée en vraie commande en attente. */
data class OnlineOrder(
    val id: String,
    val lines: List<OnlineOrderLine>,
    val name: String,
    val phone: String,
    val payment: String,
    val note: String,
    val timestamp: Long
)
