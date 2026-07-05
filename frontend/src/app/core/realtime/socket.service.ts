import { Injectable, inject } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TokenStorage } from '../auth/token-storage';
import { Message } from '../messages/message.models';

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

export interface PresenceEvent {
  userId: string;
  online: boolean;
  lastSeenAt: string | null;
}
export interface TypingEvent {
  conversationId: string;
  userId: string;
  typing: boolean;
}
export interface ReadReceiptEvent {
  conversationId: string;
  userId: string;
  lastReadSeq: number;
}

const BASE_RECONNECT_MS = 2000;
const MAX_RECONNECT_MS = 30000;

/**
 * Real-time transport — a STOMP-over-WebSocket client (ADR-9).
 *
 * Feature code subscribes to `connection$` / `messages$` and calls `send()`; it never touches
 * the STOMP client. Auth: the JWT rides the CONNECT frame; the server binds the principal to
 * the session, which is how `/user/queue/messages` reaches only this user.
 */
@Injectable({ providedIn: 'root' })
export class SocketService {
  private storage = inject(TokenStorage);
  private client: Client | null = null;
  private reconnectAttempts = 0;

  private readonly _connection = new BehaviorSubject<ConnectionState>('disconnected');
  private readonly _messages = new Subject<Message>();
  private readonly _presence = new Subject<PresenceEvent>();
  private readonly _typing = new Subject<TypingEvent>();
  private readonly _receipts = new Subject<ReadReceiptEvent>();
  private readonly _authFailure = new Subject<void>();

  readonly connection$: Observable<ConnectionState> = this._connection.asObservable();
  readonly messages$: Observable<Message> = this._messages.asObservable();
  readonly presence$: Observable<PresenceEvent> = this._presence.asObservable();
  readonly typing$: Observable<TypingEvent> = this._typing.asObservable();
  readonly receipts$: Observable<ReadReceiptEvent> = this._receipts.asObservable();
  /** Emits when the socket can't authenticate (missing/expired/invalid token). The app
   *  reacts by logging out + redirecting, instead of letting STOMP reconnect forever. */
  readonly authFailure$: Observable<void> = this._authFailure.asObservable();

  connect(): void {
    if (this.client?.active) return;
    const token = this.storage.token;
    if (!token) return;
    // Don't even open a socket with a token the server will reject — that would loop.
    if (this.storage.isExpired()) {
      this._authFailure.next();
      return;
    }

    this._connection.next('connecting');
    this.client = new Client({
      brokerURL: environment.wsUrl,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: BASE_RECONNECT_MS,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this._connection.next('connected');
        this.reconnectAttempts = 0;                 // reset backoff on a good connection
        this.client!.reconnectDelay = BASE_RECONNECT_MS;
        const sub = (dest: string, out: (v: any) => void) =>
          this.client!.subscribe(dest, (f: IMessage) => out(JSON.parse(f.body)));
        sub('/user/queue/messages', (v) => this._messages.next(v as Message));
        sub('/user/queue/presence', (v) => this._presence.next(v as PresenceEvent));
        sub('/user/queue/typing', (v) => this._typing.next(v as TypingEvent));
        sub('/user/queue/receipts', (v) => this._receipts.next(v as ReadReceiptEvent));
      },
      onWebSocketClose: () => {
        if (this.client?.active) {
          // Exponential backoff + JITTER for the next reconnect — so a whole fleet that
          // dropped together (pod kill / deploy) doesn't reconnect in lockstep and stampede
          // the survivors (scenario #8). stompjs reads reconnectDelay for its next attempt.
          this.reconnectAttempts++;
          const backoff = Math.min(BASE_RECONNECT_MS * 2 ** (this.reconnectAttempts - 1), MAX_RECONNECT_MS);
          this.client.reconnectDelay = backoff + Math.floor(Math.random() * 1000);
          this._connection.next('reconnecting');
        } else {
          this._connection.next('disconnected');
        }
      },
      onStompError: (frame) => {
        // The server rejected the CONNECT (e.g. expired/invalid token → our interceptor).
        // Stop the retry loop and tell the app to re-authenticate.
        console.error('[SocketService] STOMP error:', frame.headers['message']);
        this.failAuth();
      },
    });
    this.client.activate();
  }

  /** Tear the socket down (no more reconnects) and signal that re-auth is needed. */
  private failAuth(): void {
    this.disconnect();
    this._authFailure.next();
  }

  /**
   * Send a text message. The caller supplies the `clientMsgId` so it can render the message
   * optimistically and reconcile the echo by that id (ADR-4). Returns false if the socket
   * isn't connected, so the caller can mark the message failed.
   */
  send(conversationId: string, body: string, clientMsgId: string): boolean {
    if (!this.client?.connected) return false;
    this.client.publish({
      destination: `/app/conversations/${conversationId}/send`,
      body: JSON.stringify({ clientMsgId, type: 'TEXT', body }),
    });
    return true;
  }

  /** Signal typing start/stop for a conversation (ephemeral; the server TTLs it). */
  sendTyping(conversationId: string, state: 'start' | 'stop'): void {
    if (!this.client?.connected) return;
    this.client.publish({
      destination: `/app/conversations/${conversationId}/typing`,
      body: JSON.stringify({ state }),
    });
  }

  disconnect(): void {
    void this.client?.deactivate();
    this.client = null;
    this._connection.next('disconnected');
  }
}
