//C:\Users\Roniel Cuaresma\AndroidStudioProjects\AquaRouteSystem\app\src\main\java\com\example\aquaroute_system\View\SignupActivity.kt

package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.aquaroute_system.data.repository.AuthRepository
import com.example.aquaroute_system.databinding.ActivitySignupBinding
import com.example.aquaroute_system.ui.viewmodel.AuthState
import com.example.aquaroute_system.ui.viewmodel.AuthViewModel
import com.example.aquaroute_system.ui.viewmodel.AuthViewModelFactory
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionManager: SessionManager

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeDependencies()
        setupObservers()
        setupClickListeners()
    }

    private fun initializeDependencies() {
        sessionManager = SessionManager(this)
        authRepository = AuthRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance(),
            sessionManager = sessionManager
        )
    }

    private fun setupObservers() {
        authViewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Authenticated -> {
                    hideLoading()
                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                    navigateToLogin()
                }
                is AuthState.Loading -> showLoading()
                is AuthState.Error -> {
                    hideLoading()
                    Toast.makeText(this, "Signup failed", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        authViewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                authViewModel.clearError()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSignUp.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val email = binding.etGmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword?.text.toString().trim()

            if (validateInputs(fullName, email, password, confirmPassword)) {
                authViewModel.signUp(email, password, fullName)
            }
        }

        binding.tvLogin.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun validateInputs(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        // Your existing validation logic...
        return when {
            fullName.isEmpty() -> {
                binding.etFullName.error = "Full name is required"
                false
            }
            email.isEmpty() -> {
                binding.etGmail.error = "Email is required"
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.etGmail.error = "Enter a valid email"
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
            password != confirmPassword -> {
                binding.etConfirmPassword?.error = "Passwords do not match"
                false
            }
            else -> true
        }
    }

    private fun showLoading() {
        binding.btnSignUp.isEnabled = false
        binding.progressBar?.visibility = android.view.View.VISIBLE
    }

    private fun hideLoading() {
        binding.btnSignUp.isEnabled = true
        binding.progressBar?.visibility = android.view.View.GONE
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToMainDashboard() {
        val intent = Intent(this, LiveMapView::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}