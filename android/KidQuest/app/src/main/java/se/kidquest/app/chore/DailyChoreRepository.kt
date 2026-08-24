package se.kidquest.app.chore

import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.CreateDailyChoreRequest
import se.kidquest.app.network.DailyChoreWithCompletionResponse
import se.kidquest.app.network.MarkChoreCompletedRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DailyChoreRepository {

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun formatDate(date: LocalDate): String = date.format(dateFormatter)

    suspend fun fetchChoresForDate(memberId: String, date: LocalDate): List<DailyChoreWithCompletionResponse> =
        withContext(Dispatchers.IO) {
            ApiClient.dailyChoreApi.getChoresForDate(
                memberId = memberId,
                date = formatDate(date),
            )
        }

    /** Whether the member has any chore configured at all, on any weekday. */
    suspend fun hasAnyChore(memberId: String): Boolean = withContext(Dispatchers.IO) {
        ApiClient.dailyChoreApi.getAllChores(memberId).isNotEmpty()
    }

    suspend fun fetchChoresForToday(memberId: String): List<DailyChoreWithCompletionResponse> =
        fetchChoresForDate(memberId, LocalDate.now())

    suspend fun createChore(
        memberId: String,
        title: String,
        weekdays: Set<Int>,
        xpPoints: Int,
    ) = withContext(Dispatchers.IO) {
        val weekdayNames = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        ApiClient.dailyChoreApi.createChore(
            CreateDailyChoreRequest(
                memberId = memberId,
                title = title.trim(),
                weekdays = weekdays.sorted().map { weekdayNames[it - 1] },
                xpPoints = xpPoints,
            ),
        )
    }

    suspend fun deleteChore(choreId: String) = withContext(Dispatchers.IO) {
        val response = ApiClient.dailyChoreApi.deleteChore(choreId)
        if (!response.isSuccessful) {
            throw IllegalStateException("Kunde inte ta bort sysslan (HTTP ${response.code()})")
        }
    }

    suspend fun toggleChoreCompletion(
        choreId: String,
        isCurrentlyCompleted: Boolean,
        date: LocalDate = LocalDate.now(),
    ) = withContext(Dispatchers.IO) {
        val dateStr = formatDate(date)
        if (isCurrentlyCompleted) {
            ApiClient.dailyChoreApi.unmarkCompleted(choreId = choreId, date = dateStr)
        } else {
            ApiClient.dailyChoreApi.markCompleted(
                choreId = choreId,
                body = MarkChoreCompletedRequest(date = dateStr),
            )
        }
    }
}
