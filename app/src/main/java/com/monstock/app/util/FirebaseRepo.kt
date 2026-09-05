package com.monstock.app.util

import android.graphics.Bitmap
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.model.Ingredient
import com.monstock.app.model.Product
import com.monstock.app.model.Sale
import java.io.ByteArrayOutputStream

/**
 * Toutes les données sont stockées sous /shops/{shopCode}/... : products, sales, ingredients.
 * Ainsi, tous les appareils utilisant le même code boutique voient les mêmes données en direct.
 *
 * Les photos de produits sont enregistrées directement dans le document Firestore, sous forme
 * de texte compressé (base64), plutôt que via Firebase Storage : ça évite d'exiger le forfait
 * payant Blaze (obligatoire depuis février 2026 pour Storage) et fonctionne avec un compte
 * Firebase entièrement gratuit.
 *
 * Important : en cas d'erreur réseau passagère sur un listener, on NE remplace PAS les données
 * déjà affichées par une liste vide — ça évite qu'un produit ajouté disparaisse à l'écran
 * simplement parce qu'une lecture a échoué un court instant.
 */
class FirebaseRepo(private val shopCode: String) {

    private val db = FirebaseFirestore.getInstance()
    private fun shopDoc() = db.collection("shops").document(shopCode)

    // ---------- Produits ----------

    fun listenProducts(onChange: (List<Product>) -> Unit): ListenerRegistration {
        return shopDoc().collection("products")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val list = snap.documents.map { d ->
                    Product(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        quantity = d.getLong("quantity") ?: 0,
                        price = d.getDouble("price") ?: 0.0,
                        photoBase64 = d.getString("photoBase64") ?: "",
                        ownerId = shopCode
                    )
                }
                onChange(list.sortedBy { it.name.lowercase() })
            }
    }

    fun addProduct(
        name: String,
        quantity: Long,
        price: Double,
        photo: Bitmap? = null,
        onError: (String) -> Unit = {}
    ) {
        val data = hashMapOf<String, Any>("name" to name, "quantity" to quantity, "price" to price)
        if (photo != null) {
            data["photoBase64"] = compressToBase64(photo)
        }
        shopDoc().collection("products").add(data)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement") }
    }

    fun updateProductPhoto(productId: String, photo: Bitmap, onError: (String) -> Unit = {}) {
        shopDoc().collection("products").document(productId)
            .update("photoBase64", compressToBase64(photo))
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement de la photo") }
    }

    /** Redimensionne et compresse une photo pour qu'elle tienne largement dans un document Firestore. */
    private fun compressToBase64(bitmap: Bitmap): String {
        val maxDim = 300
        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
        val resized = if (ratio < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 55, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    }

    fun updateProductQuantity(productId: String, newQuantity: Long, onError: (String) -> Unit = {}) {
        shopDoc().collection("products").document(productId)
            .update("quantity", newQuantity)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la mise à jour") }
    }

    fun deleteProduct(productId: String, onError: (String) -> Unit = {}) {
        shopDoc().collection("products").document(productId).delete()
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la suppression") }
    }

    // ---------- Ventes ----------

    fun listenSales(onChange: (List<Sale>) -> Unit): ListenerRegistration {
        return shopDoc().collection("sales")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val list = snap.documents.map { d ->
                    Sale(
                        id = d.id,
                        productId = d.getString("productId") ?: "",
                        productName = d.getString("productName") ?: "",
                        quantity = d.getLong("quantity") ?: 0,
                        unitPrice = d.getDouble("unitPrice") ?: 0.0,
                        total = d.getDouble("total") ?: 0.0,
                        timestamp = d.getLong("timestamp") ?: 0,
                        paymentMethod = d.getString("paymentMethod") ?: "Espèces",
                        ownerId = shopCode
                    )
                }
                onChange(list)
            }
    }

    fun recordSale(product: Product, quantitySold: Long, paymentMethod: String, onError: (String) -> Unit = {}) {
        val total = quantitySold * product.price
        val sale = hashMapOf(
            "productId" to product.id,
            "productName" to product.name,
            "quantity" to quantitySold,
            "unitPrice" to product.price,
            "total" to total,
            "timestamp" to System.currentTimeMillis(),
            "paymentMethod" to paymentMethod
        )
        shopDoc().collection("sales").add(sale)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement de la vente") }
        updateProductQuantity(product.id, product.quantity - quantitySold, onError)
    }

    // ---------- Ingrédients / matières premières ----------

    fun listenIngredients(onChange: (List<Ingredient>) -> Unit): ListenerRegistration {
        return shopDoc().collection("ingredients")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val list = snap.documents.map { d ->
                    Ingredient(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        quantity = d.getDouble("quantity") ?: 0.0,
                        unit = d.getString("unit") ?: "",
                        ownerId = shopCode
                    )
                }
                onChange(list.sortedBy { it.name.lowercase() })
            }
    }

    fun addIngredient(name: String, quantity: Double, unit: String, onError: (String) -> Unit = {}) {
        val data = hashMapOf("name" to name, "quantity" to quantity, "unit" to unit)
        shopDoc().collection("ingredients").add(data)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement") }
    }

    fun updateIngredientQuantity(ingredientId: String, newQuantity: Double, onError: (String) -> Unit = {}) {
        shopDoc().collection("ingredients").document(ingredientId)
            .update("quantity", newQuantity)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la mise à jour") }
    }

    fun deleteIngredient(ingredientId: String, onError: (String) -> Unit = {}) {
        shopDoc().collection("ingredients").document(ingredientId).delete()
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la suppression") }
    }
}
