package com.ocr.fastscan.ui  // или твой пакет

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.data.model.BoxInPartial
import com.ocr.fastscan.databinding.ItemBoxInPartialBinding

class BoxesInPartialAdapter(
    private val onLongClick: (BoxInPartial) -> Unit
) : ListAdapter<BoxInPartial, BoxesInPartialAdapter.ViewHolder>(BoxDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBoxInPartialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val box = getItem(position)
        holder.bind(box)
        Log.d("onBindViewHolder", "получено: $box")

    }

    inner class ViewHolder(private val binding: ItemBoxInPartialBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(box: BoxInPartial) {
            binding.tvTrackingCode.text = "Коробка: ${box.tracking_code}"
            binding.tvDimensions.text = "Размеры: ${box.width} × ${box.height} × ${box.length} см"
            binding.tvGrossWeight.text = "Вес: ${box.gross_weight} кг"
            binding.tvVWeight.text = "Объёмный вес: ${box.v_weight} кг"

            binding.root.setOnLongClickListener {
                onLongClick(box)
                true
            }
        }
    }
}

//class BoxDiffCallback : DiffUtil.ItemCallback<BoxInPartial>() {
//    override fun areItemsTheSame(oldItem: BoxInPartial, newItem: BoxInPartial): Boolean {
//        return oldItem.guid == newItem.guid
//    }
//
//    override fun areContentsTheSame(oldItem: BoxInPartial, newItem: BoxInPartial): Boolean {
//        return oldItem == newItem
//    }
//}