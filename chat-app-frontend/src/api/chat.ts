import type {
  AddMembersRequest,
  ConversationResponse,
  CreateDirectConversationRequest,
  CreateGroupConversationRequest,
  EditMessageRequest,
  MemberResponse,
  MessageResponse,
  PageResponse,
  SendMessageRequest,
  UserSearchResult,
} from '../types/chat';
import { apiFetch } from './client';

export async function getConversations(): Promise<ConversationResponse[]> {
  return apiFetch<ConversationResponse[]>('/api/conversations');
}

export async function createDirectConversation(
  participantUsername: string
): Promise<ConversationResponse> {
  const body: CreateDirectConversationRequest = {
    participantUsername: participantUsername.trim(),
  };
  return apiFetch<ConversationResponse>('/api/conversations/direct', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export async function createGroupConversation(
  name: string,
  memberUsernames: string[]
): Promise<ConversationResponse> {
  const body: CreateGroupConversationRequest = {
    name: name.trim(),
    memberUsernames: memberUsernames.map((u) => u.trim()).filter(Boolean),
  };
  return apiFetch<ConversationResponse>('/api/conversations/group', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export async function searchUsers(
  query: string,
  page = 0,
  size = 20
): Promise<PageResponse<UserSearchResult>> {
  const encoded = encodeURIComponent(query.trim());
  return apiFetch<PageResponse<UserSearchResult>>(
    `/api/users/search?query=${encoded}&page=${page}&size=${size}`
  );
}

export async function getConversationMessages(
  conversationId: number,
  page = 0,
  size = 50
): Promise<PageResponse<MessageResponse>> {
  return apiFetch<PageResponse<MessageResponse>>(
    `/api/messages/conversation/${conversationId}?page=${page}&size=${size}`
  );
}

export async function sendMessage(
  conversationId: number,
  content: string
): Promise<MessageResponse> {
  const body: SendMessageRequest = {
    conversationId,
    content: content.trim(),
  };
  return apiFetch<MessageResponse>('/api/messages', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export async function editMessage(
  messageId: number,
  content: string
): Promise<MessageResponse> {
  const body: EditMessageRequest = {
    content: content.trim(),
  };
  return apiFetch<MessageResponse>(`/api/messages/${messageId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export async function deleteMessage(messageId: number): Promise<void> {
  return apiFetch<void>(`/api/messages/${messageId}`, {
    method: 'DELETE',
  });
}

export async function getGroupMembers(conversationId: number): Promise<MemberResponse[]> {
  return apiFetch<MemberResponse[]>(`/api/conversations/${conversationId}/members`);
}

export async function addMembersToGroup(
  conversationId: number,
  usernames: string[]
): Promise<MemberResponse[]> {
  const body: AddMembersRequest = {
    usernames: usernames.map((u) => u.trim()).filter(Boolean),
  };
  return apiFetch<MemberResponse[]>(`/api/conversations/${conversationId}/members`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export async function deleteGroupConversation(conversationId: number): Promise<void> {
  return apiFetch<void>(`/api/conversations/${conversationId}`, {
    method: 'DELETE',
  });
}

