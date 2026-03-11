package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class XpProgressResponse(
    val id: String,
    val memberId: String,
    val year: Int,
    val month: Int,
    val currentXp: Int,
    val currentLevel: Int,
    val totalTasksCompleted: Int,
    val xpForNextLevel: Int,
    val xpInCurrentLevel: Int,
)

data class AwardBonusXpRequest(
    val xpPoints: Int,
)

interface XpApi {

    @GET("xp/current")
    suspend fun getCurrentProgress(): Response<XpProgressResponse>

    @GET("xp/members/{memberId}/current")
    suspend fun getMemberXpProgress(@Path("memberId") memberId: String): Response<XpProgressResponse>

    @POST("xp/members/{memberId}/bonus")
    suspend fun awardBonusXp(
        @Path("memberId") memberId: String,
        @Body body: AwardBonusXpRequest,
    ): XpProgressResponse
}

