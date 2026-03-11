package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

enum class RecurringType {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

data class CalendarEventResponse(
    val id: String,
    val familyId: String,
    val categoryId: String?,
    val title: String,
    val description: String?,
    val startDateTime: String,
    val endDateTime: String?,
    val isAllDay: Boolean,
    val location: String?,
    val createdById: String,
    val recurringType: RecurringType?,
    val recurringInterval: Int?,
    val recurringEndDate: String?,
    val recurringEndCount: Int?,
    val isTask: Boolean,
    val xpPoints: Int?,
    val isRequired: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val participantIds: List<String>,
)

data class CalendarEventTaskCompletionResponse(
    val id: String,
    val eventId: String,
    val memberId: String,
    val occurrenceDate: String, // YYYY-MM-DD
    val completedAt: String,
)

data class CalendarTaskWithCompletion(
    val event: CalendarEventResponse,
    val completed: Boolean,
)

data class CreateCalendarEventRequest(
    val title: String,
    val description: String? = null,
    val startDateTime: String,
    val endDateTime: String? = null,
    val isAllDay: Boolean,
    val location: String? = null,
    val categoryId: String? = null,
    val participantIds: List<String> = emptyList(),
    val recurringType: RecurringType? = null,
    val recurringInterval: Int? = null,
    val recurringEndDate: String? = null,
    val recurringEndCount: Int? = null,
    val isTask: Boolean = false,
    val xpPoints: Int? = null,
    val isRequired: Boolean = true,
)

data class MarkTaskCompletedRequest(
    val memberId: String?,
    val occurrenceDate: String, // YYYY-MM-DD
)

data class UpdateCalendarEventRequest(
    val title: String,
    val description: String? = null,
    val startDateTime: String,
    val endDateTime: String? = null,
    val isAllDay: Boolean,
    val location: String? = null,
    val categoryId: String? = null,
    val participantIds: List<String> = emptyList(),
    val recurringType: RecurringType? = null,
    val recurringInterval: Int? = null,
    val recurringEndDate: String? = null,
    val recurringEndCount: Int? = null,
    val isTask: Boolean = false,
    val xpPoints: Int? = null,
    val isRequired: Boolean = true,
    val scope: String? = null,
    val occurrenceDate: String? = null,
)

interface CalendarApi {

    @GET("calendar/events")
    suspend fun getEvents(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): List<CalendarEventResponse>

    @GET("calendar/members/{memberId}/task-completions")
    suspend fun getTaskCompletionsForMember(
        @Path("memberId") memberId: String,
    ): List<CalendarEventTaskCompletionResponse>

    @GET("calendar/events/{eventId}/task-completion")
    suspend fun getTaskCompletions(
        @Path("eventId") eventId: String,
    ): List<CalendarEventTaskCompletionResponse>

    @POST("calendar/events/{eventId}/task-completion")
    suspend fun markTaskCompleted(
        @Path("eventId") eventId: String,
        @Body body: MarkTaskCompletedRequest,
    ): CalendarEventTaskCompletionResponse

    @DELETE("calendar/events/{eventId}/task-completion")
    suspend fun unmarkTaskCompleted(
        @Path("eventId") eventId: String,
        @Query("memberId") memberId: String,
        @Query("occurrenceDate") occurrenceDate: String,
    ): Response<Unit>

    @POST("calendar/events")
    suspend fun createEvent(
        @Body body: CreateCalendarEventRequest,
    ): CalendarEventResponse

    @retrofit2.http.PATCH("calendar/events/{eventId}")
    suspend fun updateEvent(
        @Path("eventId") eventId: String,
        @Body body: UpdateCalendarEventRequest,
    ): CalendarEventResponse
}

