package com.monstock.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.R
import com.monstock.app.databinding.ItemSaleBinding
import com.monstock.app.databinding.ItemSaleHeaderBinding
import com.monstock.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TYPE_HEADER = 0
private const val TYPE_ROW = 1

class SaleAdapter(private var items: List<SaleListItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class HeaderVH(val binding: ItemSaleHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    inner class RowVH(val binding: ItemSaleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) = when (items[position]) {
        is SaleListItem.Header -> TYPE_HEADER
        is SaleListItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemSaleHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            RowVH(ItemSaleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SaleListItem.Header -> {
                val h = holder as HeaderVH
                val colorRes = DAY_COLOR_PALETTE[item.colorIndex % DAY_COLOR_PALETTE.size]
                val color = ContextCompat.getColor(h.itemView.context, colorRes)
                h.binding.tvDateLabel.text = item.label
                h.binding.root.setCardBackgroundColor(color)
            }
            is SaleListItem.Row -> {
                val r = holder as RowVH
                val sale = item.sale
                val dateFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
                val context = r.itemView.context

                r.binding.tvSaleName.text = "${sale.productName} x${sale.quantity}"
                r.binding.tvSaleDetails.text =
                    "${CurrencyFormatter.format(sale.total)}  •  ${dateFmt.format(Date(sale.timestamp))}"
                r.binding.tvSalePayment.text = sale.paymentMethod

                val colorRes = when (sale.paymentMethod) {
                    "Orange Money" -> R.color.orange_money
                    "Wave" -> R.color.wave
                    else -> R.color.cash
                }
                val color = ContextCompat.getColor(context, colorRes)
                r.binding.tvSalePayment.setTextColor(color)
                r.binding.viewPaymentDot.background.setTint(color)
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<SaleListItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
