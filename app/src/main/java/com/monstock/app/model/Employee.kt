package com.monstock.app.model

/** Un utilisateur de l'app : le responsable (patron) ou un employé, chacun avec son propre code. */
data class Employee(
    var id: String = "",
    var name: String = "",
    var code: String = "",
    // "responsable" : peut ajouter/gérer les employés. "employe" : accès normal à l'app seulement.
    var role: String = "employe"
) {
    val isResponsable: Boolean get() = role == "responsable"
}
