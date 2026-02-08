package com.example.speedbreakerdetector

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsApi {

    @GET("maps/api/directions/json")
    fun getRoute(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "driving",   // ✅ ADD THIS LINE
        @Query("key") apiKey: String
    ): Call<DirectionsResponse>
}




