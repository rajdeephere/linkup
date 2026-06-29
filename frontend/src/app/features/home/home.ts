import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ConversationService } from '../../core/conversations/conversation.service';
import {
  Conversation,
  ConversationType,
  UserSummary,
} from '../../core/conversations/conversation.models';

/**
 * Day-2 home: the conversation list + a "new conversation" panel.
 *
 * This is the conversation-list view from the roadmap — it proves the REST layer
 * (create direct/group, list mine, membership) end to end. The real-time chat thread
 * grows on top of this in Day 3+.
 */
@Component({
  selector: 'app-home',
  imports: [FormsModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private auth = inject(AuthService);
  private convos = inject(ConversationService);
  private router = inject(Router);

  readonly session = this.auth.session;

  conversations = signal<Conversation[]>([]);
  users = signal<UserSummary[]>([]);
  error = signal<string | null>(null);

  // "new conversation" form state
  type = signal<ConversationType>('DIRECT');
  title = signal('');
  selected = signal<Set<string>>(new Set());
  creating = signal(false);

  readonly canCreate = computed(() => {
    const n = this.selected().size;
    if (this.type() === 'DIRECT') return n === 1;
    return n >= 1 && this.title().trim().length > 0;
  });

  constructor() {
    this.refresh();
    this.convos.listUsers().subscribe({
      next: (u) => this.users.set(u),
      error: () => this.error.set('Could not load users.'),
    });
  }

  refresh(): void {
    this.convos.list().subscribe({
      next: (c) => this.conversations.set(c),
      error: () => this.error.set('Could not load conversations.'),
    });
  }

  toggleUser(id: string): void {
    const next = new Set(this.selected());
    next.has(id) ? next.delete(id) : next.add(id);
    // a direct chat has exactly one other member — keep only the latest pick
    if (this.type() === 'DIRECT' && next.size > 1) {
      next.clear();
      next.add(id);
    }
    this.selected.set(next);
  }

  setType(t: ConversationType): void {
    this.type.set(t);
    this.selected.set(new Set()); // reset picks when switching mode
  }

  create(): void {
    if (!this.canCreate()) return;
    this.creating.set(true);
    this.error.set(null);
    this.convos
      .create({
        type: this.type(),
        title: this.type() === 'GROUP' ? this.title().trim() : undefined,
        memberUserIds: [...this.selected()],
      })
      .subscribe({
        next: () => {
          this.title.set('');
          this.selected.set(new Set());
          this.creating.set(false);
          this.refresh();
        },
        error: (err) => {
          this.creating.set(false);
          this.error.set(err?.error?.message ?? 'Could not create conversation.');
        },
      });
  }

  /** Direct chats have no title — show the other participant; groups show their title. */
  label(c: Conversation): string {
    if (c.type === 'GROUP') return c.title ?? 'Untitled group';
    const me = this.session()?.userId;
    const other = c.participants.find((p) => p.userId !== me);
    return other?.displayName ?? 'Direct chat';
  }

  subtitle(c: Conversation): string {
    if (c.type === 'GROUP') return `${c.participants.length} members`;
    return c.participants.map((p) => p.displayName).join(', ');
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
