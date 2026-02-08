/**
 * Formats a date-time string for display.
 * 
 * @param dateTimeString - ISO date-time string
 * @param isAllDay - Whether the event is all-day
 * @returns Formatted date string in Swedish locale
 */
export function formatDateTime(dateTimeString: string, isAllDay: boolean): string {
  const date = new Date(dateTimeString);
  if (isAllDay) {
    return date.toLocaleDateString("sv-SE", { year: "numeric", month: "long", day: "numeric" });
  }
  return date.toLocaleString("sv-SE", {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * Formats a date-time range for display.
 * Handles same-day and multi-day events differently.
 * 
 * @param startDateTime - ISO date-time string for start
 * @param endDateTime - ISO date-time string for end, or null
 * @param isAllDay - Whether the event is all-day
 * @returns Formatted date range string in Swedish locale
 */
export function formatDateTimeRange(
  startDateTime: string,
  endDateTime: string | null,
  isAllDay: boolean
): string {
  if (isAllDay) {
    return formatDateTime(startDateTime, true);
  }

  const start = new Date(startDateTime);
  const startDate = start.toISOString().split("T")[0];
  
  if (!endDateTime) {
    // No end time, just show start
    return formatDateTime(startDateTime, false);
  }

  const end = new Date(endDateTime);
  const endDate = end.toISOString().split("T")[0];

  // Check if same day
  if (startDate === endDate) {
    // Same day: "13 januari 16:00 - 17:00" (or with year if not current year)
    const now = new Date();
    const startYear = start.getFullYear();
    const currentYear = now.getFullYear();
    
    const datePart = startYear === currentYear
      ? start.toLocaleDateString("sv-SE", { month: "long", day: "numeric" })
      : start.toLocaleDateString("sv-SE", { year: "numeric", month: "long", day: "numeric" });
    
    const startTime = start.toLocaleTimeString("sv-SE", { hour: "2-digit", minute: "2-digit" });
    const endTime = end.toLocaleTimeString("sv-SE", { hour: "2-digit", minute: "2-digit" });
    
    return `${datePart} ${startTime} - ${endTime}`;
  } else {
    // Different days: show both full dates
    return `${formatDateTime(startDateTime, false)} - ${formatDateTime(endDateTime, false)}`;
  }
}

/**
 * Formats an all-day event date range for display.
 * Handles single-day and multi-day all-day events.
 * 
 * @param startDateTime - ISO date-time string for start
 * @param endDateTime - ISO date-time string for end, or null
 * @returns Formatted date range string in Swedish locale (e.g., "8 februari 2026 - Heldag" or "8 februari 2026 - 10 februari 2026 - Heldag")
 */
export function formatAllDayEventRange(
  startDateTime: string,
  endDateTime: string | null
): string {
  const startDate = new Date(startDateTime.substring(0, 10));
  const startDateStr = startDate.toLocaleDateString("sv-SE", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
  
  if (!endDateTime) {
    return `${startDateStr} - Heldag`;
  }
  
  const endDate = new Date(endDateTime.substring(0, 10));
  const endDateStr = endDate.toLocaleDateString("sv-SE", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
  
  // Check if same day
  if (startDateTime.substring(0, 10) === endDateTime.substring(0, 10)) {
    return `${startDateStr} - Heldag`;
  }
  
  // Multi-day: show range
  return `${startDateStr} - ${endDateStr} - Heldag`;
}

/**
 * Formats a date for use in event forms.
 * Returns date string in YYYY-MM-DD format for all-day events,
 * or YYYY-MM-DDTHH:mm format for timed events.
 * 
 * @param date - Date object to format
 * @param hour - Optional hour (0-23) for timed events
 * @returns Formatted date string (YYYY-MM-DD or YYYY-MM-DDTHH:mm)
 */
export function formatDateForEventForm(date: Date, hour?: number): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  
  if (hour !== undefined) {
    const hourStr = String(hour).padStart(2, "0");
    return `${year}-${month}-${day}T${hourStr}:00`;
  }
  
  return `${year}-${month}-${day}`;
}

/**
 * Gets all dates that an all-day event spans.
 * For single-day events, returns an array with one date.
 * For multi-day events, returns all dates from start to end (inclusive).
 * 
 * @param event - Calendar event (must be all-day)
 * @param maxDays - Maximum number of days to process (safety limit, default 365)
 * @returns Array of date strings in YYYY-MM-DD format
 */
export function getAllDayEventDates(
  event: { isAllDay: boolean; startDateTime: string; endDateTime: string | null },
  maxDays: number = 365
): string[] {
  if (!event.isAllDay) return [];
  
  const startDateStr = event.startDateTime.substring(0, 10);
  if (!event.endDateTime) {
    // Single day event
    return [startDateStr];
  }
  
  // Multi-day event - get all dates from start to end (inclusive)
  const endDateStr = event.endDateTime.substring(0, 10);
  const dates: string[] = [];
  const startDate = new Date(startDateStr + "T00:00:00");
  const endDate = new Date(endDateStr + "T00:00:00");
  
  // Safety check: if end is before start, just return start date
  if (endDate < startDate) {
    return [startDateStr];
  }
  
  // Safety limit to prevent performance issues
  let dayCount = 0;
  
  // Iterate through all dates from start to end (inclusive)
  const currentDate = new Date(startDate);
  while (currentDate <= endDate && dayCount < maxDays) {
    const year = currentDate.getFullYear();
    const month = String(currentDate.getMonth() + 1).padStart(2, "0");
    const day = String(currentDate.getDate()).padStart(2, "0");
    dates.push(`${year}-${month}-${day}`);
    currentDate.setDate(currentDate.getDate() + 1);
    dayCount++;
  }
  
  return dates;
}
