import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, MeResponse, RegisterRequest, Session } from './auth.models';
import { TokenStorage } from './token-storage';

/**
 * Owns the auth lifecycle: register, login, logout, and the reactive "current session"
 * state the rest of the app reads.
 *
 * Why signals for session state: components can read `session()` / `isAuthenticated()`
 * reactively without manual subscriptions, and the value survives a refresh because it's
 * hydrated from TokenStorage on construction.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private storage = inject(TokenStorage);

  private readonly _session = signal<Session | null>(this.storage.session);
  readonly session = this._session.asReadonly();
  readonly isAuthenticated = computed(() => this._session() !== null);

  register(req: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/v1/auth/register`, req)
      .pipe(tap((res) => this.persist(res)));
  }

  login(req: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/v1/auth/login`, req)
      .pipe(tap((res) => this.persist(res)));
  }

  /** Proves the token round-trips: protected GET that returns the caller's profile. */
  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${environment.apiBaseUrl}/v1/users/me`);
  }

  logout(): void {
    this.storage.clear();
    this._session.set(null);
  }

  private persist(res: AuthResponse): void {
    const session: Session = {
      accessToken: res.accessToken,
      userId: res.userId,
      username: res.username,
      displayName: res.displayName,
      deviceId: res.deviceId,
    };
    this.storage.save(session);
    this._session.set(session);
  }
}
