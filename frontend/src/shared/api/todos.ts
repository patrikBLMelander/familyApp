const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";

type TodoItemResponse = {
  id: string;
  description: string;
  done: boolean;
  position: number;
};

type TodoListResponse = {
  id: string;
  name: string;
  position: number;
  color: string;
  isPrivate: boolean;
  items: TodoItemResponse[];
};

function getHeaders(): HeadersInit {
  const headers: HeadersInit = { "Content-Type": "application/json" };
  const deviceToken = localStorage.getItem("deviceToken");
  if (deviceToken) {
    headers["X-Device-Token"] = deviceToken;
  }
  return headers;
}

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function fetchTodoLists(): Promise<TodoListResponse[]> {
  const response = await fetch(`${API_BASE_URL}/todo-lists`, {
    headers: getHeaders()
  });
  return handleJson<TodoListResponse[]>(response);
}

export async function createTodoList(name: string, isPrivate: boolean = false): Promise<TodoListResponse> {
  const response = await fetch(`${API_BASE_URL}/todo-lists`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ name, isPrivate })
  });
  return handleJson<TodoListResponse>(response);
}

export async function addTodoItem(listId: string, description: string): Promise<TodoListResponse> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/${listId}/items`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ description })
  });
  return handleJson<TodoListResponse>(response);
}

export async function toggleTodoItem(listId: string, itemId: string): Promise<TodoListResponse> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/${listId}/items/${itemId}/toggle`, {
    method: "PATCH",
    headers: getHeaders()
  });
  return handleJson<TodoListResponse>(response);
}

export async function clearDoneItems(listId: string): Promise<TodoListResponse> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/${listId}/items/done`, {
    method: "DELETE",
    headers: getHeaders()
  });
  return handleJson<TodoListResponse>(response);
}

export async function deleteTodoItem(listId: string, itemId: string): Promise<TodoListResponse> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/${listId}/items/${itemId}`, {
    method: "DELETE",
    headers: getHeaders()
  });
  return handleJson<TodoListResponse>(response);
}

export async function reorderTodoItems(listId: string, itemIds: string[]): Promise<TodoListResponse> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/${listId}/items/reorder`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ itemIds })
  });
  return handleJson<TodoListResponse>(response);
}

export async function reorderTodoLists(listIds: string[]): Promise<TodoListResponse[]> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/reorder`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ listIds })
  });
  return handleJson<TodoListResponse[]>(response);
}

export async function deleteTodoList(listId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/${listId}`, {
    method: "DELETE",
    headers: getHeaders()
  });
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export async function updateTodoListName(listId: string, name: string): Promise<TodoListResponse> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/${listId}`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({ name })
  });
  return handleJson<TodoListResponse>(response);
}

export async function updateTodoListColor(listId: string, color: string): Promise<TodoListResponse> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/${listId}/color`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({ color })
  });
  return handleJson<TodoListResponse>(response);
}

export async function updateTodoListPrivacy(listId: string, isPrivate: boolean): Promise<TodoListResponse> {
  const response = await fetch(`${API_BASE_URL}/todo-lists/${listId}/privacy`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({ isPrivate })
  });
  return handleJson<TodoListResponse>(response);
}

export const TODO_COLORS = [
  { value: "green", label: "Grön", gradient: "linear-gradient(135deg, #b8e6b8 0%, #a8d8a8 100%)", border: "#a8d8a8" },
  { value: "blue", label: "Blå", gradient: "linear-gradient(135deg, #b8d8f0 0%, #a8c8e0 100%)", border: "#a8c8e0" },
  { value: "pink", label: "Rosa", gradient: "linear-gradient(135deg, #f5c2d1 0%, #e5b2c1 100%)", border: "#e5b2c1" },
  { value: "purple", label: "Lila", gradient: "linear-gradient(135deg, #d8c8f0 0%, #c8b8e0 100%)", border: "#c8b8e0" },
  { value: "orange", label: "Orange", gradient: "linear-gradient(135deg, #f5d8a8 0%, #e5c898 100%)", border: "#e5c898" }
] as const;


