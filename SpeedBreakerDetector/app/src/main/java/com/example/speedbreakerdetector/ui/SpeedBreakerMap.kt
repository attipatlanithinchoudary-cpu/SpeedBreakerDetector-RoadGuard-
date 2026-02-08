package com.example.speedbreakerdetector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.speedbreakerdetector.db.SpeedBreaker // Import your Room entity

@Composable
fun SpeedBreakerMap(
    speedBreakers: List<SpeedBreaker>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (speedBreakers.isEmpty()) {
            Text("🚫 No Speed Breakers Detected")
        } else {
            Text("✅ Detected ${speedBreakers.size} speed breakers")
        }
    }
}
