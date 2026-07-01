/** Wire contracts for messages — mirror the backend DTOs. */

export type MessageType = 'TEXT' | 'IMAGE' | 'VOICE' | 'VIDEO' | 'FILE' | 'SYSTEM';

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  clientMsgId: string;
  seq: number;
  type: MessageType;
  body: string;
  createdAt: string;
}

export interface SendMessageRequest {
  clientMsgId: string;
  type: MessageType;
  body: string;
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
}
