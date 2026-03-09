package com.example.aquaroute_system.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.VoyageRecord

class VoyageHistoryAdapter(
    private val onItemClick: (VoyageRecord) -> Unit
) : RecyclerView.Adapter<VoyageHistoryAdapter.ViewHolder>() {

    private var voyages = listOf<VoyageRecord>()

    fun submitList(list: List<VoyageRecord>) {
        voyages = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_voyage_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(voyages[position])
    }

    override fun getItemCount() = voyages.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView.findViewById(R.id.cardVoyage)
        private val tvStatusIcon: TextView = itemView.findViewById(R.id.tvStatusIcon)
        private val tvStatusText: TextView = itemView.findViewById(R.id.tvStatusText)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvTrackingNumber: TextView = itemView.findViewById(R.id.tvTrackingNumber)
        private val tvRoute: TextView = itemView.findViewById(R.id.tvRoute)
        private val tvCargo: TextView = itemView.findViewById(R.id.tvCargo)
        private val tvDeliveryInfo: TextView = itemView.findViewById(R.id.tvDeliveryInfo)
        private val btnViewReceipt: TextView = itemView.findViewById(R.id.btnViewReceipt)
        private val btnRate: TextView = itemView.findViewById(R.id.btnRate)

        init {
            card.setOnClickListener {
                onItemClick(voyages[adapterPosition])
            }

            btnViewReceipt.setOnClickListener {
                // Handle view receipt
            }

            btnRate.setOnClickListener {
                // Handle rate
            }
        }

        fun bind(voyage: VoyageRecord) {
            tvStatusIcon.text = voyage.statusIcon
            tvStatusText.text = voyage.status.uppercase()
            tvDate.text = voyage.deliveryDate
            tvTrackingNumber.text = voyage.trackingNumber
            tvRoute.text = "${voyage.origin} → ${voyage.destination}"
            tvCargo.text = "Cargo: ${voyage.cargoType} · ${voyage.weight}"

            tvDeliveryInfo.text = if (voyage.isOnTime) {
                "Delivered: ${voyage.deliveryTime} · On time"
            } else {
                "Delivered: ${voyage.deliveryTime} · ${voyage.delayMinutes} min delay"
            }

            // Set status color
            tvStatusText.setTextColor(
                itemView.context.getColor(
                    when (voyage.status) {
                        "completed" -> R.color.success_green
                        "active" -> R.color.deep_ocean_blue
                        "delayed" -> R.color.error_red
                        else -> R.color.medium_text
                    }
                )
            )
        }
    }
}

// Data class for voyage history
data class VoyageRecord(
    val id: String,
    val trackingNumber: String,
    val origin: String,
    val destination: String,
    val cargoType: String,
    val weight: String,
    val status: String,
    val statusIcon: String,
    val deliveryDate: String,
    val deliveryTime: String,
    val isOnTime: Boolean,
    val delayMinutes: Int = 0
)