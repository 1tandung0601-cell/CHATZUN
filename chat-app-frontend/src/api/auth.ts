import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth';
import { apiFetch, clearAuthSession, setAuthSession } from './client';

export async function login(credentials: LoginRequest): Promise<AuthResponse> {
  const result = await apiFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  });

  if (result.success && result.token && result.username) {
    setAuthSession(result.token, result.username);
  }

  return result;
}

export async function register(data: RegisterRequest): Promise<AuthResponse> {
  const result = await apiFetch<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(data),
  });

  return result;
}

export function logout(): void {
  clearAuthSession();
}
