package com.example.speedbreakerdetector.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog // NEW: Import for the confirmation dialog
import androidx.core.view.isVisible // NEW: Import for easier visibility toggling
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.speedbreakerdetector.AppDatabase
import com.example.speedbreakerdetector.databinding.FragmentHazardsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HazardsFragment : Fragment() {

    private var _binding: FragmentHazardsBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHazardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        // NEW: Set up the click listener for the new button
        binding.buttonClearAll.setOnClickListener {
            showClearConfirmationDialog()
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerViewHazards.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch {
            db.detectionDao().getAllEvents().collectLatest { events ->
                // NEW: Show or hide the empty text view based on the list
                binding.textEmptyView.isVisible = events.isEmpty()
                binding.recyclerViewHazards.isVisible = events.isNotEmpty()

                // Create and set the adapter
                val adapter = DetectionEventAdapter(events.reversed()) // Show newest first
                binding.recyclerViewHazards.adapter = adapter
            }
        }
    }

    // NEW: A function to show the confirmation dialog
    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear History")
            .setMessage("Are you sure you want to delete all detected hazards? This action cannot be undone.")
            .setPositiveButton("Yes, Clear All") { _, _ ->
                // If the user clicks "Yes", launch a coroutine to clear the database
                lifecycleScope.launch {
                    db.detectionDao().clearAllEvents()
                    // The Flow in setupRecyclerView will automatically update the UI
                }
            }
            .setNegativeButton("Cancel", null) // Do nothing on "Cancel"
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
