package com.example.speedbreakerdetector

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ElevationApi {

    @GET("maps/api/elevation/json")
    fun getElevation(
        @Query("locations") locations: String,
        @Query("key") apiKey: String
    ): Call<ElevationResponse>
}
