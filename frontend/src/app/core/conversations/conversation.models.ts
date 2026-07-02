/** Wire contracts — mirror the backend conversation DTOs. */

export type ConversationType = 'DIRECT' | 'GROUP';
export type ParticipantRole = 'MEMBER' | 'ADMIN';

export interface ParticipantSummary {
  userId: string;
  username: string;
  displayName: string;
  role: ParticipantRole;
  lastReadSeq: number;
}

export interface Conversation {
  id: string;
  type: ConversationType;
  title: string | null;
  createdAt: string;
  lastMessageAt: string | null;
  unreadCount: number;
  participants: ParticipantSummary[];
}

export interface CreateConversationRequest {
  type: ConversationType;
  title?: string;
  memberUserIds: string[];
}

export interface UserSummary {
  id: string;
  username: string;
  displayName: string;
}
