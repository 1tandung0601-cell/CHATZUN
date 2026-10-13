package com.chatapp.service;

import com.chatapp.dto.AddMembersRequest;
import com.chatapp.dto.ConversationResponse;
import com.chatapp.dto.CreateGroupConversationRequest;
import com.chatapp.dto.MemberResponse;
import com.chatapp.entity.Conversation;
import com.chatapp.entity.ConversationMember;
import com.chatapp.entity.ConversationType;
import com.chatapp.entity.MemberRole;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    // Spring tự truyền các Repository vào Service.
    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository,
            MessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    // Lấy danh sách cuộc trò chuyện của người đang đăng nhập.
    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(String username) {

        // Tìm các cuộc trò chuyện mà người dùng tham gia.
        List<ConversationMember> members = memberRepository
                .findByUser_UsernameAndConversation_DeletedAtIsNullOrderByConversation_UpdatedAtDescConversation_IdDesc(
                        username
                );

        // Chuyển entity thành DTO, loại bỏ các cuộc trò chuyện đã bị xóa, sắp xếp theo thời gian hoạt động.
        return members.stream()
                .map(member -> toResponse(member.getConversation(), username))
                .toList();
    }

    // Mở hoặc lấy lại cuộc trò chuyện trực tiếp với một người.
    @Transactional
    public ConversationResponse createDirectConversation(
            String currentUsername,
            String participantUsername
    ) {
        // 1. Tìm người đang đăng nhập.
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        // 2. Tìm người mà client muốn trò chuyện cùng.
        User participant = userRepository.findByUsername(participantUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Participant not found"
                ));

        // 3. Không cho phép trò chuyện với chính mình.
        if (currentUser.getId().equals(participant.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot create a direct conversation with yourself"
            );
        }

        // 4. Nếu đã có cuộc trò chuyện 1-1 thì sử dụng lại.
        var existingConversation =
                conversationRepository.findDirectBetweenUsers(
                        ConversationType.DIRECT,
                        currentUser.getId(),
                        participant.getId()
                );

        if (existingConversation.isPresent()) {
            return toResponse(existingConversation.get(), currentUsername);
        }

        // 5. Tạo cuộc trò chuyện mới.
        Conversation conversation = new Conversation();
        conversation.setConversationType(ConversationType.DIRECT);

        // Gán thời gian để tránh giá trị NULL.
        LocalDateTime now = LocalDateTime.now();
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);

        // Lưu trước để lấy ID cuộc trò chuyện.
        conversation = conversationRepository.save(conversation);

        // 6. Thêm hai người vào cuộc trò chuyện.
        ConversationMember currentMember = new ConversationMember(
                conversation,
                currentUser,
                MemberRole.MEMBER
        );

        ConversationMember participantMember = new ConversationMember(
                conversation,
                participant,
                MemberRole.MEMBER
        );

        memberRepository.save(currentMember);
        memberRepository.save(participantMember);

        // 7. Trả về DTO.
        return toResponse(conversation, currentUsername);
    }

    // Tạo cuộc trò chuyện nhóm mới.
    @Transactional
    public ConversationResponse createGroupConversation(
            String currentUsername,
            CreateGroupConversationRequest request
    ) {
        // 1. Tìm người tạo nhóm
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        // 2. Validate tên nhóm
        String groupName = request.getName() != null ? request.getName().trim() : "";
        if (groupName.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tên nhóm không được để trống"
            );
        }

        // 3. Validate danh sách thành viên
        List<String> rawMembers = request.getMemberUsernames();
        if (rawMembers == null || rawMembers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Danh sách thành viên không được để trống"
            );
        }

        // Loại bỏ trùng, loại bỏ người tạo nếu vô tình được gửi lên
        Set<String> targetUsernames = distinctUsernamesIgnoreCase(rawMembers);
        targetUsernames.removeIf(name -> name.equalsIgnoreCase(currentUsername));

        if (targetUsernames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nhóm phải có ít nhất một thành viên khác ngoài bạn"
            );
        }

        // Tìm các thành viên trong database
        List<User> targetUsers = new ArrayList<>();
        for (String username : targetUsernames) {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy người dùng: " + username
                    ));
            targetUsers.add(user);
        }

        // 4. Tạo cuộc trò chuyện mới
        Conversation conversation = new Conversation();
        conversation.setConversationType(ConversationType.GROUP);
        conversation.setName(groupName);
        conversation.setOwner(currentUser); // Gán người tạo làm chủ nhóm

        LocalDateTime now = LocalDateTime.now();
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);

        conversation = conversationRepository.save(conversation);

        // 5. Thêm người tạo với vai trò ADMIN
        ConversationMember creatorMember = new ConversationMember(
                conversation,
                currentUser,
                MemberRole.ADMIN
        );
        memberRepository.save(creatorMember);

        // 6. Thêm các thành viên với vai trò MEMBER
        for (User user : targetUsers) {
            ConversationMember member = new ConversationMember(
                    conversation,
                    user,
                    MemberRole.MEMBER
            );
            memberRepository.save(member);
        }

        // 7. Trả về DTO phản hồi
        return toResponse(conversation, currentUsername);
    }

    // Kiểm tra một người dùng có phải là chủ nhóm hay không.
    public boolean isGroupOwner(Conversation conversation, User user) {
        if (conversation.getOwner() != null) {
            return conversation.getOwner().getId().equals(user.getId());
        }
        // Fallback: Tìm thành viên có vai trò ADMIN đầu tiên / gia nhập sớm nhất trong nhóm
        return memberRepository.findByConversation_Id(conversation.getId())
                .stream()
                .filter(m -> m.getRole() == MemberRole.ADMIN)
                .min(Comparator.comparing(ConversationMember::getJoinedAt))
                .map(m -> m.getUser().getId().equals(user.getId()))
                .orElse(false);
    }

    // Lấy danh sách thành viên của một cuộc trò chuyện.
    @Transactional(readOnly = true)
    public List<MemberResponse> getGroupMembers(String currentUsername, Long conversationId) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
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

        boolean isMember = memberRepository.existsByConversation_IdAndUser_Id(
                conversationId,
                currentUser.getId()
        );

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không phải thành viên của cuộc trò chuyện này"
            );
        }

        return memberRepository.findByConversation_Id(conversationId).stream()
                .map(m -> new MemberResponse(
                        m.getUser().getId(),
                        m.getUser().getUsername(),
                        m.getRole(),
                        m.getJoinedAt()
                ))
                .toList();
    }

    // Thêm một hoặc nhiều thành viên vào nhóm (cho phép mọi thành viên hiện tại thực hiện).
    @Transactional
    public List<MemberResponse> addMembersToGroup(
            String currentUsername,
            Long conversationId,
            AddMembersRequest request
    ) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
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

        if (conversation.getConversationType() != ConversationType.GROUP) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ có thể thêm thành viên vào cuộc trò chuyện nhóm"
            );
        }

        // Cho phép mọi thành viên hiện tại của nhóm thực hiện thao tác này, không chỉ ADMIN
        boolean isCallerMember = memberRepository.existsByConversation_IdAndUser_Id(
                conversationId,
                currentUser.getId()
        );

        if (!isCallerMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không phải thành viên của nhóm này"
            );
        }

        List<String> rawUsernames = request.getUsernames();
        if (rawUsernames == null || rawUsernames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Danh sách thành viên cần thêm không được để trống"
            );
        }

        // Lọc rỗng, trim, loại trùng
        Set<String> targetUsernames = distinctUsernamesIgnoreCase(rawUsernames);

        if (targetUsernames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Danh sách thành viên cần thêm không được để trống"
            );
        }

        // Lấy danh sách thành viên hiện tại của nhóm
        List<ConversationMember> currentMembers = memberRepository.findByConversation_Id(conversationId);
        Set<String> existingUsernames = currentMembers.stream()
                .map(m -> m.getUser().getUsername().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // Lọc ra các thành viên chưa có trong nhóm
        List<String> toAddUsernames = targetUsernames.stream()
                .filter(u -> !existingUsernames.contains(u.toLowerCase(Locale.ROOT)))
                .toList();

        if (toAddUsernames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tất cả người dùng được chọn đã có trong nhóm"
            );
        }

        // Tìm các user và thêm vào nhóm
        for (String username : toAddUsernames) {
            User targetUser = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy người dùng: " + username
                    ));

            ConversationMember newMember = new ConversationMember(
                    conversation,
                    targetUser,
                    MemberRole.MEMBER
            );
            memberRepository.save(newMember);
        }

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        // Trả về danh sách thành viên cập nhật của nhóm
        return memberRepository.findByConversation_Id(conversationId).stream()
                .map(m -> new MemberResponse(
                        m.getUser().getId(),
                        m.getUser().getUsername(),
                        m.getRole(),
                        m.getJoinedAt()
                ))
                .toList();
    }

    // Xóa mềm nhóm chat (chỉ chủ nhóm mới được phép thực hiện).
    @Transactional
    public void deleteGroupConversation(String currentUsername, Long conversationId) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy cuộc trò chuyện"
                ));

        if (conversation.getDeletedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cuộc trò chuyện này đã bị xóa từ trước"
            );
        }

        if (conversation.getConversationType() != ConversationType.GROUP) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không thể xóa cuộc trò chuyện trực tiếp (DIRECT)"
            );
        }

        // Chỉ chủ nhóm mới được phép xóa nhóm
        if (!isGroupOwner(conversation, currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ chủ nhóm mới có quyền xóa nhóm này"
            );
        }

        conversation.setDeletedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    // Chuyển entity Conversation thành DTO phản hồi.
    private ConversationResponse toResponse(
            Conversation conversation,
            String currentUsername
    ) {
        String participantUsername = null;
        String lastMessage = null;
        Long lastMessageId = null;
        String ownerUsername = null;
        int memberCount = 0;

        List<ConversationMember> members =
                memberRepository.findByConversation_Id(conversation.getId());
        memberCount = members.size();

        // Với phòng DIRECT, tìm username của người còn lại.
        if (conversation.getConversationType() == ConversationType.DIRECT) {
            participantUsername = members.stream()
                    .map(ConversationMember::getUser)
                    .map(User::getUsername)
                    .filter(name -> !name.equals(currentUsername))
                    .findFirst()
                    .orElse(null);
        } else {
            // Với phòng GROUP, xác định username của chủ nhóm
            if (conversation.getOwner() != null) {
                ownerUsername = conversation.getOwner().getUsername();
            } else {
                ownerUsername = members.stream()
                        .filter(m -> m.getRole() == MemberRole.ADMIN)
                        .min(Comparator.comparing(ConversationMember::getJoinedAt))
                        .map(m -> m.getUser().getUsername())
                        .orElse(null);
            }
        }

        // Lấy tin nhắn mới nhất chưa bị xóa, nếu có.
        Message latestMessage = messageRepository
                .findFirstByConversation_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        conversation.getId()
                )
                .orElse(null);

        if (latestMessage != null) {
            lastMessage = latestMessage.getContent();
                        lastMessageId = latestMessage.getId();
        }

        return new ConversationResponse(
                conversation.getId(),
                conversation.getConversationType(),
                conversation.getName(),
                participantUsername,
                lastMessage,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                ownerUsername,
                memberCount,
                lastMessageId
        );
    }

        private Set<String> distinctUsernamesIgnoreCase(List<String> usernames) {
                Map<String, String> distinct = new LinkedHashMap<>();
                usernames.stream()
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(username -> !username.isEmpty())
                                .forEach(username -> distinct.putIfAbsent(
                                                username.toLowerCase(Locale.ROOT),
                                                username
                                ));
                return new LinkedHashSet<>(distinct.values());
        }
}