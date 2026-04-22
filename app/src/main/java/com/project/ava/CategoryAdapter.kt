package com.project.ava

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.ava.data.Category
import com.project.ava.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val categories: List<Category>,
    private val onEdit: (Category) -> Unit,
    private val onDelete: (Category) -> Unit,
    private val onSelect: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.binding.categoryTitle.text = category.title
        holder.binding.categoryQrCode.text = "QR: ${category.qrCode}"
        holder.binding.root.setOnClickListener { onSelect(category) }
        holder.binding.btnEdit.setOnClickListener { onEdit(category) }
        holder.binding.btnDelete.setOnClickListener { onDelete(category) }
    }

    override fun getItemCount() = categories.size
}
