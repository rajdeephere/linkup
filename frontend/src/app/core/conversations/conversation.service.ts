import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Conversation, CreateConversationRequest, UserSummary } from './conversation.models';

/**
 * REST access to conversations. The auth token is attached automatically by the
 * global authInterceptor, so this service never touches headers.
 */
@Injectable({ providedIn: 'root' })
export class ConversationService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(): Observable<Conversation[]> {
    return this.http.get<Conversation[]>(`${this.base}/v1/conversations`);
  }

  create(req: CreateConversationRequest): Observable<Conversation> {
    return this.http.post<Conversation>(`${this.base}/v1/conversations`, req);
  }

  /** Everyone except me — to populate the member picker. */
  listUsers(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${this.base}/v1/users`);
  }

  /** Advance my read cursor (drives unread counts + the other side's blue tick). */
  read(conversationId: string, seq: number): Observable<void> {
    return this.http.post<void>(`${this.base}/v1/conversations/${conversationId}/read`, { seq });
  }

  /** On-demand presence for a user (real-time updates arrive on presence$). */
  presence(userId: string): Observable<{ userId: string; online: boolean; lastSeenAt: string | null }> {
    return this.http.get<{ userId: string; online: boolean; lastSeenAt: string | null }>(
      `${this.base}/v1/users/${userId}/presence`,
    );
  }
}
