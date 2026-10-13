package com.chatapp.config;

import com.chatapp.entity.Conversation;
import com.chatapp.entity.User;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import com.chatapp.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ConversationMemberRepository memberRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    private WebSocketAuthInterceptor interceptor;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-that-is-long-enough-for-hmac-sha-256");
        interceptor = new WebSocketAuthInterceptor(
            jwtService,
                userRepository,
                memberRepository,
                conversationRepository,
                messageRepository,
                new ObjectMapper()
        );
    }

    @Test
    void subscribe_Forbidden_WhenUserIsNotConversationMember() {
        User outsider = new User();
        ReflectionTestUtils.setField(outsider, "id", 7L);
        outsider.setUsername("outsider");
        when(userRepository.findByUsername("outsider")).thenReturn(Optional.of(outsider));
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(new Conversation()));
        when(memberRepository.existsByConversation_IdAndUser_Id(42L, 7L)).thenReturn(false);

        Message<byte[]> message = subscribeMessage(42L, "outsider");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("không phải thành viên");
    }

    @Test
    void subscribe_AllowsConversationMember() {
        User member = new User();
        ReflectionTestUtils.setField(member, "id", 8L);
        member.setUsername("member");
        when(userRepository.findByUsername("member")).thenReturn(Optional.of(member));
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(new Conversation()));
        when(memberRepository.existsByConversation_IdAndUser_Id(42L, 8L)).thenReturn(true);

        Message<byte[]> message = subscribeMessage(42L, "member");

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void subscribe_AllowsAuthenticatedConversationQueue() {
        Message<byte[]> message = userQueueSubscription("member", "/user/queue/conversations");

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void subscribe_RejectsOtherUserQueueDestinations() {
        Message<byte[]> message = userQueueSubscription("member", "/user/queue/admin");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Destination không được phép");
    }

    @Test
    void connect_RejectsInvalidJwt() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalid-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("JWT không hợp lệ");
    }

    @Test
    void sendMessage_Forbidden_WhenUserIsNotConversationMember() {
        User outsider = new User();
        ReflectionTestUtils.setField(outsider, "id", 7L);
        outsider.setUsername("outsider");
        when(userRepository.findByUsername("outsider")).thenReturn(Optional.of(outsider));
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(new Conversation()));
        when(memberRepository.existsByConversation_IdAndUser_Id(42L, 7L)).thenReturn(false);

        Message<byte[]> message = sendMessage(42L, "outsider");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("không phải thành viên");
    }

    @Test
    void sendMessage_AllowsConversationMember() {
        User member = new User();
        ReflectionTestUtils.setField(member, "id", 8L);
        member.setUsername("member");
        when(userRepository.findByUsername("member")).thenReturn(Optional.of(member));
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(new Conversation()));
        when(memberRepository.existsByConversation_IdAndUser_Id(42L, 8L)).thenReturn(true);

        Message<byte[]> message = sendMessage(42L, "member");

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    private Message<byte[]> subscribeMessage(Long conversationId, String username) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/" + conversationId);
        accessor.setUser(authenticatedPrincipal(username));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> sendMessage(Long conversationId, String username) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat.send");
        accessor.setUser(authenticatedPrincipal(username));
        byte[] payload = ("{\"conversationId\":" + conversationId + "}")
                .getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    private Message<byte[]> userQueueSubscription(String username, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(authenticatedPrincipal(username));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal authenticatedPrincipal(String username) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + jwtService.generateToken(username));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(message, null);
        return StompHeaderAccessor.wrap(message).getUser();
    }
}