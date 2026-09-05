package com.monstock.app

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Configure Firestore une seule fois au démarrage pour garantir que les données
 * (produits, ventes, ingrédients) restent disponibles même en cas de coupure réseau
 * momentanée, et se synchronisent automatiquement dès que la connexion revient.
 */
class MonStockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        @Suppress("DEPRECATION")
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}
