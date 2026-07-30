package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import com.ocr.fastscan.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.data.model.BoxInPartial
import com.ocr.fastscan.databinding.ItemOpenBoxBinding

class OpenBoxesAdapter(
    private val onClick: (BoxInPartial) -> Unit
) : ListAdapter<BoxInPartial, OpenBoxesAdapter.ViewHolder>(BoxDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOpenBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
//диалог добавления коробки
    inner class ViewHolder(private val binding: ItemOpenBoxBinding) : RecyclerView.ViewHolder(binding.root) {

        private val context = binding.root.context
        fun bind(box: BoxInPartial) {
            binding.tvTrackingCode.text = context.getString(
                R.string.track_code, box.tracking_code)
            binding.tvDimensions.text = context.getString(
                R.string.sizes, box.width, box.height, box.length)
            binding.tvGrossWeight.text = context.getString(R.string.weight_format, box.gross_weight)
            binding.tvVWeight.text = context.getString(R.string.volume_weight, box.v_weight)
            //binding.tvOrderCount.text = "Заказов: ${box.orderCount}"

            binding.root.setOnClickListener {
                onClick(box)
            }
        }
    }
}

// Тот же DiffCallback, что и для других адаптеров
class BoxDiffCallback : DiffUtil.ItemCallback<BoxInPartial>() {
    override fun areItemsTheSame(oldItem: BoxInPartial, newItem: BoxInPartial) = oldItem.guid == newItem.guid
    override fun areContentsTheSame(oldItem: BoxInPartial, newItem: BoxInPartial) = oldItem == newItem
}