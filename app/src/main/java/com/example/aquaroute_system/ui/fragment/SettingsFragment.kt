package com.example.aquaroute_system.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.aquaroute_system.databinding.FragmentSettingsBinding
import com.example.aquaroute_system.util.SessionManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        sessionManager = SessionManager(requireContext())
        setupSettings()
        return binding.root
    }

    private fun setupSettings() {
        binding.switchBeginnerMode.isChecked = sessionManager.isBeginnerMode()

        binding.switchBeginnerMode.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setBeginnerMode(isChecked)
            val msg = if (isChecked) "Beginner Mode enabled" else "Beginner Mode disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
