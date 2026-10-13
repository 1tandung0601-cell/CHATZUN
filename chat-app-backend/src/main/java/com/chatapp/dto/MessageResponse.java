package com.chatapp.dto;

import java.time.LocalDateTime;

// DTO chứa dữ liệu tin nhắn trả về client.
public class MessageResponse {

    private Long id;
    private Long conversationId;
    private String senderUsername;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime editedAt;

    public MessageResponse(
            Long id,
            Long conversationId,
            String senderUsername,
            String content,
            LocalDateTime createdAt,
            LocalDateTime editedAt
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderUsername = senderUsername;
        this.content = content;
        this.createdAt = createdAt;
        this.editedAt = editedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }
}