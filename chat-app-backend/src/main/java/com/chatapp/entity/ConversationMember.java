package com.chatapp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_members")
public class ConversationMember {

    @EmbeddedId
    private ConversationMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    // Constructor mặc định để JPA sử dụng.
    public ConversationMember() {
    }

    // Tạo thành viên mới cho một cuộc trò chuyện.
    public ConversationMember(
            Conversation conversation,
            User user,
            MemberRole role
    ) {
        this.conversation = conversation;
        this.user = user;
        this.role = role;

        // Khởi tạo khóa chính ghép từ ID cuộc trò chuyện và ID người dùng.
        this.id = new ConversationMemberId(
                conversation.getId(),
                user.getId()
        );

        // Gán thời điểm tham gia để joined_at không bị NULL.
        this.joinedAt = LocalDateTime.now();
    }

    public ConversationMemberId getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public User getUser() {
        return user;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}