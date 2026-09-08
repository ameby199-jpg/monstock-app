package com.monstock.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.adapter.SaleAdapter
import com.monstock.app.adapter.SaleListItem
import com.monstock.app.databinding.FragmentSalesBinding
import com.monstock.app.model.Sale
import com.monstock.app.util.CurrencyFormatter
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalesFragment : Fragment() {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SaleAdapter
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shopCode = ShopPrefs.getShopCode(requireContext()) ?: return
        val repo = FirebaseRepo(shopCode)

        adapter = SaleAdapter(emptyList())
        binding.recyclerViewSales.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSales.adapter = adapter

        listener = repo.listenSales { sales ->
            adapter.updateData(buildGroupedList(sales))
            binding.tvEmptySales.visibility = if (sales.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Regroupe les ventes (déjà triées du plus récent au plus ancien) par journée, avec un total par jour. */
    private fun buildGroupedList(sales: List<Sale>): List<SaleListItem> {
        val dayFmt = SimpleDateFormat("EEEE dd/MM/yyyy", Locale.FRENCH)
        val result = mutableListOf<SaleListItem>()
        var currentDayKey: String? = null
        var colorIndex = -1

        for (sale in sales) {
            val dayKey = dayFmt.format(Date(sale.timestamp))
            if (dayKey != currentDayKey) {
                currentDayKey = dayKey
                colorIndex++
                val dayTotal = sales.filter { dayFmt.format(Date(it.timestamp)) == dayKey }.sumOf { it.total }
                val label = "${dayKey.replaceFirstChar { it.uppercase() }} — Total: ${CurrencyFormatter.format(dayTotal)}"
                result.add(SaleListItem.Header(label, colorIndex))
            }
            result.add(SaleListItem.Row(sale))
        }
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        _binding = null
    }
}
