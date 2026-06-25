import { Injectable } from '@angular/core';
import { Session } from './auth.models';

const KEY = 'linkup.session';

/**
 * Tiny localStorage wrapper holding the current Session.
 *
 * Why this is separate from AuthService: the HTTP interceptor needs the token, but
 * if the interceptor injected AuthService (which injects HttpClient), we'd risk a
 * circular dependency (interceptor → AuthService → HttpClient → interceptor). This
 * dependency-free store breaks that cycle — both AuthService and the interceptor
 * depend on it, not on each other.
 *
 * Security note: localStorage is readable by any JS on the page, so it's vulnerable to
 * XSS token theft. The mitigation is to not have XSS (Angular escapes by default) and
 * keep token TTL short. A hardened variant would use an httpOnly cookie + CSRF token.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  get session(): Session | null {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  }

  get token(): string | null {
    return this.session?.accessToken ?? null;
  }

  save(session: Session): void {
    localStorage.setItem(KEY, JSON.stringify(session));
  }

  clear(): void {
    localStorage.removeItem(KEY);
  }
}
