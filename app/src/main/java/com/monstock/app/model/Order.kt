package com.monstock.app.model

data class Order(
    var id: String = "",
    var items: List<OrderLine> = emptyList(),
    var timestamp: Long = 0
)
