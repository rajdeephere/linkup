import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Login + register screen. One form toggles between the two modes.
 *
 * Reactive forms (not template-driven) because the form model lives in TypeScript where
 * it's testable and the validators are explicit — the enterprise-standard Angular choice.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  mode = signal<'login' | 'register'>('login');
  error = signal<string | null>(null);
  loading = signal(false);

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    displayName: [''],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  toggleMode(): void {
    this.mode.set(this.mode() === 'login' ? 'register' : 'login');
    this.error.set(null);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const { username, displayName, password } = this.form.getRawValue();
    const request$ =
      this.mode() === 'login'
        ? this.auth.login({ username, password, platform: 'WEB' })
        : this.auth.register({ username, displayName, password, platform: 'WEB' });

    request$.subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Something went wrong. Try again.');
      },
    });
  }
}
