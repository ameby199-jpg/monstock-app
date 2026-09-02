package com.monstock.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.databinding.ItemProductBinding
import com.monstock.app.model.Product
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private var items: List<Product>,
    private val onSell: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    inner class VH(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val product = items[position]
        val format = NumberFormat.getCurrencyInstance(Locale.FRANCE)
        holder.binding.tvName.text = product.name
        holder.binding.tvDetails.text = "Qté: ${product.quantity}  •  ${format.format(product.price)}"
        holder.binding.btnSell.setOnClickListener { onSell(product) }
        holder.binding.btnDelete.setOnClickListener { onDelete(product) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Product>) {
        items = newItems
        notifyDataSetChanged()
    }
}
