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
import { ChatMessage, Message } from '../../core/messages/message.models';
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
  private messagesByConvo = signal<Map<string, ChatMessage[]>>(new Map());
  private localCounter = 0; // monotonic client-side order for pending messages
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

  /**
   * A server message arrived. If it's the echo of one we sent optimistically (same
   * clientMsgId), reconcile that pending bubble → sent (with the real seq/id); otherwise
   * it's a new message from someone else. Deduped on clientMsgId, ordered by seq (ADR-2/4).
   */
  private onMessage(m: Message): void {
    this.mutate(m.conversationId, (list) => {
      const idx = list.findIndex((x) => x.clientMsgId === m.clientMsgId);
      const confirmed: ChatMessage = {
        ...m,
        status: 'sent',
        localSeq: idx >= 0 ? list[idx].localSeq : 0,
      };
      if (idx >= 0) {
        const next = [...list];
        next[idx] = confirmed; // pending → sent (or overwrite a duplicate)
        return next;
      }
      return [...list, confirmed];
    });
  }

  sendMessage(): void {
    const c = this.selected();
    const text = this.draft().trim();
    if (!c || !text) return;

    // Optimistic: render immediately as 'pending' before the server round-trip.
    const clientMsgId = crypto.randomUUID();
    const optimistic: ChatMessage = {
      id: clientMsgId,
      conversationId: c.id,
      senderId: this.session()!.userId,
      clientMsgId,
      seq: 0,
      type: 'TEXT',
      body: text,
      createdAt: new Date().toISOString(),
      status: 'pending',
      localSeq: ++this.localCounter,
    };
    this.mutate(c.id, (list) =>
      list.some((x) => x.clientMsgId === clientMsgId) ? list : [...list, optimistic],
    );
    this.draft.set('');

    // If the socket is down, mark it failed so the user can retry.
    if (!this.socket.send(c.id, text, clientMsgId)) {
      this.setStatus(c.id, clientMsgId, 'failed');
    }
  }

  /** Re-send a message that failed. */
  retry(m: ChatMessage): void {
    const c = this.selected();
    if (!c) return;
    this.setStatus(c.id, m.clientMsgId, 'pending');
    if (!this.socket.send(c.id, m.body, m.clientMsgId)) {
      this.setStatus(c.id, m.clientMsgId, 'failed');
    }
  }

  private setStatus(convoId: string, clientMsgId: string, status: ChatMessage['status']): void {
    this.mutate(convoId, (list) =>
      list.map((x) => (x.clientMsgId === clientMsgId ? { ...x, status } : x)),
    );
  }

  /** Apply an update to a conversation's message list and re-sort (sent by seq, pending last). */
  private mutate(convoId: string, fn: (list: ChatMessage[]) => ChatMessage[]): void {
    const map = new Map(this.messagesByConvo());
    const next = fn(map.get(convoId) ?? []).sort((a, b) => {
      const ap = a.status !== 'sent';
      const bp = b.status !== 'sent';
      if (ap && bp) return a.localSeq - b.localSeq; // both un-acked → client order
      if (ap) return 1; // pending/failed sort after confirmed
      if (bp) return -1;
      return a.seq - b.seq; // confirmed → server seq (ADR-2)
    });
    map.set(convoId, next);
    this.messagesByConvo.set(map);
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
