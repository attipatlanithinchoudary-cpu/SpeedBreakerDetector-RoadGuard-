package com.example.speedbreakerdetector

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    // Lazily initialize the database instance.
    private val db by lazy { AppDatabase.getDatabase(this) }

    // Declare the RecyclerView.
    private lateinit var historyRecyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the layout for this activity.
        setContentView(R.layout.activity_history)

        // Find the RecyclerView from the layout file.
        historyRecyclerView = findViewById(R.id.history_recycler_view)

        // Set up the RecyclerView with a vertical list layout.
        historyRecyclerView.layoutManager = LinearLayoutManager(this)

        // Load the data from the database and populate the list.
        loadHistoryData()
    }

    private fun loadHistoryData() {
        // Use a coroutine to access the database off the main thread.
        lifecycleScope.launch {
            // Fetch all detection events from the DAO as a simple list.
            // The list is pre-sorted from newest to oldest by the database query.
            val eventList = db.detectionDao().getAllEventsList()

            // Create an instance of our adapter with the data.
            val adapter = HistoryAdapter(eventList)

            // Set the adapter on the RecyclerView.
            historyRecyclerView.adapter = adapter
        }
    }
}
