import { JsonPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { MeResponse } from '../../core/auth/auth.models';

/**
 * Authenticated landing page. Its job on Day 1 is to PROVE the full auth loop end to end:
 * it calls the protected GET /v1/users/me using the stored token (attached by the
 * interceptor) and renders the result. This is the conversation-list shell that grows
 * into the real chat UI in later days.
 */
@Component({
  selector: 'app-home',
  imports: [JsonPipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private auth = inject(AuthService);
  private router = inject(Router);

  readonly session = this.auth.session;
  me = signal<MeResponse | null>(null);
  error = signal<string | null>(null);

  constructor() {
    // Round-trips the token: only succeeds if the interceptor attached a valid JWT.
    this.auth.me().subscribe({
      next: (m) => this.me.set(m),
      error: () => this.error.set('Could not load profile (token invalid or expired).'),
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
