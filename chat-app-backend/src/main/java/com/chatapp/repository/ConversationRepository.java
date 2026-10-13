
package com.chatapp.repository;

import com.chatapp.entity.Conversation;
import com.chatapp.entity.ConversationType;
import com.chatapp.entity.ConversationMember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

import java.util.Optional;

// Repository thao tác với bảng conversations thông qua JPA.
public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    // Tìm cuộc trò chuyện DIRECT đã tồn tại giữa hai người.
    // Chỉ lấy cuộc trò chuyện có đúng hai thành viên.
    @Query("""
        SELECT c
        FROM Conversation c
        WHERE c.conversationType = :type
          AND (
              SELECT COUNT(m)
              FROM ConversationMember m
              WHERE m.conversation.id = c.id
          ) = 2
          AND EXISTS (
              SELECT m1
              FROM ConversationMember m1
              WHERE m1.conversation.id = c.id
                AND m1.user.id = :userId1
          )
          AND EXISTS (
              SELECT m2
              FROM ConversationMember m2
              WHERE m2.conversation.id = c.id
                AND m2.user.id = :userId2
          )
        """)
    Optional<Conversation> findDirectBetweenUsers(
            @Param("type") ConversationType type,
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    @Modifying
    @Query("""
        update Conversation c
        set c.updatedAt = case
          when c.updatedAt < :activityAt then :activityAt
          else c.updatedAt
        end
        where c.id = :conversationId
        """)
    int advanceActivityTime(
        @Param("conversationId") Long conversationId,
        @Param("activityAt") LocalDateTime activityAt
    );
}