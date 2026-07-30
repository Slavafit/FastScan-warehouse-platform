export type Role = "ADMIN" | "WORKER" | "CLIENT";

export type Session = {
  accessToken: string;
  refreshToken: string;
  role: Role;
};

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  session?: Session | null,
): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(session?.accessToken
        ? { Authorization: `Bearer ${session.accessToken}` }
        : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || `API error ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function login(
  mode: "warehouse" | "client",
  loginValue: string,
  password: string,
) {
  return apiRequest<{
    success: boolean;
    accessToken: string;
    refreshToken: string;
    role: Role;
  }>(`/api/${mode === "client" ? "client" : "admin"}/auth/login`, {
    method: "POST",
    body: JSON.stringify({ login: loginValue, password }),
  });
}
