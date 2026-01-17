import { API_BASE_URL } from "../config";

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let errorMessage = `Request failed with status ${response.status}`;
    let errorCode: string | null = null;
    
    try {
      const errorText = await response.text();
      // Try to parse as JSON to get error message
      try {
        const errorJson = JSON.parse(errorText);
        if (errorJson.message) {
          errorMessage = errorJson.message;
        } else if (typeof errorJson === 'string') {
          errorMessage = errorJson;
        }
        if (errorJson.code) {
          errorCode = errorJson.code;
        }
      } catch {
        // If not JSON, use the text as is if it's not empty
        if (errorText && errorText.trim()) {
          errorMessage = errorText;
        }
      }
    } catch {
      // If we can't read the error, use the default message
    }
    
    // Create error with both message and code
    const error = new Error(errorMessage) as Error & { code?: string };
    if (errorCode) {
      error.code = errorCode;
    }
    throw error;
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
  adminEmail: string,
  password: string
): Promise<FamilyRegistrationResponse> {
  const response = await fetch(`${API_BASE_URL}/families/register`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ familyName, adminName, adminEmail, password }),
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

export async function loginByEmail(email: string, password: string): Promise<EmailLoginResponse> {
  const response = await fetch(`${API_BASE_URL}/families/login-by-email`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({ email, password }),
  });
  return handleJson<EmailLoginResponse>(response);
}

