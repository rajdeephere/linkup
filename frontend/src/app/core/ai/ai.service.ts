import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

interface SummaryResponse {
  summary: string;
  cached: boolean;
}
interface SuggestRepliesResponse {
  suggestions: string[];
}
export interface ModerationFlag {
  messageId: string;
  category: string;
  reason: string;
}

/**
 * AI assist (Day 12) — on-demand thread summary + smart replies. The backend decides which model
 * answers (real provider vs stub); the client just asks per conversation.
 */
@Injectable({ providedIn: 'root' })
export class AiService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  summarize(conversationId: string): Observable<SummaryResponse> {
    return this.http.post<SummaryResponse>(
      `${this.base}/v1/conversations/${conversationId}/summarize`,
      {},
    );
  }

  suggestReplies(conversationId: string): Observable<SuggestRepliesResponse> {
    return this.http.post<SuggestRepliesResponse>(
      `${this.base}/v1/conversations/${conversationId}/suggest-replies`,
      {},
    );
  }

  /** Flagged messages in a conversation (Day 13 — moderation overlay). */
  moderation(conversationId: string): Observable<ModerationFlag[]> {
    return this.http.get<ModerationFlag[]>(
      `${this.base}/v1/conversations/${conversationId}/moderation`,
    );
  }
}
