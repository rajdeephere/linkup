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
