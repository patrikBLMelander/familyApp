/**
 * Available category colors for calendar events.
 * Used in CategoryManager and EventForm.
 */
export const CATEGORY_COLORS = [
  { value: "#b8e6b8", label: "Grön" },
  { value: "#b8d8f0", label: "Blå" },
  { value: "#f5c2d1", label: "Rosa" },
  { value: "#d8c8f0", label: "Lila" },
  { value: "#f5d8a8", label: "Orange" },
  { value: "#ffb8d8", label: "Rosa (ljus)" },
  { value: "#d8b8ff", label: "Lila (ljus)" },
  { value: "#b8d8ff", label: "Blå (ljus)" },
] as const;

/**
 * Maximum number of tasks to show in month view per day.
 * Used to limit display in compact month view.
 */
export const MAX_TASKS_TO_SHOW_IN_MONTH = 2;

/**
 * Maximum number of days to process for recurring all-day events.
 * Safety limit to prevent performance issues with very long events.
 */
export const MAX_RECURRING_DAYS = 365;
