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
    throw new Error(`Request failed with status ${response.status}`);
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

export async function fetchCalendarEvents(
  startDate?: Date,
  endDate?: Date
): Promise<CalendarEventResponse[]> {
  let url = `${API_BASE_URL}/calendar/events`;
  const params = new URLSearchParams();
  
  if (startDate) {
    params.append("startDate", startDate.toISOString());
  }
  if (endDate) {
    params.append("endDate", endDate.toISOString());
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
  recurringEndCount?: number | null
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
  recurringEndCount?: number | null
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

