import { Injectable, inject } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TokenStorage } from '../auth/token-storage';
import { Message } from '../messages/message.models';

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

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

  private readonly _connection = new BehaviorSubject<ConnectionState>('disconnected');
  private readonly _messages = new Subject<Message>();
  private readonly _authFailure = new Subject<void>();

  readonly connection$: Observable<ConnectionState> = this._connection.asObservable();
  readonly messages$: Observable<Message> = this._messages.asObservable();
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
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this._connection.next('connected');
        this.client!.subscribe('/user/queue/messages', (frame: IMessage) => {
          this._messages.next(JSON.parse(frame.body) as Message);
        });
      },
      onWebSocketClose: () => {
        // Transient drop → stompjs auto-reconnects ('reconnecting'). After a fatal auth
        // failure we've already deactivated, so this resolves to 'disconnected'.
        this._connection.next(this.client?.active ? 'reconnecting' : 'disconnected');
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

  disconnect(): void {
    void this.client?.deactivate();
    this.client = null;
    this._connection.next('disconnected');
  }
}
