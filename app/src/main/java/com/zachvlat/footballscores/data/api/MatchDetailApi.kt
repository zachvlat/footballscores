package com.zachvlat.footballscores.data.api

import com.zachvlat.footballscores.data.model.MatchDetailResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface MatchDetailApi {
    
    @GET("v1/api/app/scoreboard/soccer/{matchId}?locale=en")
    suspend fun getMatchDetails(@Path("matchId") matchId: String): MatchDetailResponse
    
    companion object {
        private const val BASE_URL = "https://prod-cdn-public-api.livescore.com/"
    }
}