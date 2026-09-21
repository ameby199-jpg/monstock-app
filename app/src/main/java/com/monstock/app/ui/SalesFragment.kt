package com.monstock.app.ui

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.adapter.SaleAdapter
import com.monstock.app.adapter.SaleListItem
import com.monstock.app.databinding.FragmentSalesBinding
import com.monstock.app.model.Sale
import com.monstock.app.util.BackgroundPrefs
import com.monstock.app.util.CurrencyFormatter
import com.monstock.app.util.DayPrefs
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

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                BackgroundPrefs.saveCustomBackground(requireContext(), "sales", bitmap)
                binding.ivBackground.setImageBitmap(bitmap)
            } catch (e: Exception) {
                // Ignoré : l'utilisateur peut réessayer
            }
        }
    }

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

        BackgroundPrefs.applyBackground(requireContext(), "sales", binding.ivBackground)
        binding.btnChangeBackground.setOnClickListener { pickBackground.launch("image/*") }

        adapter = SaleAdapter(emptyList())
        binding.recyclerViewSales.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSales.adapter = adapter

        listener = repo.listenSales { sales ->
            adapter.updateData(buildGroupedList(sales))
            binding.tvEmptySales.visibility = if (sales.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /**
     * Regroupe les ventes (déjà triées du plus récent au plus ancien) par "journée métier" :
     * l'intervalle entre deux appuis sur "Terminer la journée" (Rapports), pas le jour calendaire.
     * Les ventes plus anciennes que la toute première frontière enregistrée (avant que cette
     * fonctionnalité existe) retombent sur un regroupement par jour calendaire, en dernier recours.
     */
    private fun buildGroupedList(sales: List<Sale>): List<SaleListItem> {
        val boundaries = DayPrefs.getBoundaries(requireContext()) // du plus ancien au plus récent
        val currentDayStart = DayPrefs.getDayStart(requireContext())
        val dayFmt = SimpleDateFormat("EEEE dd/MM/yyyy", Locale.FRENCH)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.FRENCH)
        val result = mutableListOf<SaleListItem>()

        var currentBucketKey: String? = null
        var colorIndex = -1

        for (sale in sales) {
            val bucketKey = bucketKeyFor(sale.timestamp, boundaries, currentDayStart)
            if (bucketKey != currentBucketKey) {
                currentBucketKey = bucketKey
                colorIndex++
                val bucketSales = sales.filter { bucketKeyFor(it.timestamp, boundaries, currentDayStart) == bucketKey }
                val total = bucketSales.sumOf { it.total }
                val label = when {
                    bucketKey == "current" ->
                        "Aujourd'hui — Total: ${CurrencyFormatter.format(total)}"
                    bucketKey.startsWith("closed:") -> {
                        val endMillis = bucketKey.removePrefix("closed:").toLong()
                        "Journée terminée à ${timeFmt.format(Date(endMillis))} le ${dayFmt.format(Date(endMillis)).replaceFirstChar { it.uppercase() }} — Total: ${CurrencyFormatter.format(total)}"
                    }
                    else -> {
                        // Ventes antérieures à la 1ère frontière enregistrée : repli sur le jour calendaire.
                        val label0 = dayFmt.format(Date(sale.timestamp)).replaceFirstChar { it.uppercase() }
                        "$label0 — Total: ${CurrencyFormatter.format(total)}"
                    }
                }
                result.add(SaleListItem.Header(label, colorIndex))
            }
            result.add(SaleListItem.Row(sale))
        }
        return result
    }

    /**
     * Détermine à quelle "journée métier" appartient une vente :
     * - "current" : après la dernière frontière (journée en cours, pas encore terminée)
     * - "closed:<timestamp>" : entre deux frontières, <timestamp> étant celle qui l'a clôturée
     * - "legacy:<jour calendaire>" : antérieure à toute frontière connue (avant cette fonctionnalité)
     */
    private fun bucketKeyFor(timestamp: Long, boundaries: List<Long>, currentDayStart: Long): String {
        if (timestamp >= currentDayStart) return "current"
        // Cherche la plus petite frontière strictement supérieure au timestamp : c'est elle qui a clôturé cette vente.
        val closingBoundary = boundaries.firstOrNull { it > timestamp }
        return if (closingBoundary != null) "closed:$closingBoundary" else "legacy:${timestamp / 86_400_000}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        _binding = null
    }
}
