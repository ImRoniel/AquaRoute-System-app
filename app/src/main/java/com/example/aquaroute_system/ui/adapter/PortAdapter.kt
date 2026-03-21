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
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.databinding.ItemPortBinding
import java.util.Locale

class PortAdapter(
    private var currentHour: Int,
    private val vesselCounts: () -> Map<String, Int>,
    private val onPortClick: (FirestorePort) -> Unit
) : ListAdapter<FirestorePort, PortAdapter.PortViewHolder>(PortDiffCallback()) {

    fun updateCurrentHour(hour: Int) {
        currentHour = hour
        notifyDataSetChanged()
    }

    // ── Status → color mapping (uses startsWith to avoid 'Closed (opens at X)' false match) ──
    private fun statusStyle(statusText: String): Pair<String, Int> = when {
        statusText.startsWith("Open",     ignoreCase = true) -> statusText to Color.parseColor("#4CAF50") // 🟢
        statusText.startsWith("Closing",  ignoreCase = true) -> statusText to Color.parseColor("#FF9800") // 🟠
        statusText.startsWith("Opens at", ignoreCase = true) -> statusText to Color.parseColor("#9E9E9E") // ⚫
        statusText.startsWith("Closed",   ignoreCase = true) -> statusText to Color.parseColor("#F44336") // 🔴
        else                                                  -> statusText to Color.parseColor("#9E9E9E") // ⚫
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        val binding = ItemPortBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PortViewHolder(private val binding: ItemPortBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(port: FirestorePort) {
            // ── Name & type ────────────────────────────────────────────
            binding.tvPortName.text = port.name.ifBlank { "Unknown Port" }
            binding.tvDistance.text = port.type.replaceFirstChar { it.uppercase() }

            // ── Status pill badge (guaranteed correct — uses startsWith) ──
            val statusText = port.getCurrentStatus(currentHour)
            val (label, color) = statusStyle(statusText)
            binding.statusChip.text = label.uppercase(Locale.getDefault())
            binding.statusChip.setTextColor(Color.WHITE)
            ViewCompat.setBackgroundTintList(
                binding.statusChip,
                ColorStateList.valueOf(color)
            )

            // ── Vessel activity ────────────────────────────────────────
            val activeVessels = vesselCounts()[port.id] ?: 0
            binding.tvActiveVessels.text = "$activeVessels Active"
            binding.vesselActivityLayout.alpha = if (activeVessels > 0) 1.0f else 0.5f

            // ── Port type icon ─────────────────────────────────────────
            val iconRes = when (port.type.lowercase()) {
                "terminal" -> R.drawable.ic_port_terminal
                "pier"     -> R.drawable.ic_port_pier
                "boatyard" -> R.drawable.ic_port_boatyard
                else       -> R.drawable.ic_port_marker
            }
            binding.ivPortType.setImageResource(iconRes)

            binding.root.setOnClickListener { onPortClick(port) }
        }
    }

    class PortDiffCallback : DiffUtil.ItemCallback<FirestorePort>() {
        override fun areItemsTheSame(old: FirestorePort, new: FirestorePort) = old.id == new.id
        override fun areContentsTheSame(old: FirestorePort, new: FirestorePort) = old == new
    }
}
