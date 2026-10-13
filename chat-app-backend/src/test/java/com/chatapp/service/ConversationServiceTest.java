package com.chatapp.service;

import com.chatapp.dto.AddMembersRequest;
import com.chatapp.dto.ConversationResponse;
import com.chatapp.dto.CreateGroupConversationRequest;
import com.chatapp.dto.MemberResponse;
import com.chatapp.entity.Conversation;
import com.chatapp.entity.ConversationMember;
import com.chatapp.entity.ConversationType;
import com.chatapp.entity.MemberRole;
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
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ConversationService conversationService;

    private User alice;
    private User bob;
    private User charlie;
    private User david;

    private Conversation groupConv;
    private Conversation directConv;

    @BeforeEach
    void setUp() throws Exception {
        alice = new User();
        setEntityId(alice, 1L);
        alice.setUsername("alice");

        bob = new User();
        setEntityId(bob, 2L);
        bob.setUsername("bob");

        charlie = new User();
        setEntityId(charlie, 3L);
        charlie.setUsername("charlie");

        david = new User();
        setEntityId(david, 4L);
        david.setUsername("david");

        groupConv = new Conversation();
        setEntityId(groupConv, 200L);
        groupConv.setConversationType(ConversationType.GROUP);
        groupConv.setName("Dev Team");
        groupConv.setOwner(alice); // Alice là chủ nhóm
        groupConv.setCreatedAt(LocalDateTime.now().minusDays(1));
        groupConv.setUpdatedAt(LocalDateTime.now().minusDays(1));

        directConv = new Conversation();
        setEntityId(directConv, 100L);
        directConv.setConversationType(ConversationType.DIRECT);
        directConv.setCreatedAt(LocalDateTime.now().minusDays(1));
        directConv.setUpdatedAt(LocalDateTime.now().minusDays(1));
    }

    private void setEntityId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }

    @Test
    void createGroupConversation_Success() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(userRepository.findByUsername("charlie")).thenReturn(Optional.of(charlie));

        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation c = invocation.getArgument(0);
            setEntityId(c, 200L);
            return c;
        });

        CreateGroupConversationRequest req = new CreateGroupConversationRequest(
                "Team Rocket",
            List.of("bob", "BOB", "charlie")
        );

        ConversationResponse response = conversationService.createGroupConversation("alice", req);

        assertThat(response).isNotNull();
        assertThat(response.conversationId()).isEqualTo(200L);
        assertThat(response.conversationType()).isEqualTo(ConversationType.GROUP);
        assertThat(response.name()).isEqualTo("Team Rocket");
        assertThat(response.ownerUsername()).isEqualTo("alice");

        // Xác minh lưu 3 thành viên (1 creator ADMIN + 2 members)
        verify(memberRepository, times(3)).save(any(ConversationMember.class));
    }

        @Test
        void getMyConversations_UsesDatabaseOrderedMemberships() {
        ConversationMember newest = new ConversationMember(groupConv, alice, MemberRole.ADMIN);
        ConversationMember older = new ConversationMember(directConv, alice, MemberRole.MEMBER);
        when(memberRepository.findByUser_UsernameAndConversation_DeletedAtIsNullOrderByConversation_UpdatedAtDescConversation_IdDesc("alice"))
            .thenReturn(List.of(newest, older));
        when(memberRepository.findByConversation_Id(200L)).thenReturn(List.of(newest));
        when(memberRepository.findByConversation_Id(100L)).thenReturn(List.of(older));
        when(messageRepository.findFirstByConversation_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(anyLong()))
            .thenReturn(Optional.empty());

        List<ConversationResponse> result = conversationService.getMyConversations("alice");

        assertThat(result).extracting(ConversationResponse::conversationId)
            .containsExactly(200L, 100L);
        verify(memberRepository)
                .findByUser_UsernameAndConversation_DeletedAtIsNullOrderByConversation_UpdatedAtDescConversation_IdDesc("alice");
        }

        @Test
        void getGroupMembers_Forbidden_WhenUserIsNotMember() {
        when(userRepository.findByUsername("david")).thenReturn(Optional.of(david));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));
        when(memberRepository.existsByConversation_IdAndUser_Id(200L, 4L)).thenReturn(false);

        assertThatThrownBy(() -> conversationService.getGroupMembers("david", 200L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));

        verify(memberRepository, never()).findByConversation_Id(200L);
        }

        @Test
        void getGroupMembers_ReturnsMembers_WhenUserIsMember() {
        ConversationMember aliceMember = new ConversationMember(groupConv, alice, MemberRole.ADMIN);
        ConversationMember bobMember = new ConversationMember(groupConv, bob, MemberRole.MEMBER);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));
        when(memberRepository.existsByConversation_IdAndUser_Id(200L, 2L)).thenReturn(true);
        when(memberRepository.findByConversation_Id(200L)).thenReturn(List.of(aliceMember, bobMember));

        List<MemberResponse> result = conversationService.getGroupMembers("bob", 200L);

        assertThat(result).extracting(MemberResponse::username)
            .containsExactly("alice", "bob");
        }

    @Test
    void addMembersToGroup_Success_WhenNormalMemberAddsUsers() {
        // Bob là thành viên bình thường (MEMBER)
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));
        when(memberRepository.existsByConversation_IdAndUser_Id(200L, 2L)).thenReturn(true);

        ConversationMember aliceMember = new ConversationMember(groupConv, alice, MemberRole.ADMIN);
        ConversationMember bobMember = new ConversationMember(groupConv, bob, MemberRole.MEMBER);
        when(memberRepository.findByConversation_Id(200L)).thenReturn(new ArrayList<>(List.of(aliceMember, bobMember)));

        when(userRepository.findByUsername("david")).thenReturn(Optional.of(david));

        AddMembersRequest req = new AddMembersRequest(List.of("david", "DAVID"));
        List<MemberResponse> result = conversationService.addMembersToGroup("bob", 200L, req);

        verify(memberRepository, times(1)).save(any(ConversationMember.class));
        verify(conversationRepository, times(1)).save(groupConv);
    }

    @Test
    void addMembersToGroup_Forbidden_WhenNotMember() {
        when(userRepository.findByUsername("david")).thenReturn(Optional.of(david));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));
        when(memberRepository.existsByConversation_IdAndUser_Id(200L, 4L)).thenReturn(false);

        AddMembersRequest req = new AddMembersRequest(List.of("charlie"));

        assertThatThrownBy(() -> conversationService.addMembersToGroup("david", 200L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("không phải thành viên");
                });
    }

    @Test
    void addMembersToGroup_BadRequest_WhenDirectConversation() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(directConv));

        AddMembersRequest req = new AddMembersRequest(List.of("david"));

        assertThatThrownBy(() -> conversationService.addMembersToGroup("alice", 100L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Chỉ có thể thêm thành viên vào cuộc trò chuyện nhóm");
                });
    }

    @Test
    void addMembersToGroup_BadRequest_WhenConversationIsDeleted() {
        groupConv.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));

        AddMembersRequest req = new AddMembersRequest(List.of("david"));

        assertThatThrownBy(() -> conversationService.addMembersToGroup("alice", 200L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("đã bị xóa");
                });
    }

    @Test
    void addMembersToGroup_BadRequest_WhenAllUsersAlreadyInGroup() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));
        when(memberRepository.existsByConversation_IdAndUser_Id(200L, 1L)).thenReturn(true);

        ConversationMember bobMember = new ConversationMember(groupConv, bob, MemberRole.MEMBER);
        when(memberRepository.findByConversation_Id(200L)).thenReturn(List.of(bobMember));

        AddMembersRequest req = new AddMembersRequest(List.of("bob"));

        assertThatThrownBy(() -> conversationService.addMembersToGroup("alice", 200L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("đã có trong nhóm");
                });
    }

    @Test
    void addMembersToGroup_NotFound_WhenUserDoesNotExist() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));
        when(memberRepository.existsByConversation_IdAndUser_Id(200L, 1L)).thenReturn(true);
        when(memberRepository.findByConversation_Id(200L)).thenReturn(List.of());

        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        AddMembersRequest req = new AddMembersRequest(List.of("ghost"));

        assertThatThrownBy(() -> conversationService.addMembersToGroup("alice", 200L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("Không tìm thấy người dùng");
                });
    }

    @Test
    void deleteGroupConversation_Success_WhenOwnerDeletes() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));

        conversationService.deleteGroupConversation("alice", 200L);

        assertThat(groupConv.getDeletedAt()).isNotNull();
        verify(conversationRepository, times(1)).save(groupConv);
    }

    @Test
    void deleteGroupConversation_Forbidden_WhenNormalMemberTriesToDelete() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));

        assertThatThrownBy(() -> conversationService.deleteGroupConversation("bob", 200L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("Chỉ chủ nhóm");
                });
    }

    @Test
    void deleteGroupConversation_BadRequest_WhenDirectConversation() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(directConv));

        assertThatThrownBy(() -> conversationService.deleteGroupConversation("alice", 100L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Không thể xóa cuộc trò chuyện trực tiếp");
                });
    }

    @Test
    void deleteGroupConversation_BadRequest_WhenAlreadyDeleted() {
        groupConv.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(groupConv));

        assertThatThrownBy(() -> conversationService.deleteGroupConversation("alice", 200L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("đã bị xóa");
                });
    }

    @Test
    void deleteGroupConversation_NotFound_WhenConversationDoesNotExist() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.deleteGroupConversation("alice", 999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}
