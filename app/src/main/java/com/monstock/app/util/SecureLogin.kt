package com.monstock.app.util

import android.app.Activity
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth

/**
 * Connexion sécurisée de la boutique : un seul compte Firebase (mot de passe boutique, à saisir une fois
 * par téléphone). Les règles Firestore peuvent alors réserver les données à ce compte.
 * Aucun mot de passe n'est écrit dans le code : la session reste enregistrée sur le téléphone.
 */
object SecureLogin {
    const val SHOP_EMAIL = "gerant@monstock.app"

    fun isSignedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null

    fun signOut() = FirebaseAuth.getInstance().signOut()

    /** « Plus tard » évite de bloquer l'app (hors connexion, ou compte pas encore créé). */
    fun showDialog(activity: Activity, onSuccess: () -> Unit, onLater: (() -> Unit)? = null) {
        val density = activity.resources.displayMetrics.density
        val input = EditText(activity).apply {
            hint = "Mot de passe boutique"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val container = FrameLayout(activity).apply {
            val pad = (20 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle("🔐 Connexion sécurisée")
            .setMessage("Entre le mot de passe boutique. Il protège tes ventes, ton stock et tes commandes. À faire une seule fois sur ce téléphone.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Se connecter", null)
        if (onLater != null) builder.setNegativeButton("Plus tard") { _, _ -> onLater() }
        val dialog = builder.create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val pw = input.text.toString()
                if (pw.length < 6) {
                    input.error = "Mot de passe trop court"
                    return@setOnClickListener
                }
                button.isEnabled = false
                FirebaseAuth.getInstance().signInWithEmailAndPassword(SHOP_EMAIL, pw)
                    .addOnSuccessListener {
                        dialog.dismiss()
                        onSuccess()
                    }
                    .addOnFailureListener {
                        button.isEnabled = true
                        input.error = "Échec : mot de passe incorrect ou pas de connexion"
                    }
            }
        }
        dialog.show()
    }
}
