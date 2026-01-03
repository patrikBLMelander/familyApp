import { API_BASE_URL } from "../config";

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
  return (await response.json()) as T;
}

export type FamilyResponse = {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
};

export type FamilyMemberResponse = {
  id: string;
  name: string;
  deviceToken: string | null;
  email: string | null;
  role: "CHILD" | "PARENT";
};

export type FamilyRegistrationResponse = {
  family: FamilyResponse;
  admin: FamilyMemberResponse;
  deviceToken: string;
};

export type EmailLoginResponse = {
  member: FamilyMemberResponse;
  deviceToken: string;
};

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

export async function registerFamily(
  familyName: string,
  adminName: string,
  adminEmail: string
): Promise<FamilyRegistrationResponse> {
  const response = await fetch(`${API_BASE_URL}/families/register`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ familyName, adminName, adminEmail }),
  });
  return handleJson<FamilyRegistrationResponse>(response);
}

export async function getFamily(familyId: string): Promise<FamilyResponse> {
  const response = await fetch(`${API_BASE_URL}/families/${familyId}`, {
    headers: getHeaders(),
  });
  return handleJson<FamilyResponse>(response);
}

export async function updateFamilyName(familyId: string, name: string): Promise<FamilyResponse> {
  const response = await fetch(`${API_BASE_URL}/families/${familyId}/name`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({ name }),
  });
  return handleJson<FamilyResponse>(response);
}

export async function loginByEmail(email: string): Promise<EmailLoginResponse> {
  const response = await fetch(`${API_BASE_URL}/families/login-by-email`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ email }),
  });
  return handleJson<EmailLoginResponse>(response);
}

