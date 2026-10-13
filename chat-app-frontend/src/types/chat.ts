export type ConversationType = 'DIRECT' | 'GROUP';

export interface ConversationResponse {
  conversationId: number;
  conversationType: ConversationType;
  name: string | null;
  participantUsername: string | null;
  lastMessage: string | null;
  createdAt: string;
  updatedAt: string;
  ownerUsername?: string | null;
  memberCount?: number | null;
  lastMessageId?: number | null;
}

export type MemberRole = 'MEMBER' | 'ADMIN';

export interface MemberResponse {
  userId: number;
  username: string;
  role: MemberRole;
  joinedAt: string;
}

export interface UserSearchResult {
  id: number;
  username: string;
}

export interface AddMembersRequest {
  usernames: string[];
}

export interface CreateDirectConversationRequest {
  participantUsername: string;
}

export interface CreateGroupConversationRequest {
  name: string;
  memberUsernames: string[];
}

export interface EditMessageRequest {
  content: string;
}

export interface MessageResponse {
  id: number;
  conversationId: number;
  senderUsername: string;
  content: string;
  createdAt: string;
  editedAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  last: boolean;
  first: boolean;
  empty: boolean;
}

export interface SendMessageRequest {
  conversationId: number;
  content: string;
}

export type WsEventType =
  | 'CONVERSATION_CREATED'
  | 'CONVERSATION_MEMBERS_ADDED'
  | 'MESSAGE_CREATED'
  | 'MESSAGE_UPDATED'
  | 'MESSAGE_DELETED'
  | 'CONVERSATION_MEMBERS_ADDED'
  | 'CONVERSATION_DELETED';

export interface WsChatMessageEvent {
  type: WsEventType;
  id?: number;
  conversationId: number;
  sender?: string;
  content?: string;
  createdAt?: string;
  editedAt?: string | null;
}

export type WsConnectionStatus = 'connected' | 'connecting' | 'disconnected';
