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
