package com.monstock.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.databinding.ItemOrderBinding
import com.monstock.app.model.Order
import com.monstock.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderAdapter(
    private var items: List<Order>,
    private val onCancel: (Order) -> Unit,
    private val onTake: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.VH>() {

    inner class VH(val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val order = items[position]
        val context = holder.binding.root.context
        val timeFmt = SimpleDateFormat("HH:mm", Locale.FRENCH)

        holder.binding.tvOrderTime.text = "Commandé à ${timeFmt.format(Date(order.timestamp))}"

        // Chaque produit de la commande : nom et prix côte à côte sur une ligne.
        holder.binding.llOrderItems.removeAllViews()
        order.items.forEach { line ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val nameView = TextView(context).apply {
                text = "${line.productName} x${line.quantity}"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val priceView = TextView(context).apply {
                text = CurrencyFormatter.format(line.unitPrice * line.quantity)
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
            }
            row.addView(nameView)
            row.addView(priceView)
            holder.binding.llOrderItems.addView(row)
        }

        val total = order.items.sumOf { it.unitPrice * it.quantity }
        holder.binding.tvOrderTotal.text = "Total : ${CurrencyFormatter.format(total)}"

        holder.binding.btnCancelOrder.setOnClickListener { onCancel(order) }
        holder.binding.btnTakeOrder.setOnClickListener { onTake(order) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Order>) {
        items = newItems
        notifyDataSetChanged()
    }
}
