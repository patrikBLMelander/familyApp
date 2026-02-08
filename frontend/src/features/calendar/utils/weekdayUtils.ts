/**
 * Utility functions for working with weekdays
 */

/**
 * Get the next occurrence of a weekday from today
 * @param weekday 0 = Sunday, 1 = Monday, ..., 6 = Saturday (JavaScript Date.getDay() format)
 * @returns Date object for the next occurrence
 */
export function getNextWeekday(weekday: number): Date {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  
  const currentDay = today.getDay(); // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
  
  // Calculate days until next occurrence
  let daysUntil = (weekday - currentDay + 7) % 7;
  
  // If today is the target day, get next week's occurrence
  if (daysUntil === 0) {
    daysUntil = 7;
  }
  
  const nextDate = new Date(today);
  nextDate.setDate(today.getDate() + daysUntil);
  
  return nextDate;
}

/**
 * Get date 2 years from a given date
 * @param date Starting date
 * @returns Date object 2 years later
 */
export function getDateTwoYearsLater(date: Date): Date {
  const twoYearsLater = new Date(date);
  twoYearsLater.setFullYear(date.getFullYear() + 2);
  return twoYearsLater;
}

/**
 * Format date to YYYY-MM-DD format
 */
export function formatDateForAPI(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Weekday names in Swedish
 */
export const WEEKDAY_NAMES = [
  "Söndag",
  "Måndag",
  "Tisdag",
  "Onsdag",
  "Torsdag",
  "Fredag",
  "Lördag",
] as const;

/**
 * Weekday short names in Swedish
 */
export const WEEKDAY_SHORT_NAMES = [
  "Sön",
  "Mån",
  "Tis",
  "Ons",
  "Tor",
  "Fre",
  "Lör",
] as const;
