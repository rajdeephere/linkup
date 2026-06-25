import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { TokenStorage } from './token-storage';

/**
 * Functional HTTP interceptor (Angular's modern style). It attaches
 * "Authorization: Bearer <token>" to every API call, so individual services never
 * hand-build auth headers — cross-cutting concern handled in exactly one place.
 *
 * It only adds the header for calls to our own API origin, so a token never leaks to a
 * third-party host (e.g. an S3 presigned URL in Phase 3).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(TokenStorage).token;
  const isApiCall = req.url.startsWith(environment.apiBaseUrl);

  if (token && isApiCall) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(req);
};
