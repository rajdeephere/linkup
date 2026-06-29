import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';
const KEY = 'linkup.theme';

/**
 * App theme (light/dark). Persists to localStorage and applies `data-theme` on <html>,
 * which the CSS variables in styles.scss key off. Initial value: stored choice, else the
 * OS preference.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.initial());

  constructor() {
    this.apply(this.theme());
  }

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    localStorage.setItem(KEY, next);
    this.apply(next);
  }

  private initial(): Theme {
    const saved = localStorage.getItem(KEY) as Theme | null;
    if (saved === 'light' || saved === 'dark') return saved;
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private apply(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }
}
