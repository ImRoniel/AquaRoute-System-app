package com.example.aquaroute_system.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.WeatherCondition

class WeatherAdapter : RecyclerView.Adapter<WeatherAdapter.WeatherViewHolder>() {

    private var weatherList: List<WeatherCondition> = emptyList()

    fun submitList(list: List<WeatherCondition>) {
        weatherList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeatherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weather, parent, false)
        return WeatherViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeatherViewHolder, position: Int) {
        holder.bind(weatherList[position])
    }

    override fun getItemCount() = weatherList.size

    class WeatherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        private val tvTemp: TextView = itemView.findViewById(R.id.tvTemp)
        private val tvIcon: TextView = itemView.findViewById(R.id.tvIcon)
        private val tvCondition: TextView = itemView.findViewById(R.id.tvCondition)
        private val tvWaves: TextView = itemView.findViewById(R.id.tvWaves)
        private val tvWind: TextView = itemView.findViewById(R.id.tvWind)
        private val tvAdvisory: TextView = itemView.findViewById(R.id.tvAdvisory)

        fun bind(weather: WeatherCondition) {
            tvLocation.text = weather.location
            tvTemp.text = "${weather.temperature}°C"
            tvIcon.text = weather.icon
            tvCondition.text = weather.condition.replaceFirstChar { it.uppercase() }
            tvWaves.text = "Waves: ${weather.waves}"
            tvWind.text = "Wind: ${weather.windSpeed}"

            if (weather.hasAdvisory && !weather.advisoryMessage.isNullOrBlank()) {
                tvAdvisory.text = "⚠️ ${weather.advisoryMessage}"
                tvAdvisory.visibility = View.VISIBLE
            } else {
                tvAdvisory.visibility = View.GONE
            }
        }
    }
}