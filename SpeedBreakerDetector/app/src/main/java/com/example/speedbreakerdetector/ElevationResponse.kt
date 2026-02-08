package com.example.speedbreakerdetector

data class ElevationResponse(
    val results: List<ElevationResult>,
    val status: String
)

data class ElevationResult(
    val elevation: Double
)
