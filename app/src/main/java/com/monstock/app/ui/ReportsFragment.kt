package com.monstock.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.databinding.FragmentReportsBinding
import com.monstock.app.databinding.ItemSaleBinding
import com.monstock.app.model.Sale
import com.monstock.app.util.CurrencyFormatter
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs
import java.util.Calendar

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private var listener: ListenerRegistration? = null

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

        binding.recyclerViewTop.layoutManager = LinearLayoutManager(requireContext())

        listener = repo.listenSales { sales -> updateStats(sales) }
    }

    private fun updateStats(sales: List<Sale>) {
        val now = Calendar.getInstance()

        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis

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

        binding.tvToday.text = CurrencyFormatter.format(salesToday.sumOf { it.total })
        binding.tvWeek.text = CurrencyFormatter.format(salesWeek.sumOf { it.total })
        binding.tvMonth.text = CurrencyFormatter.format(salesMonth.sumOf { it.total })

        binding.tvTodayBreakdown.text = paymentBreakdown(salesToday)
        binding.tvWeekBreakdown.text = paymentBreakdown(salesWeek)
        binding.tvMonthBreakdown.text = paymentBreakdown(salesMonth)

        val topByProduct = sales.groupBy { it.productName }
            .map { (name, list) -> Triple(name, list.sumOf { it.quantity }, list.sumOf { it.total }) }
            .sortedByDescending { it.third }
            .take(10)

        binding.recyclerViewTop.adapter = TopProductAdapter(topByProduct)
    }

    private fun paymentBreakdown(sales: List<Sale>): String {
        if (sales.isEmpty()) return "Aucune vente"
        val byMethod = sales.groupBy { it.paymentMethod }
        return listOf("Espèces", "Orange Money", "Wave").mapNotNull { method ->
            val total = byMethod[method]?.sumOf { it.total } ?: return@mapNotNull null
            "$method: ${CurrencyFormatter.format(total)}"
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
