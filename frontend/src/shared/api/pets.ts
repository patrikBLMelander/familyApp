import { API_BASE_URL } from "../config";

export type PetResponse = {
  id: string;
  memberId: string;
  year: number;
  month: number;
  selectedEggType: string;
  petType: string;
  name: string | null;
  growthStage: number;
  hatchedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PetHistoryResponse = {
  id: string;
  memberId: string;
  year: number;
  month: number;
  selectedEggType: string;
  petType: string;
  finalGrowthStage: number;
  createdAt: string;
};

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Request failed with status ${response.status}: ${errorText}`);
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

export async function fetchCurrentPet(): Promise<PetResponse> {
  const response = await fetch(`${API_BASE_URL}/pets/current`, {
    headers: getHeaders(),
  });
  return handleJson<PetResponse>(response);
}

export async function selectEgg(eggType: string, name: string | null): Promise<PetResponse> {
  const response = await fetch(`${API_BASE_URL}/pets/select-egg`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ eggType, name }),
  });
  return handleJson<PetResponse>(response);
}

export async function fetchPetHistory(): Promise<PetHistoryResponse[]> {
  const response = await fetch(`${API_BASE_URL}/pets/history`, {
    headers: getHeaders(),
  });
  return handleJson<PetHistoryResponse[]>(response);
}

export async function getAvailableEggTypes(): Promise<string[]> {
  const response = await fetch(`${API_BASE_URL}/pets/available-eggs`, {
    headers: getHeaders(),
  });
  return handleJson<string[]>(response);
}

export async function fetchMemberPet(memberId: string): Promise<PetResponse | null> {
  const response = await fetch(`${API_BASE_URL}/pets/members/${memberId}/current`, {
    headers: getHeaders(),
  });
  if (response.status === 404) {
    return null; // No pet for this member
  }
  return handleJson<PetResponse>(response);
}

export async function fetchMemberPetHistory(memberId: string): Promise<PetHistoryResponse[]> {
  const response = await fetch(`${API_BASE_URL}/pets/members/${memberId}/history`, {
    headers: getHeaders(),
  });
  return handleJson<PetHistoryResponse[]>(response);
}

