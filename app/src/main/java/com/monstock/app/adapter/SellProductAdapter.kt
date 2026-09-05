package com.monstock.app.adapter

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.R
import com.monstock.app.databinding.ItemSellProductBinding
import com.monstock.app.model.Product
import java.text.NumberFormat
import java.util.Locale

class SellProductAdapter(
    private var items: List<Product>,
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<SellProductAdapter.VH>() {

    inner class VH(val binding: ItemSellProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSellProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val product = items[position]
        val format = NumberFormat.getCurrencyInstance(Locale.FRANCE)
        holder.binding.tvName.text = product.name
        holder.binding.tvDetails.text = "Qté: ${product.quantity}  •  ${format.format(product.price)}"

        val bitmap = decodePhoto(product.photoBase64)
        if (bitmap != null) {
            holder.binding.ivPhoto.setImageBitmap(bitmap)
        } else {
            holder.binding.ivPhoto.setImageResource(R.drawable.ic_product_placeholder)
        }

        holder.binding.root.setOnClickListener { onClick(product) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Product>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun decodePhoto(base64: String) = try {
        if (base64.isEmpty()) null
        else {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    } catch (e: Exception) {
        null
    }
}
