import { Injectable, inject } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TokenStorage } from '../auth/token-storage';

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

/**
 * An inbound real-time message. Fully exercised in Day 4 when the server assigns `seq`
 * and we render in `seq` order with `clientMsgId` dedup.
 */
export interface InboundMessage {
  conversationId: string;
  clientMsgId: string;
  seq: number;
  senderId: string;
  type: string;
  body: string;
}

/**
 * Real-time transport — a STOMP-over-WebSocket client (Day 3 makes the Day-1 stub live).
 *
 * The socket is modelled as RxJS streams (ADR-9): feature code subscribes to `connection$`
 * and `messages$` and never touches the STOMP client directly. The public surface is
 * unchanged from the stub, so nothing that depended on it needs to change.
 *
 * Auth: the JWT is sent on the STOMP CONNECT frame ("Authorization" header); the server
 * verifies it and binds the principal to the session (StompAuthChannelInterceptor).
 */
@Injectable({ providedIn: 'root' })
export class SocketService {
  private storage = inject(TokenStorage);
  private client: Client | null = null;

  private readonly _connection = new BehaviorSubject<ConnectionState>('disconnected');
  private readonly _messages = new Subject<InboundMessage>();

  /** Hot stream of connection state — drives the reconnect indicator. */
  readonly connection$: Observable<ConnectionState> = this._connection.asObservable();
  /** Hot stream of inbound messages (deduped/ordered by the consumer in Day 4). */
  readonly messages$: Observable<InboundMessage> = this._messages.asObservable();

  connect(): void {
    if (this.client?.active) return;
    const token = this.storage.token;
    if (!token) return;

    this._connection.next('connecting');
    this.client = new Client({
      brokerURL: environment.wsUrl,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 2000, // stompjs auto-reconnects; richer backoff+jitter comes in Phase 2
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this._connection.next('connected');
        // My inbound queue. Spring routes /user/queue/messages to THIS session's principal.
        this.client!.subscribe('/user/queue/messages', (frame: IMessage) => {
          this._messages.next(JSON.parse(frame.body) as InboundMessage);
        });
      },
      onWebSocketClose: () => {
        // If the client is still active, stompjs will retry → show 'reconnecting'.
        this._connection.next(this.client?.active ? 'reconnecting' : 'disconnected');
      },
      onStompError: (frame) =>
        console.error('[SocketService] STOMP error:', frame.headers['message']),
    });
    this.client.activate();
  }

  disconnect(): void {
    void this.client?.deactivate();
    this.client = null;
    this._connection.next('disconnected');
  }
}
