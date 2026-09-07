package com.monstock.app.util

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Protège les suppressions (produit, ingrédient) derrière un code secret choisi par le patron.
 * La première fois, on demande de créer ce code ; ensuite il est redemandé à chaque suppression.
 */
object DeleteGuard {

    fun confirmDelete(context: Context, itemName: String, onConfirmed: () -> Unit) {
        val code = ShopPrefs.getDeleteCode(context)
        if (code.isNullOrEmpty()) {
            val input = EditText(context).apply {
                hint = "Choisis un code (4 chiffres min.)"
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            AlertDialog.Builder(context)
                .setTitle("Configurer un code de suppression")
                .setMessage("Pour éviter les suppressions accidentelles, choisis un code secret. Il te sera redemandé à chaque suppression.")
                .setView(input)
                .setPositiveButton("Définir") { _, _ ->
                    val newCode = input.text.toString().trim()
                    if (newCode.length >= 4) {
                        ShopPrefs.setDeleteCode(context, newCode)
                        askCodeAndConfirm(context, itemName, newCode, onConfirmed)
                    } else {
                        Toast.makeText(context, "Le code doit contenir au moins 4 chiffres", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        } else {
            askCodeAndConfirm(context, itemName, code, onConfirmed)
        }
    }

    private fun askCodeAndConfirm(context: Context, itemName: String, code: String, onConfirmed: () -> Unit) {
        val input = EditText(context).apply {
            hint = "Code de suppression"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(context)
            .setTitle("Supprimer \"$itemName\" ?")
            .setMessage("Cette action est définitive. Entre le code de suppression pour confirmer.")
            .setView(input)
            .setPositiveButton("Supprimer") { _, _ ->
                if (input.text.toString().trim() == code) {
                    onConfirmed()
                } else {
                    Toast.makeText(context, "Code incorrect — suppression annulée", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
