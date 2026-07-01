import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MessageHistoryResponse } from './message.models';

/**
 * Durable message reads (Day 6) — the REST side of messaging. The live path is the socket;
 * this loads history on open, pages older on scroll-back, and catches up after a reconnect.
 */
@Injectable({ providedIn: 'root' })
export class MessageService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** `before` = older page (scroll-back); `after` = missed messages (sync); neither = latest. */
  history(
    conversationId: string,
    opts: { before?: number; after?: number; limit?: number } = {},
  ): Observable<MessageHistoryResponse> {
    let params = new HttpParams();
    if (opts.before != null) params = params.set('before', opts.before);
    if (opts.after != null) params = params.set('after', opts.after);
    if (opts.limit != null) params = params.set('limit', opts.limit);
    return this.http.get<MessageHistoryResponse>(
      `${this.base}/v1/conversations/${conversationId}/messages`,
      { params },
    );
  }
}
