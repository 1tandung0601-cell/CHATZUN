package com.chatapp.dto;

import java.time.LocalDateTime;

// DTO phát sự kiện realtime qua WebSocket STOMP
public class ChatMessageEvent {

    private String type;
    private Long id;
    private Long conversationId;
    private String sender;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime editedAt;

    public ChatMessageEvent() {
    }

    public ChatMessageEvent(
            String type,
            Long id,
            Long conversationId,
            String sender,
            String content,
            LocalDateTime createdAt,
            LocalDateTime editedAt
    ) {
        this.type = type;
        this.id = id;
        this.conversationId = conversationId;
        this.sender = sender;
        this.content = content;
        this.createdAt = createdAt;
        this.editedAt = editedAt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(LocalDateTime editedAt) {
        this.editedAt = editedAt;
    }
}
