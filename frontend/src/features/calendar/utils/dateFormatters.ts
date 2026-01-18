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
