package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.OrderDetails
import com.ocr.fastscan.databinding.ItemOrderInBoxBinding

class OrdersAdapter(
    private val onDelete: (OrderDetails) -> Unit
) : ListAdapter<OrderDetails, OrdersAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderInBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDelete)
    }

    class ViewHolder(private val binding: ItemOrderInBoxBinding) : RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context

        fun bind(order: OrderDetails, onDelete: (OrderDetails) -> Unit) {
            binding.tvTrackingNumber.text = order.trackingNumber ?: "..."
            binding.tvWeight.text = context.getString(R.string.weight_format, order.grossWeight ?: 0.0)
            binding.btnDelete.setOnClickListener { onDelete(order) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<OrderDetails>() {
        override fun areItemsTheSame(oldItem: OrderDetails, newItem: OrderDetails): Boolean =
            oldItem.guid == newItem.guid

        override fun areContentsTheSame(oldItem: OrderDetails, newItem: OrderDetails): Boolean =
            oldItem == newItem
    }
}
