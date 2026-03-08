package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aquaroute_system.databinding.ActivityLoginsignupBinding

class LoginSignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginsignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginsignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mark that user has seen onboarding
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
            .putBoolean("is_first_run", false)
            .apply()

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.btnSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }
}