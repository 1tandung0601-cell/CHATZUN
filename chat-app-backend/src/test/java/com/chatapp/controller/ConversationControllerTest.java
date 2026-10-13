package com.chatapp.controller;

import com.chatapp.dto.AddMembersRequest;
import com.chatapp.dto.ChatMessageEvent;
import com.chatapp.dto.ConversationResponse;
import com.chatapp.dto.CreateGroupConversationRequest;
import com.chatapp.dto.MemberResponse;
import com.chatapp.entity.ConversationType;
import com.chatapp.entity.MemberRole;
import com.chatapp.service.ConversationEventPublisher;
import com.chatapp.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    private ConversationService conversationService;

    private ConversationEventPublisher eventPublisher;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    private ConversationController controller;

        private final List<String> actions = new java.util.ArrayList<>();

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

        @BeforeEach
        void setUp() {
                actions.clear();
                conversationService = new ConversationService(null, null, null, null) {
                        @Override
                        public ConversationResponse createGroupConversation(
                                        String username,
                                        CreateGroupConversationRequest request
                        ) {
                                actions.add("create");
                                return new ConversationResponse(
                                                42L, ConversationType.GROUP, "Team", null, null,
                                                LocalDateTime.now(), LocalDateTime.now(), "alice", 2, null);
                        }

                        @Override
                        public List<MemberResponse> addMembersToGroup(
                                        String username,
                                        Long conversationId,
                                        AddMembersRequest request
                        ) {
                                actions.add("add-members");
                                return List.of(
                                                new MemberResponse(1L, "alice", MemberRole.ADMIN, LocalDateTime.now()),
                                                new MemberResponse(2L, "bob", MemberRole.MEMBER, LocalDateTime.now())
                                );
                        }
                };
                eventPublisher = new ConversationEventPublisher(null, null) {
                        @Override
                        public void publishToMembers(Long conversationId, ChatMessageEvent event) {
                                actions.add("publish:" + event.getType());
                        }
                };
                controller = new ConversationController(conversationService, eventPublisher, messagingTemplate);
        }

    @Test
    void createGroup_NotifiesEveryPersistedMemberAfterServiceReturns() {
        CreateGroupConversationRequest request =
                new CreateGroupConversationRequest("Team", List.of("bob"));
        var response = controller.createGroupConversation(request, authentication);

        assertThat(response.getBody().conversationId()).isEqualTo(42L);
        assertThat(actions).containsExactly("create", "publish:CONVERSATION_CREATED");
    }

    @Test
    void addMembers_NotifiesMemberQueueAndOpenGroupTopicAfterServiceReturns() {
        AddMembersRequest request = new AddMembersRequest(List.of("bob"));
        List<MemberResponse> members = controller.addMembers(42L, request, authentication).getBody();

        assertThat(members).hasSize(2);
        assertThat(actions).containsExactly("add-members", "publish:CONVERSATION_MEMBERS_ADDED");
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/42"), any(ChatMessageEvent.class));
    }
}