import type { AuthUser, ErrorResponse } from '../types/auth';

const TOKEN_KEY = 'chatzun_token';
const USERNAME_KEY = 'chatzun_username';

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): AuthUser | null {
  const token = localStorage.getItem(TOKEN_KEY);
  const username = localStorage.getItem(USERNAME_KEY);
  if (token && username) {
    return { token, username };
  }
  return null;
}

export function setAuthSession(token: string, username: string): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USERNAME_KEY, username);
}

export function clearAuthSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
}

export const UNAUTHORIZED_EVENT = 'chatzun:unauthorized';

export async function apiFetch<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json');
  }

  const requestToken = getStoredToken();
  const isAuthenticationEndpoint = endpoint.startsWith('/api/auth/');
  if (!isAuthenticationEndpoint && requestToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${requestToken}`);
  }

  let response: Response;
  try {
    response = await fetch(endpoint, {
      ...options,
      headers,
    });
  } catch {
    throw new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng hoặc backend.');
  }

  // Xử lý khi token hết hạn hoặc chưa xác thực (401)
  if (
    response.status === 401 &&
    !isAuthenticationEndpoint &&
    requestToken === getStoredToken()
  ) {
    clearAuthSession();
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
  }

  let data: any = null;
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    try {
      data = await response.json();
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    const errorBody = data as ErrorResponse | null;
    const errorMessage = errorBody?.message || `Lỗi yêu cầu: HTTP ${response.status}`;
    throw new Error(errorMessage);
  }

  return data as T;
}
