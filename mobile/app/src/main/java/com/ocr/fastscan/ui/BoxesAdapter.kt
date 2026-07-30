package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.BoxItem
import com.ocr.fastscan.databinding.ItemBoxBinding

class BoxesAdapter(
    private val onClick: (BoxItem) -> Unit,
    private val onLongClick: (BoxItem) -> Unit
) : ListAdapter<BoxItem, BoxesAdapter.ViewHolder>(DiffCallback) {

    private var highlightedTrackingCode: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val box = getItem(position)
        holder.bind(box, box.trackingCode == highlightedTrackingCode, onClick, onLongClick)
    }

    fun highlightBox(trackingCode: String) {
        val previousCode = highlightedTrackingCode
        highlightedTrackingCode = trackingCode
        notifyHighlightChanged(previousCode)
        notifyHighlightChanged(trackingCode)
    }

    fun clearHighlight(trackingCode: String) {
        if (highlightedTrackingCode != trackingCode) return
        highlightedTrackingCode = null
        notifyHighlightChanged(trackingCode)
    }

    private fun notifyHighlightChanged(trackingCode: String?) {
        val position = currentList.indexOfFirst { it.trackingCode == trackingCode }
        if (position >= 0) notifyItemChanged(position)
    }

    class ViewHolder(private val binding: ItemBoxBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            box: BoxItem,
            isHighlighted: Boolean,
            onClick: (BoxItem) -> Unit,
            onLongClick: (BoxItem) -> Unit
        ) {
            binding.tvTrackingCode.text = box.trackingCode
            binding.tvGrossWeight.text = getString(R.string.weight_format, box.grossWeight ?: 0.0)
            binding.tvVWeight.text = getString(R.string.volume_weight, box.vWeight ?: 0.0)
            binding.tvSizes.text = getString(R.string.sizes, box.length ?: 0, box.width ?: 0, box.height ?: 0)
            binding.tvOrderCount.text = getString(R.string.orders_count, box.orderCount ?: 0)
            binding.tvDate.text = box.dateCreated

            val context = binding.root.context
            val backgroundColor = if (isHighlighted) {
                ContextCompat.getColor(context, R.color.box_card_highlight)
            } else {
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
            }
            binding.root.setCardBackgroundColor(backgroundColor)
            binding.root.cardElevation = dp(if (isHighlighted) 10f else 3f)
            binding.root.strokeColor = ContextCompat.getColor(
                context,
                if (isHighlighted) R.color.card_stroke_highlight else R.color.card_stroke
            )
            binding.root.strokeWidth = dp(if (isHighlighted) 2f else 1f).toInt()

            binding.root.setOnClickListener { onClick(box) }
            binding.root.setOnLongClickListener {
                onLongClick(box)
                true
            }
        }

        private fun getString(resId: Int, vararg formatArgs: Any): String {
            return itemView.context.getString(resId, *formatArgs)
        }

        private fun dp(value: Float): Float {
            return value * itemView.resources.displayMetrics.density
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<BoxItem>() {
        override fun areItemsTheSame(oldItem: BoxItem, newItem: BoxItem): Boolean =
            oldItem.guid == newItem.guid

        override fun areContentsTheSame(oldItem: BoxItem, newItem: BoxItem): Boolean =
            oldItem == newItem
    }
}
