package com.example.aquaroute_system.View

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.repository.WeatherRepository
import com.example.aquaroute_system.databinding.ActivityWeatherAdvisoriesBinding
import com.example.aquaroute_system.ui.adapter.WeatherAdvisoryAdapter
import com.example.aquaroute_system.ui.viewmodel.WeatherAdvisoriesViewModel
import com.example.aquaroute_system.ui.viewmodel.WeatherAdvisoriesViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore

class WeatherAdvisoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeatherAdvisoriesBinding
    private lateinit var viewModel: WeatherAdvisoriesViewModel
    private lateinit var adapter: WeatherAdvisoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherAdvisoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViewModel()
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViewModel() {
        val firestore = FirebaseFirestore.getInstance()
        val repository = WeatherRepository(firestore)
        val factory = WeatherAdvisoriesViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(WeatherAdvisoriesViewModel::class.java)
    }

    private fun setupRecyclerView() {
        adapter = WeatherAdvisoryAdapter()
        binding.rvWeatherAdvisories.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun observeViewModel() {
        viewModel.weatherState.observe(this) { result ->
            when (result) {
                is Result.Loading -> {
                    if (!binding.swipeRefreshLayout.isRefreshing) {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                    }
                    binding.llEmptyError.visibility = android.view.View.GONE
                }
                is Result.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    
                    if (result.data.isEmpty()) {
                        showEmptyState("No weather advisories at the moment.")
                    } else {
                        binding.llEmptyError.visibility = android.view.View.GONE
                        adapter.submitList(result.data)
                    }
                }
                is Result.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    showErrorState(result.exception.message ?: "An unknown error occurred")
                }
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.refreshData()
        }
    }

    private fun showEmptyState(message: String) {
        binding.llEmptyError.visibility = android.view.View.VISIBLE
        binding.tvEmptyError.text = message
        binding.btnRetry.visibility = android.view.View.GONE
        adapter.submitList(emptyList())
    }

    private fun showErrorState(message: String) {
        binding.llEmptyError.visibility = android.view.View.VISIBLE
        binding.tvEmptyError.text = "Error: $message"
        binding.btnRetry.visibility = android.view.View.VISIBLE
    }
}
