package com.monstock.app.util

import android.content.Context
import com.monstock.app.model.Employee

/**
 * Une empreinte digitale enregistrée sur le téléphone ne permet de savoir QUE "c'est un doigt
 * autorisé sur cet appareil", pas quel employé précisément. On retient donc, par appareil, UN
 * seul employé "rapide" : quand son 🫆 est activé (voir Rapports > Employés), l'écran de
 * connexion propose l'empreinte à la place du code, et une empreinte reconnue par le téléphone
 * connecte directement en tant que cet employé.
 */
object BiometricPrefs {

    private const val PREFS_NAME = "monstock_biometric"
    private const val KEY_ID = "biometric_employee_id"
    private const val KEY_NAME = "biometric_employee_name"
    private const val KEY_CODE = "biometric_employee_code"
    private const val KEY_ROLE = "biometric_employee_role"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setBiometricEmployee(context: Context, employee: Employee) {
        prefs(context).edit()
            .putString(KEY_ID, employee.id)
            .putString(KEY_NAME, employee.name)
            .putString(KEY_CODE, employee.code)
            .putString(KEY_ROLE, employee.role)
            .apply()
    }

    fun clearBiometricEmployee(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun getBiometricEmployeeId(context: Context): String? = prefs(context).getString(KEY_ID, null)

    fun getBiometricEmployee(context: Context): Employee? {
        val id = prefs(context).getString(KEY_ID, null) ?: return null
        return Employee(
            id = id,
            name = prefs(context).getString(KEY_NAME, "") ?: "",
            code = prefs(context).getString(KEY_CODE, "") ?: "",
            role = prefs(context).getString(KEY_ROLE, "employe") ?: "employe"
        )
    }
}
