package com.monstock.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
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
        val timeFmt = SimpleDateFormat("HH:mm", Locale.FRENCH)
        holder.binding.tvOrderProduct.text = order.productName
        holder.binding.tvOrderDetails.text =
            "x${order.quantity}  •  ${CurrencyFormatter.format(order.quantity * order.unitPrice)}  •  ${timeFmt.format(Date(order.timestamp))}"
        holder.binding.btnCancelOrder.setOnClickListener { onCancel(order) }
        holder.binding.btnTakeOrder.setOnClickListener { onTake(order) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Order>) {
        items = newItems
        notifyDataSetChanged()
    }
}
