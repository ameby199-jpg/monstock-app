package com.monstock.app.util

import android.graphics.Bitmap
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.monstock.app.model.Employee
import com.monstock.app.model.ExpenseNote
import com.monstock.app.model.Ingredient
import com.monstock.app.model.IngredientHistoryEntry
import com.monstock.app.model.IngredientHistoryItem
import com.monstock.app.model.OnlineOrder
import com.monstock.app.model.OnlineOrderLine
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
                        composants = d.getString("composants") ?: "",
                        isDerived = d.getBoolean("isDerived") ?: false,
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
        isDerived: Boolean = false,
        onError: (String) -> Unit = {}
    ) {
        val data = hashMapOf<String, Any>(
            "name" to name,
            "quantity" to quantity,
            "price" to price,
            "costPrice" to costPrice,
            "isDerived" to isDerived
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
        isDerived: Boolean = false,
        onError: (String) -> Unit = {}
    ) {
        val data = hashMapOf<String, Any>(
            "name" to name,
            "quantity" to quantity,
            "price" to price,
            "costPrice" to costPrice,
            "isDerived" to isDerived
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

    /** Mise à jour rapide de la note "Composants" (appui direct sur la ligne dans Stock). */
    fun updateProductComposants(productId: String, composants: String, onError: (String) -> Unit = {}) {
        shopDoc().collection("products").document(productId)
            .update("composants", composants)
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
                        employeeName = d.getString("employeeName") ?: "",
                        customerProfile = d.getString("customerProfile") ?: "",
                        customerName = d.getString("customerName") ?: "",
                        customerPhone = d.getString("customerPhone") ?: "",
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
        employeeName: String = "",
        customerProfile: String = "",
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
            "fromOrder" to false,
            "employeeName" to employeeName,
            "customerProfile" to customerProfile
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
                            costPrice = (m["costPrice"] as? Number)?.toDouble() ?: 0.0,
                            customerProfile = m["customerProfile"] as? String ?: "",
                            paymentMethod = m["paymentMethod"] as? String ?: "Espèces"
                        )
                    }
                    Order(
                        id = d.id, items = items, timestamp = d.getLong("timestamp") ?: 0,
                        customerName = d.getString("customerName") ?: "",
                        customerPhone = d.getString("customerPhone") ?: "",
                        note = d.getString("note") ?: "",
                        source = d.getString("source") ?: "",
                        onlineId = d.getString("onlineId") ?: "",
                        ready = d.getBoolean("ready") ?: false
                    )
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
                "costPrice" to line.costPrice,
                "customerProfile" to line.customerProfile,
                "paymentMethod" to line.paymentMethod
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
        employeeName: String = "",
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
                "paymentMethod" to line.paymentMethod,
                "fromOrder" to true,
                "employeeName" to employeeName,
                "customerProfile" to line.customerProfile,
                "customerName" to order.customerName,
                "customerPhone" to order.customerPhone
            )
            shopDoc().collection("sales").add(sale)
                .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement de la vente") }

            val current = currentQuantities[line.productId] ?: 0L
            updateProductQuantity(line.productId, current - line.quantity, onError)
        }

        shopDoc().collection("orders").document(order.id).delete()
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la suppression de la commande") }
        setOnlineStatus(order, "done")

        onSuccess()
    }


    // ---------- Commandes en ligne (page web envoyée par WhatsApp) ----------
    // Le lien public ne contient JAMAIS le code boutique : il utilise un identifiant public aléatoire
    // (publicId), et le catalogue publié ne contient ni prix d'achat ni ventes.
    // Les données publiques sont sous /catalogs/{publicId}/products et /catalogs/{publicId}/orders.

    private var publicId: String? = null
    private val syncedHashes = mutableMapOf<String, Int>()
    private var catalogCleaned = false

    private fun catalogDoc(pid: String) = db.collection("catalogs").document(pid)

    /** Lit l'identifiant public s'il existe déjà (null si la fonction n'a jamais été activée). */
    fun getPublicId(onResult: (String?) -> Unit) {
        publicId?.let { onResult(it); return }
        shopDoc().get()
            .addOnSuccessListener { d ->
                val existing = d.getString("publicId")
                if (!existing.isNullOrBlank()) publicId = existing
                onResult(existing?.takeIf { it.isNotBlank() })
            }
            .addOnFailureListener { onResult(null) }
    }

    /** Lit l'identifiant public, ou le crée au premier usage (activation de la fonction). */
    fun getOrCreatePublicId(onResult: (String?) -> Unit) {
        getPublicId { existing ->
            if (existing != null) { onResult(existing); return@getPublicId }
            val fresh = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            shopDoc().set(hashMapOf("publicId" to fresh), SetOptions.merge())
                .addOnSuccessListener { publicId = fresh; onResult(fresh) }
                .addOnFailureListener { onResult(null) }
        }
    }

    /** Publie le catalogue (nom, prix de vente, stock, photo, dérivé) ; n'écrit que ce qui a changé. */
    fun syncCatalog(pid: String, products: List<Product>) {
        val col = catalogDoc(pid).collection("products")
        products.forEach { p ->
            val hash = listOf(p.name, p.price, p.quantity, p.isDerived, p.photoBase64.length, p.photoBase64.hashCode()).hashCode()
            if (syncedHashes[p.id] != hash) {
                syncedHashes[p.id] = hash
                col.document(p.id).set(
                    hashMapOf(
                        "name" to p.name,
                        "price" to p.price,
                        "quantity" to p.quantity,
                        "isDerived" to p.isDerived,
                        "photoBase64" to p.photoBase64
                    )
                ).addOnFailureListener { syncedHashes.remove(p.id) }
            }
        }
        val currentIds = products.map { it.id }.toSet()
        (syncedHashes.keys - currentIds).toList().forEach { id ->
            syncedHashes.remove(id)
            col.document(id).delete()
        }
        // Premier passage après le démarrage : retire du catalogue les produits supprimés entre-temps.
        if (!catalogCleaned && products.isNotEmpty()) {
            catalogCleaned = true
            col.get().addOnSuccessListener { snap ->
                snap.documents.filter { it.id !in currentIds }.forEach { it.reference.delete() }
            }
        }
    }

    fun listenOnlineOrders(pid: String, onChange: (List<OnlineOrder>) -> Unit): ListenerRegistration {
        return catalogDoc(pid).collection("orders")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                onChange(snap.documents.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    val raw = d.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val lines = raw.mapNotNull { m ->
                        val id = m["productId"] as? String ?: return@mapNotNull null
                        val q = (m["quantity"] as? Number)?.toLong() ?: 0L
                        if (q > 0) OnlineOrderLine(id, q.coerceAtMost(999)) else null
                    }
                    OnlineOrder(
                        id = d.id,
                        lines = lines,
                        name = (d.getString("name") ?: "").take(60),
                        phone = (d.getString("phone") ?: "").take(30),
                        payment = d.getString("payment") ?: "Espèces",
                        note = (d.getString("note") ?: "").take(300),
                        timestamp = d.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                })
            }
    }

    /**
     * Transforme une commande web en vraie commande en attente. Les noms et PRIX viennent des produits
     * de l'app (jamais du client). Une transaction garantit qu'un seul appareil l'importe, même si
     * plusieurs téléphones sont ouverts en même temps.
     */
    fun importOnlineOrder(pid: String, online: OnlineOrder, products: List<Product>, onDone: (Boolean) -> Unit) {
        val ref = catalogDoc(pid).collection("orders").document(online.id)
        val byId = products.associateBy { it.id }
        val payment = if (online.payment == "Orange Money" || online.payment == "Wave") online.payment else "Espèces"
        val items = online.lines.mapNotNull { l ->
            val p = byId[l.productId] ?: return@mapNotNull null
            hashMapOf(
                "productId" to p.id,
                "productName" to p.name,
                "quantity" to l.quantity,
                "unitPrice" to p.price,
                "costPrice" to p.costPrice,
                "customerProfile" to "🌐",
                "paymentMethod" to payment
            )
        }
        if (items.isEmpty()) {
            ref.delete()
            onDone(false)
            return
        }
        val newRef = shopDoc().collection("orders").document()
        val data = hashMapOf(
            "items" to items,
            "timestamp" to System.currentTimeMillis(),
            "customerName" to online.name,
            "customerPhone" to online.phone,
            "note" to online.note,
            "source" to "web",
            "onlineId" to online.id,
            "ready" to false
        )
        db.runTransaction<Boolean> { tx ->
            if (!tx.get(ref).exists()) {
                false
            } else {
                tx.delete(ref)
                tx.set(newRef, data)
                true
            }
        }
            .addOnSuccessListener {
                if (it == true) {
                    catalogDoc(pid).collection("status").document(online.id)
                        .set(hashMapOf("state" to "received", "updatedAt" to System.currentTimeMillis()))
                }
                onDone(it == true)
            }
            .addOnFailureListener { onDone(false) }
    }

    /** Met à jour l'état visible par le client sur la page web : received / ready / done / cancelled. */
    fun setOnlineStatus(order: Order, state: String) {
        val pid = publicId ?: return
        if (order.source != "web" || order.onlineId.isBlank()) return
        catalogDoc(pid).collection("status").document(order.onlineId)
            .set(hashMapOf("state" to state, "updatedAt" to System.currentTimeMillis()))
    }

    /** Bouton « Prêt » : le client voit « Votre commande est prête » sur sa page, et la commande est marquée. */
    fun markOrderReady(order: Order) {
        setOnlineStatus(order, "ready")
        shopDoc().collection("orders").document(order.id).update("ready", true)
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

    /** Mise à jour rapide de la seule colonne "Stock actuel" (appui direct dans le tableau). */
    fun updateStockActuel(ingredientId: String, stockActuel: Double, onError: (String) -> Unit = {}) {
        shopDoc().collection("ingredients").document(ingredientId)
            .update("stockActuel", stockActuel)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la mise à jour") }
    }

    /** Mise à jour rapide de la seule colonne "Achat du jour" (appui direct dans le tableau). */
    fun updateAchatDuJour(ingredientId: String, achatDuJour: Double, onError: (String) -> Unit = {}) {
        shopDoc().collection("ingredients").document(ingredientId)
            .update("achatDuJour", achatDuJour)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la mise à jour") }
    }

    fun deleteIngredient(ingredientId: String, onError: (String) -> Unit = {}) {
        shopDoc().collection("ingredients").document(ingredientId).delete()
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la suppression") }
    }

    /**
     * Bouton ⛓️ : pour chaque ingrédient, "Nouveau stock" devient le "Stock actuel",
     * puis "Nouveau stock" et "Achat du jour" repartent à 0.
     * Avant ce reset, l'état du jour (stock actuel / achat du jour / nouveau stock de chaque
     * produit) est enregistré dans l'historique (bouton 💾), consultable à tout moment.
     * Utilise un batch Firestore pour appliquer tout ça d'un coup.
     */
    fun validateAllStocks(ingredients: List<Ingredient>, expenses: List<ExpenseNote> = emptyList(), onError: (String) -> Unit = {}) {
        val batch = db.batch()
        val collection = shopDoc().collection("ingredients")

        // 1) Enregistrement de la photo du jour dans l'historique, avant tout changement.
        val itemsData = ingredients.map { ing ->
            hashMapOf(
                "name" to ing.name,
                "stockActuel" to ing.stockActuel,
                "achatDuJour" to ing.achatDuJour,
                "nouveauStock" to ing.nouveauStock
            )
        }
        val expensesData = expenses.map { exp -> hashMapOf("note" to exp.note, "amount" to exp.amount) }
        val historyDoc = shopDoc().collection("ingredientHistory").document()
        batch.set(historyDoc, hashMapOf("timestamp" to System.currentTimeMillis(), "items" to itemsData, "expenses" to expensesData))

        // 2) Report du nouveau stock vers le stock actuel, puis remise à zéro.
        for (ing in ingredients) {
            val data = mapOf(
                "stockActuel" to ing.nouveauStock,
                "nouveauStock" to 0.0,
                "achatDuJour" to 0.0
            )
            batch.update(collection.document(ing.id), data)
        }
        batch.commit()
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la mise à jour") }
    }

    /** Liste des journées enregistrées via le bouton ⛓️, de la plus récente à la plus ancienne. */
    fun getIngredientHistory(onResult: (List<IngredientHistoryEntry>) -> Unit, onError: (String) -> Unit = {}) {
        shopDoc().collection("ingredientHistory")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { d ->
                    val timestamp = d.getLong("timestamp") ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val itemsRaw = d.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val items = itemsRaw.map { m ->
                        IngredientHistoryItem(
                            name = m["name"] as? String ?: "",
                            stockActuel = (m["stockActuel"] as? Number)?.toDouble() ?: 0.0,
                            achatDuJour = (m["achatDuJour"] as? Number)?.toDouble() ?: 0.0,
                            nouveauStock = (m["nouveauStock"] as? Number)?.toDouble() ?: 0.0
                        )
                    }
                    @Suppress("UNCHECKED_CAST")
                    val expensesRaw = d.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                    val expenses = expensesRaw.map { m ->
                        ExpenseNote(
                            note = m["note"] as? String ?: "",
                            amount = (m["amount"] as? Number)?.toDouble() ?: 0.0
                        )
                    }
                    IngredientHistoryEntry(id = d.id, timestamp = timestamp, items = items, expenses = expenses)
                }
                onResult(list)
            }
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec du chargement de l'historique") }
    }

    // ---------- Employés (connexion par code personnel) ----------

    fun listenEmployees(onChange: (List<Employee>) -> Unit): ListenerRegistration {
        return shopDoc().collection("employees")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val list = snap.documents.map { d ->
                    Employee(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        code = d.getString("code") ?: "",
                        role = d.getString("role") ?: "employe"
                    )
                }
                onChange(list.sortedBy { it.name.lowercase() })
            }
    }

    /** Lecture unique (pas de listener) : utilisée pour vérifier le code au moment de la connexion. */
    fun getEmployeesOnce(onResult: (List<Employee>) -> Unit, onError: (String) -> Unit = {}) {
        shopDoc().collection("employees").get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { d ->
                    Employee(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        code = d.getString("code") ?: "",
                        role = d.getString("role") ?: "employe"
                    )
                }
                onResult(list)
            }
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la connexion") }
    }

    fun addEmployee(name: String, code: String, role: String, onError: (String) -> Unit = {}) {
        val data = hashMapOf("name" to name, "code" to code, "role" to role)
        shopDoc().collection("employees").add(data)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de l'enregistrement") }
    }

    fun updateEmployee(employeeId: String, name: String, code: String, role: String, onError: (String) -> Unit = {}) {
        val data = mapOf("name" to name, "code" to code, "role" to role)
        shopDoc().collection("employees").document(employeeId).update(data)
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la mise à jour") }
    }

    fun deleteEmployee(employeeId: String, onError: (String) -> Unit = {}) {
        shopDoc().collection("employees").document(employeeId).delete()
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Échec de la suppression") }
    }
}
