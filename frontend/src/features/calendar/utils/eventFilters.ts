import { CalendarEventResponse } from "../../../shared/api/calendar";

/**
 * Gets all events that occur on a specific day.
 * Handles both all-day events (including multi-day) and timed events.
 * 
 * @param events - Array of calendar events to filter
 * @param day - The day to filter events for (Date object or day number)
 * @param year - Optional year (required if day is a number)
 * @param month - Optional month index 0-11 (required if day is a number)
 * @returns Array of events that occur on the specified day
 */
export function getEventsForDay(
  events: CalendarEventResponse[],
  day: Date | number,
  year?: number,
  month?: number
): CalendarEventResponse[] {
  // Normalize day to Date object
  let targetDate: Date;
  let dayDateStr: string;
  
  if (day instanceof Date) {
    targetDate = day;
    dayDateStr = targetDate.toISOString().split("T")[0];
  } else {
    // day is a number, year and month must be provided
    if (year === undefined || month === undefined) {
      throw new Error("year and month must be provided when day is a number");
    }
    targetDate = new Date(year, month, day);
    dayDateStr = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }
  
  const dayStart = new Date(targetDate);
  dayStart.setHours(0, 0, 0, 0);
  const dayEnd = new Date(targetDate);
  dayEnd.setHours(23, 59, 59, 999);

  return events.filter(event => {
    if (event.isAllDay) {
      // For all-day events, check if this day falls within the event's date range
      const eventStartDateStr = event.startDateTime.substring(0, 10);
      if (!event.endDateTime) {
        // Single day event
        return eventStartDateStr === dayDateStr;
      }
      // Multi-day event - check if day is within range (inclusive)
      const eventEndDateStr = event.endDateTime.substring(0, 10);
      return dayDateStr >= eventStartDateStr && dayDateStr <= eventEndDateStr;
    }
    // For timed events, check if event starts within the day
    const eventStart = new Date(event.startDateTime);
    return eventStart >= dayStart && eventStart <= dayEnd;
  });
}
