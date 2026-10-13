package com.chatapp.service;

import com.chatapp.entity.Conversation;
import com.chatapp.entity.ConversationType;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MessageService messageService;

    private User alice;
    private User bob;
    private Conversation conversation;
    private Message message;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(messageService, "editDeleteTimeoutMinutes", 15);

        alice = new User();
        setEntityId(alice, 1L);
        alice.setUsername("alice");

        bob = new User();
        setEntityId(bob, 2L);
        bob.setUsername("bob");

        conversation = new Conversation();
        setEntityId(conversation, 100L);
        conversation.setConversationType(ConversationType.DIRECT);
        conversation.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        conversation.setUpdatedAt(LocalDateTime.now().minusMinutes(5));

        message = new Message(conversation, alice, "Hello world");
        setEntityId(message, 500L);
        // Tin nhắn được gửi cách đây 2 phút (trong thời hạn 15 phút)
        setCreatedAt(message, LocalDateTime.now().minusMinutes(2));
    }

    private void setEntityId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }

    private void setCreatedAt(Message msg, LocalDateTime time) throws Exception {
        Field field = msg.getClass().getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(msg, time);
    }

    @Test
    void editMessage_Success_WithinTimeout() {
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(memberRepository.existsByConversation_IdAndUser_Id(100L, 1L)).thenReturn(true);

        Message updated = messageService.editMessage("alice", 500L, "Hello world edited");

        assertThat(updated.getContent()).isEqualTo("Hello world edited");
        assertThat(updated.getEditedAt()).isNotNull();
    }

    @Test
    void editMessage_Forbidden_WhenNotOwner() {
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.editMessage("bob", 500L, "Hacked content"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("chính mình");
                });
    }

    @Test
    void editMessage_Forbidden_WhenNotConversationMember() {
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(memberRepository.existsByConversation_IdAndUser_Id(100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.editMessage("alice", 500L, "New text"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("thành viên");
                });
    }

    @Test
    void editMessage_Forbidden_WhenExpiredAfter15Minutes() throws Exception {
        // Tin nhắn tạo cách đây 20 phút (quá hạn 15 phút)
        setCreatedAt(message, LocalDateTime.now().minusMinutes(20));

        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(memberRepository.existsByConversation_IdAndUser_Id(100L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> messageService.editMessage("alice", 500L, "Late edit"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("Đã quá 15 phút");
                });
    }

    @Test
    void deleteMessage_Success_WithinTimeout() {
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(memberRepository.existsByConversation_IdAndUser_Id(100L, 1L)).thenReturn(true);

        Message deleted = messageService.deleteMessage("alice", 500L);

        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteMessage_Forbidden_WhenNotOwner() {
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.deleteMessage("bob", 500L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("chính mình");
                });
    }

    @Test
    void deleteMessage_Forbidden_WhenSenderIsNoLongerConversationMember() {
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(memberRepository.existsByConversation_IdAndUser_Id(100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.deleteMessage("alice", 500L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("không phải thành viên");
                });

        assertThat(message.getDeletedAt()).isNull();
    }

    @Test
    void deleteMessage_Forbidden_WhenExpiredAfter15Minutes() throws Exception {
        setCreatedAt(message, LocalDateTime.now().minusMinutes(16));

        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(memberRepository.existsByConversation_IdAndUser_Id(100L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> messageService.deleteMessage("alice", 500L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("Đã quá 15 phút");
                });
    }

    @Test
    void deleteMessage_NotFound_WhenAlreadyDeleted() {
        message.setDeletedAt(LocalDateTime.now().minusMinutes(1));
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.deleteMessage("alice", 500L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("đã bị xóa");
                });
    }

    @Test
    void sendMessage_BadRequest_WhenConversationIsDeleted() {
        conversation.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));

        com.chatapp.dto.SendMessageRequest req = new com.chatapp.dto.SendMessageRequest();
        req.setConversationId(100L);
        req.setContent("Hello");

        assertThatThrownBy(() -> messageService.sendMessage("alice", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("đã bị xóa");
                });
    }

            @Test
            void getConversationMessages_Forbidden_WhenUserIsNotMember() {
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
            when(memberRepository.existsByConversation_IdAndUser_Id(100L, 2L)).thenReturn(false);

            assertThatThrownBy(() -> messageService.getConversationMessages(
                "bob", 100L, org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));

            org.mockito.Mockito.verify(messageRepository, org.mockito.Mockito.never())
                .findByConversation_IdAndDeletedAtIsNull(anyLong(), any());
            }

            @Test
            void sendMessage_Forbidden_WhenUserIsNotMember() {
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
            when(memberRepository.existsByConversation_IdAndUser_Id(100L, 2L)).thenReturn(false);

            com.chatapp.dto.SendMessageRequest request = new com.chatapp.dto.SendMessageRequest();
            request.setConversationId(100L);
            request.setContent("Unauthorized message");

            assertThatThrownBy(() -> messageService.sendMessage("bob", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));

            org.mockito.Mockito.verify(messageRepository, org.mockito.Mockito.never())
                .save(any(Message.class));
            }

            @Test
            void sendMessage_Succeeds_WhenUserIsMember() throws Exception {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
            when(memberRepository.existsByConversation_IdAndUser_Id(100L, 1L)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                Message saved = invocation.getArgument(0);
                setCreatedAt(saved, LocalDateTime.now());
                return saved;
            });

            com.chatapp.dto.SendMessageRequest request = new com.chatapp.dto.SendMessageRequest();
            request.setConversationId(100L);
            request.setContent("Member message");

            Message saved = messageService.sendMessage("alice", request);

            assertThat(saved.getContent()).isEqualTo("Member message");
            org.mockito.Mockito.verify(conversationRepository)
                .advanceActivityTime(100L, saved.getCreatedAt());
            }

    @Test
    void editMessage_BadRequest_WhenConversationIsDeleted() {
        conversation.setDeletedAt(LocalDateTime.now());
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.editMessage("alice", 500L, "New text"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("đã bị xóa");
                });
    }
}
