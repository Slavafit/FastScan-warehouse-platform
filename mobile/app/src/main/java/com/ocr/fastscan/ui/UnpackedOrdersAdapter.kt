package com.ocr.fastscan.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.UnpackedOrder
import com.ocr.fastscan.databinding.ItemUnpackedOrderBinding
import java.text.SimpleDateFormat
import java.util.Locale

class UnpackedOrdersAdapter : ListAdapter<UnpackedOrder, UnpackedOrdersAdapter.ViewHolder>(UnpackedDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUnpackedOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemUnpackedOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context

        fun bind(order: UnpackedOrder) {
            binding.tvTracking.text = order.trackingNumber
            binding.tvClient.text = order.clientName
            binding.tvClientCode.text = order.clientCode
            binding.tvWeight.text = context.getString(R.string.weight_format, order.grossWeight)
            binding.tvDate.text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(order.registeredAt.toDate())

            binding.root.setOnLongClickListener {
                copyToClipboard(order.trackingNumber)
                true
            }
        }

        private fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("tracking_code", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.track_copied, text), Toast.LENGTH_SHORT).show()
        }
    }
}

class UnpackedDiffCallback : DiffUtil.ItemCallback<UnpackedOrder>() {
    override fun areItemsTheSame(oldItem: UnpackedOrder, newItem: UnpackedOrder): Boolean =
        oldItem.trackingNumber == newItem.trackingNumber

    override fun areContentsTheSame(oldItem: UnpackedOrder, newItem: UnpackedOrder): Boolean =
        oldItem == newItem
}
