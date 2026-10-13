package com.chatapp.controller;

import com.chatapp.dto.ChatMessageEvent;
import com.chatapp.entity.Conversation;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.service.ConversationEventPublisher;
import com.chatapp.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    private final List<String> actions = new ArrayList<>();
    private final List<ChatMessageEvent> publishedEvents = new ArrayList<>();
    private ChatWebSocketController controller;

    @BeforeEach
    void setUp() {
        actions.clear();
        publishedEvents.clear();

        MessageService messageService = new MessageService(null, null, null, null) {
            @Override
            public Message sendMessage(String username, com.chatapp.dto.SendMessageRequest request) {
                actions.add("save");
                if ("fail".equals(request.getContent())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                }
                return savedMessage();
            }
        };
        ConversationEventPublisher eventPublisher = new ConversationEventPublisher(null, null) {
            @Override
            public void publishToMembers(Long conversationId, ChatMessageEvent event) {
                actions.add("notify-members");
                publishedEvents.add(event);
            }
        };
        controller = new ChatWebSocketController(messageService, eventPublisher, messagingTemplate);
    }

    @Test
    void sendMessage_NotifiesEveryMemberForFirstMessageAfterSave() {
        ChatWebSocketController.ChatMessage request = new ChatWebSocketController.ChatMessage();
        request.setConversationId(42L);
        request.setContent("first message");

        controller.sendMessage(request, authenticatedHeaders());

        assertThat(actions).containsExactly("save", "notify-members");
        assertThat(publishedEvents).singleElement().satisfies(event -> {
            assertThat(event.getType()).isEqualTo("MESSAGE_CREATED");
            assertThat(event.getConversationId()).isEqualTo(42L);
        });
        verify(messagingTemplate).convertAndSend(
            eq("/topic/conversations/42"),
            any(ChatWebSocketController.ChatMessage.class)
        );
    }

    @Test
    void sendMessage_DoesNotNotifyWhenSavingFails() {
        ChatWebSocketController.ChatMessage request = new ChatWebSocketController.ChatMessage();
        request.setConversationId(42L);
        request.setContent("fail");

        assertThatThrownBy(() -> controller.sendMessage(request, authenticatedHeaders()))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(actions).containsExactly("save");
        assertThat(publishedEvents).isEmpty();
        verifyNoInteractions(messagingTemplate);
    }

    private SimpMessageHeaderAccessor authenticatedHeaders() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("alice", null, List.of()));
        return accessor;
    }

    private Message savedMessage() {
        Conversation conversation = new Conversation();
        ReflectionTestUtils.setField(conversation, "id", 42L);
        User sender = new User();
        sender.setUsername("alice");
        Message message = new Message(conversation, sender, "first message");
        ReflectionTestUtils.setField(message, "id", 9L);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.now());
        return message;
    }
}