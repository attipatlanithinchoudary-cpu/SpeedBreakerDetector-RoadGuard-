package com.example.speedbreakerdetector

object SeverityUtils {
    fun calculateSeverity(force: Float): String {
        return when {
            force < 3f -> "LOW"
            force < 6f -> "MEDIUM"
            else -> "HIGH"
        }
    }
}


