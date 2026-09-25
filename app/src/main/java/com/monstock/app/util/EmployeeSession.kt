package com.monstock.app.util

import android.content.Context
import com.monstock.app.model.Employee

/**
 * Retient qui est connecté sur CET appareil (pas partagé entre appareils). L'app redemande le
 * code à chaque ouverture : voir MainActivity, qui appelle logout() avant d'afficher l'écran
 * de connexion, sauf immédiatement après une connexion réussie dans la même session.
 */
object EmployeeSession {

    private const val PREFS_NAME = "monstock_session"
    private const val KEY_ID = "employee_id"
    private const val KEY_NAME = "employee_name"
    private const val KEY_ROLE = "employee_role"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun login(context: Context, employee: Employee) {
        prefs(context).edit()
            .putString(KEY_ID, employee.id)
            .putString(KEY_NAME, employee.name)
            .putString(KEY_ROLE, employee.role)
            .apply()
    }

    fun logout(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun isLoggedIn(context: Context): Boolean = prefs(context).getString(KEY_ID, null) != null

    fun getCurrentName(context: Context): String = prefs(context).getString(KEY_NAME, "") ?: ""

    fun isResponsable(context: Context): Boolean = prefs(context).getString(KEY_ROLE, "") == "responsable"
}
