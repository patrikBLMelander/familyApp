import { API_BASE_URL } from "../config";

export type FamilyMemberRole = "CHILD" | "ASSISTANT" | "PARENT";

export type FamilyMemberResponse = {
  id: string;
  name: string;
  deviceToken: string | null;
  email: string | null;
  role: FamilyMemberRole;
  familyId?: string;
  menstrualCycleEnabled?: boolean;
  menstrualCyclePrivate?: boolean;
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

export async function fetchAllFamilyMembers(): Promise<FamilyMemberResponse[]> {
  const response = await fetch(`${API_BASE_URL}/family-members`, {
    headers: getHeaders(),
  });
  return handleJson<FamilyMemberResponse[]>(response);
}

export async function createFamilyMember(name: string, role: FamilyMemberRole): Promise<FamilyMemberResponse> {
  const response = await fetch(`${API_BASE_URL}/family-members`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ name, role }),
  });
  return handleJson<FamilyMemberResponse>(response);
}

export async function updateFamilyMember(
  memberId: string,
  name: string
): Promise<FamilyMemberResponse> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({ name }),
  });
  return handleJson<FamilyMemberResponse>(response);
}

export async function updateFamilyMemberPassword(
  memberId: string,
  password: string
): Promise<FamilyMemberResponse> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}/password`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({ password }),
  });
  return handleJson<FamilyMemberResponse>(response);
}

export async function updateFamilyMemberEmail(
  memberId: string,
  email: string
): Promise<FamilyMemberResponse> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}/email`, {
    method: "PATCH",
    headers: getHeaders(),
    body: JSON.stringify({ email }),
  });
  return handleJson<FamilyMemberResponse>(response);
}

export async function deleteFamilyMember(memberId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}`, {
    method: "DELETE",
    headers: getHeaders(),
  });
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export async function generateInviteToken(memberId: string): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/family-members/${memberId}/generate-invite`, {
    method: "POST",
    headers: getHeaders(),
  });
  const data = await handleJson<{ token: string }>(response);
  return data.token;
}

export async function getMemberByDeviceToken(deviceToken: string): Promise<FamilyMemberResponse> {
  const response = await fetch(`${API_BASE_URL}/family-members/by-device-token/${deviceToken}`, {
    headers: getHeaders(),
  });
  return handleJson<FamilyMemberResponse>(response);
}

export async function linkDeviceByInviteToken(
  inviteToken: string,
  deviceToken: string
): Promise<FamilyMemberResponse> {
  const response = await fetch(`${API_BASE_URL}/family-members/link-device-by-token`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ inviteToken, deviceToken }),
  });
  return handleJson<FamilyMemberResponse>(response);
}

