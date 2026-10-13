package com.chatapp.service;

import com.chatapp.dto.ChatMessageEvent;
import com.chatapp.entity.ConversationMember;
import com.chatapp.entity.User;
import com.chatapp.repository.ConversationMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationEventPublisherTest {

    @Mock
    private ConversationMemberRepository memberRepository;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private ConversationEventPublisher publisher;

    @Test
    void publishToMembers_SendsOnlyToPersistedConversationMembers() {
        User alice = user("alice");
        User bob = user("bob");
        ConversationMember aliceMember = member(alice);
        ConversationMember bobMember = member(bob);
        ChatMessageEvent event = new ChatMessageEvent(
                "MESSAGE_CREATED", 9L, 42L, "alice", "hello", null, null);
        when(memberRepository.findByConversation_Id(42L))
                .thenReturn(List.of(aliceMember, bobMember));

        publisher.publishToMembers(42L, event);

        verify(messagingTemplate).convertAndSendToUser("alice", "/queue/conversations", event);
        verify(messagingTemplate).convertAndSendToUser("bob", "/queue/conversations", event);
        verifyNoMoreInteractions(messagingTemplate);
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        return user;
    }

    private ConversationMember member(User user) {
        ConversationMember member = new ConversationMember();
        ReflectionTestUtils.setField(member, "user", user);
        return member;
    }
}