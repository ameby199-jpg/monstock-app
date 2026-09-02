package com.monstock.app.util

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.model.Product
import com.monstock.app.model.Sale

/**
 * Toutes les données sont stockées sous /shops/{shopCode}/products et /shops/{shopCode}/sales
 * Ainsi, tous les appareils utilisant le même code boutique voient les mêmes données en direct.
 */
class FirebaseRepo(private val shopCode: String) {

    private val db = FirebaseFirestore.getInstance()
    private fun shopDoc() = db.collection("shops").document(shopCode)

    fun listenProducts(onChange: (List<Product>) -> Unit): ListenerRegistration {
        return shopDoc().collection("products")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map { d ->
                    Product(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        quantity = d.getLong("quantity") ?: 0,
                        price = d.getDouble("price") ?: 0.0,
                        ownerId = shopCode
                    )
                } ?: emptyList()
                onChange(list.sortedBy { it.name.lowercase() })
            }
    }

    fun addProduct(name: String, quantity: Long, price: Double) {
        val data = hashMapOf("name" to name, "quantity" to quantity, "price" to price)
        shopDoc().collection("products").add(data)
    }

    fun updateProductQuantity(productId: String, newQuantity: Long) {
        shopDoc().collection("products").document(productId)
            .update("quantity", newQuantity)
    }

    fun deleteProduct(productId: String) {
        shopDoc().collection("products").document(productId).delete()
    }

    fun listenSales(onChange: (List<Sale>) -> Unit): ListenerRegistration {
        return shopDoc().collection("sales")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map { d ->
                    Sale(
                        id = d.id,
                        productId = d.getString("productId") ?: "",
                        productName = d.getString("productName") ?: "",
                        quantity = d.getLong("quantity") ?: 0,
                        unitPrice = d.getDouble("unitPrice") ?: 0.0,
                        total = d.getDouble("total") ?: 0.0,
                        timestamp = d.getLong("timestamp") ?: 0,
                        ownerId = shopCode
                    )
                } ?: emptyList()
                onChange(list)
            }
    }

    fun recordSale(product: Product, quantitySold: Long) {
        val total = quantitySold * product.price
        val sale = hashMapOf(
            "productId" to product.id,
            "productName" to product.name,
            "quantity" to quantitySold,
            "unitPrice" to product.price,
            "total" to total,
            "timestamp" to System.currentTimeMillis()
        )
        shopDoc().collection("sales").add(sale)
        updateProductQuantity(product.id, product.quantity - quantitySold)
    }
}
