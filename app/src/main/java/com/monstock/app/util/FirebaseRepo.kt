package com.monstock.app.util

import android.graphics.Bitmap
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.model.Ingredient
import com.monstock.app.model.Order
import com.monstock.app.model.OrderLine
import com.monstock.app.model.Product
import com.monstock.app.model.Sale
import java.io.ByteArrayOutputStream

/**
 * Toutes les données sont stockées sous /shops/{shopCode}/... : products, sales, ingredients, orders.
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
                        costPrice = d.getDouble("costPrice") ?: 0.0,
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
        costPrice: Double = 0.0,
        photo: Bitmap? = null,
        onError: (String) -> Unit = {}
    ) {
        val data = hashMapOf<String, Any>(
            "name" to name,
            "quantity" to quantity,
            "price" to price,
            "costPrice" to costPrice
        )
        if (photo != null) {
            data["photoBase64"] = compressToBase64(photo)
        }
        shopDoc().collection("products").add(data)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement") }
    }

    /** Met à jour les informations d'un produit existant (nom, quantité, prix, prix d'achat, et photo si fournie). */
    fun updateProduct(
        productId: String,
        name: String,
        quantity: Long,
        price: Double,
        costPrice: Double,
        photo: Bitmap? = null,
        onError: (String) -> Unit = {}
    ) {
        val data = hashMapOf<String, Any>(
            "name" to name,
            "quantity" to quantity,
            "price" to price,
            "costPrice" to costPrice
        )
        if (photo != null) {
            data["photoBase64"] = compressToBase64(photo)
        }
        shopDoc().collection("products").document(productId)
            .update(data as Map<String, Any>)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la mise à jour") }
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
                        costPrice = d.getDouble("costPrice") ?: 0.0,
                        total = d.getDouble("total") ?: 0.0,
                        timestamp = d.getLong("timestamp") ?: 0,
                        orderTimestamp = d.getLong("orderTimestamp") ?: 0,
                        paymentMethod = d.getString("paymentMethod") ?: "Espèces",
                        fromOrder = d.getBoolean("fromOrder") ?: false,
                        ownerId = shopCode
                    )
                }
                onChange(list)
            }
    }

    fun recordSale(
        product: Product,
        quantitySold: Long,
        paymentMethod: String,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val total = quantitySold * product.price
        val sale = hashMapOf(
            "productId" to product.id,
            "productName" to product.name,
            "quantity" to quantitySold,
            "unitPrice" to product.price,
            "costPrice" to product.costPrice,
            "total" to total,
            "timestamp" to System.currentTimeMillis(),
            "paymentMethod" to paymentMethod,
            "fromOrder" to false
        )
        shopDoc().collection("sales").add(sale)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement de la vente") }
        updateProductQuantity(product.id, product.quantity - quantitySold, onError)
        onSuccess()
    }

    fun resetSales(onError: (String) -> Unit = {}, onSuccess: () -> Unit = {}) {
        shopDoc().collection("sales").get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la réinitialisation") }
            }
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la lecture des ventes") }
    }

    // ---------- Commandes ----------
    // Une commande peut contenir plusieurs produits différents (panier).

    fun listenOrders(onChange: (List<Order>) -> Unit): ListenerRegistration {
        return shopDoc().collection("orders")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val list = snap.documents.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    val rawItems = d.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val items = rawItems.map { m ->
                        OrderLine(
                            productId = m["productId"] as? String ?: "",
                            productName = m["productName"] as? String ?: "",
                            quantity = (m["quantity"] as? Number)?.toLong() ?: 0,
                            unitPrice = (m["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                            costPrice = (m["costPrice"] as? Number)?.toDouble() ?: 0.0
                        )
                    }
                    Order(id = d.id, items = items, timestamp = d.getLong("timestamp") ?: 0)
                }
                onChange(list)
            }
    }

    /** Enregistre une commande en attente pouvant contenir plusieurs produits (ne touche pas au stock). */
    fun addOrder(items: List<OrderLine>, onError: (String) -> Unit = {}, onSuccess: () -> Unit = {}) {
        val itemMaps = items.map { line ->
            hashMapOf(
                "productId" to line.productId,
                "productName" to line.productName,
                "quantity" to line.quantity,
                "unitPrice" to line.unitPrice,
                "costPrice" to line.costPrice
            )
        }
        val data = hashMapOf(
            "items" to itemMaps,
            "timestamp" to System.currentTimeMillis()
        )
        shopDoc().collection("orders").add(data)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement de la commande") }
        onSuccess()
    }

    /** Annule une commande en attente : elle est simplement retirée, sans impact sur le stock ni les ventes. */
    fun cancelOrder(orderId: String, onError: (String) -> Unit = {}) {
        shopDoc().collection("orders").document(orderId).delete()
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'annulation") }
    }

    /**
     * Transforme une commande (un ou plusieurs produits) en ventes réelles : une vente est créée
     * par produit, avec l'heure de la commande ET l'heure de la prise en compte, le stock de
     * chaque produit est déduit, puis la commande est retirée de la liste d'attente.
     *
     * [currentQuantities] doit venir des données déjà en mémoire (pas d'un nouvel appel réseau),
     * et toutes les écritures sont indépendantes les unes des autres, pour que ça fonctionne
     * immédiatement même sans connexion internet.
     */
    fun takeOrder(
        order: Order,
        currentQuantities: Map<String, Long>,
        paymentMethod: String = "Espèces",
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val now = System.currentTimeMillis()
        order.items.forEach { line ->
            val total = line.quantity * line.unitPrice
            val sale = hashMapOf(
                "productId" to line.productId,
                "productName" to line.productName,
                "quantity" to line.quantity,
                "unitPrice" to line.unitPrice,
                "costPrice" to line.costPrice,
                "total" to total,
                "timestamp" to now,
                "orderTimestamp" to order.timestamp,
                "paymentMethod" to paymentMethod,
                "fromOrder" to true
            )
            shopDoc().collection("sales").add(sale)
                .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement de la vente") }

            val current = currentQuantities[line.productId] ?: 0L
            updateProductQuantity(line.productId, current - line.quantity, onError)
        }

        shopDoc().collection("orders").document(order.id).delete()
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la suppression de la commande") }

        onSuccess()
    }

    // ---------- Ingrédients / matières premières ----------

    fun listenIngredients(onChange: (List<Ingredient>) -> Unit): ListenerRegistration {
        return shopDoc().collection("ingredients")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val list = snap.documents.map { d ->
                    val stockActuel = d.getDouble("stockActuel") ?: 0.0
                    Ingredient(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        price = d.getDouble("price") ?: 0.0,
                        stockActuel = stockActuel,
                        achatDuJour = d.getDouble("achatDuJour") ?: 0.0,
                        // Si jamais renseigné (anciens ingrédients), on part du stock actuel :
                        // Chiffres affiche alors 0 au lieu d'un faux écart.
                        nouveauStock = d.getDouble("nouveauStock") ?: stockActuel,
                        ownerId = shopCode
                    )
                }
                onChange(list.sortedBy { it.name.lowercase() })
            }
    }

    fun addIngredient(name: String, price: Double, stockActuel: Double, onError: (String) -> Unit = {}) {
        val data = hashMapOf(
            "name" to name,
            "price" to price,
            "stockActuel" to stockActuel,
            "achatDuJour" to 0.0,
            "nouveauStock" to stockActuel
        )
        shopDoc().collection("ingredients").add(data)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement") }
    }

    /** Met à jour prix / stock actuel / achat du jour ; les colonnes auto sont recalculées côté affichage. */
    fun updateIngredient(
        ingredientId: String,
        price: Double,
        stockActuel: Double,
        achatDuJour: Double,
        onError: (String) -> Unit = {}
    ) {
        val data = mapOf(
            "price" to price,
            "stockActuel" to stockActuel,
            "achatDuJour" to achatDuJour
        )
        shopDoc().collection("ingredients").document(ingredientId)
            .update(data)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la mise à jour") }
    }

    /** Mise à jour rapide de la seule colonne "Nouveau stock" (appui direct dans le tableau). */
    fun updateNouveauStock(ingredientId: String, nouveauStock: Double, onError: (String) -> Unit = {}) {
        shopDoc().collection("ingredients").document(ingredientId)
            .update("nouveauStock", nouveauStock)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la mise à jour") }
    }

    fun deleteIngredient(ingredientId: String, onError: (String) -> Unit = {}) {
        shopDoc().collection("ingredients").document(ingredientId).delete()
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la suppression") }
    }
}
