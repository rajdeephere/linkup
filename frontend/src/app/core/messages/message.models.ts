/** Wire contracts for messages — mirror the backend DTOs. */

export type MessageType = 'TEXT' | 'IMAGE' | 'VOICE' | 'VIDEO' | 'FILE' | 'SYSTEM';

/** A media reference carried by a non-TEXT message (Day 10) — the bytes live in blob storage. */
export interface MessageAttachment {
  blobKey: string;
  mimeType: string;
  sizeBytes?: number | null;
  width?: number | null;
  height?: number | null;
  durationMs?: number | null;
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  clientMsgId: string;
  seq: number;
  type: MessageType;
  body: string | null;
  attachment?: MessageAttachment | null;
  createdAt: string;
}

export interface SendMessageRequest {
  clientMsgId: string;
  type: MessageType;
  body?: string | null;
  attachment?: MessageAttachment | null;
}

/** A page of history/sync messages (always ascending seq). */
export interface MessageHistoryResponse {
  messages: Message[];
  hasMore: boolean;
}

/** Client-side view of a message with local send state (for optimistic rendering). */
export type SendStatus = 'pending' | 'sent' | 'failed';

export interface ChatMessage extends Message {
  status: SendStatus;
  /** Local ordering for pending messages (before a server `seq` exists). */
  localSeq: number;
  /** Object-URL preview for an optimistic media send (before the echo resolves a real URL). */
  localPreviewUrl?: string;
}
