package com.example.speedbreakerdetector.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.speedbreakerdetector.DetectionEvent
import com.example.speedbreakerdetector.databinding.ItemDetectionEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetectionEventAdapter(private val events: List<DetectionEvent>) :
    RecyclerView.Adapter<DetectionEventAdapter.EventViewHolder>() {

    class EventViewHolder(val binding: ItemDetectionEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemDetectionEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EventViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return events.size
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val currentEvent = events[position]

        // --- CHANGE IS HERE ---
        // Set the title based on the event's type, making it more readable.
        // E.g., "SPEED_BUMP" becomes "Speed Bump".
        val title = currentEvent.type.replace("_", " ").lowercase(Locale.ROOT)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        holder.binding.textEventType.text = title
        // --- END OF CHANGE ---

        // For manually added potholes, the force is 0. It's better not to show it.
        if (currentEvent.force > 0) {
            holder.binding.textForce.text = "Force: ${"%.2f".format(currentEvent.force)} m/s²"
            holder.binding.textForce.visibility = View.VISIBLE
        } else {
            holder.binding.textForce.visibility = View.GONE // Hide the force TextView
        }

        val formattedDate = SimpleDateFormat("dd-MM-yy HH:mm:ss", Locale.US).format(Date(currentEvent.timestamp))
        holder.binding.textTimestamp.text = formattedDate

        if (currentEvent.latitude != 0.0 && currentEvent.longitude != 0.0) {
            holder.binding.textLocation.text = "Location: ${"%.4f".format(currentEvent.latitude)}, ${"%.4f".format(currentEvent.longitude)}"
        } else {
            holder.binding.textLocation.text = "Location: Not available"
        }
    }
}
