package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Dữ liệu client gửi lên khi muốn gửi tin nhắn.
public class SendMessageRequest {

    @NotNull(message = "Conversation ID không được để trống")
    private Long conversationId;

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(max = 5000, message = "Tin nhắn không được vượt quá 5000 ký tự")
    private String content;

    public SendMessageRequest() {
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
