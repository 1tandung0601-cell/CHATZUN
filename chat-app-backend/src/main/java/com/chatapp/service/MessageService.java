
package com.chatapp.service;

import com.chatapp.dto.SendMessageRequest;
import com.chatapp.entity.Conversation;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Value("${chat.message.edit-delete-timeout-minutes:15}")
    private int editDeleteTimeoutMinutes = 15;

    // Spring tự động cung cấp các Repository cần thiết.
    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    // Lấy lịch sử tin nhắn theo từng trang.
    @Transactional(readOnly = true)
    public Page<Message> getConversationMessages(
            String currentUsername,
            Long conversationId,
            Pageable pageable
    ) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy người dùng"
                ));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy cuộc trò chuyện"
                ));

        if (conversation.getDeletedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cuộc trò chuyện này đã bị xóa hoặc vô hiệu hóa"
            );
        }

        // Chỉ thành viên của cuộc trò chuyện mới được xem lịch sử.
        boolean isMember =
                memberRepository.existsByConversation_IdAndUser_Id(
                        conversationId,
                        currentUser.getId()
                );

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không phải thành viên của cuộc trò chuyện này"
            );
        }

        return messageRepository.findByConversation_IdAndDeletedAtIsNull(
                conversationId,
                pageable
        );
    }

    // Gửi tin nhắn mới.
    @Transactional
    public Message sendMessage(
            String currentUsername,
            SendMessageRequest request
    ) {
        User sender = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy người gửi"
                ));

        Conversation conversation = conversationRepository
                .findById(request.getConversationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy cuộc trò chuyện"
                ));

        if (conversation.getDeletedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cuộc trò chuyện này đã bị xóa hoặc vô hiệu hóa"
            );
        }

        // Kiểm tra người gửi có phải thành viên không.
        boolean isMember =
                memberRepository.existsByConversation_IdAndUser_Id(
                        conversation.getId(),
                        sender.getId()
                );

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không phải thành viên của cuộc trò chuyện này"
            );
        }

        Message message = new Message(
                conversation,
                sender,
                request.getContent().trim()
        );

        Message savedMessage = messageRepository.save(message);

        // Keep the activity timestamp aligned with the latest persisted message.
        conversationRepository.advanceActivityTime(
                conversation.getId(),
                savedMessage.getCreatedAt()
        );

        return savedMessage;
    }

    // Sửa nội dung tin nhắn của chính mình.
    @Transactional
    public Message editMessage(
            String currentUsername,
            Long messageId,
            String newContent
    ) {
        Message message = findOwnActiveMessage(currentUsername, messageId);

        String content = newContent == null ? "" : newContent.trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nội dung tin nhắn không được để trống"
            );
        }

        // Cập nhật nội dung và thời điểm sửa.
        message.setContent(content);
        message.setEditedAt(LocalDateTime.now());

        // Cập nhật thời gian hoạt động của cuộc trò chuyện.
        message.getConversation().setUpdatedAt(LocalDateTime.now());

        return message;
    }

    // Xóa mềm tin nhắn của chính mình.
    @Transactional
    public Message deleteMessage(
            String currentUsername,
            Long messageId
    ) {
        Message message = findOwnActiveMessage(currentUsername, messageId);

        // Không xóa bản ghi khỏi database, chỉ đánh dấu đã xóa.
        message.setDeletedAt(LocalDateTime.now());

        // Cập nhật thời gian hoạt động của cuộc trò chuyện.
        message.getConversation().setUpdatedAt(LocalDateTime.now());

        return message;
    }

    // Tìm tin nhắn chưa bị xóa và xác minh quyền sở hữu, quyền thành viên và thời hạn.
    private Message findOwnActiveMessage(
            String currentUsername,
            Long messageId
    ) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy tin nhắn"
                ));

        if (message.getDeletedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Tin nhắn đã bị xóa"
            );
        }

        if (message.getConversation().getDeletedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cuộc trò chuyện này đã bị xóa hoặc vô hiệu hóa"
            );
        }

        if (!message.getSender().getUsername().equals(currentUsername)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn chỉ được sửa hoặc xóa tin nhắn của chính mình"
            );
        }

        // Kiểm tra người gửi có còn là thành viên của cuộc trò chuyện hay không
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy người dùng"
                ));

        boolean isMember = memberRepository.existsByConversation_IdAndUser_Id(
                message.getConversation().getId(),
                currentUser.getId()
        );

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không phải thành viên của cuộc trò chuyện này"
            );
        }

        // Cho phép sửa hoặc xóa trong thời hạn cấu hình tính từ khi gửi
        LocalDateTime deadline = message.getCreatedAt().plusMinutes(editDeleteTimeoutMinutes);

        if (LocalDateTime.now().isAfter(deadline)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Đã quá " + editDeleteTimeoutMinutes + " phút, không thể sửa hoặc xóa tin nhắn."
            );
        }

        return message;
    }

    public int getEditDeleteTimeoutMinutes() {
        return editDeleteTimeoutMinutes;
    }

    public void setEditDeleteTimeoutMinutes(int editDeleteTimeoutMinutes) {
        this.editDeleteTimeoutMinutes = editDeleteTimeoutMinutes;
    }
}
