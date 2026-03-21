package com.example.aquaroute_system.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.databinding.ItemFerryBinding

class FerryAdapter(
    private val onFerryClick: (Ferry) -> Unit
) : ListAdapter<Ferry, FerryAdapter.FerryViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Ferry>() {
        override fun areItemsTheSame(old: Ferry, new: Ferry) = old.id == new.id
        override fun areContentsTheSame(old: Ferry, new: Ferry) = old == new

        // ── Status color + label map (matches confirmed Firestore values) ──
        fun statusStyle(status: String): Pair<String, Int> = when (status.lowercase()) {
            "on_time"     -> "ON TIME"     to Color.parseColor("#4CAF50") // 🟢 green
            "delayed"     -> "DELAYED"     to Color.parseColor("#FF9800") // 🟠 orange
            "cancelled"   -> "CANCELLED"   to Color.parseColor("#F44336") // 🔴 red
            "docked"      -> "DOCKED"      to Color.parseColor("#2196F3") // 🔵 blue
            "maintenance" -> "MAINTENANCE" to Color.parseColor("#9E9E9E") // ⚫ grey
            else          -> status.uppercase().ifBlank { "UNKNOWN" } to Color.parseColor("#9E9E9E")
        }
    }

    inner class FerryViewHolder(private val binding: ItemFerryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ferry: Ferry) {
            // ── Primary info ──────────────────────────────────────────
            binding.tvFerryName.text = ferry.name.ifBlank { "Unknown Ferry" }
            binding.tvFerryRoute.text = ferry.route.ifBlank { "Route unknown" }

            // ── Stats row ─────────────────────────────────────────────
            binding.tvFerrySpeed.text =
                if (ferry.speed_knots > 0) "${ferry.speed_knots} kn" else "— kn"

            binding.tvFerryEta.text =
                if (ferry.eta > 0) "${ferry.eta} min" else "TBD"

            binding.tvFerryDistance.text =
                if (ferry.route_distance_km > 0)
                    String.format("%.1f km", ferry.route_distance_km)
                else "— km"

            // ── Status pill badge ─────────────────────────────────────
            val (label, color) = statusStyle(ferry.status)
            binding.tvFerryStatus.text = label
            binding.tvFerryStatus.setTextColor(Color.WHITE)
            ViewCompat.setBackgroundTintList(
                binding.tvFerryStatus,
                ColorStateList.valueOf(color)
            )

            // ── Left accent strip color ───────────────────────────────
            binding.viewStatusStrip.setBackgroundColor(color)

            // ── Click listeners ───────────────────────────────────────
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
