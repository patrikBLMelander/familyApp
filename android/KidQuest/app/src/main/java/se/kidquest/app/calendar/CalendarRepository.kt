package se.kidquest.app.calendar

import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.CalendarEventTaskCompletionResponse
import se.kidquest.app.network.CalendarTaskWithCompletion
import se.kidquest.app.network.CreateCalendarEventRequest
import se.kidquest.app.network.UpdateCalendarEventRequest
import se.kidquest.app.network.MarkTaskCompletedRequest
import se.kidquest.app.network.RecurringType
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object CalendarRepository {

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun formatDateTime(dateTime: LocalDateTime): String =
        dateTime.format(dateTimeFormatter)

    private fun formatDate(date: LocalDate): String =
        date.format(dateFormatter)

    /**
     * Hämta dagens uppgifter (kalender-tasks) för ett barn.
     */
    suspend fun fetchTasksForDate(memberId: String, date: LocalDate): List<CalendarTaskWithCompletion> =
        withContext(Dispatchers.IO) {
            val start = date.atStartOfDay()
            val end = date.atTime(LocalTime.MAX)

            val eventsDeferred = async {
                ApiClient.calendarApi.getEvents(
                    startDate = formatDateTime(start),
                    endDate = formatDateTime(end),
                )
            }
            val completionsDeferred = async {
                ApiClient.calendarApi.getTaskCompletionsForMember(memberId)
            }

            val events = eventsDeferred.await()
            val completions = completionsDeferred.await()
            val taskEvents = events.filter { it.isTask && it.participantIds.contains(memberId) }
            val dateStr = formatDate(date)

            val completionMap = completions
                .filter { it.occurrenceDate == dateStr }
                .associateBy(CalendarEventTaskCompletionResponse::eventId)

            taskEvents.map { event ->
                CalendarTaskWithCompletion(
                    event = event,
                    completed = completionMap.containsKey(event.id),
                )
            }
        }

    suspend fun fetchTasksForToday(memberId: String): List<CalendarTaskWithCompletion> =
        fetchTasksForDate(memberId, LocalDate.now())

    /**
     * Toggle completion för ett task för ett visst datum.
     */
    suspend fun toggleTaskCompletion(
        eventId: String,
        memberId: String,
        date: LocalDate = LocalDate.now(),
    ) = withContext(Dispatchers.IO) {
        val dateStr = formatDate(date)

        val completions = ApiClient.calendarApi.getTaskCompletions(eventId)
        val existing = completions.find { it.memberId == memberId && it.occurrenceDate == dateStr }

        if (existing != null) {
            ApiClient.calendarApi.unmarkTaskCompleted(
                eventId = eventId,
                memberId = memberId,
                occurrenceDate = dateStr,
            )
        } else {
            ApiClient.calendarApi.markTaskCompleted(
                eventId = eventId,
                body = MarkTaskCompletedRequest(
                    memberId = memberId,
                    occurrenceDate = dateStr,
                ),
            )
        }
    }

    /**
     * Skapa en engångsuppgift (heldag) för idag för ett barn.
     */
    suspend fun createSingleTaskToday(
        memberId: String,
        title: String,
        xpMultiplier: Int = 1,
        isRequired: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val start = today.atStartOfDay()

        ApiClient.calendarApi.createEvent(
            CreateCalendarEventRequest(
                title = title.trim(),
                startDateTime = formatDateTime(start),
                endDateTime = null,
                isAllDay = true,
                description = null,
                categoryId = null,
                location = null,
                participantIds = listOf(memberId),
                recurringType = null,
                recurringInterval = null,
                recurringEndDate = null,
                recurringEndCount = null,
                isTask = true,
                xpPoints = xpMultiplier,
                isRequired = isRequired,
            ),
        )
    }

    /**
     * Skapa återkommande veckouppgifter för ett barn för valda veckodagar.
     * weekday: 1 = Monday ... 7 = Sunday (samma som java.time.DayOfWeek).
     */
    suspend fun createRecurringWeeklyTasks(
        memberId: String,
        title: String,
        weekdays: Set<Int>,
        xpMultiplier: Int = 1,
        isRequired: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        if (weekdays.isEmpty()) return@withContext

        val now = LocalDate.now()
        val endDate = now.plusYears(2)

        val createJobs = weekdays.map { weekday ->
            val targetDayOfWeek = java.time.DayOfWeek.of(weekday)
            var date = now
            while (date.dayOfWeek != targetDayOfWeek) {
                date = date.plusDays(1)
            }

            val start = date.atStartOfDay()
            val recurringEndDate = formatDate(endDate)

            CreateCalendarEventRequest(
                title = title.trim(),
                startDateTime = formatDateTime(start),
                endDateTime = null,
                isAllDay = true,
                description = null,
                categoryId = null,
                location = null,
                participantIds = listOf(memberId),
                recurringType = RecurringType.WEEKLY,
                recurringInterval = 1,
                recurringEndDate = recurringEndDate,
                recurringEndCount = null,
                isTask = true,
                xpPoints = xpMultiplier,
                isRequired = isRequired,
            )
        }

        createJobs.forEach { body ->
            ApiClient.calendarApi.createEvent(body)
        }
    }

    /**
     * Uppdatera titel och XP för en befintlig task (hela eventet/serien).
     */
    suspend fun updateTaskTitleAndXp(
        event: se.kidquest.app.network.CalendarEventResponse,
        newTitle: String,
        xpMultiplier: Int,
    ) = withContext(Dispatchers.IO) {
        val body = UpdateCalendarEventRequest(
            title = newTitle.trim(),
            description = event.description,
            startDateTime = event.startDateTime,
            endDateTime = event.endDateTime,
            isAllDay = event.isAllDay,
            location = event.location,
            categoryId = event.categoryId,
            participantIds = event.participantIds,
            recurringType = event.recurringType,
            recurringInterval = event.recurringInterval,
            recurringEndDate = event.recurringEndDate,
            recurringEndCount = event.recurringEndCount,
            isTask = event.isTask,
            xpPoints = xpMultiplier,
            isRequired = event.isRequired,
            scope = null,
            occurrenceDate = null,
        )

        ApiClient.calendarApi.updateEvent(
            eventId = event.id,
            body = body,
        )
    }
}

