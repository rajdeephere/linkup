import { Component, computed, input } from '@angular/core';

const PALETTE = [
  '#6366f1', '#ec4899', '#f59e0b', '#10b981',
  '#3b82f6', '#8b5cf6', '#ef4444', '#14b8a6',
];

/**
 * A circular initials avatar with a deterministic color derived from a key (e.g. user id),
 * so the same person is always the same color. Used in the sidebar, thread header, footer.
 */
@Component({
  selector: 'app-avatar',
  standalone: true,
  template: `<span
    class="avatar"
    [style.width.px]="size()"
    [style.height.px]="size()"
    [style.fontSize.px]="size() * 0.4"
    [style.background]="color()"
    >{{ initials() }}</span
  >`,
  styles: [
    `.avatar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      color: #fff;
      font-weight: 600;
      flex: none;
      letter-spacing: 0.02em;
      user-select: none;
    }`,
  ],
})
export class Avatar {
  name = input('');
  /** Stable key for color selection (defaults to name). */
  key = input('');
  size = input(40);

  initials = computed(() => {
    const parts = this.name().trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  });

  color = computed(() => {
    const k = this.key() || this.name();
    let hash = 0;
    for (let i = 0; i < k.length; i++) hash = (hash * 31 + k.charCodeAt(i)) >>> 0;
    return PALETTE[hash % PALETTE.length];
  });
}
