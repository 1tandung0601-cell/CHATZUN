package com.chatapp.controller;

import com.chatapp.dto.ChatMessageEvent;
import com.chatapp.dto.SendMessageRequest;
import com.chatapp.entity.Conversation;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.service.ConversationEventPublisher;
import com.chatapp.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    private MessageService messageService;

    private ConversationEventPublisher eventPublisher;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    private MessageController controller;

    private final List<String> actions = new java.util.ArrayList<>();
    private final List<ChatMessageEvent> publishedEvents = new java.util.ArrayList<>();

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

    @BeforeEach
    void setUp() {
        actions.clear();
        publishedEvents.clear();
        messageService = new MessageService(null, null, null, null) {
            @Override
            public Message sendMessage(String username, SendMessageRequest request) {
                actions.add("save");
                if ("fail".equals(request.getContent())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                }
                return savedMessage();
            }
        };
        eventPublisher = new ConversationEventPublisher(null, null) {
            @Override
            public void publishToMembers(Long conversationId, ChatMessageEvent event) {
                actions.add("publish:" + event.getType());
                publishedEvents.add(event);
            }
        };
        controller = new MessageController(messageService, eventPublisher, messagingTemplate);
    }

    @Test
    void sendMessage_PublishesPersistedMessageToTopicAndEveryMemberQueue() {
        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(42L);
        request.setContent("first message");
        Message message = savedMessage();
        controller.sendMessage(request, authentication);

        assertThat(message.getId()).isEqualTo(9L);
        assertThat(actions).containsExactly("save", "publish:MESSAGE_CREATED");
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/42"), any(ChatMessageEvent.class));
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).getType()).isEqualTo("MESSAGE_CREATED");
        assertThat(publishedEvents.get(0).getId()).isEqualTo(9L);
    }

    @Test
    void sendMessage_DoesNotPublishWhenPersistenceServiceFails() {
        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(42L);
        request.setContent("fail");

        assertThatThrownBy(() -> controller.sendMessage(request, authentication))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(actions).containsExactly("save");
        assertThat(publishedEvents).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(messagingTemplate);
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