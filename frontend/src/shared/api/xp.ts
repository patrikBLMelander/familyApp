import { API_BASE_URL } from "../config";

export type XpProgressResponse = {
  id: string;
  memberId: string;
  year: number;
  month: number;
  currentXp: number;
  currentLevel: number;
  totalTasksCompleted: number;
  xpForNextLevel: number;
  xpInCurrentLevel: number;
};

export type XpHistoryResponse = {
  id: string;
  memberId: string;
  year: number;
  month: number;
  finalXp: number;
  finalLevel: number;
  totalTasksCompleted: number;
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

export async function fetchCurrentXpProgress(): Promise<XpProgressResponse | null> {
  const response = await fetch(`${API_BASE_URL}/xp/current`, {
    headers: getHeaders()
  });
  if (response.status === 404) {
    return null; // No XP progress for member
  }
  return handleJson<XpProgressResponse>(response);
}

export async function fetchXpHistory(): Promise<XpHistoryResponse[]> {
  const response = await fetch(`${API_BASE_URL}/xp/history`, {
    headers: getHeaders()
  });
  return handleJson<XpHistoryResponse[]>(response);
}

export async function fetchMemberXpProgress(memberId: string): Promise<XpProgressResponse | null> {
  const response = await fetch(`${API_BASE_URL}/xp/members/${memberId}/current`, {
    headers: getHeaders()
  });
  if (response.status === 404 || response.status === 204) {
    return null; // No progress for this member
  }
  if (!response.ok) {
    throw new Error(`Failed to fetch XP progress: ${response.statusText}`);
  }
  const text = await response.text();
  if (!text || text.trim() === '' || text === 'null') {
    return null; // Empty or null response
  }
  return JSON.parse(text) as XpProgressResponse;
}

export async function fetchMemberXpHistory(memberId: string): Promise<XpHistoryResponse[]> {
  const response = await fetch(`${API_BASE_URL}/xp/members/${memberId}/history`, {
    headers: getHeaders()
  });
  return handleJson<XpHistoryResponse[]>(response);
}

export async function awardBonusXp(memberId: string, xpPoints: number): Promise<XpProgressResponse> {
  const response = await fetch(`${API_BASE_URL}/xp/members/${memberId}/bonus`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ xpPoints })
  });
  return handleJson<XpProgressResponse>(response);
}

