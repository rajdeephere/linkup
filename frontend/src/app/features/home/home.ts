import { Component, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ConversationService } from '../../core/conversations/conversation.service';
import {
  Conversation,
  ConversationType,
  UserSummary,
} from '../../core/conversations/conversation.models';
import { Message } from '../../core/messages/message.models';
import { SocketService } from '../../core/realtime/socket.service';
import { ThemeService } from '../../core/theme/theme.service';
import { Avatar } from '../../shared/avatar';

/**
 * The main app shell: a sidebar (conversations + new-chat + user/theme controls) and a
 * main panel (the selected conversation). Real-time messaging fills the main panel in Day 4;
 * for now it shows the conversation header + a placeholder thread + a disabled composer, so
 * the chat layout is already in place.
 */
@Component({
  selector: 'app-home',
  imports: [FormsModule, Avatar],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private auth = inject(AuthService);
  private convos = inject(ConversationService);
  private router = inject(Router);
  private socket = inject(SocketService);
  readonly theme = inject(ThemeService);

  readonly session = this.auth.session;
  readonly connection = toSignal(this.socket.connection$, { initialValue: 'disconnected' as const });

  conversations = signal<Conversation[]>([]);
  users = signal<UserSummary[]>([]);
  selected = signal<Conversation | null>(null);
  error = signal<string | null>(null);

  // messages bucketed by conversation id; the thread shows the selected one, ordered by seq
  private messagesByConvo = signal<Map<string, Message[]>>(new Map());
  readonly messages = computed(() => {
    const c = this.selected();
    return c ? this.messagesByConvo().get(c.id) ?? [] : [];
  });
  draft = signal('');
  private threadEl = viewChild<ElementRef<HTMLDivElement>>('threadScroll');

  // new-conversation modal state
  showCreate = signal(false);
  type = signal<ConversationType>('DIRECT');
  title = signal('');
  picked = signal<Set<string>>(new Set());
  creating = signal(false);

  readonly canCreate = computed(() => {
    const n = this.picked().size;
    return this.type() === 'DIRECT' ? n === 1 : n >= 1 && this.title().trim().length > 0;
  });

  constructor() {
    this.socket.connect();
    this.socket.messages$.subscribe((m) => this.onMessage(m));
    this.refresh();
    this.convos.listUsers().subscribe({
      next: (u) => this.users.set(u),
      error: () => this.error.set('Could not load users.'),
    });
    // auto-scroll the thread to the bottom whenever the visible messages change
    effect(() => {
      this.messages();
      const el = this.threadEl()?.nativeElement;
      if (el) queueMicrotask(() => (el.scrollTop = el.scrollHeight));
    });
  }

  /** Bucket an inbound message into its conversation, deduped on clientMsgId, ordered by seq. */
  private onMessage(m: Message): void {
    const map = new Map(this.messagesByConvo());
    const list = map.get(m.conversationId) ?? [];
    if (list.some((x) => x.clientMsgId === m.clientMsgId)) return; // dedup the echo (ADR-4)
    map.set(m.conversationId, [...list, m].sort((a, b) => a.seq - b.seq)); // order by seq (ADR-2)
    this.messagesByConvo.set(map);
  }

  sendMessage(): void {
    const c = this.selected();
    const text = this.draft().trim();
    if (!c || !text) return;
    this.socket.send(c.id, text);
    this.draft.set('');
  }

  isMine(m: Message): boolean {
    return m.senderId === this.session()?.userId;
  }

  /** Sender's display name (for group bubbles) from the conversation's participant list. */
  senderName(m: Message): string {
    const p = this.selected()?.participants.find((x) => x.userId === m.senderId);
    return p?.displayName ?? 'Someone';
  }

  refresh(): void {
    this.convos.list().subscribe({
      next: (c) => {
        this.conversations.set(c);
        // keep the selected conversation's reference fresh after a reload
        const sel = this.selected();
        if (sel) this.selected.set(c.find((x) => x.id === sel.id) ?? null);
      },
      error: () => this.error.set('Could not load conversations.'),
    });
  }

  select(c: Conversation): void {
    this.selected.set(c);
  }

  // --- new-conversation modal ---

  openCreate(): void {
    this.type.set('DIRECT');
    this.title.set('');
    this.picked.set(new Set());
    this.error.set(null);
    this.showCreate.set(true);
  }

  closeCreate(): void {
    this.showCreate.set(false);
  }

  setType(t: ConversationType): void {
    this.type.set(t);
    this.picked.set(new Set());
  }

  togglePick(id: string): void {
    const next = new Set(this.picked());
    next.has(id) ? next.delete(id) : next.add(id);
    if (this.type() === 'DIRECT' && next.size > 1) {
      next.clear();
      next.add(id);
    }
    this.picked.set(next);
  }

  create(): void {
    if (!this.canCreate()) return;
    this.creating.set(true);
    this.error.set(null);
    this.convos
      .create({
        type: this.type(),
        title: this.type() === 'GROUP' ? this.title().trim() : undefined,
        memberUserIds: [...this.picked()],
      })
      .subscribe({
        next: (created) => {
          this.creating.set(false);
          this.showCreate.set(false);
          this.refresh();
          this.selected.set(created); // open the new conversation
        },
        error: (err) => {
          this.creating.set(false);
          this.error.set(err?.error?.message ?? 'Could not create conversation.');
        },
      });
  }

  // --- display helpers ---

  /** Direct chats have no title — show the other participant; groups show their title. */
  label(c: Conversation): string {
    if (c.type === 'GROUP') return c.title ?? 'Untitled group';
    return this.other(c)?.displayName ?? 'Direct chat';
  }

  subtitle(c: Conversation): string {
    if (c.type === 'GROUP') return `${c.participants.length} members`;
    const o = this.other(c);
    return o ? `@${o.username}` : '';
  }

  /** Stable color key for a conversation's avatar. */
  avatarKey(c: Conversation): string {
    return c.type === 'GROUP' ? c.id : this.other(c)?.userId ?? c.id;
  }

  /** Comma-separated participant names for the group thread header. */
  joinNames(c: Conversation): string {
    return c.participants.map((p) => p.displayName).join(', ');
  }

  private other(c: Conversation) {
    const me = this.session()?.userId;
    return c.participants.find((p) => p.userId !== me);
  }

  logout(): void {
    this.socket.disconnect();
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
