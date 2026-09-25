package com.monstock.app.ui

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.MainActivity
import com.monstock.app.databinding.DialogEmployeesBinding
import com.monstock.app.databinding.FragmentReportsBinding
import com.monstock.app.databinding.ItemSaleBinding
import com.monstock.app.model.Employee
import com.monstock.app.model.Sale
import com.monstock.app.util.BackgroundPrefs
import com.monstock.app.util.CurrencyFormatter
import com.monstock.app.util.DayPrefs
import com.monstock.app.util.EmployeeSession
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs
import java.util.Calendar

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private var listener: ListenerRegistration? = null
    private var latestSalesToday: List<Sale> = emptyList()
    private var latestSalesWeek: List<Sale> = emptyList()
    private var latestSalesMonth: List<Sale> = emptyList()

    // Bénéfices masqués par défaut ; il faut le code pour les révéler, à chaque ouverture de l'écran.
    private var profitVisible = false
    private val profitPasscode = "1234"

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                BackgroundPrefs.saveCustomBackground(requireContext(), "reports", bitmap)
                binding.ivBackground.setImageBitmap(bitmap)
            } catch (e: Exception) {
                // Ignoré : l'utilisateur peut réessayer
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shopCode = ShopPrefs.getShopCode(requireContext()) ?: return
        val repo = FirebaseRepo(shopCode)

        BackgroundPrefs.applyBackground(requireContext(), "reports", binding.ivBackground)
        binding.btnChangeBackground.setOnClickListener { pickBackground.launch("image/*") }

        binding.recyclerViewTop.layoutManager = LinearLayoutManager(requireContext())

        binding.btnResetReports.setOnClickListener {
            com.monstock.app.util.DeleteGuard.confirmDelete(requireContext(), "tout l'historique des ventes") {
                repo.resetSales(
                    onError = { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    },
                    onSuccess = {
                        android.widget.Toast.makeText(requireContext(), "✅ Rapports réinitialisés", android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        binding.btnEndDay.setOnClickListener { showEndDayConfirmation() }

        binding.btnLogout.setOnClickListener { logout() }
        if (EmployeeSession.isResponsable(requireContext())) {
            binding.btnManageEmployees.visibility = View.VISIBLE
            binding.btnManageEmployees.setOnClickListener { showEmployeesDialog(repo) }
        }

        val profitClick = View.OnClickListener { toggleProfitVisibility() }
        binding.tvTodayProfit.setOnClickListener(profitClick)
        binding.tvWeekProfit.setOnClickListener(profitClick)
        binding.tvMonthProfit.setOnClickListener(profitClick)

        listener = repo.listenSales { sales -> updateStats(sales) }
    }

    /** Bénéfices masqués par "🔒 Appuyer pour afficher" ; un appui demande le code pour les révéler. */
    private fun toggleProfitVisibility() {
        if (profitVisible) {
            profitVisible = false
            renderProfits()
            return
        }
        val input = android.widget.EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)
        AlertDialog.Builder(requireContext())
            .setTitle("Code requis")
            .setMessage("Entrer le code pour afficher les bénéfices.")
            .setView(input)
            .setPositiveButton("Valider") { _, _ ->
                if (input.text.toString() == profitPasscode) {
                    profitVisible = true
                    renderProfits()
                } else {
                    android.widget.Toast.makeText(requireContext(), "Code incorrect", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun renderProfits() {
        if (profitVisible) {
            binding.tvTodayProfit.text = CurrencyFormatter.format(profit(latestSalesToday))
            binding.tvWeekProfit.text = CurrencyFormatter.format(profit(latestSalesWeek))
            binding.tvMonthProfit.text = CurrencyFormatter.format(profit(latestSalesMonth))
        } else {
            val hidden = "🔒 Appuyer pour afficher"
            binding.tvTodayProfit.text = hidden
            binding.tvWeekProfit.text = hidden
            binding.tvMonthProfit.text = hidden
        }
    }

    /**
     * Affiche un résumé de la journée en cours (CA + bénéfice), sans rien supprimer.
     * En validant, la journée suivante repart de maintenant plutôt que de minuit — utile
     * pour qui termine tard sans que ses ventes se retrouvent coupées entre deux jours.
     */
    private fun showEndDayConfirmation() {
        val revenue = latestSalesToday.sumOf { it.total }
        val profit = profit(latestSalesToday)
        val count = latestSalesToday.size

        AlertDialog.Builder(requireContext())
            .setTitle("Terminer la journée ?")
            .setMessage(
                "Résumé du jour :\n\n" +
                    "Ventes : $count\n" +
                    "Chiffre d'affaires : ${CurrencyFormatter.format(revenue)}\n" +
                    "Bénéfice : ${CurrencyFormatter.format(profit)}\n\n" +
                    "Rien ne sera supprimé. La prochaine journée commencera à partir de maintenant."
            )
            .setPositiveButton("Terminer") { _, _ ->
                DayPrefs.startNewDay(requireContext())
                android.widget.Toast.makeText(
                    requireContext(),
                    "✅ Journée terminée — la nouvelle journée commence maintenant",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /** Redemande le code à la prochaine ouverture, pour qu'un autre employé puisse se connecter. */
    private fun logout() {
        EmployeeSession.logout(requireContext())
        val intent = android.content.Intent(requireActivity(), MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    /** Réservé au responsable : liste des employés, avec ajout/modification/suppression de leur code. */
    private fun showEmployeesDialog(repo: FirebaseRepo) {
        repo.getEmployeesOnce(
            onResult = { employees -> renderEmployeesDialog(repo, employees) },
            onError = { msg -> android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show() }
        )
    }

    private fun renderEmployeesDialog(repo: FirebaseRepo, employees: List<Employee>) {
        val dialogBinding = DialogEmployeesBinding.inflate(layoutInflater)
        dialogBinding.llEmployeesList.removeAllViews()

        employees.forEach { employee ->
            val row = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
                val margin = (6 * resources.displayMetrics.density).toInt()
                setPadding(margin, margin, margin, margin)
            }
            val info = android.widget.TextView(requireContext()).apply {
                text = "${employee.name}${if (employee.isResponsable) "  👑" else ""}"
                textSize = 15f
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val editBtn = android.widget.TextView(requireContext()).apply {
                text = "✏️"
                textSize = 18f
                setPadding(24, 0, 24, 0)
                setOnClickListener { showAddEditEmployeeDialog(repo, employee) }
            }
            val deleteBtn = android.widget.TextView(requireContext()).apply {
                text = "🗑"
                textSize = 18f
                setOnClickListener {
                    com.monstock.app.util.DeleteGuard.confirmDelete(requireContext(), employee.name) {
                        repo.deleteEmployee(employee.id) { msg ->
                            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            row.addView(info)
            row.addView(editBtn)
            row.addView(deleteBtn)
            dialogBinding.llEmployeesList.addView(row)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Employés")
            .setView(dialogBinding.root)
            .setNegativeButton("Fermer", null)
            .create()

        dialogBinding.btnAddEmployeeRow.setOnClickListener {
            dialog.dismiss()
            showAddEditEmployeeDialog(repo, null)
        }
        dialog.show()
    }

    /** [employee] null = ajout ; sinon modification de son nom / code / rôle. */
    private fun showAddEditEmployeeDialog(repo: FirebaseRepo, employee: Employee?) {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val nameInput = android.widget.EditText(requireContext()).apply {
            hint = "Nom"
            setText(employee?.name ?: "")
        }
        val codeInput = android.widget.EditText(requireContext()).apply {
            hint = "Code"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(employee?.code ?: "")
        }
        val responsableCheck = android.widget.CheckBox(requireContext()).apply {
            text = "Responsable (peut gérer les employés)"
            isChecked = employee?.isResponsable ?: false
        }
        container.addView(nameInput)
        container.addView(codeInput)
        container.addView(responsableCheck)

        AlertDialog.Builder(requireContext())
            .setTitle(if (employee == null) "Ajouter un employé" else "Modifier : ${employee.name}")
            .setView(container)
            .setPositiveButton("Enregistrer") { _, _ ->
                val name = nameInput.text.toString().trim()
                val code = codeInput.text.toString().trim()
                val role = if (responsableCheck.isChecked) "responsable" else "employe"
                if (name.isEmpty() || code.isEmpty()) return@setPositiveButton
                val onError: (String) -> Unit = { msg ->
                    android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                }
                if (employee == null) {
                    repo.addEmployee(name, code, role, onError)
                } else {
                    repo.updateEmployee(employee.id, name, code, role, onError)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun updateStats(sales: List<Sale>) {
        val now = Calendar.getInstance()

        // "Aujourd'hui" part de la dernière fois où l'utilisateur a terminé sa journée,
        // pas forcément de minuit — voir DayPrefs.
        val startOfDay = DayPrefs.getDayStart(requireContext())

        val startOfWeek = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis

        val startOfMonth = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis

        val salesToday = sales.filter { it.timestamp >= startOfDay }
        val salesWeek = sales.filter { it.timestamp >= startOfWeek }
        val salesMonth = sales.filter { it.timestamp >= startOfMonth }
        latestSalesToday = salesToday
        latestSalesWeek = salesWeek
        latestSalesMonth = salesMonth

        binding.tvToday.text = CurrencyFormatter.format(salesToday.sumOf { it.total })
        binding.tvWeek.text = CurrencyFormatter.format(salesWeek.sumOf { it.total })
        binding.tvMonth.text = CurrencyFormatter.format(salesMonth.sumOf { it.total })

        binding.tvTodayBreakdown.text = paymentBreakdown(salesToday)
        binding.tvWeekBreakdown.text = paymentBreakdown(salesWeek)
        binding.tvMonthBreakdown.text = paymentBreakdown(salesMonth)

        binding.tvTodayOrdersSplit.text = ordersSplit(salesToday)
        binding.tvWeekOrdersSplit.text = ordersSplit(salesWeek)
        binding.tvMonthOrdersSplit.text = ordersSplit(salesMonth)

        renderProfits()

        // Tous les produits vendus AUJOURD'HUI (et non l'historique complet), du plus vendu au moins vendu.
        val topByProduct = salesToday.groupBy { it.productName }
            .map { (name, list) -> Triple(name, list.sumOf { it.quantity }, list.sumOf { it.total }) }
            .sortedByDescending { it.third }

        binding.recyclerViewTop.adapter = TopProductAdapter(topByProduct)
    }

    /** Bénéfice = (prix de vente - prix d'achat) x quantité, pour une liste de ventes. */
    private fun profit(sales: List<Sale>): Double =
        sales.sumOf { (it.unitPrice - it.costPrice) * it.quantity }

    /** Répartition ventes directes / issues d'une commande ⏰, pour une liste de ventes. */
    private fun ordersSplit(sales: List<Sale>): String {
        if (sales.isEmpty()) return ""
        val direct = sales.filter { !it.fromOrder }.sumOf { it.total }
        val fromOrders = sales.filter { it.fromOrder }.sumOf { it.total }
        return "🛒 Ventes directes : ${CurrencyFormatter.format(direct)}  •  🕑 Via commande : ${CurrencyFormatter.format(fromOrders)}"
    }

    private fun paymentBreakdown(sales: List<Sale>): String {
        if (sales.isEmpty()) return "Aucune vente"
        val byMethod = sales.groupBy { it.paymentMethod }
        return listOf("Espèces", "Orange Money", "Wave").mapNotNull { method ->
            val total = byMethod[method]?.sumOf { it.total } ?: return@mapNotNull null
            "$method : ${CurrencyFormatter.format(total)}"
        }.joinToString("  •  ")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        _binding = null
    }
}

private class TopProductAdapter(
    private val items: List<Triple<String, Long, Double>>
) : RecyclerView.Adapter<TopProductAdapter.VH>() {

    inner class VH(val binding: ItemSaleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSaleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (name, qty, total) = items[position]
        holder.binding.tvSaleName.text = name
        holder.binding.tvSaleDetails.text = "Vendu: $qty  •  ${CurrencyFormatter.format(total)}"
        holder.binding.tvSalePayment.text = ""
        holder.binding.viewPaymentDot.visibility = android.view.View.GONE
    }

    override fun getItemCount() = items.size
}
