package com.chatapp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "messages",
        indexes = {
                // Hỗ trợ truy vấn tin nhắn theo cuộc trò chuyện và thời gian.
                @Index(
                        name = "idx_messages_conversation_time",
                        columnList = "conversation_id, created_at, id"
                ),

                // Hỗ trợ truy vấn tin nhắn theo người gửi.
                @Index(
                        name = "idx_messages_sender",
                        columnList = "sender_id"
                )
        }
)
public class Message {

    // Khóa chính của tin nhắn, do MySQL tự tăng.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nhiều tin nhắn có thể thuộc cùng một cuộc trò chuyện.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    // Một user có thể gửi nhiều tin nhắn.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // Nội dung tin nhắn.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Thời điểm tạo tin nhắn.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Thời điểm chỉnh sửa gần nhất.
    // null nếu tin nhắn chưa được chỉnh sửa.
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    // Thời điểm xóa mềm.
    // null nếu tin nhắn chưa bị xóa.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // JPA cần constructor rỗng để khởi tạo Entity.
    public Message() {
    }

    // Constructor dùng khi tạo tin nhắn mới trong Service.
    public Message(
            Conversation conversation,
            User sender,
            String content
    ) {
        this.conversation = conversation;
        this.sender = sender;
        this.content = content;
    }

    // Tự gán thời gian ngay trước khi JPA lưu Entity lần đầu.
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public User getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(LocalDateTime editedAt) {
        this.editedAt = editedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}