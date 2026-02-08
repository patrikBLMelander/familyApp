/**
 * Utility functions for date handling in local timezone.
 * These functions ensure consistent date parsing and formatting across the application,
 * avoiding timezone-related bugs.
 */

/**
 * Creates a Date object in local timezone from a YYYY-MM-DD string.
 * @param dateString - Date string in format YYYY-MM-DD
 * @returns Date object set to midnight in local timezone
 */
export function parseLocalDate(dateString: string): Date {
  const [year, month, day] = dateString.split('-').map(Number);
  return new Date(year, month - 1, day, 0, 0, 0, 0);
}

/**
 * Formats a Date object as YYYY-MM-DD string in local timezone.
 * @param date - Date object to format
 * @returns Date string in format YYYY-MM-DD
 */
export function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Gets a date key (YYYY-MM-DD) from a Date object, normalized to midnight.
 * @param date - Date object
 * @returns Date string in format YYYY-MM-DD
 */
export function getDateKey(date: Date): string {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return formatLocalDate(d);
}
