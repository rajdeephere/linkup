/** Wire contracts — mirror the backend DTOs exactly so the compiler catches drift. */

export type Platform = 'WEB' | 'IOS' | 'ANDROID';

export interface RegisterRequest {
  username: string;
  displayName: string;
  password: string;
  platform: Platform;
}

export interface LoginRequest {
  username: string;
  password: string;
  platform: Platform;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  username: string;
  displayName: string;
  deviceId: string;
}

export interface MeResponse {
  id: string;
  username: string;
  displayName: string;
  status: string;
  createdAt: string;
}

/** What we persist locally to keep the user "logged in" across refreshes. */
export interface Session {
  accessToken: string;
  userId: string;
  username: string;
  displayName: string;
  deviceId: string;
}
