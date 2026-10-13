export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  success: boolean;
  message: string;
  username?: string;
  token?: string | null;
}

export interface ErrorResponse {
  success: boolean;
  message: string;
}

export interface AuthUser {
  username: string;
  token: string;
}
