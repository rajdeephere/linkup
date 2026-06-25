import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorage } from './token-storage';

/**
 * Route guard (functional CanActivateFn): blocks protected routes when there's no
 * token and redirects to /login. This is a UX gate, not a security boundary — the real
 * enforcement is the backend rejecting tokenless requests (the client can be bypassed).
 */
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  if (inject(TokenStorage).token) {
    return true;
  }
  return router.parseUrl('/login');
};
