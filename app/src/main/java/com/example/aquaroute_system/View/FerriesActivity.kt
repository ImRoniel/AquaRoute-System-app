package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.WeatherRefreshRepository
import com.example.aquaroute_system.data.repository.WeatherRepository
import com.example.aquaroute_system.databinding.ActivityFerriesBinding
import com.example.aquaroute_system.ui.adapter.FerryAdapter
import com.example.aquaroute_system.ui.viewmodel.FerriesViewModel
import com.example.aquaroute_system.ui.viewmodel.FerriesViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore

class FerriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFerriesBinding
    private lateinit var viewModel: FerriesViewModel
    private lateinit var adapter: FerryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFerriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViewModel()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupViewModel() {
        val firestore = FirebaseFirestore.getInstance()
        val ferryRepository = FerryRepository(firestore)
        val weatherRepository = WeatherRepository(firestore)
        val weatherRefreshRepository = WeatherRefreshRepository("https://aquaroute-system-web.onrender.com/")
        val factory = FerriesViewModelFactory(ferryRepository, weatherRepository, weatherRefreshRepository)
        viewModel = ViewModelProvider(this, factory).get(FerriesViewModel::class.java)
    }

    private fun setupRecyclerView() {
        adapter = FerryAdapter { ferry ->
            // Launch mapping view for specific ferry
            val intent = Intent(this, LiveMapView::class.java)
            intent.putExtra("FERRY_ID", ferry.id)
            startActivity(intent)
        }
        binding.rvFerries.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.ferries.observe(this) { ferries ->
            adapter.submitList(ferries)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                binding.emptyState.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = error
                binding.rvFerries.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvFerries.visibility = View.VISIBLE
            }
        }
    }

    private fun setupClickListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.refresh()
        }

        // Apply scale animation to retry button
        setScaleAnimation(binding.btnRetry)
    }

    private fun setScaleAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    false
                }
                else -> false
            }
        }
    }
}
