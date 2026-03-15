import { API_BASE_URL } from "../config";

export type DailyChoreResponse = {
  id: string;
  memberId: string;
  title: string;
  weekdays: string[]; // e.g. ["MON", "TUE", "WED", "THU", "FRI"]
  xpPoints: number;
  isActive: boolean;
  createdAt: string;
};

export type DailyChoreWithCompletionResponse = {
  chore: DailyChoreResponse;
  completed: boolean;
  completionId: string | null;
};

export type DailyChoreCompletionResponse = {
  id: string;
  choreId: string;
  memberId: string;
  occurrenceDate: string;
  completedAt: string;
};

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Request failed with status ${response.status}: ${errorText}`);
  }
  return (await response.json()) as T;
}

function getHeaders(): HeadersInit {
  const headers: HeadersInit = { "Content-Type": "application/json" };
  const deviceToken = localStorage.getItem("deviceToken");
  if (deviceToken) headers["X-Device-Token"] = deviceToken;
  return headers;
}

export async function getDailyChoresForMember(memberId: string): Promise<DailyChoreResponse[]> {
  const response = await fetch(`${API_BASE_URL}/daily-chores/members/${memberId}`, {
    headers: getHeaders(),
  });
  return handleJson<DailyChoreResponse[]>(response);
}

export async function getDailyChoresForDate(
  memberId: string,
  date: string // YYYY-MM-DD
): Promise<DailyChoreWithCompletionResponse[]> {
  const response = await fetch(
    `${API_BASE_URL}/daily-chores/members/${memberId}/for-date?date=${date}`,
    { headers: getHeaders() }
  );
  return handleJson<DailyChoreWithCompletionResponse[]>(response);
}

export async function createDailyChore(
  memberId: string,
  title: string,
  weekdays: string[],
  xpPoints: number
): Promise<DailyChoreResponse> {
  const response = await fetch(`${API_BASE_URL}/daily-chores`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ memberId, title, weekdays, xpPoints }),
  });
  return handleJson<DailyChoreResponse>(response);
}

export async function deleteDailyChore(choreId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/daily-chores/${choreId}`, {
    method: "DELETE",
    headers: getHeaders(),
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Request failed with status ${response.status}: ${errorText}`);
  }
}

export async function markDailyChoreCompleted(
  choreId: string,
  date: string // YYYY-MM-DD
): Promise<DailyChoreCompletionResponse> {
  const response = await fetch(`${API_BASE_URL}/daily-chores/${choreId}/completion`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ date }),
  });
  return handleJson<DailyChoreCompletionResponse>(response);
}

export async function unmarkDailyChoreCompleted(
  choreId: string,
  date: string // YYYY-MM-DD
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/daily-chores/${choreId}/completion?date=${date}`,
    { method: "DELETE", headers: getHeaders() }
  );
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Request failed with status ${response.status}: ${errorText}`);
  }
}
