package com.example.aquaroute_system.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.databinding.ItemFerryBinding

class FerryAdapter(
    private val onFerryClick: (Ferry) -> Unit
) : ListAdapter<Ferry, FerryAdapter.FerryViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Ferry>() {
        override fun areItemsTheSame(old: Ferry, new: Ferry) = old.id == new.id
        override fun areContentsTheSame(old: Ferry, new: Ferry) = old == new
    }

    inner class FerryViewHolder(private val binding: ItemFerryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ferry: Ferry) {
            binding.tvFerryName.text = ferry.name.ifBlank { "Unknown Ferry" }
            binding.tvFerryRoute.text = ferry.route.ifBlank { "Route unknown" }

            // Speed
            binding.tvFerrySpeed.text = if (ferry.speed_knots > 0) "${ferry.speed_knots} knots" else "— knots"

            // ETA
            binding.tvFerryEta.text = if (ferry.eta > 0) "${ferry.eta} min" else "TBD"

            // Status chip colour
            val statusText = ferry.status.ifBlank { "Unknown" }
            binding.tvFerryStatus.text = statusText.uppercase()
            val statusColor = when (ferry.status.lowercase()) {
                "at sea", "en route", "underway", "active" ->
                    android.graphics.Color.parseColor("#4CAF50")   // green
                "docked" ->
                    android.graphics.Color.parseColor("#2196F3")   // blue
                else ->
                    android.graphics.Color.parseColor("#9E9E9E")   // grey
            }
            binding.tvFerryStatus.setTextColor(statusColor)

            binding.root.setOnClickListener { onFerryClick(ferry) }
            binding.btnTrackFerry.setOnClickListener { onFerryClick(ferry) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FerryViewHolder {
        val binding = ItemFerryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FerryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FerryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
