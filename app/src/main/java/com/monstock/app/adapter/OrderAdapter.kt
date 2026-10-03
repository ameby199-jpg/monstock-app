package com.monstock.app.adapter

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.R
import com.monstock.app.databinding.ItemOrderBinding
import com.monstock.app.model.Order
import com.monstock.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Abréviation affichée sous l'emoji du profil client, pour que ce soit lisible même en petit. */
private fun profileAbbreviation(profile: String): String = when (profile) {
    "👨🏿" -> "HM"
    "👩🏿" -> "FM"
    "👦🏿" -> "GR"
    "👧🏿" -> "FIL"
    else -> ""
}

/** Icône courte pour le mode de paiement, à côté de son nom. */
private fun paymentIcon(method: String): String = when (method) {
    "Orange Money" -> "🟠"
    "Wave" -> "🔵"
    else -> "💵"
}

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

        // Chaque produit de la commande : badge profil bien visible, nom, prix, et mode de paiement.
        val density = context.resources.displayMetrics.density
        holder.binding.llOrderItems.removeAllViews()
        order.items.forEach { line ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (6 * density).toInt() }
            }

            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val nameRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            if (line.customerProfile.isNotBlank()) {
                // Badge profil : emoji en grand sur fond circulaire, avec son abréviation dessous.
                val badgeColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (10 * density).toInt() }
                }
                val emojiView = TextView(context).apply {
                    text = line.customerProfile
                    textSize = 26f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
                    background = ContextCompat.getDrawable(context, R.drawable.bg_profile_badge)
                }
                val abbrevView = TextView(context).apply {
                    text = profileAbbreviation(line.customerProfile)
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#2196F3"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                badgeColumn.addView(emojiView)
                badgeColumn.addView(abbrevView)
                row.addView(badgeColumn)
            }

            val nameView = TextView(context).apply {
                text = "${line.productName} x${line.quantity}"
                textSize = 16f
            }
            val priceView = TextView(context).apply {
                text = CurrencyFormatter.format(line.unitPrice * line.quantity)
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.END
            }
            nameRow.addView(nameView)
            nameRow.addView(priceView)

            val paymentView = TextView(context).apply {
                text = "${paymentIcon(line.paymentMethod)} ${line.paymentMethod}"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
            }

            textColumn.addView(nameRow)
            textColumn.addView(paymentView)
            row.addView(textColumn)
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
