package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class DailyChoreResponse(
    val id: String,
    val memberId: String,
    val title: String,
    val weekdays: List<String>,
    val xpPoints: Int,
    val isActive: Boolean,
)

data class DailyChoreWithCompletionResponse(
    val chore: DailyChoreResponse,
    val completed: Boolean,
    val completionId: String?,
)

data class DailyChoreCompletionResponse(
    val id: String,
    val choreId: String,
    val memberId: String,
    val occurrenceDate: String,
    val completedAt: String,
)

data class MarkChoreCompletedRequest(
    val date: String,
)

data class CreateDailyChoreRequest(
    val memberId: String,
    val title: String,
    val weekdays: List<String>,
    val xpPoints: Int,
)

interface DailyChoreApi {

    @GET("daily-chores/members/{memberId}/for-date")
    suspend fun getChoresForDate(
        @Path("memberId") memberId: String,
        @Query("date") date: String,
    ): List<DailyChoreWithCompletionResponse>

    @POST("daily-chores/{choreId}/completion")
    suspend fun markCompleted(
        @Path("choreId") choreId: String,
        @Body body: MarkChoreCompletedRequest,
    ): DailyChoreCompletionResponse

    @DELETE("daily-chores/{choreId}/completion")
    suspend fun unmarkCompleted(
        @Path("choreId") choreId: String,
        @Query("date") date: String,
    ): Response<Unit>

    @POST("daily-chores")
    suspend fun createChore(
        @Body body: CreateDailyChoreRequest,
    ): DailyChoreResponse

    /** Removes the chore itself, not a completion. Existing completions go with it. */
    @DELETE("daily-chores/{choreId}")
    suspend fun deleteChore(
        @Path("choreId") choreId: String,
    ): Response<Unit>
}
