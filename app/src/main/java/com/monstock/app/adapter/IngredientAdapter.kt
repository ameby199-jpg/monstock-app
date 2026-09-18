package com.monstock.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.monstock.app.databinding.ItemIngredientBinding
import com.monstock.app.model.Ingredient
import com.monstock.app.util.CurrencyFormatter
import kotlin.math.abs

class IngredientAdapter(
    private var items: List<Ingredient>,
    private val onEdit: (Ingredient) -> Unit,
    private val onDelete: (Ingredient) -> Unit,
    private val onEditStockActuel: (Ingredient) -> Unit,
    private val onEditAchat: (Ingredient) -> Unit,
    private val onEditNouveauStock: (Ingredient) -> Unit
) : RecyclerView.Adapter<IngredientAdapter.VH>() {

    inner class VH(val binding: ItemIngredientBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ing = items[position]
        holder.binding.tvName.text = ing.name
        holder.binding.tvStockActuel.text = CurrencyFormatter.format(ing.stockActuel)
        holder.binding.tvAchat.text = CurrencyFormatter.format(ing.achatDuJour)
        holder.binding.tvNouveauStock.text = CurrencyFormatter.format(ing.nouveauStock)

        // Chiffres = Nouveau stock - Stock actuel, calculé automatiquement comme dans Excel.
        val diff = ing.chiffreValue
        if (diff >= 0) {
            holder.binding.tvChiffres.text = "BNF ${CurrencyFormatter.format(diff)}"
            holder.binding.tvChiffres.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            holder.binding.tvChiffres.text = "− ${CurrencyFormatter.format(abs(diff))}"
            holder.binding.tvChiffres.setTextColor(Color.parseColor("#C62828"))
        }

        // Un simple appui sur chacune de ces 3 colonnes ouvre son édition rapide.
        holder.binding.tvStockActuel.setOnClickListener { onEditStockActuel(ing) }
        holder.binding.tvAchat.setOnClickListener { onEditAchat(ing) }
        holder.binding.tvNouveauStock.setOnClickListener { onEditNouveauStock(ing) }

        holder.binding.btnEdit.setOnClickListener { onEdit(ing) }
        holder.binding.btnDelete.setOnClickListener { onDelete(ing) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Ingredient>) {
        items = newItems
        notifyDataSetChanged()
    }
}
