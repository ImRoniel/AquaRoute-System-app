package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aquaroute_system.databinding.ActivitySignupBinding

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSignUp.setOnClickListener {
            if (validateInputs()) {
                Toast.makeText(this, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                navigateToLogin()
            }
        }

        binding.tvLogin.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun validateInputs(): Boolean {
        val fullName = binding.etFullName.text.toString().trim()
        val gender = binding.etGender.text.toString().trim()
        val age = binding.etAge.text.toString().trim()
        val email = binding.etGmail.text.toString().trim()
        val phone = binding.etNumber.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val isTermsChecked = binding.cbTerms.isChecked

        var isValid = true

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Full name is required"
            isValid = false
        }

        if (gender.isEmpty()) {
            binding.etGender.error = "Gender is required"
            isValid = false
        }

        if (age.isEmpty()) {
            binding.etAge.error = "Age is required"
            isValid = false
        } else {
            try {
                val ageInt = age.toInt()
                if (ageInt < 1 || ageInt > 120) {
                    binding.etAge.error = "Enter a valid age"
                    isValid = false
                }
            } catch (e: NumberFormatException) {
                binding.etAge.error = "Enter a valid number"
                isValid = false
            }
        }

        if (email.isEmpty()) {
            binding.etGmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etGmail.error = "Enter a valid email"
            isValid = false
        }

        if (phone.isEmpty()) {
            binding.etNumber.error = "Phone number is required"
            isValid = false
        } else if (phone.length < 11) {
            binding.etNumber.error = "Enter a valid phone number"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            isValid = false
        }

        if (!isTermsChecked) {
            Toast.makeText(this, "Please agree to terms & conditions", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish( )
    }


    private fun navigateToMainDashboard() {
        val intent = Intent(this, MainDashboard::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}