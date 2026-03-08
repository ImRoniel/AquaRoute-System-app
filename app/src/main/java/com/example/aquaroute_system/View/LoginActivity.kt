package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aquaroute_system.R
import com.example.aquaroute_system.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Login button click
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                // TODO: Add your actual authentication logic here
                // For now, just navigate to MainDashboard
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                navigateToMainDashboard()
            }
        }

        // Guest button click
        binding.btnGuest.setOnClickListener {
            // Navigate as guest
            Toast.makeText(this, "Continuing as Guest", Toast.LENGTH_SHORT).show()
            navigateToMainDashboard()
        }

        // Create Account text click
        binding.tvCreateAccount.setOnClickListener {
            // Navigate to SignupActivity
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

    private fun navigateToMainDashboard() {
        val intent = Intent(this, MainDashboard::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }



}