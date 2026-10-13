package com.chatapp.controller;

import com.chatapp.dto.AddMembersRequest;
import com.chatapp.dto.ChatMessageEvent;
import com.chatapp.dto.ConversationResponse;
import com.chatapp.dto.CreateDirectConversationRequest;
import com.chatapp.dto.CreateGroupConversationRequest;
import com.chatapp.dto.MemberResponse;
import com.chatapp.service.ConversationEventPublisher;
import com.chatapp.service.ConversationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
        private final ConversationEventPublisher eventPublisher;
        private final SimpMessageSendingOperations messagingTemplate;

    public ConversationController(
            ConversationService conversationService,
                        ConversationEventPublisher eventPublisher,
            SimpMessageSendingOperations messagingTemplate
    ) {
        this.conversationService = conversationService;
                this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    // Lấy danh sách cuộc trò chuyện của tài khoản đang đăng nhập.
    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getMyConversations(
            Authentication authentication
    ) {
        // Username lấy từ danh tính đã được Spring Security xác thực.
        String currentUsername = authentication.getName();

        List<ConversationResponse> conversations =
                conversationService.getMyConversations(currentUsername);

        return ResponseEntity.ok(conversations);
    }

    // Mở hoặc lấy lại cuộc trò chuyện trực tiếp với một người.
    @PostMapping("/direct")
    public ResponseEntity<ConversationResponse> createDirectConversation(
            @Valid @RequestBody CreateDirectConversationRequest request,
            Authentication authentication
    ) {
        String currentUsername = authentication.getName();

        ConversationResponse response =
                conversationService.createDirectConversation(
                        currentUsername,
                        request.getParticipantUsername()
                );

        return ResponseEntity.ok(response);
    }

    // Tạo cuộc trò chuyện nhóm mới.
    // POST /api/conversations/group
    @PostMapping("/group")
    public ResponseEntity<ConversationResponse> createGroupConversation(
            @Valid @RequestBody CreateGroupConversationRequest request,
            Authentication authentication
    ) {
        String currentUsername = authentication.getName();

        ConversationResponse response =
                conversationService.createGroupConversation(
                        currentUsername,
                        request
                );

        ChatMessageEvent event = new ChatMessageEvent(
                "CONVERSATION_CREATED",
                null,
                response.conversationId(),
                currentUsername,
                null,
                response.createdAt(),
                null
        );
        eventPublisher.publishToMembers(response.conversationId(), event);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Lấy danh sách thành viên của một cuộc trò chuyện.
    // GET /api/conversations/{conversationId}/members
    @GetMapping("/{conversationId}/members")
    public ResponseEntity<List<MemberResponse>> getGroupMembers(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        String currentUsername = authentication.getName();
        List<MemberResponse> members = conversationService.getGroupMembers(currentUsername, conversationId);
        return ResponseEntity.ok(members);
    }

    // Thêm một hoặc nhiều thành viên vào nhóm chat.
    // POST /api/conversations/{conversationId}/members
    @PostMapping("/{conversationId}/members")
    public ResponseEntity<List<MemberResponse>> addMembers(
            @PathVariable Long conversationId,
            @Valid @RequestBody AddMembersRequest request,
            Authentication authentication
    ) {
        String currentUsername = authentication.getName();
        List<MemberResponse> updatedMembers = conversationService.addMembersToGroup(
                currentUsername,
                conversationId,
                request
        );

        ChatMessageEvent event = new ChatMessageEvent();
        event.setType("CONVERSATION_MEMBERS_ADDED");
        event.setConversationId(conversationId);
        event.setSender(currentUsername);
        event.setContent(String.join(", ", request.getUsernames()));
        event.setCreatedAt(LocalDateTime.now());
        eventPublisher.publishToMembers(conversationId, event);

        // Phát sự kiện WebSocket báo cho tất cả client trong phòng biết danh sách thành viên đã cập nhật
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, event);

        return ResponseEntity.ok(updatedMembers);
    }

    // Xóa nhóm chat (chỉ chủ nhóm mới có quyền xóa).
    // DELETE /api/conversations/{conversationId}
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        String currentUsername = authentication.getName();
        conversationService.deleteGroupConversation(currentUsername, conversationId);

        // Phát sự kiện WebSocket thông báo nhóm đã bị xóa cho tất cả client
        ChatMessageEvent event = new ChatMessageEvent();
        event.setType("CONVERSATION_DELETED");
        event.setConversationId(conversationId);
        event.setSender(currentUsername);
        event.setContent("Nhóm đã bị xóa bởi chủ nhóm");
        event.setCreatedAt(LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, event);

        return ResponseEntity.noContent().build();
    }
}