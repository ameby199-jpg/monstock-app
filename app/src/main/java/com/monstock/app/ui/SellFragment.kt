package com.monstock.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.R
import com.monstock.app.adapter.SellProductAdapter
import com.monstock.app.databinding.DialogSellBinding
import com.monstock.app.databinding.FragmentSellBinding
import com.monstock.app.model.Product
import com.monstock.app.model.Sale
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs

class SellFragment : Fragment() {

    private var _binding: FragmentSellBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: FirebaseRepo
    private lateinit var adapter: SellProductAdapter
    private var productsListener: ListenerRegistration? = null
    private var salesListener: ListenerRegistration? = null

    private var latestProducts: List<Product> = emptyList()
    private var salesCountByProductId: Map<String, Long> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSellBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shopCode = ShopPrefs.getShopCode(requireContext()) ?: return
        repo = FirebaseRepo(shopCode)

        adapter = SellProductAdapter(emptyList()) { showSellDialog(it) }
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter

        productsListener = repo.listenProducts { products ->
            latestProducts = products
            refreshList()
        }
        salesListener = repo.listenSales { sales ->
            salesCountByProductId = sales.groupBy { it.productId }.mapValues { (_, list) -> list.sumOf { it.quantity } }
            refreshList()
        }
    }

    /** Les produits les plus vendus apparaissent en premier ; les jamais vendus restent en bas. */
    private fun refreshList() {
        val inStock = latestProducts.filter { it.quantity > 0 }
        val sorted = inStock.sortedWith(
            compareByDescending<Product> { salesCountByProductId[it.id] ?: 0L }
                .thenBy { it.name.lowercase() }
        )
        adapter.updateData(sorted)
        binding.tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSellDialog(product: Product) {
        val dialogBinding = DialogSellBinding.inflate(layoutInflater)
        dialogBinding.tvAvailableStock.text = "En stock : ${product.quantity} unité(s)"
        dialogBinding.etQuantitySold.setText("1")
        dialogBinding.btnMinus.setOnClickListener {
            val current = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 1L
            if (current > 1) dialogBinding.etQuantitySold.setText((current - 1).toString())
        }
        dialogBinding.btnPlus.setOnClickListener {
            val current = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
            if (current < product.quantity) dialogBinding.etQuantitySold.setText((current + 1).toString())
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Vendre : ${product.name}")
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.sell) { _, _ ->
                val qtySold = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
                val paymentMethod = when (dialogBinding.rgPayment.checkedRadioButtonId) {
                    dialogBinding.rbOrangeMoney.id -> "Orange Money"
                    dialogBinding.rbWave.id -> "Wave"
                    else -> "Espèces"
                }
                if (qtySold in 1..product.quantity) {
                    repo.recordSale(
                        product, qtySold, paymentMethod,
                        onError = { msg ->
                            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                        },
                        onSuccess = {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "✅ Vente réussie : ${product.name} x$qtySold",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        productsListener?.remove()
        salesListener?.remove()
        _binding = null
    }
}
