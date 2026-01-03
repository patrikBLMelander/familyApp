const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";

export type DayOfWeek = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export type DailyTaskResponse = {
  id: string;
  name: string;
  description: string | null;
  daysOfWeek: DayOfWeek[];
  memberIds: string[];
  position: number;
};

export type DailyTaskWithCompletionResponse = {
  task: DailyTaskResponse;
  completed: boolean;
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

export async function fetchTasksForToday(memberId?: string): Promise<DailyTaskWithCompletionResponse[]> {
  const url = memberId 
    ? `${API_BASE_URL}/daily-tasks/today?memberId=${memberId}`
    : `${API_BASE_URL}/daily-tasks/today`;
  const response = await fetch(url, { headers: getHeaders() });
  return handleJson<DailyTaskWithCompletionResponse[]>(response);
}

export async function fetchAllDailyTasks(): Promise<DailyTaskResponse[]> {
  const response = await fetch(`${API_BASE_URL}/daily-tasks`, {
    headers: getHeaders()
  });
  return handleJson<DailyTaskResponse[]>(response);
}

export async function createDailyTask(
  name: string,
  description: string | null,
  daysOfWeek: DayOfWeek[],
  memberIds?: string[]
): Promise<DailyTaskResponse> {
  const response = await fetch(`${API_BASE_URL}/daily-tasks`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ name, description, daysOfWeek, memberIds: memberIds || [] })
  });
  return handleJson<DailyTaskResponse>(response);
}

export async function updateDailyTask(
  taskId: string,
  name: string,
  description: string | null,
  daysOfWeek: DayOfWeek[],
  memberIds?: string[]
): Promise<DailyTaskResponse> {
  const response = await fetch(`${API_BASE_URL}/daily-tasks/${taskId}`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({ name, description, daysOfWeek, memberIds: memberIds || [] })
  });
  return handleJson<DailyTaskResponse>(response);
}

export async function deleteDailyTask(taskId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/daily-tasks/${taskId}`, {
    method: "DELETE",
    headers: getHeaders()
  });
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export type ChildCompletionResponse = {
  childId: string;
  childName: string;
  completed: boolean;
};

export type DailyTaskWithChildrenCompletionResponse = {
  task: DailyTaskResponse;
  childCompletions: ChildCompletionResponse[];
};

export async function toggleTaskCompletion(taskId: string, memberId?: string): Promise<void> {
  const url = memberId
    ? `${API_BASE_URL}/daily-tasks/${taskId}/toggle?memberId=${memberId}`
    : `${API_BASE_URL}/daily-tasks/${taskId}/toggle`;
  const response = await fetch(url, {
    method: "POST",
    headers: getHeaders()
  });
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export async function fetchTasksForTodayWithChildren(): Promise<DailyTaskWithChildrenCompletionResponse[]> {
  const response = await fetch(`${API_BASE_URL}/daily-tasks/today/with-children`, {
    headers: getHeaders()
  });
  return handleJson<DailyTaskWithChildrenCompletionResponse[]>(response);
}

export async function reorderDailyTasks(taskIds: string[]): Promise<DailyTaskResponse[]> {
  const response = await fetch(`${API_BASE_URL}/daily-tasks/reorder`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ taskIds })
  });
  return handleJson<DailyTaskResponse[]>(response);
}

