import { API_BASE_URL } from "../config";

export type MenstrualCycleEntry = {
  id: string;
  periodStartDate: string;
  periodLength: number | null;
  cycleLength: number | null;
  createdAt: string;
  updatedAt: string;
};

export type CyclePrediction = {
  nextPeriodStart: string;
  nextPeriodEnd: string;
  ovulationDate: string;
  fertileWindowStart: string;
  fertileWindowEnd: string;
  currentCycleDay: number;
  currentPhase: "menstruation" | "follicular" | "ovulation" | "luteal";
};

export type MenstrualCycleSettings = {
  enabled: boolean;
  isPrivate: boolean;
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

export async function getMenstrualCycleEntries(memberId: string): Promise<MenstrualCycleEntry[]> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}/menstrual-cycle/entries`, {
    headers: getHeaders(),
  });
  return handleJson<MenstrualCycleEntry[]>(response);
}

export async function createMenstrualCycleEntry(
  memberId: string,
  periodStartDate: string,
  periodLength?: number
): Promise<MenstrualCycleEntry> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}/menstrual-cycle/entries`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({
      periodStartDate,
      periodLength: periodLength || null,
    }),
  });
  return handleJson<MenstrualCycleEntry>(response);
}

export async function deleteMenstrualCycleEntry(memberId: string, entryId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}/menstrual-cycle/entries/${entryId}`, {
    method: "DELETE",
    headers: getHeaders(),
  });
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export async function getCyclePrediction(memberId: string): Promise<CyclePrediction> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}/menstrual-cycle/prediction`, {
    headers: getHeaders(),
  });
  return handleJson<CyclePrediction>(response);
}

export async function updateMenstrualCycleSettings(
  memberId: string,
  enabled: boolean,
  isPrivate: boolean
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}/menstrual-cycle-settings`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({
      enabled,
      isPrivate,
    }),
  });
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `Request failed with status ${response.status}`);
  }
}
