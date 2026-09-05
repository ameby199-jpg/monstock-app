package com.monstock.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.databinding.ItemIngredientBinding
import com.monstock.app.model.Ingredient

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
        val ingredient = items[position]
        holder.binding.tvName.text = ingredient.name
        val qtyText = if (ingredient.quantity == ingredient.quantity.toLong().toDouble()) {
            ingredient.quantity.toLong().toString()
        } else {
            ingredient.quantity.toString()
        }
        holder.binding.tvDetails.text = "$qtyText ${ingredient.unit}"
        holder.binding.btnEdit.setOnClickListener { onEdit(ingredient) }
        holder.binding.btnDelete.setOnClickListener { onDelete(ingredient) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Ingredient>) {
        items = newItems
        notifyDataSetChanged()
    }
}
