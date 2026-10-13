import { Client, type StompSubscription } from '@stomp/stompjs';
import type { WsChatMessageEvent, WsConnectionStatus } from '../types/chat';
import { clearAuthSession, getStoredToken, UNAUTHORIZED_EVENT } from './client';

type StatusListener = (status: WsConnectionStatus) => void;
type MessageEventListener = (event: WsChatMessageEvent) => void;
type AccountEventListener = (event: WsChatMessageEvent) => void;

class WebSocketManager {
  private client: Client | null = null;
  private connectionToken: string | null = null;
  private currentSubscription: StompSubscription | null = null;
  private accountSubscription: StompSubscription | null = null;
  private currentConversationId: number | null = null;
  private currentEventListener: MessageEventListener | null = null;
  private accountEventListener: AccountEventListener | null = null;
  private statusListeners = new Set<StatusListener>();
  private status: WsConnectionStatus = 'disconnected';

  public getStatus(): WsConnectionStatus {
    return this.status;
  }

  public onStatusChange(listener: StatusListener): () => void {
    this.statusListeners.add(listener);
    listener(this.status);
    return () => {
      this.statusListeners.delete(listener);
    };
  }

  private setStatus(newStatus: WsConnectionStatus): void {
    if (this.status !== newStatus) {
      this.status = newStatus;
      this.statusListeners.forEach((listener) => listener(newStatus));
    }
  }

  /**
   * Khởi tạo kết nối STOMP với JWT token
   */
  public connect(token: string): void {
    if (this.client && this.client.active && this.connectionToken === token) {
      return;
    }

    this.disconnect();
    this.connectionToken = token;
    this.setStatus('connecting');

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL = `${protocol}//${window.location.host}/ws`;

    const client = new Client({
      brokerURL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: (msg: string) => {
        if (import.meta.env.DEV) {
          console.debug('[STOMP]', msg);
        }
      },
      onConnect: () => {
        if (this.client !== client) return;
        this.setStatus('connected');
        this.currentSubscription = null;
        this.accountSubscription = null;
        this.subscribeToAccountInternal();
        // Nếu trước đó đang có phòng chờ subscribe (hoặc reconnect lại), tiến hành subscribe lại
        if (this.currentConversationId !== null && this.currentEventListener !== null) {
          this.subscribeInternal(this.currentConversationId, this.currentEventListener);
        }
      },
      onDisconnect: () => {
        if (this.client !== client) return;
        this.currentSubscription = null;
        this.accountSubscription = null;
        this.setStatus('disconnected');
      },
      onStompError: (frame) => {
        if (this.client !== client) return;
        console.warn('[STOMP Error]', frame.headers['message'], frame.body);
        this.setStatus('disconnected');

        const errorText = `${frame.headers['message'] || ''} ${frame.body || ''}`;
        if (/jwt.*(không hợp lệ|hết hạn|invalid|expired)/i.test(errorText)) {
          const currentToken = getStoredToken();
          if (currentToken === token) {
            clearAuthSession();
            window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
          } else if (currentToken) {
            this.connect(currentToken);
          } else {
            this.disconnect();
          }
        }
      },
      onWebSocketClose: () => {
        if (this.client !== client) return;
        this.currentSubscription = null;
        this.accountSubscription = null;
        this.setStatus('disconnected');
      },
    });

    this.client = client;
    client.activate();
  }

  public subscribeToAccountEvents(listener: AccountEventListener): () => void {
    this.accountEventListener = listener;
    this.subscribeToAccountInternal();

    return () => {
      if (this.accountEventListener !== listener) return;
      this.unsubscribeAccount();
      this.accountEventListener = null;
    };
  }

  private subscribeToAccountInternal(): void {
    if (!this.client?.connected || !this.accountEventListener || this.accountSubscription) {
      return;
    }

    try {
      this.accountSubscription = this.client.subscribe('/user/queue/conversations', (frame) => {
        try {
          this.accountEventListener?.(JSON.parse(frame.body) as WsChatMessageEvent);
        } catch (err) {
          console.error('Không thể parse sự kiện đồng bộ cuộc trò chuyện:', err);
        }
      });
    } catch (err) {
      console.warn('Lỗi khi đăng ký sự kiện đồng bộ cuộc trò chuyện:', err);
    }
  }

  /**
   * Đăng ký nhận tin nhắn của một cuộc trò chuyện
   */
  public subscribeToConversation(
    conversationId: number,
    listener: MessageEventListener
  ): () => void {
    this.currentConversationId = conversationId;
    this.currentEventListener = listener;

    if (this.client && this.client.connected) {
      this.subscribeInternal(conversationId, listener);
    }

    return () => {
      if (this.currentConversationId === conversationId) {
        this.unsubscribeCurrent();
        this.currentConversationId = null;
        this.currentEventListener = null;
      }
    };
  }

  private subscribeInternal(
    conversationId: number,
    listener: MessageEventListener
  ): void {
    this.unsubscribeCurrent();

    if (!this.client || !this.client.connected) {
      return;
    }

    const destination = `/topic/conversations/${conversationId}`;
    try {
      this.currentSubscription = this.client.subscribe(destination, (frame) => {
        try {
          const payload = JSON.parse(frame.body) as WsChatMessageEvent;
          listener(payload);
        } catch (err) {
          console.error('Không thể parse dữ liệu tin nhắn STOMP:', err);
        }
      });
    } catch (err) {
      console.warn('Lỗi khi subscribe topic:', destination, err);
    }
  }

  private unsubscribeCurrent(): void {
    if (this.currentSubscription) {
      try {
        this.currentSubscription.unsubscribe();
      } catch {
        // bỏ qua lỗi nếu kết nối đã đóng
      }
      this.currentSubscription = null;
    }
  }

  private unsubscribeAccount(): void {
    if (!this.accountSubscription) return;
    try {
      this.accountSubscription.unsubscribe();
    } catch {
      // Ignore subscriptions from a closed socket.
    }
    this.accountSubscription = null;
  }

  /**
   * Gửi tin nhắn mới qua STOMP /app/chat.send
   */
  public sendMessage(conversationId: number, content: string): boolean {
    if (!this.client || !this.client.connected) {
      return false;
    }

    try {
      this.client.publish({
        destination: '/app/chat.send',
        body: JSON.stringify({
          conversationId,
          content: content.trim(),
        }),
      });
      return true;
    } catch (err) {
      console.error('Lỗi khi gửi tin nhắn qua STOMP:', err);
      return false;
    }
  }

  /**
   * Sửa tin nhắn qua STOMP /app/chat.edit
   */
  public editMessage(messageId: number, content: string): boolean {
    if (!this.client || !this.client.connected) {
      return false;
    }

    try {
      this.client.publish({
        destination: '/app/chat.edit',
        body: JSON.stringify({
          messageId,
          content: content.trim(),
        }),
      });
      return true;
    } catch (err) {
      console.error('Lỗi khi sửa tin nhắn qua STOMP:', err);
      return false;
    }
  }

  /**
   * Xóa tin nhắn qua STOMP /app/chat.delete
   */
  public deleteMessage(messageId: number): boolean {
    if (!this.client || !this.client.connected) {
      return false;
    }

    try {
      this.client.publish({
        destination: '/app/chat.delete',
        body: JSON.stringify({
          messageId,
        }),
      });
      return true;
    } catch (err) {
      console.error('Lỗi khi xóa tin nhắn qua STOMP:', err);
      return false;
    }
  }

  /**
   * Ngắt kết nối hoàn toàn và dọn dẹp subscription
   */
  public disconnect(): void {
    this.unsubscribeCurrent();
    this.unsubscribeAccount();
    this.currentConversationId = null;
    this.currentEventListener = null;
    this.accountEventListener = null;

    const client = this.client;
    this.client = null;
    this.connectionToken = null;

    if (client) {
      try {
        client.deactivate();
      } catch {
        // ignore
      }
    }

    this.setStatus('disconnected');
  }

  public isConnected(): boolean {
    return Boolean(this.client && this.client.connected);
  }
}

export const wsManager = new WebSocketManager();
