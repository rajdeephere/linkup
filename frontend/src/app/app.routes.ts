import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

/**
 * Routes are lazy-loaded via loadComponent so each feature is its own bundle — the login
 * screen doesn't ship the chat code and vice-versa. The home route is gated by authGuard.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  { path: '**', redirectTo: '' },
];
