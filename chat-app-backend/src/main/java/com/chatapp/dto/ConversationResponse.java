package com.chatapp.dto;

import com.chatapp.entity.ConversationType;

import java.time.LocalDateTime;

// DTO chứa dữ liệu cuộc trò chuyện trả về cho client.
public record ConversationResponse(
        Long conversationId,
        ConversationType conversationType,
        String name,
        String participantUsername,
        String lastMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String ownerUsername,
        Integer memberCount,
        Long lastMessageId
) {
    // Constructor tương thích ngược
    public ConversationResponse(
            Long conversationId,
            ConversationType conversationType,
            String name,
            String participantUsername,
            String lastMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(conversationId, conversationType, name, participantUsername, lastMessage, createdAt, updatedAt, null, null, null);
    }

    public ConversationResponse(
            Long conversationId,
            ConversationType conversationType,
            String name,
            String participantUsername,
            String lastMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String ownerUsername,
            Integer memberCount
    ) {
        this(conversationId, conversationType, name, participantUsername, lastMessage, createdAt, updatedAt, ownerUsername, memberCount, null);
    }
}
