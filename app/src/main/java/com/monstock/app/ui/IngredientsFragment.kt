package com.monstock.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs

class IngredientsFragment : Fragment() {

    private var _binding: FragmentIngredientsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: FirebaseRepo
    private lateinit var adapter: IngredientAdapter
    private var listener: ListenerRegistration? = null

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

        adapter = IngredientAdapter(
            items = emptyList(),
            onEdit = { showEditQuantityDialog(it) },
            onDelete = { repo.deleteIngredient(it.id) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }

        listener = repo.listenIngredients { ingredients ->
            adapter.updateData(ingredients)
            binding.tvEmpty.visibility = if (ingredients.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddIngredientBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_ingredient)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val qty = dialogBinding.etQuantity.text.toString().toDoubleOrNull() ?: 0.0
                val unit = dialogBinding.etUnit.text.toString().trim()
                if (name.isNotEmpty()) {
                    repo.addIngredient(name, qty, unit) { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditQuantityDialog(ingredient: Ingredient) {
        val dialogBinding = DialogEditQuantityBinding.inflate(layoutInflater)
        dialogBinding.etNewQuantity.setText(ingredient.quantity.toString())
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_quantity)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newQty = dialogBinding.etNewQuantity.text.toString().toDoubleOrNull()
                if (newQty != null) {
                    repo.updateIngredientQuantity(ingredient.id, newQty)
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
