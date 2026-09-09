package com.monstock.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.databinding.ItemIngredientBinding
import com.monstock.app.model.Ingredient
import com.monstock.app.util.CurrencyFormatter

class IngredientAdapter(
    private var items: List<Ingredient>,
    private val onEdit: (Ingredient) -> Unit,
    private val onDelete: (Ingredient) -> Unit
) : RecyclerView.Adapter<IngredientAdapter.VH>() {

    inner class VH(val binding: ItemIngredientBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ing = items[position]
        holder.binding.tvName.text = ing.name
        holder.binding.tvPrice.text = CurrencyFormatter.format(ing.price)
        holder.binding.tvStockActuel.text = CurrencyFormatter.format(ing.stockActuel)
        holder.binding.tvStockPresent.text = CurrencyFormatter.format(ing.stockPresent)
        holder.binding.tvAchat.text = CurrencyFormatter.format(ing.achatDuJour)
        holder.binding.tvDepense.text = CurrencyFormatter.format(ing.depenseDuJour)

        holder.binding.btnEdit.setOnClickListener { onEdit(ing) }
        holder.binding.btnDelete.setOnClickListener { onDelete(ing) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Ingredient>) {
        items = newItems
        notifyDataSetChanged()
    }
}
