package com.example.aquaroute_system.View

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aquaroute_system.databinding.ActivityCargoDetailsBinding

class CargoDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCargoDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCargoDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cargoId = intent.getStringExtra("CARGO_ID")

        setupViews()

        if (cargoId != null) {
            loadCargoDetails(cargoId)
        }
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadCargoDetails(cargoId: String) {
        // TODO: Load cargo details from ViewModel
        Toast.makeText(this, "Loading cargo details for ID: $cargoId", Toast.LENGTH_SHORT).show()
    }
}