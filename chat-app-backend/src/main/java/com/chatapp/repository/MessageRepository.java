
package com.chatapp.repository;

import com.chatapp.entity.Message;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// Repository xử lý việc đọc và lưu tin nhắn.
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Lấy tin nhắn chưa bị xóa theo từng trang.
    // Tải kèm thông tin người gửi để tránh lỗi LazyInitializationException.
    // Thứ tự: thời gian tăng dần, nếu trùng thời gian thì theo ID tăng dần.
    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findByConversation_IdAndDeletedAtIsNull(
            Long conversationId,
            Pageable pageable
    );

    // Lấy tin nhắn của một user.
    List<Message> findBySender_Id(Long senderId);
// Lấy tin nhắn mới nhất chưa bị xóa trong một cuộc trò chuyện.
    @EntityGraph(attributePaths = {"sender"})
    Optional<Message> findFirstByConversation_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long conversationId
    );

    @Query("select m.conversation.id from Message m where m.id = :messageId and m.deletedAt is null")
    Optional<Long> findActiveConversationIdById(@Param("messageId") Long messageId);

}
