package com.example.speedbreakerdetector.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.speedbreakerdetector.SettingsManager // NEW: Import the SettingsManager
import com.example.speedbreakerdetector.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // NEW: Declare a variable for our SettingsManager
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // NEW: Initialize the SettingsManager using the fragment's context
        settingsManager = SettingsManager(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // NEW: Call the function to load initial values
        loadSettings()

        // NEW: Set up listeners to save the settings when a switch is toggled
        setupClickListeners()
    }

    // NEW: A function to load settings and update the UI
    private fun loadSettings() {
        // Load the saved value for vibration and set the switch accordingly
        binding.switchVibration.isChecked = settingsManager.isVibrationEnabled()

        // Load the saved value for sound and set the switch accordingly
        binding.switchDetectionSounds.isChecked = settingsManager.isSoundEnabled()

        // We can load other settings here in the future
    }

    // NEW: A function to handle clicks and save the new state
    private fun setupClickListeners() {
        // When the vibration switch is flipped...
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            // ...save the new value (true or false)
            settingsManager.setVibrationEnabled(isChecked)
        }

        // When the sound switch is flipped...
        binding.switchDetectionSounds.setOnCheckedChangeListener { _, isChecked ->
            // ...save the new value
            settingsManager.setSoundEnabled(isChecked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
