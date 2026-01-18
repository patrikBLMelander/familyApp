/**
 * Data structure for event form submission.
 * Used when creating or updating calendar events.
 */
export type EventFormData = {
  title: string;
  startDateTime: string; // ISO format: YYYY-MM-DDTHH:mm
  endDateTime: string | null; // ISO format: YYYY-MM-DDTHH:mm or null
  isAllDay: boolean;
  description?: string;
  categoryId?: string;
  location?: string;
  participantIds?: string[];
  recurringType?: "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY" | null;
  recurringInterval?: number | null;
  recurringEndDate?: string | null; // ISO date format: YYYY-MM-DD
  recurringEndCount?: number | null;
  isTask?: boolean;
  xpPoints?: number | null;
  isRequired?: boolean;
};
