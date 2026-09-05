package com.monstock.app.adapter

import android.graphics.Color
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.R
import com.monstock.app.databinding.ItemSaleBinding
import com.monstock.app.model.Sale
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaleAdapter(private var items: List<Sale>) : RecyclerView.Adapter<SaleAdapter.VH>() {

    inner class VH(val binding: ItemSaleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSaleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sale = items[position]
        val format = NumberFormat.getCurrencyInstance(Locale.FRANCE)
        val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val context = holder.itemView.context

        holder.binding.tvSaleName.text = "${sale.productName} x${sale.quantity}"
        holder.binding.tvSaleDetails.text =
            "${format.format(sale.total)}  •  ${dateFmt.format(Date(sale.timestamp))}"
        holder.binding.tvSalePayment.text = sale.paymentMethod

        val colorRes = when (sale.paymentMethod) {
            "Orange Money" -> R.color.orange_money
            "Wave" -> R.color.wave
            else -> R.color.cash
        }
        val color = ContextCompat.getColor(context, colorRes)
        holder.binding.tvSalePayment.setTextColor(color)
        holder.binding.viewPaymentDot.background.setTint(color)
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Sale>) {
        items = newItems
        notifyDataSetChanged()
    }
}
