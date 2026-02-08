package com.example.speedbreakerdetector

import com.google.gson.annotations.SerializedName

data class DirectionsResponse(
    val routes: List<Route>
)

data class Route(
    val overviewPolyline: OverviewPolyline,
    val legs: List<Leg>
)

data class Leg(
    val steps: List<Step>
)

data class Step(
    val html_instructions: String,
    val distance: Distance,
    val polyline: OverviewPolyline
)

data class Distance(
    val text: String,
    val value: Int
)

data class OverviewPolyline(
    val points: String
)
