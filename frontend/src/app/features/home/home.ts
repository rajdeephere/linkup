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
import { ChatMessage, Message, MessageType } from '../../core/messages/message.models';
import { MessageService } from '../../core/messages/message.service';
import { MediaService } from '../../core/media/media.service';
import { PushService } from '../../core/push/push.service';
import {
  PresenceEvent,
  SocketService,
  TypingEvent,
} from '../../core/realtime/socket.service';
import { ThemeService } from '../../core/theme/theme.service';
import { Avatar } from '../../shared/avatar';

/**
 * The main app shell: a sidebar (conversations + unread badges + new-chat + user/theme
 * controls) and the chat panel (thread + composer). Real-time: live messages, optimistic
 * send, history/sync, presence (online/last-seen), typing indicators, and read receipts
 * (the blue double-tick) + unread counts.
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
  private msgApi = inject(MessageService);
  private media = inject(MediaService);
  private push = inject(PushService);
  private router = inject(Router);
  private socket = inject(SocketService);
  readonly theme = inject(ThemeService);

  readonly session = this.auth.session;
  readonly connection = toSignal(this.socket.connection$, { initialValue: 'disconnected' as const });

  conversations = signal<Conversation[]>([]);
  users = signal<UserSummary[]>([]);
  // selected is derived from conversations so receipt/unread updates flow into the thread
  selectedId = signal<string | null>(null);
  readonly selected = computed(
    () => this.conversations().find((c) => c.id === this.selectedId()) ?? null,
  );
  error = signal<string | null>(null);

  // presence (userId → state) and typing (convId → set of userIds), both real-time
  presenceByUser = signal<Map<string, PresenceEvent>>(new Map());
  typingByConvo = signal<Map<string, Set<string>>>(new Map());
  private typingTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private lastTypingSent = 0;
  private stopTypingTimer?: ReturnType<typeof setTimeout>;

  // messages bucketed by conversation id; the thread shows the selected one, ordered by seq
  private messagesByConvo = signal<Map<string, ChatMessage[]>>(new Map());
  private localCounter = 0;
  readonly messages = computed(() => {
    const c = this.selected();
    return c ? this.messagesByConvo().get(c.id) ?? [] : [];
  });
  draft = signal('');
  loadingOlder = signal(false);

  // media (Day 10): resolved presigned GET URLs (blobKey → url), recording state
  private mediaUrls = signal<Map<string, string>>(new Map());
  private mediaInFlight = new Set<string>();
  recording = signal(false);
  private recorder: MediaRecorder | null = null;
  private recordChunks: Blob[] = [];
  private threadEl = viewChild<ElementRef<HTMLDivElement>>('threadScroll');
  private stickToBottom = true;
  private loadedConvos = new Set<string>();
  private historyExhausted = new Set<string>();

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
    // Register this device for web push so it can be woken while offline (Day 11).
    const active = this.session();
    if (active) void this.push.init(active.deviceId);
    this.socket.messages$.subscribe((m) => this.onMessage(m));
    this.socket.presence$.subscribe((p) => this.updatePresence(p));
    this.socket.typing$.subscribe((t) => this.onTyping(t));
    this.socket.receipts$.subscribe((r) =>
      this.updateConversation(r.conversationId, (c) => ({
        ...c,
        participants: c.participants.map((p) =>
          p.userId === r.userId ? { ...p, lastReadSeq: Math.max(p.lastReadSeq, r.lastReadSeq) } : p,
        ),
      })),
    );
    this.socket.connection$.subscribe((s) => {
      if (s === 'connected') this.syncOpenConversation();
    });
    this.refresh();
    this.convos.listUsers().subscribe({
      next: (u) => this.users.set(u),
      error: () => this.error.set('Could not load users.'),
    });
    effect(() => {
      this.messages();
      if (this.stickToBottom) this.scrollToBottomSoon();
    });
    // Resolve a presigned GET URL for any media message that doesn't have a local preview.
    effect(() => {
      for (const m of this.messages()) {
        if ((m.type === 'IMAGE' || m.type === 'VOICE') && m.attachment?.blobKey && !m.localPreviewUrl) {
          this.ensureMediaResolved(m.attachment.blobKey);
        }
      }
    });
  }

  // --- inbound messages ---

  private onMessage(m: Message): void {
    this.mutate(m.conversationId, (list) => {
      const idx = list.findIndex((x) => x.clientMsgId === m.clientMsgId);
      const confirmed: ChatMessage = {
        ...m,
        status: 'sent',
        localSeq: idx >= 0 ? list[idx].localSeq : 0,
        // keep my own optimistic preview so my media bubble doesn't flicker on reconcile
        localPreviewUrl: idx >= 0 ? list[idx].localPreviewUrl : undefined,
      };
      if (idx >= 0) {
        const next = [...list];
        next[idx] = confirmed;
        return next;
      }
      return [...list, confirmed];
    });
    // If it's for the open conversation, I'm reading it → advance my read cursor.
    if (m.conversationId === this.selectedId() && m.senderId !== this.session()?.userId) {
      this.markReadOpen();
    }
  }

  sendMessage(): void {
    const c = this.selected();
    const text = this.draft().trim();
    if (!c || !text) return;
    this.stickToBottom = true;
    this.stopTyping(c.id);

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
    if (!this.socket.send(c.id, text, clientMsgId)) {
      this.setStatus(c.id, clientMsgId, 'failed');
    }
  }

  retry(m: ChatMessage): void {
    const c = this.selected();
    if (!c) return;
    this.setStatus(c.id, m.clientMsgId, 'pending');
    // Media whose upload already succeeded just needs the tiny reference re-sent; text re-sends body.
    const ok =
      m.type !== 'TEXT' && m.attachment
        ? this.socket.sendAttachment(c.id, m.type, m.attachment, m.clientMsgId)
        : this.socket.send(c.id, m.body ?? '', m.clientMsgId);
    if (!ok) this.setStatus(c.id, m.clientMsgId, 'failed');
  }

  // --- media (Day 10): direct-to-blob upload for images + voice notes ---

  /** Hidden file input picked an image → upload it directly to blob storage, then send. */
  onPickImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // let the same file be picked again later
    if (file) this.sendAttachmentMessage(file, 'IMAGE');
  }

  /** Toggle voice-note recording (MediaRecorder → webm/opus → direct-to-blob on stop). */
  async toggleRecording(): Promise<void> {
    if (this.recording()) {
      this.recorder?.stop();
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      this.error.set('Voice recording is not supported in this browser.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.recordChunks = [];
      this.recorder = new MediaRecorder(stream);
      this.recorder.ondataavailable = (e) => {
        if (e.data.size > 0) this.recordChunks.push(e.data);
      };
      this.recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop()); // release the mic
        this.recording.set(false);
        const type = this.recorder?.mimeType || 'audio/webm';
        const blob = new Blob(this.recordChunks, { type });
        this.recorder = null;
        if (blob.size > 0) {
          const file = new File([blob], `voice-${Date.now()}.webm`, { type });
          this.sendAttachmentMessage(file, 'VOICE');
        }
      };
      this.recorder.start();
      this.recording.set(true);
    } catch {
      this.error.set('Could not access the microphone.');
    }
  }

  /** Optimistically render a media bubble, upload the bytes directly, then send the reference. */
  private async sendAttachmentMessage(file: File, type: MessageType): Promise<void> {
    const c = this.selected();
    if (!c) return;
    this.stickToBottom = true;

    const clientMsgId = crypto.randomUUID();
    const optimistic: ChatMessage = {
      id: clientMsgId,
      conversationId: c.id,
      senderId: this.session()!.userId,
      clientMsgId,
      seq: 0,
      type,
      body: null,
      attachment: null,
      createdAt: new Date().toISOString(),
      status: 'pending',
      localSeq: ++this.localCounter,
      localPreviewUrl: URL.createObjectURL(file), // instant preview while uploading
    };
    this.mutate(c.id, (list) => [...list, optimistic]);

    try {
      const attachment = await this.media.upload(file);
      // stash the reference on the pending bubble so a failed send can be retried
      this.mutate(c.id, (list) =>
        list.map((x) => (x.clientMsgId === clientMsgId ? { ...x, attachment } : x)),
      );
      if (!this.socket.sendAttachment(c.id, type, attachment, clientMsgId)) {
        this.setStatus(c.id, clientMsgId, 'failed');
      }
    } catch {
      this.setStatus(c.id, clientMsgId, 'failed');
      this.error.set('Upload failed — tap to retry.');
    }
  }

  private ensureMediaResolved(blobKey: string): void {
    if (this.mediaUrls().has(blobKey) || this.mediaInFlight.has(blobKey)) return;
    this.mediaInFlight.add(blobKey);
    this.media
      .resolveUrl(blobKey)
      .then((url) => this.mediaUrls.update((m) => new Map(m).set(blobKey, url)))
      .catch(() => {})
      .finally(() => this.mediaInFlight.delete(blobKey));
  }

  /** The src for a media bubble: local preview (my upload) → resolved presigned GET → null. */
  mediaSrc(m: ChatMessage): string | null {
    if (m.localPreviewUrl) return m.localPreviewUrl;
    const key = m.attachment?.blobKey;
    return key ? this.mediaUrls().get(key) ?? null : null;
  }

  private setStatus(convoId: string, clientMsgId: string, status: ChatMessage['status']): void {
    this.mutate(convoId, (list) =>
      list.map((x) => (x.clientMsgId === clientMsgId ? { ...x, status } : x)),
    );
  }

  private mutate(convoId: string, fn: (list: ChatMessage[]) => ChatMessage[]): void {
    const map = new Map(this.messagesByConvo());
    const next = fn(map.get(convoId) ?? []).sort((a, b) => {
      const ap = a.status !== 'sent';
      const bp = b.status !== 'sent';
      if (ap && bp) return a.localSeq - b.localSeq;
      if (ap) return 1;
      if (bp) return -1;
      return a.seq - b.seq;
    });
    map.set(convoId, next);
    this.messagesByConvo.set(map);
  }

  // --- read receipts / unread ---

  /** Advance my read cursor to the newest message in the open conversation. */
  private markReadOpen(): void {
    const c = this.selected();
    if (!c) return;
    const newest = this.newestSeq(c.id);
    if (newest == null) return;
    const me = this.session()?.userId;
    const myRead = c.participants.find((p) => p.userId === me)?.lastReadSeq ?? 0;
    if (newest <= myRead) return;
    // optimistic: clear my unread + advance my cursor locally
    this.updateConversation(c.id, (conv) => ({
      ...conv,
      unreadCount: 0,
      participants: conv.participants.map((p) => (p.userId === me ? { ...p, lastReadSeq: newest } : p)),
    }));
    this.convos.read(c.id, newest).subscribe({ error: () => {} });
  }

  /** My sent message is "read" when every OTHER participant's cursor has reached it. */
  isRead(m: ChatMessage): boolean {
    if (!this.isMine(m) || m.status !== 'sent') return false;
    const c = this.selected();
    if (!c) return false;
    const me = this.session()?.userId;
    const others = c.participants.filter((p) => p.userId !== me);
    return others.length > 0 && others.every((p) => p.lastReadSeq >= m.seq);
  }

  // --- presence ---

  private updatePresence(p: PresenceEvent): void {
    this.presenceByUser.update((m) => new Map(m).set(p.userId, p));
  }

  presenceText(c: Conversation): string {
    if (c.type !== 'DIRECT') return `${c.participants.length} members`;
    const o = this.other(c);
    if (!o) return '';
    const p = this.presenceByUser().get(o.userId);
    if (!p) return `@${o.username}`;
    if (p.online) return 'online';
    return p.lastSeenAt ? `last seen ${this.timeAgo(p.lastSeenAt)}` : 'offline';
  }

  isPeerOnline(c: Conversation): boolean {
    if (c.type !== 'DIRECT') return false;
    const o = this.other(c);
    return !!o && !!this.presenceByUser().get(o.userId)?.online;
  }

  // --- typing ---

  private onTyping(t: TypingEvent): void {
    const key = `${t.conversationId}:${t.userId}`;
    clearTimeout(this.typingTimers.get(key));
    this.typingByConvo.update((map) => {
      const m = new Map(map);
      const set = new Set(m.get(t.conversationId) ?? []);
      t.typing ? set.add(t.userId) : set.delete(t.userId);
      m.set(t.conversationId, set);
      return m;
    });
    if (t.typing) {
      // auto-clear if the 'stop' is ever missed (client crash, dropped frame)
      this.typingTimers.set(key, setTimeout(() => this.onTyping({ ...t, typing: false }), 5000));
    } else {
      this.typingTimers.delete(key);
    }
  }

  typingText(c: Conversation): string {
    const me = this.session()?.userId;
    const typers = [...(this.typingByConvo().get(c.id) ?? [])].filter((u) => u !== me);
    if (!typers.length) return '';
    const names = typers.map(
      (u) => c.participants.find((p) => p.userId === u)?.displayName ?? 'Someone',
    );
    return names.length === 1 ? `${names[0]} is typing…` : `${names.length} people are typing…`;
  }

  /** Composer input handler — updates the draft and emits throttled typing signals. */
  onDraftInput(value: string): void {
    this.draft.set(value);
    const c = this.selected();
    if (!c) return;
    const now = Date.now();
    if (now - this.lastTypingSent > 2500) {
      this.socket.sendTyping(c.id, 'start');
      this.lastTypingSent = now;
    }
    clearTimeout(this.stopTypingTimer);
    this.stopTypingTimer = setTimeout(() => this.stopTyping(c.id), 3000);
  }

  private stopTyping(conversationId: string): void {
    clearTimeout(this.stopTypingTimer);
    if (this.lastTypingSent) {
      this.socket.sendTyping(conversationId, 'stop');
      this.lastTypingSent = 0;
    }
  }

  // --- conversations / selection ---

  refresh(): void {
    this.convos.list().subscribe({
      next: (c) => this.conversations.set(c),
      error: () => this.error.set('Could not load conversations.'),
    });
  }

  select(c: Conversation): void {
    this.selectedId.set(c.id);
    this.stickToBottom = true;
    if (c.type === 'DIRECT') {
      const o = this.other(c);
      if (o) this.convos.presence(o.userId).subscribe((p) => this.updatePresence(p));
    }
    if (!this.loadedConvos.has(c.id)) {
      this.loadedConvos.add(c.id);
      this.msgApi.history(c.id, { limit: 50 }).subscribe({
        next: (res) => {
          this.mergeMessages(c.id, res.messages);
          if (!res.hasMore) this.historyExhausted.add(c.id);
          this.markReadOpen();
          this.scrollToBottomSoon();
        },
        error: () => this.error.set('Could not load messages.'),
      });
    } else {
      this.markReadOpen();
      this.scrollToBottomSoon();
    }
  }

  private updateConversation(convId: string, fn: (c: Conversation) => Conversation): void {
    this.conversations.update((list) => list.map((c) => (c.id === convId ? fn(c) : c)));
  }

  onThreadScroll(): void {
    const el = this.threadEl()?.nativeElement;
    if (!el) return;
    this.stickToBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    if (el.scrollTop < 60) this.loadOlder();
  }

  loadOlder(): void {
    const c = this.selected();
    if (!c || this.loadingOlder() || this.historyExhausted.has(c.id)) return;
    const oldest = this.oldestSeq(c.id);
    if (oldest == null) return;
    this.loadingOlder.set(true);
    const prevHeight = this.threadEl()?.nativeElement.scrollHeight ?? 0;
    this.msgApi.history(c.id, { before: oldest, limit: 50 }).subscribe({
      next: (res) => {
        this.mergeMessages(c.id, res.messages);
        if (!res.hasMore) this.historyExhausted.add(c.id);
        queueMicrotask(() => {
          const e = this.threadEl()?.nativeElement;
          if (e) e.scrollTop = e.scrollHeight - prevHeight;
        });
        this.loadingOlder.set(false);
      },
      error: () => this.loadingOlder.set(false),
    });
  }

  private syncOpenConversation(): void {
    const c = this.selected();
    if (!c || !this.loadedConvos.has(c.id)) return;
    this.msgApi.history(c.id, { after: this.newestSeq(c.id) ?? 0, limit: 100 }).subscribe({
      next: (res) => {
        if (res.messages.length) this.mergeMessages(c.id, res.messages);
      },
    });
  }

  private mergeMessages(convoId: string, incoming: Message[]): void {
    this.mutate(convoId, (list) => {
      const known = new Set(list.map((x) => x.clientMsgId));
      const additions: ChatMessage[] = incoming
        .filter((m) => !known.has(m.clientMsgId))
        .map((m) => ({ ...m, status: 'sent', localSeq: 0 }));
      return additions.length ? [...list, ...additions] : list;
    });
  }

  private oldestSeq(convoId: string): number | null {
    const c = (this.messagesByConvo().get(convoId) ?? []).filter((m) => m.status === 'sent');
    return c.length ? c[0].seq : null;
  }

  private newestSeq(convoId: string): number | null {
    const c = (this.messagesByConvo().get(convoId) ?? []).filter((m) => m.status === 'sent');
    return c.length ? c[c.length - 1].seq : null;
  }

  private scrollToBottomSoon(): void {
    queueMicrotask(() => {
      const el = this.threadEl()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
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
          this.conversations.update((list) => [created, ...list.filter((x) => x.id !== created.id)]);
          this.select(created);
          this.refresh();
        },
        error: (err) => {
          this.creating.set(false);
          this.error.set(err?.error?.message ?? 'Could not create conversation.');
        },
      });
  }

  // --- display helpers ---

  label(c: Conversation): string {
    if (c.type === 'GROUP') return c.title ?? 'Untitled group';
    return this.other(c)?.displayName ?? 'Direct chat';
  }

  subtitle(c: Conversation): string {
    if (c.type === 'GROUP') return `${c.participants.length} members`;
    const o = this.other(c);
    return o ? `@${o.username}` : '';
  }

  avatarKey(c: Conversation): string {
    return c.type === 'GROUP' ? c.id : this.other(c)?.userId ?? c.id;
  }

  isMine(m: Message): boolean {
    return m.senderId === this.session()?.userId;
  }

  senderName(m: Message): string {
    const p = this.selected()?.participants.find((x) => x.userId === m.senderId);
    return p?.displayName ?? 'Someone';
  }

  private other(c: Conversation) {
    const me = this.session()?.userId;
    return c.participants.find((p) => p.userId !== me);
  }

  private timeAgo(iso: string): string {
    const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    return `${Math.floor(hrs / 24)}d ago`;
  }

  logout(): void {
    this.socket.disconnect();
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
