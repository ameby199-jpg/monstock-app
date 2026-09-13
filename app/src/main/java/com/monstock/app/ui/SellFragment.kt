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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.R
import com.monstock.app.adapter.OrderAdapter
import com.monstock.app.adapter.SellProductAdapter
import com.monstock.app.databinding.DialogOrdersBinding
import com.monstock.app.databinding.DialogSellBinding
import com.monstock.app.databinding.DialogThemePickerBinding
import com.monstock.app.databinding.FragmentSellBinding
import com.monstock.app.model.Order
import com.monstock.app.model.Product
import com.monstock.app.model.Sale
import com.monstock.app.util.BackgroundPrefs
import com.monstock.app.util.CurrencyFormatter
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs
import com.monstock.app.util.ThemePrefs

class SellFragment : Fragment() {

    private var _binding: FragmentSellBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: FirebaseRepo
    private lateinit var adapter: SellProductAdapter
    private var productsListener: ListenerRegistration? = null
    private var salesListener: ListenerRegistration? = null
    private var ordersListener: ListenerRegistration? = null

    private var latestProducts: List<Product> = emptyList()
    private var salesCountByProductId: Map<String, Long> = emptyMap()
    private var latestOrders: List<Order> = emptyList()

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                BackgroundPrefs.saveCustomBackground(requireContext(), "home", bitmap)
                binding.ivBackground.setImageBitmap(bitmap)
            } catch (e: Exception) {
                // Ignoré : l'utilisateur peut réessayer
            }
        }
    }

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

        BackgroundPrefs.applyBackground(requireContext(), "home", binding.ivBackground)
        binding.btnChangeBackground.setOnClickListener { pickBackground.launch("image/*") }
        binding.btnTheme.setOnClickListener { showThemePickerDialog() }

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

        binding.btnOrders.setOnClickListener { showOrdersDialog() }
        ordersListener = repo.listenOrders { orders ->
            latestOrders = orders
            if (orders.isEmpty()) {
                binding.tvOrdersBadge.visibility = View.GONE
            } else {
                binding.tvOrdersBadge.visibility = View.VISIBLE
                binding.tvOrdersBadge.text = orders.size.toString()
            }
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
        dialogBinding.tvUnitPrice.text = "Prix : ${CurrencyFormatter.format(product.price)}"
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
            .setNeutralButton("Commande") { _, _ ->
                val qtyOrdered = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
                if (qtyOrdered in 1..product.quantity) {
                    repo.addOrder(
                        product, qtyOrdered,
                        onError = { msg ->
                            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                        },
                        onSuccess = {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "🕑 Commande enregistrée : ${product.name} x$qtyOrdered",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showOrdersDialog() {
        val dialogBinding = DialogOrdersBinding.inflate(layoutInflater)
        val ordersAdapter = OrderAdapter(
            latestOrders,
            onCancel = { order ->
                repo.cancelOrder(order.id) { msg ->
                    android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                }
            },
            onTake = { order ->
                repo.takeOrder(
                    order,
                    onError = { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    },
                    onSuccess = {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "✅ Commande vendue : ${order.productName} x${order.quantity}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        )
        dialogBinding.recyclerViewOrders.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.recyclerViewOrders.adapter = ordersAdapter
        dialogBinding.tvOrdersEmpty.visibility = if (latestOrders.isEmpty()) View.VISIBLE else View.GONE

        AlertDialog.Builder(requireContext())
            .setTitle("Commandes en attente")
            .setView(dialogBinding.root)
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun showThemePickerDialog() {
        val dialogBinding = DialogThemePickerBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Couleur de l'application")
            .setView(dialogBinding.root)
            .setNegativeButton("Fermer", null)
            .create()

        val swatches = listOf(
            dialogBinding.swatchBlue to ThemePrefs.AppTheme.BLUE,
            dialogBinding.swatchGreen to ThemePrefs.AppTheme.GREEN,
            dialogBinding.swatchOrange to ThemePrefs.AppTheme.ORANGE,
            dialogBinding.swatchPurple to ThemePrefs.AppTheme.PURPLE,
            dialogBinding.swatchRed to ThemePrefs.AppTheme.RED,
            dialogBinding.swatchTeal to ThemePrefs.AppTheme.TEAL
        )
        swatches.forEach { (view, theme) ->
            view.setOnClickListener {
                ThemePrefs.setSelectedTheme(requireContext(), theme)
                dialog.dismiss()
                requireActivity().recreate()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        productsListener?.remove()
        salesListener?.remove()
        ordersListener?.remove()
        _binding = null
    }
}
