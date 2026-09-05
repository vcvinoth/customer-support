import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ChatResponse {
  content: string;
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly conversationId = crypto.randomUUID();

  constructor(private readonly http: HttpClient) {}

  sendMessage(prompt: string): Observable<ChatResponse> {
    return this.http.post<ChatResponse>('/api/chat', {
      prompt,
      conversationId: this.conversationId,
    });
  }
}
