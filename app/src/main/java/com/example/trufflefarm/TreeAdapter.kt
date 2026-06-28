package com.example.trufflefarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TreeAdapter(
    private val items: List<TreeItem>,
    private val onItemClick: (TreeItem) -> Unit
) : RecyclerView.Adapter<TreeAdapter.ViewHolder>() {

    data class TreeItem(
        val name: String,
        val type: String,
        val notes: String,
        val lat: Double,
        val lng: Double,
        val date: String,
        val photoPath: String,
        val level: Int
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(android.R.id.text1)
        val text2: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val indent = "      ".repeat(item.level)
        val typeIcon = when(item.type) {
            "FARM" -> "🚜"
            "SOIL" -> "🟤"
            "PLANTS" -> "🌳"
            "NOTE" -> "📍"
            else -> "•"
        }
        val photoIcon = if (item.photoPath.isNotEmpty()) "📷 " else ""
        
        holder.text1.text = "${indent}${typeIcon} ${photoIcon}${item.name}"
        holder.text1.setTypeface(null, if (item.level == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        
        val details = mutableListOf<String>()
        if (item.notes.isNotEmpty()) details.add(item.notes)
        if (item.date.isNotEmpty()) details.add(item.date)
        
        if (details.isNotEmpty()) {
            holder.text2.visibility = View.VISIBLE
            holder.text2.text = "${indent}      ${details.joinToString(" | ")}"
        } else {
            holder.text2.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
