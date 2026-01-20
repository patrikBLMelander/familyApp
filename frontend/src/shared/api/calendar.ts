import { API_BASE_URL } from "../config";

export type RecurringType = "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";

export type CalendarEventResponse = {
  id: string;
  familyId: string;
  categoryId: string | null;
  title: string;
  description: string | null;
  startDateTime: string;
  endDateTime: string | null;
  isAllDay: boolean;
  location: string | null;
  createdById: string;
  recurringType: RecurringType | null;
  recurringInterval: number | null;
  recurringEndDate: string | null;
  recurringEndCount: number | null;
  isTask: boolean;
  xpPoints: number | null;
  isRequired: boolean;
  createdAt: string;
  updatedAt: string;
  participantIds: string[];
};

export type CalendarEventCategoryResponse = {
  id: string;
  familyId: string;
  name: string;
  color: string;
  createdAt: string;
  updatedAt: string;
};

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let errorMessage = `Request failed with status ${response.status}`;
    try {
      const errorData = await response.json();
      if (errorData.message) {
        errorMessage = errorData.message;
      } else if (typeof errorData === 'string') {
        errorMessage = errorData;
      }
    } catch {
      // If response is not JSON, use status text
      errorMessage = response.statusText || errorMessage;
    }
    throw new Error(errorMessage);
  }
  return (await response.json()) as T;
}

function getHeaders(): HeadersInit {
  const headers: HeadersInit = {
    "Content-Type": "application/json",
  };
  const deviceToken = localStorage.getItem("deviceToken");
  if (deviceToken) {
    headers["X-Device-Token"] = deviceToken;
  }
  return headers;
}

// Helper function to format Date to yyyy-MM-dd'T'HH:mm format (local time, no timezone)
function formatLocalDateTime(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

// Helper function to format Date to yyyy-MM-dd format (local time)
function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export async function fetchCalendarEvents(
  startDate?: Date,
  endDate?: Date
): Promise<CalendarEventResponse[]> {
  let url = `${API_BASE_URL}/calendar/events`;
  const params = new URLSearchParams();
  
  if (startDate) {
    params.append("startDate", formatLocalDateTime(startDate));
  }
  if (endDate) {
    params.append("endDate", formatLocalDateTime(endDate));
  }
  
  if (params.toString()) {
    url += `?${params.toString()}`;
  }
  
  const response = await fetch(url, { headers: getHeaders() });
  return handleJson<CalendarEventResponse[]>(response);
}

export async function createCalendarEvent(
  title: string,
  startDateTime: string, // Changed from Date to string (YYYY-MM-DDTHH:mm format)
  endDateTime: string | null, // Changed from Date to string
  isAllDay: boolean,
  description?: string,
  categoryId?: string,
  location?: string,
  participantIds?: string[],
  recurringType?: RecurringType | null,
  recurringInterval?: number | null,
  recurringEndDate?: string | null, // Changed from Date to string (YYYY-MM-DD format)
  recurringEndCount?: number | null,
  isTask?: boolean,
  xpPoints?: number | null,
  isRequired?: boolean
): Promise<CalendarEventResponse> {
  const response = await fetch(`${API_BASE_URL}/calendar/events`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({
      title,
      description: description || null,
      startDateTime: startDateTime, // Send directly without timezone conversion
      endDateTime: endDateTime || null,
      isAllDay,
      location: location || null,
      categoryId: categoryId || null,
      participantIds: participantIds || [],
      recurringType: recurringType || null,
      recurringInterval: recurringInterval || null,
      recurringEndDate: recurringEndDate || null,
      recurringEndCount: recurringEndCount || null,
      isTask: isTask || false,
      xpPoints: xpPoints !== undefined ? xpPoints : null,
      isRequired: isRequired !== undefined ? isRequired : true,
    }),
  });
  return handleJson<CalendarEventResponse>(response);
}

export async function updateCalendarEvent(
  eventId: string,
  title: string,
  startDateTime: string, // Changed from Date to string (YYYY-MM-DDTHH:mm format)
  endDateTime: string | null, // Changed from Date to string
  isAllDay: boolean,
  description?: string,
  categoryId?: string,
  location?: string,
  participantIds?: string[],
  recurringType?: RecurringType | null,
  recurringInterval?: number | null,
  recurringEndDate?: string | null, // Changed from Date to string (YYYY-MM-DD format)
  recurringEndCount?: number | null,
  isTask?: boolean,
  xpPoints?: number | null,
  isRequired?: boolean
): Promise<CalendarEventResponse> {
  const response = await fetch(`${API_BASE_URL}/calendar/events/${eventId}`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({
      title,
      description: description || null,
      startDateTime: startDateTime, // Send directly without timezone conversion
      endDateTime: endDateTime || null,
      isAllDay,
      location: location || null,
      categoryId: categoryId || null,
      participantIds: participantIds || [],
      recurringType: recurringType || null,
      recurringInterval: recurringInterval || null,
      recurringEndDate: recurringEndDate || null,
      recurringEndCount: recurringEndCount || null,
      isTask: isTask !== undefined ? isTask : false,
      xpPoints: xpPoints !== undefined ? xpPoints : null,
      isRequired: isRequired !== undefined ? isRequired : true,
    }),
  });
  return handleJson<CalendarEventResponse>(response);
}

export async function deleteCalendarEvent(eventId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/calendar/events/${eventId}`, {
    method: "DELETE",
    headers: getHeaders(),
  });
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export async function fetchCalendarCategories(): Promise<CalendarEventCategoryResponse[]> {
  const response = await fetch(`${API_BASE_URL}/calendar/categories`, {
    headers: getHeaders(),
  });
  return handleJson<CalendarEventCategoryResponse[]>(response);
}

export async function createCalendarCategory(
  name: string,
  color: string
): Promise<CalendarEventCategoryResponse> {
  const response = await fetch(`${API_BASE_URL}/calendar/categories`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({
      name,
      color,
    }),
  });
  return handleJson<CalendarEventCategoryResponse>(response);
}

export async function updateCalendarCategory(
  categoryId: string,
  name: string,
  color: string
): Promise<CalendarEventCategoryResponse> {
  const response = await fetch(`${API_BASE_URL}/calendar/categories/${categoryId}`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({
      name,
      color,
    }),
  });
  return handleJson<CalendarEventCategoryResponse>(response);
}

export async function deleteCalendarCategory(categoryId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/calendar/categories/${categoryId}`, {
    method: "DELETE",
    headers: getHeaders(),
  });
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

// Task Completion Types and Functions

export type CalendarEventTaskCompletionResponse = {
  id: string;
  eventId: string;
  memberId: string;
  occurrenceDate: string; // YYYY-MM-DD format
  completedAt: string;
};

export async function markTaskCompleted(
  eventId: string,
  memberId: string,
  occurrenceDate: string // YYYY-MM-DD format
): Promise<CalendarEventTaskCompletionResponse> {
  const response = await fetch(`${API_BASE_URL}/calendar/events/${eventId}/task-completion`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({
      memberId,
      occurrenceDate,
    }),
  });
  return handleJson<CalendarEventTaskCompletionResponse>(response);
}

export async function unmarkTaskCompleted(
  eventId: string,
  memberId: string,
  occurrenceDate: string // YYYY-MM-DD format
): Promise<void> {
  const params = new URLSearchParams({
    memberId,
    occurrenceDate,
  });
  const response = await fetch(
    `${API_BASE_URL}/calendar/events/${eventId}/task-completion?${params.toString()}`,
    {
      method: "DELETE",
      headers: getHeaders(),
    }
  );
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export async function getTaskCompletions(
  eventId: string
): Promise<CalendarEventTaskCompletionResponse[]> {
  const response = await fetch(`${API_BASE_URL}/calendar/events/${eventId}/task-completion`, {
    headers: getHeaders(),
  });
  return handleJson<CalendarEventTaskCompletionResponse[]>(response);
}

export async function getTaskCompletionsForMember(
  memberId: string
): Promise<CalendarEventTaskCompletionResponse[]> {
  const response = await fetch(`${API_BASE_URL}/calendar/members/${memberId}/task-completions`, {
    headers: getHeaders(),
  });
  return handleJson<CalendarEventTaskCompletionResponse[]>(response);
}

// Helper type for tasks with completion status (similar to DailyTaskWithCompletionResponse)
export type CalendarTaskWithCompletionResponse = {
  event: CalendarEventResponse;
  completed: boolean;
};

/**
 * Get tasks for a specific date for a specific member.
 * Returns calendar events that are tasks (isTask=true) and where the member is a participant.
 */
export async function fetchTasksForDate(memberId: string, date: Date): Promise<CalendarTaskWithCompletionResponse[]> {
  const dateStart = new Date(date);
  dateStart.setHours(0, 0, 0, 0);
  const dateEnd = new Date(date);
  dateEnd.setHours(23, 59, 59, 999);

  // Fetch events for the date
  const events = await fetchCalendarEvents(dateStart, dateEnd);
  
  // Filter to only tasks where member is a participant
  const taskEvents = events.filter(event => 
    event.isTask && event.participantIds.includes(memberId)
  );

  // Get completions for this member
  const completions = await getTaskCompletionsForMember(memberId);
  const dateStr = formatLocalDate(date); // YYYY-MM-DD
  
  // Create map of eventId -> completion for the date
  const completionMap = new Map<string, CalendarEventTaskCompletionResponse>();
  completions.forEach(completion => {
    if (completion.occurrenceDate === dateStr) {
      completionMap.set(completion.eventId, completion);
    }
  });

  // Map events to tasks with completion status
  return taskEvents.map(event => ({
    event,
    completed: completionMap.has(event.id),
  }));
}

/**
 * Get tasks for today for a specific member.
 * Returns calendar events that are tasks (isTask=true) and where the member is a participant.
 */
export async function fetchTasksForToday(memberId: string): Promise<CalendarTaskWithCompletionResponse[]> {
  return fetchTasksForDate(memberId, new Date());
}

/**
 * Mark a task as completed for a member for a specific date.
 */
export async function toggleTaskCompletion(eventId: string, memberId: string, date?: Date): Promise<void> {
  const targetDate = date || new Date();
  const dateStr = formatLocalDate(targetDate); // YYYY-MM-DD
  
  // Check if already completed
  const completions = await getTaskCompletions(eventId);
  const existingCompletion = completions.find(
    c => c.memberId === memberId && c.occurrenceDate === dateStr
  );

  if (existingCompletion) {
    // Unmark as completed
    await unmarkTaskCompleted(eventId, memberId, dateStr);
  } else {
    // Mark as completed
    await markTaskCompleted(eventId, memberId, dateStr);
  }
}
