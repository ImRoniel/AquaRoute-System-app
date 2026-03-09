package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.AuthRepository
import com.example.aquaroute_system.databinding.ActivityLoginBinding
import com.example.aquaroute_system.ui.viewmodel.AuthState
import com.example.aquaroute_system.ui.viewmodel.AuthViewModel
import com.example.aquaroute_system.ui.viewmodel.AuthViewModelFactory
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeDependencies()
        setupViewModel()
        setupObservers()
        setupClickListeners()

        // Check if already logged in
        if (sessionManager.isLoggedIn()) {
            Log.d("LoginActivity", "User already logged in, navigating to MainDashboard")
            navigateToMainDashboard()
        }
    }

    private fun initializeDependencies() {
        sessionManager = SessionManager(this)

        try {
            FirebaseApp.initializeApp(this)
            Log.d("LoginActivity", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Firebase init failed", e)
            Toast.makeText(this, "Firebase init error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        authRepository = AuthRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance(),
            sessionManager = sessionManager
        )
    }

    private fun setupViewModel() {
        val factory = AuthViewModelFactory(authRepository)
        authViewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]
    }

    private fun setupObservers() {
        authViewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Authenticated -> {
                    hideLoading()
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()

                    // DEBUG: Check what's in session
                    Log.d("LoginActivity", "=== POST-LOGIN SESSION CHECK ===")
                    Log.d("LoginActivity", "isLoggedIn: ${sessionManager.isLoggedIn()}")
                    Log.d("LoginActivity", "UserId: ${sessionManager.getUserId()}")
                    Log.d("LoginActivity", "UserEmail: ${sessionManager.getUserEmail()}")
                    Log.d("LoginActivity", "UserData: ${sessionManager.getUserData()}")

                    navigateToMainDashboard()
                }
                is AuthState.Loading -> showLoading()
                is AuthState.Error -> {
                    hideLoading()
                    val error = authViewModel.errorMessage.value
                    Toast.makeText(this, "Login failed: $error", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        authViewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, "Error details: $it", Toast.LENGTH_LONG).show()
                authViewModel.clearError()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                authViewModel.login(email, password)
            }
        }

        binding.btnGuest.setOnClickListener {
            Toast.makeText(this, "Continuing as Guest", Toast.LENGTH_SHORT).show()
            navigateToMainDashboard()
        }

        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        return when {
            email.isEmpty() -> {
                binding.etEmail.error = "Email is required"
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.etEmail.error = "Enter a valid email"
                false
            }
            password.isEmpty() -> {
                binding.etPassword.error = "Password is required"
                false
            }
            password.length < 6 -> {
                binding.etPassword.error = "Password must be at least 6 characters"
                false
            }
            else -> true
        }
    }

    private fun showLoading() {
        binding.btnLogin.isEnabled = false
        binding.btnGuest.isEnabled = false
        binding.progressBar?.visibility = android.view.View.VISIBLE
    }

    private fun hideLoading() {
        binding.btnLogin.isEnabled = true
        binding.btnGuest.isEnabled = true
        binding.progressBar?.visibility = android.view.View.GONE
    }

    private fun navigateToMainDashboard() {
        val intent = Intent(this, MainDashboard::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}