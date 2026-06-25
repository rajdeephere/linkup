import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';

/**
 * Connection lifecycle states the UI reacts to (reconnect banner, etc).
 */
export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

/**
 * Placeholder shape for an inbound real-time message. Fleshed out in Day 4 when the
 * server assigns `seq` and we render in `seq` order.
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
 * SocketService — STUB (Day 1).
 *
 * This is intentionally a stub: the real WebSocket/STOMP transport lands in Day 3.
 * But the *architecture* (ADR-9) is established now so the rest of the app codes against
 * stable observable streams from the start:
 *
 *   - connection$  — the socket's lifecycle as a stream (drives the reconnect banner)
 *   - messages$    — inbound messages (will be deduped on clientMsgId, ordered by seq)
 *
 * Modelling the socket as RxJS streams is the whole reason we chose Angular: incoming
 * messages, presence, typing, receipts, and connection state are all *streams*, and
 * RxJS expresses them (merge, scan-into-state, debounce, retryWithBackoff) far more
 * cleanly than imperative event handlers. Day 3 fills in connect()/send() over a real
 * socket without changing this public surface — that stability is the point of the stub.
 */
@Injectable({ providedIn: 'root' })
export class SocketService {
  private readonly _connection = new BehaviorSubject<ConnectionState>('disconnected');
  private readonly _messages = new Subject<InboundMessage>();

  /** Hot stream of connection state; starts 'disconnected'. */
  readonly connection$: Observable<ConnectionState> = this._connection.asObservable();

  /** Hot stream of inbound messages. */
  readonly messages$: Observable<InboundMessage> = this._messages.asObservable();

  /** Day 3: open a STOMP-over-WebSocket connection, authenticate, subscribe queues. */
  connect(): void {
    // Stub — no real socket yet. Logged so the wiring is visibly in place.
    console.info('[SocketService] connect() stub — real WebSocket transport arrives in Day 3.');
  }

  disconnect(): void {
    this._connection.next('disconnected');
  }
}
