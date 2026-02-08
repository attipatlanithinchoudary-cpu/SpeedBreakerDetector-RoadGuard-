package com.example.speedbreakerdetector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * This adapter takes a list of DetectionEvent objects and knows how to bind them
 * to the layout defined in 'history_item.xml'.
 */
class HistoryAdapter(private val events: List<DetectionEvent>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    /**
     * This ViewHolder holds references to the views in each row of the list.
     * It avoids repeatedly calling findViewById for performance.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeText: TextView = view.findViewById(R.id.item_type_text)
        val forceText: TextView = view.findViewById(R.id.item_force_text)
        val locationText: TextView = view.findViewById(R.id.item_location_text)
        val timestampText: TextView = view.findViewById(R.id.item_timestamp_text)
    }

    /**
     * Called when the RecyclerView needs a new ViewHolder. It inflates the XML layout
     * for a single row.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return ViewHolder(view)
    }

    /**
     * Called by the RecyclerView to display the data at a specific position.
     * This method updates the contents of the ViewHolder's views to reflect the
     * item at the given position.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]

        // Make the type more readable (e.g., "SPEED_BUMP" -> "Speed Bump")
        val formattedType = event.type.replace("_", " ").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        holder.typeText.text = formattedType

        // Format the numbers to two decimal places for cleaner display
        holder.forceText.text = String.format(Locale.US, "Force Magnitude: %.2f m/s²", event.force)
        holder.locationText.text = String.format(Locale.US, "Location: %.4f, %.4f", event.latitude, event.longitude)

        // Format the timestamp (which is a Long) into a human-readable date and time
        val sdf = SimpleDateFormat("MMM dd, yyyy, h:mm a", Locale.getDefault())
        holder.timestampText.text = sdf.format(Date(event.timestamp))
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     */
    override fun getItemCount() = events.size
}
