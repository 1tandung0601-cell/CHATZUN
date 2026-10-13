package com.chatapp.controller;

import com.chatapp.dto.SendMessageRequest;
import com.chatapp.entity.Message;
import com.chatapp.service.ConversationEventPublisher;
import com.chatapp.service.MessageService;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
public class ChatWebSocketController {

    private final MessageService messageService;
    private final ConversationEventPublisher eventPublisher;
    private final SimpMessageSendingOperations messagingTemplate;

    // Spring tự động cung cấp Service và công cụ phát message.
    public ChatWebSocketController(
            MessageService messageService,
            ConversationEventPublisher eventPublisher,
            SimpMessageSendingOperations messagingTemplate
    ) {
        this.messageService = messageService;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    // Nhận tin nhắn mới từ client qua /app/chat.send.
    @MessageMapping("/chat.send")
    public void sendMessage(
            @Payload ChatMessage request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Principal principal = getAuthenticatedUser(headerAccessor);

        // Kiểm tra dữ liệu đầu vào.
        if (request.getConversationId() == null
                || request.getContent() == null
                || request.getContent().isBlank()) {
            throw new IllegalArgumentException(
                    "Conversation ID và nội dung tin nhắn là bắt buộc"
            );
        }

        // Tạo request dùng chung với REST API.
        SendMessageRequest sendRequest = new SendMessageRequest();
        sendRequest.setConversationId(request.getConversationId());
        sendRequest.setContent(request.getContent());

        // Service kiểm tra quyền thành viên và lưu tin nhắn.
        Message savedMessage = messageService.sendMessage(
                principal.getName(),
                sendRequest
        );

        // Phát sự kiện đến đúng phòng chat.
        ChatMessage response = toEvent(
                savedMessage,
                "MESSAGE_CREATED"
        );

        broadcast(response);
    }

    // Sửa tin nhắn qua /app/chat.edit.
    // Client gửi: {"messageId": 1, "content": "Nội dung mới"}
    @MessageMapping("/chat.edit")
    public void editMessage(
            @Payload EditChatMessage request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Principal principal = getAuthenticatedUser(headerAccessor);

        if (request == null
                || request.getMessageId() == null
                || request.getContent() == null
                || request.getContent().isBlank()) {
            throw new IllegalArgumentException(
                    "Message ID và nội dung mới là bắt buộc"
            );
        }

        // Service kiểm tra quyền sở hữu và cập nhật database.
        Message updatedMessage = messageService.editMessage(
                principal.getName(),
                request.getMessageId(),
                request.getContent()
        );

        // Báo cho các client trong phòng biết tin nhắn đã thay đổi.
        ChatMessage response = toEvent(
                updatedMessage,
                "MESSAGE_UPDATED"
        );

        broadcast(response);
    }

    // Xóa tin nhắn qua /app/chat.delete.
    // Client gửi: {"messageId": 1}
    @MessageMapping("/chat.delete")
    public void deleteMessage(
            @Payload DeleteChatMessage request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Principal principal = getAuthenticatedUser(headerAccessor);

        if (request == null || request.getMessageId() == null) {
            throw new IllegalArgumentException(
                    "Message ID là bắt buộc"
            );
        }

        // Service kiểm tra quyền sở hữu và đánh dấu tin nhắn đã xóa.
        Message deletedMessage = messageService.deleteMessage(
                principal.getName(),
                request.getMessageId()
        );

        // Không phát lại nội dung đã xóa cho các client.
        ChatMessage response = new ChatMessage();
        response.setType("MESSAGE_DELETED");
        response.setId(deletedMessage.getId());
        response.setConversationId(
                deletedMessage.getConversation().getId()
        );

        broadcast(response);
    }

    // Lấy danh tính từ phiên WebSocket đã xác thực JWT.
    private Principal getAuthenticatedUser(
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Principal principal = headerAccessor.getUser();

        if (principal == null) {
            throw new IllegalStateException(
                    "WebSocket chưa được xác thực"
            );
        }

        return principal;
    }

    // Chuyển Message entity thành dữ liệu gửi cho client.
    private ChatMessage toEvent(Message message, String type) {
        ChatMessage response = new ChatMessage();

        response.setType(type);
        response.setId(message.getId());
        response.setConversationId(message.getConversation().getId());
        response.setSender(message.getSender().getUsername());
        response.setContent(message.getContent());
        response.setCreatedAt(message.getCreatedAt());
        response.setEditedAt(message.getEditedAt());

        return response;
    }

    // Phát sự kiện đến tất cả client đăng ký topic của phòng chat.
    private void broadcast(ChatMessage message) {
        String destination = "/topic/conversations/"
                + message.getConversationId();

        messagingTemplate.convertAndSend(destination, message);
        if ("MESSAGE_CREATED".equals(message.getType())) {
            eventPublisher.publishToMembers(message.getConversationId(),
                new com.chatapp.dto.ChatMessageEvent(
                    message.getType(),
                    message.getId(),
                    message.getConversationId(),
                    message.getSender(),
                    message.getContent(),
                    message.getCreatedAt(),
                    message.getEditedAt()
                ));
        }
    }

    // DTO nhận dữ liệu từ client và phát sự kiện xuống client.
    public static class ChatMessage {

        private String type;
        private Long id;
        private Long conversationId;
        private String sender;
        private String content;
        private LocalDateTime createdAt;
        private LocalDateTime editedAt;

        public ChatMessage() {
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

    // DTO nhận yêu cầu sửa tin nhắn qua WebSocket.
    public static class EditChatMessage {

        private Long messageId;
        private String content;

        public EditChatMessage() {
        }

        public Long getMessageId() {
            return messageId;
        }

        public void setMessageId(Long messageId) {
            this.messageId = messageId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    // DTO nhận yêu cầu xóa tin nhắn qua WebSocket.
    public static class DeleteChatMessage {

        private Long messageId;

        public DeleteChatMessage() {
        }

        public Long getMessageId() {
            return messageId;
        }

        public void setMessageId(Long messageId) {
            this.messageId = messageId;
        }
    }
}
