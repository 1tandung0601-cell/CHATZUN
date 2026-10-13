
package com.chatapp.config;

import com.chatapp.entity.Conversation;
import com.chatapp.entity.User;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import com.chatapp.service.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.io.IOException;
import java.util.Collections;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ConversationMemberRepository memberRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public WebSocketAuthInterceptor(
            JwtService jwtService,
            UserRepository userRepository,
            ConversationMemberRepository memberRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // Bước 1: Xác thực JWT khi client kết nối.
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String authorization =
                    accessor.getFirstNativeHeader("Authorization");

            if (authorization == null
                    || !authorization.startsWith("Bearer ")) {
                throw new MessagingException("Thiếu JWT trong STOMP CONNECT");
            }

            String token = authorization.substring(7);

            if (!jwtService.isTokenValid(token)) {
                throw new MessagingException("JWT không hợp lệ hoặc đã hết hạn");
            }

            String username = jwtService.extractUsername(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username, null, Collections.emptyList());

            accessor.setUser(authentication);
        }

        // Bước 2: Kiểm tra quyền khi client đăng ký nhận tin.
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {

            Principal principal = accessor.getUser();

            if (principal == null) {
                throw new MessagingException("WebSocket chưa được xác thực");
            }

            String destination = accessor.getDestination();

            if ("/user/queue/conversations".equals(destination)) {
                return message;
            }

            // Chỉ cho phép subscribe vào topic conversation hợp lệ.
            String prefix = "/topic/conversations/";

            if (destination == null || !destination.startsWith(prefix)) {
                throw new MessagingException("Destination không được phép");
            }

            String conversationIdText =
                    destination.substring(prefix.length());

            // Không cho phép ID rỗng hoặc đường dẫn có thêm dấu /.
            if (conversationIdText.isBlank()
                    || conversationIdText.contains("/")) {
                throw new MessagingException("Conversation ID không hợp lệ");
            }

            final Long conversationId;

            try {
                conversationId = Long.parseLong(conversationIdText);
            } catch (NumberFormatException exception) {
                throw new MessagingException("Conversation ID không hợp lệ");
            }

            // Lấy danh tính từ JWT, không tin username do client tự gửi.
            assertConversationMember(principal, conversationId);
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            Principal principal = accessor.getUser();
            if (principal == null) {
                throw new MessagingException("WebSocket chưa được xác thực");
            }

            String destination = accessor.getDestination();
            JsonNode payload = parsePayload(message.getPayload());
            Long conversationId;

            if ("/app/chat.send".equals(destination)) {
                conversationId = readLong(payload, "conversationId");
            } else if ("/app/chat.edit".equals(destination)
                    || "/app/chat.delete".equals(destination)) {
                Long messageId = readLong(payload, "messageId");
                conversationId = messageRepository.findActiveConversationIdById(messageId)
                        .orElseThrow(() -> new MessagingException("Không tìm thấy tin nhắn"));
            } else {
                throw new MessagingException("Destination không được phép");
            }

            assertConversationMember(principal, conversationId);
        }

        return message;
    }

    private void assertConversationMember(Principal principal, Long conversationId) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new MessagingException("Không tìm thấy người dùng"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new MessagingException("Không tìm thấy cuộc trò chuyện"));

        if (conversation.getDeletedAt() != null) {
            throw new MessagingException("Cuộc trò chuyện này đã bị xóa hoặc vô hiệu hóa");
        }

        if (!memberRepository.existsByConversation_IdAndUser_Id(conversationId, user.getId())) {
            throw new MessagingException("Bạn không phải thành viên của phòng chat này");
        }
    }

    private JsonNode parsePayload(Object payload) {
        try {
            if (payload instanceof byte[] bytes) {
                return objectMapper.readTree(bytes);
            }
            if (payload instanceof String text) {
                return objectMapper.readTree(text);
            }
        } catch (IOException exception) {
            throw new MessagingException("Payload STOMP không hợp lệ", exception);
        }
        throw new MessagingException("Payload STOMP không hợp lệ");
    }

    private Long readLong(JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.canConvertToLong() || value.asLong() < 1) {
            throw new MessagingException(fieldName + " không hợp lệ");
        }
        return value.asLong();
    }
}
