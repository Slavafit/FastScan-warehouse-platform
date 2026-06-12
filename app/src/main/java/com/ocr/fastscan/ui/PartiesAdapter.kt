package com.ocr.fastscan.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.Partial
import com.ocr.fastscan.databinding.ItemPartyBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PartiesAdapter(
    private val onClick: (Partial) -> Unit
) : ListAdapter<Partial, PartiesAdapter.PartyViewHolder>(PartyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartyViewHolder {
        val binding = ItemPartyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PartyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartyViewHolder, position: Int) {
        val party = getItem(position)
        holder.bind(party)
    }

    inner class PartyViewHolder(private val binding: ItemPartyBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(party: Partial) {
            val context = binding.root.context

            binding.tvPartialCode.text = context.getString(R.string.track_code, party.partial_code)
            binding.tvPartialName.text = context.getString(
                R.string.part_name,
                PartyNumberFormatter.display(party.partial_name)
            )
            binding.tvWarehouse.text = context.getString(R.string.country, party.warehouse_name)
            binding.tvBoxCount.text = context.getString(R.string.boxes_count, party.boxCount)

            val dateCreated = formatBoxDate(context, party.date_created)  // ← форматируем
            binding.tvDate.text = context.getString(R.string.date, dateCreated)

            binding.root.setOnClickListener {
                onClick(party)
            }
        }
    }
    //формат даты Результат: "23.02.2026 17:14"
    private fun formatBoxDate(context: Context, isoDate: String?): String {
        if (isoDate.isNullOrBlank())
        {
            return context.getString(R.string.no_date)
        }

        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(isoDate) ?: return context.getString(R.string.no_date)

            val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            return outputFormat.format(date)
        } catch (e: Exception) {
            return isoDate
        }
    }
}

class PartyDiffCallback : DiffUtil.ItemCallback<Partial>() {
    override fun areItemsTheSame(oldItem: Partial, newItem: Partial): Boolean =
        oldItem.guid == newItem.guid

    override fun areContentsTheSame(oldItem: Partial, newItem: Partial): Boolean =
        oldItem == newItem
}
