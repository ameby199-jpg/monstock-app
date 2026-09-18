package com.monstock.app.ui

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.R
import com.monstock.app.adapter.IngredientAdapter
import com.monstock.app.databinding.DialogAddIngredientBinding
import com.monstock.app.databinding.DialogEditQuantityBinding
import com.monstock.app.databinding.FragmentIngredientsBinding
import com.monstock.app.model.Ingredient
import com.monstock.app.util.BackgroundPrefs
import com.monstock.app.util.CurrencyFormatter
import com.monstock.app.util.DeleteGuard
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs
import kotlin.math.abs

class IngredientsFragment : Fragment() {

    private var _binding: FragmentIngredientsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: FirebaseRepo
    private lateinit var adapter: IngredientAdapter
    private var listener: ListenerRegistration? = null

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                BackgroundPrefs.saveCustomBackground(requireContext(), "ingredients", bitmap)
                binding.ivBackground.setImageBitmap(bitmap)
            } catch (e: Exception) {
                // Ignoré : l'utilisateur peut réessayer
            }
        }
    }

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

        BackgroundPrefs.applyBackground(requireContext(), "ingredients", binding.ivBackground)
        binding.btnChangeBackground.setOnClickListener { pickBackground.launch("image/*") }

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
            onEditNouveauStock = { showEditNouveauStockDialog(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }

        listener = repo.listenIngredients { ingredients ->
            adapter.updateData(ingredients)
            binding.tvEmpty.visibility = if (ingredients.isEmpty()) View.VISIBLE else View.GONE
            updateTotals(ingredients)
        }
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
                    repo.addIngredient(name, price, stockActuel) { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    }
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
                repo.updateIngredient(ingredient.id, price, stockActuel, achat) { msg ->
                    android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Édition rapide en appuyant directement sur la colonne "Nouveau stock" du tableau. */
    private fun showEditNouveauStockDialog(ingredient: Ingredient) {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(ingredient.nouveauStock.toString())
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)

        AlertDialog.Builder(requireContext())
            .setTitle("Nouveau stock : ${ingredient.name}")
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val value = input.text.toString().toDoubleOrNull() ?: ingredient.nouveauStock
                repo.updateNouveauStock(ingredient.id, value) { msg ->
                    android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                }
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
