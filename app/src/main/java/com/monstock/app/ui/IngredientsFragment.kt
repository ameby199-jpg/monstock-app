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
            }
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

    /** Reproduit la ligne TOTAL du tableau Excel : Stock actuel, Stock présent (auto), Achat/Dépensé du jour (auto). */
    private fun updateTotals(ingredients: List<Ingredient>) {
        val totalStockActuel = ingredients.sumOf { it.stockActuel }
        val totalStockPresent = ingredients.sumOf { it.stockPresent }
        val totalAchat = ingredients.sumOf { it.achatDuJour }

        binding.tvTotalStock.text =
            "Stock actuel: ${CurrencyFormatter.format(totalStockActuel)}  •  Stock présent (auto): ${CurrencyFormatter.format(totalStockPresent)}"
        binding.tvTotalAchat.text =
            "Achat du jour: ${CurrencyFormatter.format(totalAchat)}  •  Dépensé (auto): ${CurrencyFormatter.format(totalAchat)}"
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

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        _binding = null
    }
}
