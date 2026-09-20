package com.monstock.app.ui

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.R
import com.monstock.app.adapter.IngredientAdapter
import com.monstock.app.databinding.DialogAddIngredientBinding
import com.monstock.app.databinding.DialogEditQuantityBinding
import com.monstock.app.databinding.DialogIngredientHistoryBinding
import com.monstock.app.databinding.DialogIngredientHistoryDetailBinding
import com.monstock.app.databinding.FragmentIngredientsBinding
import com.monstock.app.model.Ingredient
import com.monstock.app.model.IngredientHistoryEntry
import com.monstock.app.util.CurrencyFormatter
import com.monstock.app.util.DeleteGuard
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class IngredientsFragment : Fragment() {

    private var _binding: FragmentIngredientsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: FirebaseRepo
    private lateinit var adapter: IngredientAdapter
    private var listener: ListenerRegistration? = null

    // Liste la plus récente reçue du listener, utilisée par le bouton ⛓️ pour reporter les stocks.
    private var currentIngredients: List<Ingredient> = emptyList()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIngredientsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shopCode = ShopPrefs.getShopCode(requireContext()) ?: return
        repo = FirebaseRepo(shopCode)

        binding.btnValidateStock.setOnClickListener { confirmValidateAllStocks() }
        binding.btnHistory.setOnClickListener { showHistoryDialog() }

        adapter = IngredientAdapter(
            items = emptyList(),
            onEdit = { showEditDialog(it) },
            onDelete = { ingredient ->
                DeleteGuard.confirmDelete(requireContext(), ingredient.name) {
                    repo.deleteIngredient(ingredient.id) { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            onEditStockActuel = { ingredient ->
                showQuickEditDialog(
                    title = "Stock actuel : ${ingredient.name}",
                    currentValue = ingredient.stockActuel
                ) { value -> repo.updateStockActuel(ingredient.id, value) { msg -> toastError(msg) } }
            },
            onEditAchat = { ingredient ->
                showQuickEditDialog(
                    title = "Achat du jour : ${ingredient.name}",
                    currentValue = ingredient.achatDuJour
                ) { value -> repo.updateAchatDuJour(ingredient.id, value) { msg -> toastError(msg) } }
            },
            onEditNouveauStock = { ingredient ->
                showQuickEditDialog(
                    title = "Nouveau stock : ${ingredient.name}",
                    currentValue = ingredient.nouveauStock
                ) { value -> repo.updateNouveauStock(ingredient.id, value) { msg -> toastError(msg) } }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }

        listener = repo.listenIngredients { ingredients ->
            currentIngredients = ingredients
            adapter.updateData(ingredients)
            binding.tvEmpty.visibility = if (ingredients.isEmpty()) View.VISIBLE else View.GONE
            updateTotals(ingredients)
        }
    }

    /**
     * Bouton ⛓️ : reporte "Nouveau stock" dans "Stock actuel" pour tous les produits,
     * puis remet "Nouveau stock" et "Achat du jour" à zéro. L'état du jour est d'abord
     * sauvegardé dans l'historique (bouton 💾). Action groupée et irréversible,
     * donc confirmation obligatoire avant de l'appliquer.
     */
    private fun confirmValidateAllStocks() {
        if (currentIngredients.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle("Reporter le nouveau stock ?")
            .setMessage(
                "Cette journée sera d'abord enregistrée dans l'historique (💾).\n\n" +
                    "Ensuite, pour chaque produit : le \"Nouveau stock\" remplacera le \"Stock actuel\", " +
                    "puis \"Nouveau stock\" et \"Achat du jour\" repasseront à 0.\n\nCette action ne peut pas être annulée."
            )
            .setPositiveButton("Valider") { _, _ ->
                repo.validateAllStocks(currentIngredients) { msg -> toastError(msg) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Bouton 💾 : liste des journées enregistrées, la plus récente en premier. */
    private fun showHistoryDialog() {
        repo.getIngredientHistory(
            onResult = { entries -> renderHistoryDialog(entries) },
            onError = { msg -> toastError(msg) }
        )
    }

    private fun renderHistoryDialog(entries: List<IngredientHistoryEntry>) {
        val dialogBinding = DialogIngredientHistoryBinding.inflate(layoutInflater)
        dialogBinding.llHistoryEntries.removeAllViews()

        if (entries.isEmpty()) {
            dialogBinding.tvHistoryEmpty.visibility = View.VISIBLE
        } else {
            dialogBinding.tvHistoryEmpty.visibility = View.GONE
            entries.forEach { entry -> dialogBinding.llHistoryEntries.addView(buildHistoryRow(entry)) }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Historique des journées")
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.cancel, null)
            .show()
    }

    private fun buildHistoryRow(entry: IngredientHistoryEntry): LinearLayout {
        val density = resources.displayMetrics.density
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            setBackgroundColor(Color.parseColor("#F2F2F2"))
            val margin = (6 * density).toInt()
            (layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, margin)
            isClickable = true
            isFocusable = true
        }
        val dateView = TextView(requireContext()).apply {
            text = dateFormat.format(Date(entry.timestamp))
            textSize = 15f
            setTextColor(Color.parseColor("#222222"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val summaryView = TextView(requireContext()).apply {
            val chiffres = entry.totalChiffres
            text = if (chiffres >= 0) {
                "Achat du jour : ${CurrencyFormatter.format(entry.totalAchat)} • BNF ${CurrencyFormatter.format(chiffres)}"
            } else {
                "Achat du jour : ${CurrencyFormatter.format(entry.totalAchat)} • − ${CurrencyFormatter.format(abs(chiffres))}"
            }
            textSize = 13f
            setTextColor(if (chiffres >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        }
        row.addView(dateView)
        row.addView(summaryView)
        row.setOnClickListener { showHistoryDetailDialog(entry) }
        return row
    }

    /** Détail produit par produit d'une journée enregistrée dans l'historique. */
    private fun showHistoryDetailDialog(entry: IngredientHistoryEntry) {
        val dialogBinding = DialogIngredientHistoryDetailBinding.inflate(layoutInflater)
        dialogBinding.llDetailItems.removeAllViews()

        entry.items.forEach { item ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val margin = (8 * resources.displayMetrics.density).toInt()
                (layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, margin)
            }
            val nameView = TextView(requireContext()).apply {
                text = item.name
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val detailView = TextView(requireContext()).apply {
                val diff = item.chiffreValue
                val chiffresText = if (diff >= 0) "BNF ${CurrencyFormatter.format(diff)}" else "− ${CurrencyFormatter.format(abs(diff))}"
                text = "Stock actuel : ${CurrencyFormatter.format(item.stockActuel)} • " +
                    "Achat : ${CurrencyFormatter.format(item.achatDuJour)} • " +
                    "Nouveau stock : ${CurrencyFormatter.format(item.nouveauStock)} • $chiffresText"
                textSize = 12f
                setTextColor(Color.parseColor("#666666"))
            }
            row.addView(nameView)
            row.addView(detailView)
            dialogBinding.llDetailItems.addView(row)
        }

        val totalDiff = entry.totalChiffres
        dialogBinding.tvDetailTotal.text = if (totalDiff >= 0) {
            "Total : BNF ${CurrencyFormatter.format(totalDiff)}"
        } else {
            "Total : − ${CurrencyFormatter.format(abs(totalDiff))}"
        }
        dialogBinding.tvDetailTotal.setTextColor(
            if (totalDiff >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        )

        AlertDialog.Builder(requireContext())
            .setTitle(dateFormat.format(Date(entry.timestamp)))
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.cancel, null)
            .show()
    }

    private fun toastError(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
    }

    /** Totaux automatiques en bas du tableau, comme dans Excel. */
    private fun updateTotals(ingredients: List<Ingredient>) {
        val totalStockActuel = ingredients.sumOf { it.stockActuel }
        val totalAchat = ingredients.sumOf { it.achatDuJour }
        val totalNouveau = ingredients.sumOf { it.nouveauStock }
        val totalChiffres = ingredients.sumOf { it.chiffreValue }

        binding.tvTotalStock.text = "Stock actuel : ${CurrencyFormatter.format(totalStockActuel)}"
        binding.tvTotalAchat.text = "Achat du jour : ${CurrencyFormatter.format(totalAchat)}"
        binding.tvTotalNouveau.text = "Nouveau stock : ${CurrencyFormatter.format(totalNouveau)}"

        if (totalChiffres >= 0) {
            binding.tvTotalChiffres.text = "Chiffres : BNF ${CurrencyFormatter.format(totalChiffres)}"
            binding.tvTotalChiffres.setTextColor(android.graphics.Color.parseColor("#B9F6CA"))
        } else {
            binding.tvTotalChiffres.text = "Chiffres : − ${CurrencyFormatter.format(abs(totalChiffres))}"
            binding.tvTotalChiffres.setTextColor(android.graphics.Color.parseColor("#FFCDD2"))
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddIngredientBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_ingredient)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val price = dialogBinding.etPrice.text.toString().toDoubleOrNull() ?: 0.0
                val stockActuel = dialogBinding.etStockActuel.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) {
                    repo.addIngredient(name, price, stockActuel) { msg -> toastError(msg) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(ingredient: Ingredient) {
        val dialogBinding = DialogEditQuantityBinding.inflate(layoutInflater)
        dialogBinding.etPrice.setText(ingredient.price.toString())
        dialogBinding.etStockActuel.setText(ingredient.stockActuel.toString())
        dialogBinding.etAchatDuJour.setText(ingredient.achatDuJour.toString())
        AlertDialog.Builder(requireContext())
            .setTitle(ingredient.name)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val price = dialogBinding.etPrice.text.toString().toDoubleOrNull() ?: ingredient.price
                val stockActuel = dialogBinding.etStockActuel.text.toString().toDoubleOrNull() ?: ingredient.stockActuel
                val achat = dialogBinding.etAchatDuJour.text.toString().toDoubleOrNull() ?: 0.0
                repo.updateIngredient(ingredient.id, price, stockActuel, achat) { msg -> toastError(msg) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Édition rapide en appuyant directement sur une colonne du tableau (Stock actuel, Achat du jour, Nouveau stock). */
    private fun showQuickEditDialog(title: String, currentValue: Double, onSave: (Double) -> Unit) {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(currentValue.toString())
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val value = input.text.toString().toDoubleOrNull() ?: currentValue
                onSave(value)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        _binding = null
    }
}
