package com.monstock.app.adapter

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.R
import com.monstock.app.databinding.ItemProductBinding
import com.monstock.app.model.Product
import com.monstock.app.util.CurrencyFormatter

class ProductAdapter(
    private var items: List<Product>,
    private val onSell: (Product) -> Unit,
    private val onDelete: (Product) -> Unit,
    private val onPhoto: ((Product) -> Unit)? = null
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    inner class VH(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val product = items[position]
        holder.binding.tvName.text = product.name
        holder.binding.tvDetails.text = "Qté: ${product.quantity}  •  ${CurrencyFormatter.format(product.price)}"

        val bitmap = decodePhoto(product.photoBase64)
        if (bitmap != null) {
            holder.binding.ivPhoto.setImageBitmap(bitmap)
        } else {
            holder.binding.ivPhoto.setImageResource(R.drawable.ic_product_placeholder)
        }

        holder.binding.btnSell.setOnClickListener { onSell(product) }
        holder.binding.btnDelete.setOnClickListener { onDelete(product) }
        if (onPhoto != null) {
            holder.binding.ivPhoto.setOnClickListener { onPhoto.invoke(product) }
        }
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
