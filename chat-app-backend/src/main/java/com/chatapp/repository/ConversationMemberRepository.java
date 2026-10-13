package com.chatapp.repository;

import com.chatapp.entity.ConversationMember;
import com.chatapp.entity.ConversationMemberId;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Quản lý các thành viên tham gia từng cuộc trò chuyện.
@Repository
public interface ConversationMemberRepository
        extends JpaRepository<ConversationMember, ConversationMemberId> {

    // Lấy danh sách thành viên của một cuộc trò chuyện kèm thông tin user.
    @EntityGraph(attributePaths = {"user"})
    List<ConversationMember> findByConversation_Id(Long conversationId);

    // Tìm thành viên cụ thể trong một cuộc trò chuyện.
    Optional<ConversationMember> findByConversation_IdAndUser_Id(
            Long conversationId,
            Long userId
    );

    // Kiểm tra một user có tham gia cuộc trò chuyện hay không.
    boolean existsByConversation_IdAndUser_Id(
            Long conversationId,
            Long userId
    );

    // Lấy danh sách cuộc trò chuyện theo ID người dùng.
    List<ConversationMember> findByUser_Id(Long userId);

        // Lấy các cuộc trò chuyện còn hoạt động theo thời gian cập nhật mới nhất.
        @EntityGraph(attributePaths = {"conversation"})
        List<ConversationMember> findByUser_UsernameAndConversation_DeletedAtIsNullOrderByConversation_UpdatedAtDescConversation_IdDesc(
                        String username
        );
}