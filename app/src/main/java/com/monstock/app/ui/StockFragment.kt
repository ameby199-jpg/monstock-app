package com.monstock.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.adapter.ProductAdapter
import com.monstock.app.databinding.DialogAddProductBinding
import com.monstock.app.databinding.DialogSellBinding
import com.monstock.app.databinding.FragmentStockBinding
import com.monstock.app.model.Product
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs

class StockFragment : Fragment() {

    private var _binding: FragmentStockBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: FirebaseRepo
    private lateinit var adapter: ProductAdapter
    private var listener: ListenerRegistration? = null
    private var currentProducts: List<Product> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shopCode = ShopPrefs.getShopCode(requireContext()) ?: return
        repo = FirebaseRepo(shopCode)

        adapter = ProductAdapter(
            items = emptyList(),
            onSell = { showSellDialog(it) },
            onDelete = { repo.deleteProduct(it.id) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }

        listener = repo.listenProducts { products ->
            currentProducts = products
            adapter.updateData(products)
            binding.tvEmpty.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddProductBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle(com.monstock.app.R.string.add_product)
            .setView(dialogBinding.root)
            .setPositiveButton(com.monstock.app.R.string.save) { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val qty = dialogBinding.etQuantity.text.toString().toLongOrNull() ?: 0L
                val price = dialogBinding.etPrice.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) {
                    repo.addProduct(name, qty, price)
                }
            }
            .setNegativeButton(com.monstock.app.R.string.cancel, null)
            .show()
    }

    private fun showSellDialog(product: Product) {
        val dialogBinding = DialogSellBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle("Vendre : ${product.name}")
            .setView(dialogBinding.root)
            .setPositiveButton(com.monstock.app.R.string.sell) { _, _ ->
                val qtySold = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
                if (qtySold in 1..product.quantity) {
                    repo.recordSale(product, qtySold)
                }
            }
            .setNegativeButton(com.monstock.app.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        _binding = null
    }
}
