import { Component, ElementRef, effect, signal, viewChild } from '@angular/core';
import { ChatService } from './chat-service';

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

@Component({
  imports: [],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly messages = signal<ChatMessage[]>([
    { role: 'assistant', content: "Hi! I'm your support assistant. How can I help you today?" },
  ]);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);

  private readonly scrollAnchor = viewChild<ElementRef<HTMLDivElement>>('scrollAnchor');

  constructor(private readonly chatService: ChatService) {
    effect(() => {
      this.messages();
      this.sending();
      const anchor = this.scrollAnchor()?.nativeElement;
      anchor?.scrollIntoView?.({ behavior: 'smooth' });
    });
  }

  protected onDraftInput(event: Event): void {
    this.draft.set((event.target as HTMLTextAreaElement).value);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  protected send(): void {
    const prompt = this.draft().trim();
    if (!prompt || this.sending()) {
      return;
    }

    this.messages.update((msgs) => [...msgs, { role: 'user', content: prompt }]);
    this.draft.set('');
    this.sending.set(true);

    this.chatService.sendMessage(prompt).subscribe({
      next: (response) => {
        this.messages.update((msgs) => [...msgs, { role: 'assistant', content: response.content }]);
        this.sending.set(false);
      },
      error: () => {
        this.messages.update((msgs) => [
          ...msgs,
          { role: 'assistant', content: 'Sorry, something went wrong reaching support. Please try again.' },
        ]);
        this.sending.set(false);
      },
    });
  }
}
