package com.monstock.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.adapter.SaleAdapter
import com.monstock.app.databinding.FragmentSalesBinding
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs

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
            adapter.updateData(sales)
            binding.tvEmptySales.visibility = if (sales.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        _binding = null
    }
}
